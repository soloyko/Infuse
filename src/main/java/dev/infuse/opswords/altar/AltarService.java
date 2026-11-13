package dev.infuse.opswords.altar;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Aura;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Weapon altars: a single block (a barrier for collision) drawn with display entities: the 3D
 * altar model, a glowing rune layer tinted with the weapon's colour, the weapon floating above it
 * and its name. Players place their own altar to show off a weapon; admins can set up public
 * shrines whose weapon the first player to reach it claims.
 */
public final class AltarService implements Listener {

    private static final int DEFAULT_TINT = 0xE8C35A;
    private static final double FLOAT_HEIGHT = 1.12;
    private static final float WEAPON_HEIGHT = 1.25f;

    private final OPSwordsPlugin plugin;
    private final File file;
    private final NamespacedKey altarItemKey;
    private final NamespacedKey displayKey;
    private final Map<String, Altar> altars = new LinkedHashMap<>();
    /** Display entities that belong to a live altar (anything else tagged as ours is a leftover). */
    private final java.util.Set<UUID> liveDisplays = new java.util.HashSet<>();
    private ModelBounds models;
    private BukkitTask animator;

    public AltarService(OPSwordsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "altars.yml");
        this.altarItemKey = new NamespacedKey(plugin, "altar");
        this.displayKey = new NamespacedKey(plugin, "altar_display");
    }

    // ---------------------------------------------------------------- lifecycle

    public void start() {
        models = new ModelBounds(plugin);
        load();
        for (Altar altar : altars.values()) {
            if (altar.block.isChunkLoaded()) {
                show(altar);
            }
        }
        animator = Bukkit.getScheduler().runTaskTimer(plugin, this::animate, 2L, 2L);
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
        }
        for (Altar altar : altars.values()) {
            hide(altar);
        }
    }

    // ---------------------------------------------------------------- items

    public ItemStack createAltarItem(int amount) {
        ItemStack item = ItemStack.of(Material.LODESTONE, Math.max(1, Math.min(64, amount)));
        item.editMeta(meta -> {
            meta.itemName(Text.gradient("⛩ Weapon Altar", net.kyori.adventure.text.format.TextColor.color(0xF5D78E),
                    net.kyori.adventure.text.format.TextColor.color(0xB57EDC)).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Text.plain("A shrine for a legendary weapon.", NamedTextColor.GRAY),
                    Component.empty(),
                    Text.plain("▸ Place it, then right-click it with an", NamedTextColor.GRAY),
                    Text.plain("   OP weapon to put it on display.", NamedTextColor.GRAY),
                    Text.plain("▸ Right-click with an empty hand to take it back.", NamedTextColor.GRAY),
                    Text.plain("▸ Sneak + right-click an empty altar to pick it up.", NamedTextColor.GRAY)));
            meta.setItemModel(new NamespacedKey("op_swords", "altar"));
            meta.setRarity(org.bukkit.inventory.ItemRarity.EPIC);
            meta.getPersistentDataContainer().set(altarItemKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public boolean isAltarItem(@Nullable ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(altarItemKey);
    }

    private static ItemStack modelItem(String model, int tint) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("op_swords", model));
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setColors(List.of(Color.fromRGB(tint)));
            meta.setCustomModelDataComponent(cmd);
        });
        return item;
    }

    // ---------------------------------------------------------------- placing and removing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isAltarItem(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        block.setType(Material.BARRIER, false);
        Altar altar = new Altar(block.getLocation(), player.getUniqueId(), player.getName(), null);
        altars.put(altar.key(), altar);
        save();
        show(altar);
        Fx.sound(altar.center(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
        Fx.particle(altar.center().add(0, 0.6, 0), Particle.END_ROD, 20, 0.4, 0.05);
    }

    /** Admin: a public shrine on top of {@code ground} holding a fresh weapon. */
    public boolean createShrine(Block ground, SwordType type) {
        Block block = ground.getRelative(0, 1, 0);
        if (!block.getType().isAir() || altars.containsKey(Altar.key(block.getLocation()))) {
            return false;
        }
        block.setType(Material.BARRIER, false);
        Altar altar = new Altar(block.getLocation(), null, null, plugin.items().create(type));
        altars.put(altar.key(), altar);
        save();
        show(altar);
        Fx.sound(altar.center(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.8f);
        return true;
    }

    /** Admin: refill a shrine with a fresh weapon. Returns false if it isn't an empty shrine. */
    public boolean refill(Block block, SwordType type) {
        Altar altar = altars.get(Altar.key(block.getLocation()));
        if (altar == null || altar.item != null) {
            return false;
        }
        altar.item = plugin.items().create(type);
        save();
        refresh(altar);
        return true;
    }

    public @Nullable Block altarAt(Block block) {
        return altars.containsKey(Altar.key(block.getLocation())) ? block : null;
    }

    /** Removes an altar, dropping its weapon and the altar item. */
    public boolean remove(Block block, boolean dropAltarItem) {
        Altar altar = altars.remove(Altar.key(block.getLocation()));
        if (altar == null) {
            return false;
        }
        hide(altar);
        block.setType(Material.AIR, false);
        World world = block.getWorld();
        if (altar.item != null) {
            world.dropItemNaturally(altar.center(), altar.item);
        }
        if (dropAltarItem && !altar.isShrine()) {
            world.dropItemNaturally(altar.center(), createAltarItem(1));
        }
        Fx.particle(altar.center(), Particle.BLOCK, 30, 0.4, 0.1, Material.POLISHED_DEEPSLATE.createBlockData());
        Fx.sound(altar.center(), Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1f, 0.8f);
        save();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Altar altar = altars.get(Altar.key(event.getBlock().getLocation()));
        if (altar == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.hasPermission("opswords.admin") || player.getUniqueId().equals(altar.owner)) {
            remove(event.getBlock(), player.getGameMode() != GameMode.CREATIVE || !altar.isShrine());
        }
    }

    public Collection<Location> locations() {
        List<Location> out = new ArrayList<>();
        altars.values().forEach(a -> out.add(a.block.clone()));
        return out;
    }

    // ---------------------------------------------------------------- using

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Altar altar = altars.get(Altar.key(event.getClickedBlock().getLocation()));
        if (altar == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean mayManage = altar.isShrine() ? player.hasPermission("opswords.admin")
                : player.getUniqueId().equals(altar.owner) || player.hasPermission("opswords.admin");

        if (altar.item != null) {
            if (altar.isShrine()) {
                claim(player, altar);
            } else if (mayManage && hand.isEmpty()) {
                player.getInventory().setItemInMainHand(altar.item);
                altar.item = null;
                save();
                refresh(altar);
                Fx.sound(altar.center(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1f);
            } else {
                SwordType type = plugin.items().typeOf(altar.item);
                Text.send(player, Component.text("This is " + altar.ownerName + "'s altar", NamedTextColor.GRAY)
                        .append(type == null ? Component.empty() : Component.text(", holding ", NamedTextColor.GRAY)
                                .append(Text.sword(type).hoverEvent(altar.item.asHoverEvent())))
                        .append(Component.text(".", NamedTextColor.GRAY)));
            }
            return;
        }
        if (plugin.items().isSword(hand)) {
            if (!mayManage) {
                Text.send(player, "<red>Only " + (altar.isShrine() ? "admins" : altar.ownerName) + " can use this altar.");
                return;
            }
            ItemStack placed = hand.clone();
            placed.setAmount(1);
            hand.setAmount(hand.getAmount() - 1);
            altar.item = placed;
            save();
            refresh(altar);
            Fx.sound(altar.center(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.2f);
            Fx.sound(altar.center(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
            return;
        }
        if (player.isSneaking() && hand.isEmpty() && mayManage && !altar.isShrine()) {
            remove(event.getClickedBlock(), false);
            player.getInventory().addItem(createAltarItem(1)).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            return;
        }
        Text.send(player, "<gray>Right-click with an OP weapon to place it on the altar.");
    }

    private void claim(Player player, Altar altar) {
        ItemStack weapon = altar.item;
        SwordType type = plugin.items().typeOf(weapon);
        if (weapon == null || type == null) {
            return;
        }
        altar.item = null;
        save();
        refresh(altar);
        player.getInventory().addItem(weapon).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        if (plugin.settings().uniqueSwords && !plugin.forges().isForged(type)) {
            plugin.forges().record(type, player);
        }
        Location at = altar.center().add(0, FLOAT_HEIGHT, 0);
        Fx.burst(at, Particle.END_ROD, 60, 0.3);
        Fx.sound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.broadcast(Text.PREFIX.append(player.displayName())
                .append(Component.text(" claimed ", NamedTextColor.GRAY))
                .append(Text.sword(type).hoverEvent(weapon.asHoverEvent()))
                .append(Component.text(" from its shrine!", NamedTextColor.GRAY)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 0.4f, 1.4f);
        }
    }

    // ---------------------------------------------------------------- visuals

    private void show(Altar altar) {
        hide(altar);
        World world = altar.block.getWorld();
        if (altar.block.getBlock().getType() != Material.BARRIER) {
            altar.block.getBlock().setType(Material.BARRIER, false);
        }
        SwordType type = plugin.items().typeOf(altar.item);
        int tint = type == null ? DEFAULT_TINT : type.color().value();
        Location center = altar.center();

        altar.visuals.add(world.spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(modelItem("altar_base", tint));
            prepare(d);
        }));
        altar.visuals.add(world.spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(modelItem("altar_glow", tint));
            d.setBrightness(new Display.Brightness(15, 15));
            prepare(d);
        }));
        if (altar.item != null && type != null) {
            ItemDisplay weapon = world.spawn(center.clone().add(0, FLOAT_HEIGHT, 0), ItemDisplay.class, d -> {
                d.setItemStack(altar.item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(models.onAltar(type, WEAPON_HEIGHT, 0));
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(tint));
                d.setTeleportDuration(20);
                prepare(d);
            });
            altar.weaponDisplay = weapon;
            altar.visuals.add(weapon);
            altar.visuals.add(world.spawn(center.clone().add(0, FLOAT_HEIGHT + WEAPON_HEIGHT / 2 + 0.3, 0), TextDisplay.class, d -> {
                d.text(Text.sword(type).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(altar.isShrine()
                                ? Text.plain("✦ Right-click to claim ✦", NamedTextColor.GOLD)
                                : Text.plain(altar.ownerName + "'s altar", NamedTextColor.GRAY)));
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(HOLOGRAM_SCALE),
                        new Quaternionf()));
                prepare(d);
                d.setViewRange(0.4f);
            }));
        } else {
            altar.weaponDisplay = null;
        }
    }

    private static final float HOLOGRAM_SCALE = 0.7f;

    private void prepare(Display d) {
        liveDisplays.add(d.getUniqueId());
        d.setPersistent(false);
        d.setViewRange(1.5f);
        d.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void hide(Altar altar) {
        for (Display d : altar.visuals) {
            liveDisplays.remove(d.getUniqueId());
            if (d.isValid()) {
                d.remove();
            }
        }
        altar.visuals.clear();
        altar.weaponDisplay = null;
    }

    private void refresh(Altar altar) {
        if (altar.block.isChunkLoaded()) {
            show(altar);
        }
    }

    /** Spins and bobs the floating weapons, with their themed particles. */
    private void animate() {
        for (Altar altar : altars.values()) {
            Display weapon = altar.weaponDisplay;
            if (weapon == null || !weapon.isValid()) {
                continue;
            }
            altar.spin++;
            SwordType type = plugin.items().typeOf(altar.item);
            if (type == null) {
                continue;
            }
            if (altar.spin % 10 == 1) {
                // quarter turn every second, interpolated by the client so it looks continuous
                float angle = (float) (altar.spin / 10 * Math.PI / 2);
                weapon.setInterpolationDelay(0);
                weapon.setInterpolationDuration(20);
                weapon.setTransformation(models.onAltar(type, WEAPON_HEIGHT, angle));
                double bob = (altar.spin / 10) % 2 == 0 ? 0.08 : -0.08;
                weapon.teleport(altar.center().add(0, FLOAT_HEIGHT + bob, 0));
            }
            if (altar.spin % 5 == 0) {
                Aura.ambient(altar.center().add(0, FLOAT_HEIGHT, 0), type);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Altar altar : altars.values()) {
            if (inChunk(altar, event.getChunk())) {
                show(altar);
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Altar altar : altars.values()) {
            if (inChunk(altar, event.getChunk())) {
                hide(altar);
            }
        }
    }

    /**
     * Deletes stray altar displays. They are never saved, so this only matters if something else
     * persisted one; displays of live altars (which can be in the event too) are kept.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(displayKey) && !liveDisplays.contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private static boolean inChunk(Altar altar, Chunk chunk) {
        return altar.block.getWorld() == chunk.getWorld()
                && (altar.block.getBlockX() >> 4) == chunk.getX() && (altar.block.getBlockZ() >> 4) == chunk.getZ();
    }

    // ---------------------------------------------------------------- storage

    private void load() {
        altars.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection list = data.getConfigurationSection("altars");
        if (list == null) {
            return;
        }
        for (String key : list.getKeys(false)) {
            ConfigurationSection s = list.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            World world = Bukkit.getWorld(s.getString("world", ""));
            if (world == null) {
                plugin.getLogger().warning("Altar " + key + " is in a world that isn't loaded; skipping it.");
                continue;
            }
            Location loc = new Location(world, s.getInt("x"), s.getInt("y"), s.getInt("z"));
            String ownerId = s.getString("owner");
            ItemStack item = null;
            String encoded = s.getString("item");
            if (encoded != null && !encoded.isEmpty()) {
                try {
                    item = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not read the weapon on altar " + key, e);
                }
            }
            Altar altar = new Altar(loc, ownerId == null ? null : UUID.fromString(ownerId), s.getString("owner-name"), item);
            altars.put(altar.key(), altar);
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.options().setHeader(List.of("Weapon altars. Managed by the plugin; use /opswords altar commands."));
        int i = 0;
        for (Altar altar : altars.values()) {
            String path = "altars." + (i++);
            data.set(path + ".world", altar.block.getWorld().getName());
            data.set(path + ".x", altar.block.getBlockX());
            data.set(path + ".y", altar.block.getBlockY());
            data.set(path + ".z", altar.block.getBlockZ());
            data.set(path + ".owner", altar.owner == null ? null : altar.owner.toString());
            data.set(path + ".owner-name", altar.ownerName);
            data.set(path + ".item", altar.item == null ? null : Base64.getEncoder().encodeToString(altar.item.serializeAsBytes()));
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save altars.yml", e);
        }
    }
}
