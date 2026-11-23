package dev.blackclover.smp.game;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Altar displays, grimoire crafting and library protection. */
public final class AltarService implements Listener {

    private final BlackCloverPlugin plugin;
    private final GrimoireService grimoires;
    private final Map<GrimoireType, UUID> displays = new EnumMap<>(GrimoireType.class);

    public AltarService(BlackCloverPlugin plugin, GrimoireService grimoires) {
        this.plugin = plugin;
        this.grimoires = grimoires;
        grimoires.onReturnedHome(this::refresh);
    }

    private GameState game() {
        return plugin.game();
    }

    public void startTasks() {
        for (Library lib : game().libraries().values()) {
            World w = plugin.getServer().getWorld(lib.world());
            if (w != null && w.isChunkLoaded(lib.altarX() >> 4, lib.altarZ() >> 4)) {
                spawnDisplay(lib);
            }
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::ambientParticles, 40L, 20L);
    }

    // ---------------------------------------------------------------- displays

    private boolean active(GrimoireType type) {
        return plugin.registry().get(type).status() == GrimoireStatus.LIBRARY;
    }

    private String recipeText() {
        return plugin.settings().recipe().entrySet().stream()
                .map(e -> e.getValue() + " " + pretty(e.getKey()))
                .collect(Collectors.joining(", "));
    }

    private static String pretty(Material m) {
        String[] words = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private Component displayText(GrimoireType type) {
        if (active(type)) {
            return Msg.mm("<c><b><name> Grimoire</b></c>\n<gray>Right-click the altar carrying:\n<white><recipe>",
                    Msg.color("c", type.rgb()), Msg.arg("name", type.displayName()), Msg.arg("recipe", recipeText()));
        }
        return Msg.mm("<c><name> Grimoire</c>\n<dark_gray>This grimoire has already chosen its master",
                Msg.color("c", type.rgb()), Msg.arg("name", type.displayName()));
    }

    private void spawnDisplay(Library lib) {
        World w = plugin.getServer().getWorld(lib.world());
        if (w == null) {
            return;
        }
        UUID existing = displays.get(lib.type());
        if (existing != null) {
            Entity e = plugin.getServer().getEntity(existing);
            if (e != null && e.isValid()) {
                return;
            }
        }
        Location at = new Location(w, lib.altarX() + 0.5, lib.altarY() + 1.9, lib.altarZ() + 0.5);
        TextDisplay display = w.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(0.3f);
            d.setShadowed(true);
            d.text(displayText(lib.type()));
        });
        displays.put(lib.type(), display.getUniqueId());
    }

    public void refresh(GrimoireType type) {
        UUID id = displays.get(type);
        if (id != null && plugin.getServer().getEntity(id) instanceof TextDisplay d) {
            d.text(displayText(type));
        }
    }

    public void removeAllDisplays() {
        for (UUID id : displays.values()) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        displays.clear();
    }

    /** Spawns displays for a newly built library. */
    public void onLibraryBuilt(Library lib) {
        spawnDisplay(lib);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk c = event.getChunk();
        for (Library lib : game().libraries().values()) {
            if (lib.world().equals(c.getWorld().getName()) && lib.altarX() >> 4 == c.getX() && lib.altarZ() >> 4 == c.getZ()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> spawnDisplay(lib));
            }
        }
    }

    private void ambientParticles() {
        for (Library lib : game().libraries().values()) {
            if (!active(lib.type())) {
                continue;
            }
            World w = plugin.getServer().getWorld(lib.world());
            if (w == null || !w.isChunkLoaded(lib.altarX() >> 4, lib.altarZ() >> 4)) {
                continue;
            }
            Location at = new Location(w, lib.altarX() + 0.5, lib.altarY() + 1.2, lib.altarZ() + 0.5);
            w.spawnParticle(Particle.ENCHANT, at, 25, 0.6, 0.6, 0.6, 0.6);
        }
    }

    // ---------------------------------------------------------------- crafting

    public Optional<Library> libraryAt(Block b) {
        String w = b.getWorld().getName();
        for (Library lib : game().libraries().values()) {
            if (lib.contains(w, b.getX(), b.getY(), b.getZ())) {
                return Optional.of(lib);
            }
        }
        return Optional.empty();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAltarClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block b = event.getClickedBlock();
        if (b.getType() != Material.LECTERN) {
            return;
        }
        Optional<Library> lib = libraryAt(b).filter(l -> l.isAltar(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
        if (lib.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        craft(event.getPlayer(), lib.get().type());
    }

    private void craft(Player player, GrimoireType type) {
        if (!active(type)) {
            Msg.send(player, "<dark_gray>This grimoire has already chosen its master.");
            return;
        }
        if (grimoires.carried(player).isPresent()) {
            Msg.send(player, "<red>You already carry a grimoire. A mage can only hold one.");
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<Material, Integer> e : plugin.settings().recipe().entrySet()) {
            if (!inv.containsAtLeast(new ItemStack(e.getKey()), e.getValue())) {
                Msg.send(player, "<red>The altar needs: <white><recipe>", Msg.arg("recipe", recipeText()));
                return;
            }
        }
        if (inv.firstEmpty() < 0) {
            Msg.send(player, "<red>Free an inventory slot first.");
            return;
        }
        for (Map.Entry<Material, Integer> e : plugin.settings().recipe().entrySet()) {
            inv.removeItem(new ItemStack(e.getKey(), e.getValue()));
        }
        plugin.registry().craft(type, player.getUniqueId());
        grimoires.give(player, type);
        refresh(type);
        ceremony(player, type);
        Msg.broadcast("<white><player></white> <gray>has been chosen by the <c><name> Grimoire</c>!",
                Msg.arg("player", player.getName()), Msg.arg("name", type.displayName()), Msg.color("c", type.rgb()));
        plugin.saveAsync();
    }

    private void ceremony(Player player, GrimoireType type) {
        Msg.title(player, "<gray>A grimoire has chosen you…", "<c><b><name> Grimoire</b></c>",
                Msg.color("c", type.rgb()), Msg.arg("name", type.displayName()));
        Location at = player.getLocation().add(0, 1, 0);
        World w = player.getWorld();
        w.playSound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
        w.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        plugin.getServer().getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<>() {
            int step;

            @Override
            public void accept(org.bukkit.scheduler.BukkitTask task) {
                if (step++ > 30 || !player.isOnline()) {
                    task.cancel();
                    return;
                }
                double angle = step * 0.4;
                double r = 1.2;
                Location p = player.getLocation().add(Math.cos(angle) * r, step * 0.07, Math.sin(angle) * r);
                w.spawnParticle(Particle.END_ROD, p, 2, 0, 0, 0, 0);
                w.spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.5, 0), 10, 0.4, 0.4, 0.4, 0.8);
            }
        }, 0L, 1L);
    }

    // ---------------------------------------------------------------- protection

    private boolean bypass(Player p) {
        return p != null && p.getGameMode() == org.bukkit.GameMode.CREATIVE && p.hasPermission("blackclover.admin");
    }

    private boolean protectedBlock(Block b) {
        return libraryAt(b).isPresent();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedBlock(event.getBlock()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protectedBlock(event.getBlock()) && !bypass(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyProtected(event.getBlocks(), event.getDirection()) || protectedBlock(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyProtected(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean anyProtected(List<Block> blocks, org.bukkit.block.BlockFace dir) {
        for (Block b : blocks) {
            if (protectedBlock(b) || protectedBlock(b.getRelative(dir))) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (protectedBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (protectedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
