package dev.infuse.opswords.altar;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.Settings;
import dev.infuse.opswords.ability.Aura;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.manager.ForgeRegistry;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Weapon altars: one block (a barrier for collision) drawn with display entities: the 3D altar
 * model, a glowing rune layer and halo tinted with the weapon's colour, the weapon floating above
 * it, its recipe circling above the weapon and its name.
 *
 * <p>Only admins place altars and choose which weapon each one forges. Players forge that weapon
 * by right-clicking the altar with the recipe's ingredients in their inventory; there is no
 * crafting grid to fill in.
 */
public final class AltarService implements Listener {

    private static final String ADMIN = "opswords.admin";
    private static final int DEFAULT_TINT = 0xE8C35A;
    private static final double FLOAT_HEIGHT = 1.12;
    private static final float WEAPON_HEIGHT = 1.25f;
    private static final double HALO_HEIGHT = 0.56;
    private static final double RING_HEIGHT = FLOAT_HEIGHT + 0.9;
    private static final double RING_RADIUS = 0.62;
    private static final float ICON_SCALE = 0.3f;
    private static final double HOLOGRAM_HEIGHT = RING_HEIGHT + 0.3;
    private static final float HOLOGRAM_SCALE = 0.62f;
    /** Animation steps (2 ticks each) for one turn of the ingredient ring. */
    private static final int RING_PERIOD = 90;

    private final OPSwordsPlugin plugin;
    private final File file;
    private final NamespacedKey altarItemKey;
    private final NamespacedKey displayKey;
    private final Map<String, Altar> altars = new LinkedHashMap<>();
    /** Display entities that belong to a live altar (anything else tagged as ours is a leftover). */
    private final Set<UUID> liveDisplays = new HashSet<>();
    /** Weapons that sat on players' own altars before 1.5, waiting to be given back. */
    private final Map<UUID, List<ItemStack>> returns = new HashMap<>();
    /** Players currently shown an altar checklist in the action bar, until this time (ms). */
    private final Map<UUID, Long> checklistUntil = new HashMap<>();
    /** Holding right-click repeats the click; ignore an altar's clicks until this time (ms). */
    private final Map<UUID, Long> clickLock = new HashMap<>();
    private ModelBounds models;
    private BukkitTask animator;
    private long steps;

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
        Bukkit.getOnlinePlayers().forEach(this::giveReturns);
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

    /** Re-draws every altar, e.g. after a config reload changed a recipe. */
    public void refreshAll() {
        altars.values().forEach(this::refresh);
    }

    // ---------------------------------------------------------------- items

    public ItemStack createAltarItem(int amount) {
        ItemStack item = ItemStack.of(Material.LODESTONE, Math.max(1, Math.min(64, amount)));
        item.editMeta(meta -> {
            meta.itemName(Text.gradient("⛩ Weapon Altar", TextColor.color(0xF5D78E), TextColor.color(0xB57EDC))
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Text.caps("Admin item", NamedTextColor.RED),
                    Component.empty(),
                    Text.plain("A forge for one legendary weapon.", NamedTextColor.GRAY),
                    Component.empty(),
                    Text.plain("▸ Place it, then right-click it with an", NamedTextColor.GRAY),
                    Text.plain("   OP weapon to choose what it forges.", NamedTextColor.GRAY),
                    Text.plain("▸ Players forge that weapon by right-clicking", NamedTextColor.GRAY),
                    Text.plain("   with the ingredients in their inventory.", NamedTextColor.GRAY),
                    Text.plain("▸ Sneak + right-click with an empty hand", NamedTextColor.GRAY),
                    Text.plain("   to pick it back up.", NamedTextColor.GRAY)));
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

    // ---------------------------------------------------------------- recipes

    /** What forging a weapon at an altar takes: every ingredient of its recipe, with counts. */
    public Map<Material, Integer> ingredients(SwordType type) {
        Settings.Recipe recipe = plugin.settings().sword(type).recipe();
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (String row : recipe.shape()) {
            for (char c : row.toCharArray()) {
                Material mat = recipe.ingredients().get(c);
                if (mat != null) {
                    out.merge(mat, 1, Integer::sum);
                }
            }
        }
        return out;
    }

    /** Whether this weapon can be forged at all on this server (null) or why not. */
    private @Nullable String unavailable(SwordType type) {
        Settings settings = plugin.settings();
        Settings.Sword sword = settings.sword(type);
        if (!settings.craftingEnabled || !sword.enabled() || !sword.recipe().enabled() || ingredients(type).isEmpty()) {
            return "can't be forged on this server";
        }
        if (settings.uniqueSwords) {
            ForgeRegistry.Forged forged = plugin.forges().get(type);
            if (forged != null) {
                return "has already been forged by " + forged.name();
            }
        }
        return null;
    }

    private boolean usable(ItemStack stack, Material material) {
        return stack != null && stack.getType() == material && !plugin.items().isSword(stack) && !isAltarItem(stack);
    }

    private int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (usable(stack, material)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void take(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (!usable(stack, material)) {
                continue;
            }
            int used = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - used);
            amount -= used;
            contents[i] = stack.getAmount() <= 0 ? null : stack;
        }
        inventory.setStorageContents(contents);
    }

    private static Component itemName(Material material) {
        return Component.translatable(material.translationKey());
    }

    // ---------------------------------------------------------------- placing and removing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isAltarItem(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN) || !plugin.settings().altarsEnabled) {
            event.setCancelled(true);
            Text.send(player, "<red>Only admins can place weapon altars.");
            return;
        }
        Block block = event.getBlockPlaced();
        block.setType(Material.BARRIER, false);
        Altar altar = new Altar(block.getLocation(), null);
        altars.put(altar.key(), altar);
        save();
        show(altar);
        Fx.sound(altar.center(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.8f);
        Fx.particle(altar.center().add(0, 0.6, 0), Particle.END_ROD, 20, 0.4, 0.05);
        Text.send(player, "<gray>Right-click the altar with an OP weapon to choose what it forges.");
    }

    /**
     * Admin: makes the altar at {@code block} forge {@code type}, or places a new altar on top of
     * {@code block} if it isn't one. Returns false if there is no room for a new altar.
     */
    public boolean setWeapon(Block block, SwordType type) {
        Altar altar = altars.get(Altar.key(block.getLocation()));
        if (altar == null) {
            Block above = block.getRelative(0, 1, 0);
            if (!above.getType().isAir() || altars.containsKey(Altar.key(above.getLocation()))) {
                return false;
            }
            above.setType(Material.BARRIER, false);
            altar = new Altar(above.getLocation(), null);
            altars.put(altar.key(), altar);
        }
        altar.type = type;
        save();
        refresh(altar);
        Fx.sound(altar.center(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1f);
        Fx.sound(altar.center(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.2f);
        return true;
    }

    public @Nullable Block altarAt(Block block) {
        return altars.containsKey(Altar.key(block.getLocation())) ? block : null;
    }

    /** Removes an altar, optionally dropping the altar item. */
    public boolean remove(Block block, boolean dropAltarItem) {
        Altar altar = altars.remove(Altar.key(block.getLocation()));
        if (altar == null) {
            return false;
        }
        hide(altar);
        block.setType(Material.AIR, false);
        if (dropAltarItem) {
            block.getWorld().dropItemNaturally(altar.center(), createAltarItem(1));
        }
        Fx.particle(altar.center(), Particle.BLOCK, 30, 0.4, 0.1, Material.POLISHED_DEEPSLATE.createBlockData());
        Fx.sound(altar.center(), Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1f, 0.8f);
        save();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!altars.containsKey(Altar.key(event.getBlock().getLocation()))) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.hasPermission(ADMIN)) {
            remove(event.getBlock(), player.getGameMode() != GameMode.CREATIVE);
        }
    }

    public Collection<Location> locations() {
        List<Location> out = new ArrayList<>();
        altars.values().forEach(a -> out.add(a.block.clone()));
        return out;
    }

    /** The weapon each altar forges, for /opswords altar list. */
    public Map<Location, SwordType> weapons() {
        Map<Location, SwordType> out = new LinkedHashMap<>();
        altars.values().forEach(a -> out.put(a.block.clone(), a.type));
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
        long now = System.currentTimeMillis();
        if (clickLock.getOrDefault(player.getUniqueId(), 0L) > now) {
            return;
        }
        clickLock.put(player.getUniqueId(), now + 400);
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean admin = player.hasPermission(ADMIN);

        if (admin && plugin.items().isSword(hand)) {
            SwordType type = plugin.items().typeOf(hand);
            if (type != null && type != altar.type) {
                setWeapon(event.getClickedBlock(), type);
                Text.send(player, Component.text("This altar now forges ", NamedTextColor.GRAY)
                        .append(Text.sword(type)).append(Component.text(".", NamedTextColor.GRAY)));
                return;
            }
        }
        if (admin && player.isSneaking() && hand.isEmpty()) {
            remove(event.getClickedBlock(), false);
            player.getInventory().addItem(createAltarItem(1)).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            return;
        }
        if (altar.type == null) {
            Text.send(player, admin ? "<gray>Right-click with an OP weapon to choose what this altar forges."
                    : "<gray>This altar is dormant.");
            return;
        }
        forge(player, altar);
    }

    private void forge(Player player, Altar altar) {
        SwordType type = altar.type;
        if (type == null) {
            return;
        }
        if (!player.hasPermission("opswords.craft")) {
            Text.send(player, "<red>You can't forge weapons.");
            return;
        }
        String reason = unavailable(type);
        if (reason != null) {
            Text.send(player, Text.sword(type).append(Component.text(" " + reason + ".", NamedTextColor.RED)));
            deny(player, altar);
            return;
        }
        Map<Material, Integer> need = ingredients(type);
        PlayerInventory inventory = player.getInventory();
        boolean ready = true;
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : need.entrySet()) {
            int have = Math.min(count(inventory, e.getKey()), e.getValue());
            boolean ok = have >= e.getValue();
            ready &= ok;
            lines.add(Component.text(ok ? "  ✔ " : "  ✘ ", ok ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .append(Component.text(e.getValue() + "× ", NamedTextColor.WHITE))
                    .append(itemName(e.getKey()).color(ok ? NamedTextColor.GRAY : NamedTextColor.WHITE))
                    .append(Component.text("  " + have + "/" + e.getValue(), ok ? NamedTextColor.DARK_GRAY : NamedTextColor.RED)));
        }
        if (!ready) {
            Text.send(player, Component.text("To forge ", NamedTextColor.GRAY).append(Text.sword(type))
                    .append(Component.text(" you need, in your inventory:", NamedTextColor.GRAY)));
            lines.forEach(player::sendMessage);
            deny(player, altar);
            return;
        }
        need.forEach((material, amount) -> take(inventory, material, amount));
        ItemStack weapon = plugin.items().createForged(type, player.getName());
        inventory.addItem(weapon).values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        if (plugin.settings().uniqueSwords) {
            plugin.forges().record(type, player);
            refresh(altar);
        }
        plugin.recipes().announce(player, type, weapon);
        celebrate(player, altar, type);
        clickLock.put(player.getUniqueId(), System.currentTimeMillis() + 1500);
    }

    private void deny(Player player, Altar altar) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
        Fx.particle(altar.center().add(0, 0.7, 0), Particle.SMOKE, 12, 0.3, 0.01);
    }

    /** The forge effect: the recipe circling the weapon collapses into it in a burst of light. */
    private void celebrate(Player player, Altar altar, SwordType type) {
        Location weaponAt = altar.center().add(0, FLOAT_HEIGHT, 0);
        int tint = type.color().value();
        altar.hiddenUntil = altar.spin + 25;
        for (Altar.Ingredient ingredient : altar.ingredients) {
            shrink(ingredient.icon(), 0f, 8);
            if (ingredient.label() != null) {
                shrink(ingredient.label(), 0f, 4);
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Fx.burst(weaponAt, Particle.END_ROD, 50, 0.25);
            Fx.particle(weaponAt, Particle.DUST, 40, 0.5, 0, Fx.dust(tint, 1.6f));
            Fx.particle(weaponAt, Particle.FLASH, 1, 0, 0, Color.WHITE);
            Fx.sound(weaponAt, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
            Fx.sound(weaponAt, Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.8f);
        }, 8L);
        for (int i = 0; i < 8; i++) {
            int step = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> Fx.ring(altar.center().add(0, 0.6 + step * 0.18, 0),
                    0.9 - step * 0.08, 14, p -> Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(tint, 1.1f))), i);
        }
        Fx.sound(altar.center(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.7f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    private static void shrink(Display display, float scale, int ticks) {
        if (!display.isValid()) {
            return;
        }
        Transformation t = display.getTransformation();
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(new Transformation(t.getTranslation(), t.getLeftRotation(),
                new Vector3f(scale), t.getRightRotation()));
    }

    // ---------------------------------------------------------------- visuals

    private void show(Altar altar) {
        hide(altar);
        World world = altar.block.getWorld();
        if (altar.block.getBlock().getType() != Material.BARRIER) {
            altar.block.getBlock().setType(Material.BARRIER, false);
        }
        SwordType type = altar.type;
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
        ItemDisplay halo = world.spawn(center.clone().add(0, HALO_HEIGHT, 0), ItemDisplay.class, d -> {
            d.setItemStack(modelItem("altar_halo", tint));
            d.setBrightness(new Display.Brightness(15, 15));
            prepare(d);
        });
        altar.halo = halo;
        altar.visuals.add(halo);
        if (type == null) {
            altar.weaponDisplay = null;
            return;
        }
        ItemDisplay weapon = world.spawn(center.clone().add(0, FLOAT_HEIGHT, 0), ItemDisplay.class, d -> {
            d.setItemStack(plugin.items().create(type));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(models.onAltar(type, WEAPON_HEIGHT, 0));
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(tint));
            d.setTeleportDuration(20);
            prepare(d);
        });
        altar.weaponDisplay = weapon;
        altar.visuals.add(weapon);

        String reason = unavailable(type);
        if (reason == null) {
            Map<Material, Integer> need = ingredients(type);
            int i = 0;
            for (Map.Entry<Material, Integer> e : need.entrySet()) {
                Location at = ringPoint(altar, i, need.size());
                ItemDisplay icon = world.spawn(at, ItemDisplay.class, d -> {
                    d.setItemStack(ItemStack.of(e.getKey()));
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setTransformation(scaled(ICON_SCALE));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTeleportDuration(2);
                    prepare(d);
                    d.setViewRange(0.5f);
                });
                altar.visuals.add(icon);
                TextDisplay label = null;
                if (e.getValue() > 1) {
                    label = world.spawn(at.clone().add(0, -0.24, 0), TextDisplay.class, d -> {
                        d.text(Component.text("×" + e.getValue(), NamedTextColor.WHITE));
                        hologram(d, 0.42f);
                        d.setTeleportDuration(2);
                        d.setViewRange(0.4f);
                    });
                    altar.visuals.add(label);
                }
                altar.ingredients.add(new Altar.Ingredient(icon, label));
                i++;
            }
        }
        Component subtitle = reason == null
                ? Text.caps("✦ right-click to forge ✦", NamedTextColor.GOLD)
                : reason.startsWith("has already")
                ? Text.caps("forged by ", NamedTextColor.GRAY).append(Component.text(
                plugin.forges().get(type) == null ? "?" : plugin.forges().get(type).name(), NamedTextColor.WHITE))
                : Text.caps("cannot be forged here", NamedTextColor.RED);
        altar.visuals.add(world.spawn(center.clone().add(0, reason == null ? HOLOGRAM_HEIGHT : RING_HEIGHT, 0),
                TextDisplay.class, d -> {
                    d.text(Text.sword(type).decorate(TextDecoration.BOLD)
                            .append(Component.newline())
                            .append(Text.caps(type.title(), NamedTextColor.GRAY))
                            .append(Component.newline())
                            .append(subtitle));
                    hologram(d, HOLOGRAM_SCALE);
                    d.setViewRange(0.5f);
                }));
    }

    private void hologram(TextDisplay d, float scale) {
        d.setBillboard(Display.Billboard.CENTER);
        d.setShadowed(true);
        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        d.setAlignment(TextDisplay.TextAlignment.CENTER);
        d.setTransformation(scaled(scale));
        prepare(d);
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    private Location ringPoint(Altar altar, int index, int count) {
        double angle = (steps % RING_PERIOD) * Math.PI * 2 / RING_PERIOD + index * Math.PI * 2 / Math.max(1, count);
        return altar.center().add(Math.cos(angle) * RING_RADIUS, RING_HEIGHT, Math.sin(angle) * RING_RADIUS);
    }

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
        altar.ingredients.clear();
        altar.weaponDisplay = null;
        altar.halo = null;
    }

    private void refresh(Altar altar) {
        if (altar.block.isChunkLoaded()) {
            show(altar);
        }
    }

    /** Spins the weapon and halo, bobs the weapon and circles the recipe around above it. */
    private void animate() {
        steps++;
        for (Altar altar : altars.values()) {
            if (altar.visuals.isEmpty()) {
                continue;
            }
            altar.spin++;
            if (altar.spin % 10 == 1) {
                // a quarter turn every second, interpolated by the client so it looks continuous
                float angle = (float) (altar.spin / 10 * Math.PI / 2);
                Display halo = altar.halo;
                if (halo != null && halo.isValid()) {
                    halo.setInterpolationDelay(0);
                    halo.setInterpolationDuration(20);
                    halo.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY(-angle / 2),
                            new Vector3f(1), new Quaternionf()));
                }
                Display weapon = altar.weaponDisplay;
                if (weapon != null && weapon.isValid() && altar.type != null) {
                    weapon.setInterpolationDelay(0);
                    weapon.setInterpolationDuration(20);
                    weapon.setTransformation(models.onAltar(altar.type, WEAPON_HEIGHT, angle));
                    double bob = (altar.spin / 10) % 2 == 0 ? 0.08 : -0.08;
                    weapon.teleport(altar.center().add(0, FLOAT_HEIGHT + bob, 0));
                }
            }
            if (altar.spin == altar.hiddenUntil) {
                for (Altar.Ingredient ingredient : altar.ingredients) {
                    shrink(ingredient.icon(), ICON_SCALE, 10);
                    if (ingredient.label() != null) {
                        shrink(ingredient.label(), 0.42f, 10);
                    }
                }
            }
            int count = altar.ingredients.size();
            for (int i = 0; i < count; i++) {
                Altar.Ingredient ingredient = altar.ingredients.get(i);
                Location at = ringPoint(altar, i, count);
                if (ingredient.icon().isValid()) {
                    ingredient.icon().teleport(at);
                }
                if (ingredient.label() != null && ingredient.label().isValid()) {
                    ingredient.label().teleport(at.clone().add(0, -0.24, 0));
                }
            }
            if (altar.type != null && altar.spin % 5 == 0) {
                Aura.ambient(altar.center().add(0, FLOAT_HEIGHT, 0), altar.type);
            }
        }
        if (steps % 5 == 0) {
            showChecklists();
        }
    }

    /** True while the player's action bar shows an altar checklist (the weapon HUD waits). */
    public boolean showingChecklist(Player player) {
        Long until = checklistUntil.get(player.getUniqueId());
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            checklistUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /** Players looking at an altar see what they have and what they still need in the action bar. */
    private void showChecklists() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Block target = player.getTargetBlockExact(5);
            if (target == null) {
                continue;
            }
            Altar altar = altars.get(Altar.key(target.getLocation()));
            if (altar == null || altar.type == null || unavailable(altar.type) != null) {
                continue;
            }
            Component bar = Text.caps("still needed: ", NamedTextColor.GRAY);
            boolean first = true;
            boolean ready = true;
            int done = 0;
            int shown = 0;
            Map<Material, Integer> need = ingredients(altar.type);
            for (Map.Entry<Material, Integer> e : need.entrySet()) {
                int have = Math.min(count(player.getInventory(), e.getKey()), e.getValue());
                if (have >= e.getValue()) {
                    done++;
                    continue;
                }
                ready = false;
                if (++shown > 3) {
                    continue;
                }
                if (!first) {
                    bar = bar.append(Component.text(" · ", NamedTextColor.DARK_GRAY));
                }
                first = false;
                bar = bar.append(Component.text((e.getValue() - have) + "× ", NamedTextColor.RED))
                        .append(itemName(e.getKey()).color(NamedTextColor.WHITE));
            }
            if (shown > 3) {
                bar = bar.append(Component.text(" · +" + (shown - 3) + " more", NamedTextColor.GRAY));
            }
            bar = bar.append(Component.text("   " + done + "/" + need.size(), NamedTextColor.DARK_GRAY));
            player.sendActionBar(ready
                    ? Text.caps("✔ ready · right-click to forge ", NamedTextColor.GREEN).append(Text.sword(altar.type))
                    : bar);
            checklistUntil.put(player.getUniqueId(), System.currentTimeMillis() + 800);
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

    // ---------------------------------------------------------------- returns (players' altars from 1.4)

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveReturns(event.getPlayer());
    }

    private void giveReturns(Player player) {
        List<ItemStack> items = returns.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ItemStack item : items) {
            player.getInventory().addItem(item).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        Text.send(player, "<gray>Altars are now forges run by admins, so the weapon"
                + (items.size() == 1 ? "" : "s") + " you had on display " + (items.size() == 1 ? "was" : "were")
                + " returned to you.");
        save();
    }

    // ---------------------------------------------------------------- storage

    private void load() {
        altars.clear();
        returns.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection list = data.getConfigurationSection("altars");
        if (list != null) {
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
                SwordType type = SwordType.byId(s.getString("weapon", ""));
                // 1.4 stored the weapon item itself, and players could own altars
                ItemStack old = decode(s.getString("item"), key);
                if (old != null) {
                    if (type == null) {
                        type = plugin.items().typeOf(old);
                    }
                    String owner = s.getString("owner");
                    if (owner != null) {
                        returns.computeIfAbsent(UUID.fromString(owner), k -> new ArrayList<>()).add(old);
                    }
                }
                Altar altar = new Altar(loc, type);
                altars.put(altar.key(), altar);
            }
        }
        ConfigurationSection pending = data.getConfigurationSection("returns");
        if (pending != null) {
            for (String owner : pending.getKeys(false)) {
                for (String encoded : pending.getStringList(owner)) {
                    ItemStack item = decode(encoded, "returns." + owner);
                    if (item != null) {
                        returns.computeIfAbsent(UUID.fromString(owner), k -> new ArrayList<>()).add(item);
                    }
                }
            }
        }
        if (data.isConfigurationSection("altars") && list != null && list.getKeys(false).stream()
                .anyMatch(k -> list.isSet(k + ".item"))) {
            save(); // rewrite in the new format so returned weapons can't be handed out twice
        }
    }

    private @Nullable ItemStack decode(@Nullable String encoded, String where) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the item in altars.yml at " + where, e);
            return null;
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
            data.set(path + ".weapon", altar.type == null ? null : altar.type.id());
        }
        for (Map.Entry<UUID, List<ItemStack>> e : returns.entrySet()) {
            data.set("returns." + e.getKey(), e.getValue().stream()
                    .map(item -> Base64.getEncoder().encodeToString(item.serializeAsBytes())).toList());
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save altars.yml", e);
        }
    }
}
