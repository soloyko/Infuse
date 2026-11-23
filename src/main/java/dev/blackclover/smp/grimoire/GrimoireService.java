package dev.blackclover.smp.grimoire;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Applies {@link GrimoireRegistry} decisions to real items, players and the world. */
public final class GrimoireService {

    /** Supplies per-holder lore values; replaced once the magic system is wired up. */
    public interface HolderInfo {
        int level(Player player, GrimoireType type);

        String selectedSpell(Player player, GrimoireType type);
    }

    private final BlackCloverPlugin plugin;
    private final GrimoireRegistry registry;
    private final GrimoireItems items;
    private final List<Consumer<GrimoireType>> returnHomeHooks = new ArrayList<>();
    private HolderInfo holderInfo = new HolderInfo() {
        @Override
        public int level(Player player, GrimoireType type) {
            return 1;
        }

        @Override
        public String selectedSpell(Player player, GrimoireType type) {
            return null;
        }
    };

    public GrimoireService(BlackCloverPlugin plugin, GrimoireRegistry registry, GrimoireItems items) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
    }

    public GrimoireItems items() {
        return items;
    }

    public GrimoireRegistry registry() {
        return registry;
    }

    public void setHolderInfo(HolderInfo holderInfo) {
        this.holderInfo = holderInfo;
    }

    public void onReturnedHome(Consumer<GrimoireType> hook) {
        returnHomeHooks.add(hook);
    }

    public Optional<GrimoireType> carried(Player player) {
        return registry.heldBy(player.getUniqueId());
    }

    /** True if this exact item is the live copy of its grimoire. */
    public boolean isLive(ItemStack item) {
        Optional<GrimoireType> type = items.typeOf(item);
        return type.isPresent() && registry.isCurrent(type.get(), items.instanceOf(item).orElse(null));
    }

    public ItemStack newItem(GrimoireType type, Player holder) {
        return items.create(type, registry.get(type).instance(),
                holderInfo.level(holder, type), holderInfo.selectedSpell(holder, type));
    }

    /** Puts the (already registered) grimoire into the player's inventory. False if there is no room. */
    public boolean give(Player player, GrimoireType type) {
        if (player.getInventory().firstEmpty() < 0) {
            return false;
        }
        player.getInventory().addItem(newItem(type, player));
        return true;
    }

    /** Sends a grimoire back to its library / the admin pool and deletes every live copy we can reach. */
    public void returnHome(GrimoireType type, String reason) {
        GrimoireState before = registry.get(type);
        registry.returnHome(type);
        if (before.status() == GrimoireStatus.HELD && before.holder() != null) {
            Player holder = Bukkit.getPlayer(before.holder());
            if (holder != null) {
                removeAll(holder, type);
                Msg.send(holder, "<gray>Your <white><type></white> grimoire returned home <dark_gray>(<reason>)</dark_gray>.",
                        Msg.arg("type", type.displayName()), Msg.arg("reason", reason));
            }
        }
        if (before.status() == GrimoireStatus.GROUND && before.itemEntity() != null) {
            Entity e = Bukkit.getEntity(before.itemEntity());
            if (e != null) {
                e.remove();
            }
        }
        plugin.getLogger().info(type.displayName() + " grimoire returned home: " + reason);
        for (Consumer<GrimoireType> hook : returnHomeHooks) {
            hook.accept(type);
        }
        plugin.saveAsync();
    }

    /** Drops the grimoire as an indestructible item at {@code loc}. Below the world floor it goes home instead. */
    public void dropAt(GrimoireType type, Location loc) {
        World world = loc.getWorld();
        if (world == null || loc.getY() < world.getMinHeight()) {
            returnHome(type, "fell into the void");
            return;
        }
        ItemStack stack = items.create(type, registry.get(type).instance(), 0, null);
        Item item = world.dropItem(loc, stack, this::applyGroundProps);
        registry.markGround(type, item.getUniqueId(), toBlockLoc(item.getLocation()));
    }

    public void applyGroundProps(Item item) {
        item.setInvulnerable(true);
        item.setUnlimitedLifetime(true);
        item.setGlowing(true);
        item.setCanMobPickup(false);
    }

    public static BlockLoc toBlockLoc(Location loc) {
        return new BlockLoc(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    /** Removes every copy of {@code type} from the player's inventory and cursor. */
    public void removeAll(Player player, GrimoireType type) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (items.typeOf(contents[i]).filter(t -> t == type).isPresent()) {
                inv.setItem(i, null);
            }
        }
        if (items.typeOf(player.getItemOnCursor()).filter(t -> t == type).isPresent()) {
            player.setItemOnCursor(null);
        }
    }

    /**
     * Deletes stale or duplicate grimoire items, keeps at most one live copy, and sends home a
     * grimoire the registry says this player holds but that is no longer on them.
     */
    public void validate(Player player) {
        UUID id = player.getUniqueId();
        Optional<GrimoireType> held = registry.heldBy(id);
        boolean keptOne = false;
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (!items.isGrimoire(contents[i])) {
                continue;
            }
            if (!keptOne && isHeldCopy(contents[i], held)) {
                keptOne = true;
            } else {
                inv.setItem(i, null);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (items.isGrimoire(cursor)) {
            if (!keptOne && isHeldCopy(cursor, held)) {
                keptOne = true;
            } else {
                player.setItemOnCursor(null);
            }
        }
        if (held.isPresent() && !keptOne) {
            returnHome(held.get(), "lost from " + player.getName() + "'s inventory");
        }
    }

    private boolean isHeldCopy(ItemStack item, Optional<GrimoireType> held) {
        return held.isPresent() && items.typeOf(item).orElse(null) == held.get() && isLive(item);
    }

    /** Updates name/lore of the player's live grimoire. */
    public void refreshLore(Player player) {
        Optional<GrimoireType> held = carried(player);
        if (held.isEmpty()) {
            return;
        }
        GrimoireType type = held.get();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (items.typeOf(stack).orElse(null) == type) {
                items.decorate(stack, type, holderInfo.level(player, type), holderInfo.selectedSpell(player, type));
            }
        }
    }

    /** Sends home ground grimoires whose item entity vanished, and tracks drifting items. */
    public void checkGroundItems() {
        for (GrimoireState s : List.copyOf(registry.all())) {
            if (s.status() != GrimoireStatus.GROUND || s.ground() == null) {
                continue;
            }
            World world = Bukkit.getWorld(s.ground().world());
            if (world == null) {
                continue;
            }
            int cx = (int) Math.floor(s.ground().x()) >> 4;
            int cz = (int) Math.floor(s.ground().z()) >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                continue;
            }
            Chunk chunk = world.getChunkAt(cx, cz);
            if (!chunk.isEntitiesLoaded()) {
                continue;
            }
            Entity e = s.itemEntity() == null ? null : Bukkit.getEntity(s.itemEntity());
            if (e == null || e.isDead()) {
                returnHome(s.type(), "item was lost");
            } else if (e.getLocation().getY() < world.getMinHeight()) {
                returnHome(s.type(), "fell into the void");
            } else {
                registry.markGround(s.type(), e.getUniqueId(), toBlockLoc(e.getLocation()));
            }
        }
    }

    /** Sends home grimoires whose holder has been offline longer than the configured time. */
    public void returnFromInactiveHolders() {
        long now = System.currentTimeMillis();
        long limit = plugin.settings().offlineReturnMillis();
        for (GrimoireState s : List.copyOf(registry.all())) {
            if (s.status() != GrimoireStatus.HELD || s.holder() == null || Bukkit.getPlayer(s.holder()) != null) {
                continue;
            }
            PlayerData pd = plugin.players().get(s.holder());
            if (pd != null && pd.lastSeenMillis() > 0 && now - pd.lastSeenMillis() > limit) {
                returnHome(s.type(), "holder " + pd.name() + " was inactive");
            }
        }
    }
}
