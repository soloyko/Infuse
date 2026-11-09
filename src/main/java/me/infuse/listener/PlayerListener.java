package me.infuse.listener;

import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.effect.EffectManager;
import me.infuse.effect.Equipped;
import me.infuse.effect.ItemFactory;
import me.infuse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HexFormat;

public final class PlayerListener implements Listener {
    private final InfusePlugin plugin;

    public PlayerListener(InfusePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Remove any stale debuff left over from a crash/restart
        me.infuse.util.Util.modifier(p, Attribute.JUMP_STRENGTH, plugin.effects().frostJump, 0,
                AttributeModifier.Operation.ADD_SCALAR, false);
        plugin.effects().refresh(p);
        plugin.rituals().showBars(p);
        plugin.effects().syncThieves();
        sendPack(p);
    }

    private void sendPack(Player p) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        byte[] hash = null;
        if (sha1 != null && sha1.length() == 40) {
            try {
                hash = HexFormat.of().parseHex(sha1);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("resource-pack.sha1 is not a valid SHA-1 hex string");
            }
        }
        Component prompt = MiniMessage.miniMessage().deserialize(
                plugin.getConfig().getString("resource-pack.prompt", "<light_purple>Infuse needs its resource pack for effect icons."));
        p.setResourcePack(url, hash, prompt, plugin.getConfig().getBoolean("resource-pack.required", false));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.data().unload(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.effects().refresh(e.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        Equipped eq = plugin.items().read(e.getItem());
        if (eq == null) return;
        Player p = e.getPlayer();
        if (!eq.type().enabled()) {
            e.setCancelled(true);
            Msg.send(p, "effect-disabled", Msg.comp("effect", ItemFactory.name(eq.type(), eq.augmented())));
            return;
        }
        EffectManager.EquipResult result = plugin.effects().equip(p, eq);
        switch (result) {
            case DUPLICATE -> {
                e.setCancelled(true);
                Msg.send(p, "equip-duplicate", Msg.comp("effect", ItemFactory.name(eq.type(), eq.augmented())));
            }
            case FULL -> {
                e.setCancelled(true);
                Msg.send(p, "equip-full");
            }
            case PRIMARY, SECONDARY -> {
                boolean primary = result == EffectManager.EquipResult.PRIMARY;
                Msg.send(p, "equipped", Msg.comp("effect", ItemFactory.name(eq.type(), eq.augmented())),
                        Msg.comp("slot", Msg.get(primary ? "slot-primary" : "slot-secondary")));
                me.infuse.util.Fx.equip(p, eq, primary);
                e.setReplacement(ItemStack.empty());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        String mode = plugin.getConfig().getString("death.drop-mode", "primary-first").toLowerCase();
        if (!plugin.getConfig().getBoolean("death.drop-effect", true) || mode.equals("none")) return;
        if (plugin.getConfig().getBoolean("death.only-when-killed-by-player", false) && p.getKiller() == null) return;
        PlayerData d = plugin.data().get(p);
        boolean hasP = d.get(PlayerData.PRIMARY) != null, hasS = d.get(PlayerData.SECONDARY) != null;
        if (!hasP && !hasS) return;
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        switch (mode) {
            case "both" -> {
                if (hasP) slots.add(PlayerData.PRIMARY);
                if (hasS) slots.add(PlayerData.SECONDARY);
            }
            case "secondary-first" -> slots.add(hasS ? PlayerData.SECONDARY : PlayerData.PRIMARY);
            case "random" -> slots.add(hasP && hasS
                    ? java.util.concurrent.ThreadLocalRandom.current().nextInt(2) : hasP ? PlayerData.PRIMARY : PlayerData.SECONDARY);
            default -> slots.add(hasP ? PlayerData.PRIMARY : PlayerData.SECONDARY); // primary-first
        }
        for (int slot : slots) {
            Equipped lost = plugin.effects().unequip(p, slot);
            if (lost == null) continue;
            ItemStack bottle = plugin.items().create(lost.type(), lost.augmented());
            if (e.getKeepInventory()) p.getWorld().dropItemNaturally(p.getLocation(), bottle);
            else e.getDrops().add(bottle);
            me.infuse.util.Fx.effectLost(p.getLocation(), lost.type());
        }
        d.clearSparks();
    }

    /** Dropped effect bottles show their name floating above them; augmented ones glow. */
    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent e) {
        if (!plugin.getConfig().getBoolean("visuals.dropped-bottle-names", true)) return;
        Equipped eq = plugin.items().read(e.getEntity().getItemStack());
        if (eq == null) return;
        e.getEntity().customName(ItemFactory.name(eq.type(), eq.augmented()));
        e.getEntity().setCustomNameVisible(true);
        if (eq.augmented()) e.getEntity().setGlowing(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        if (!d.offhandControls() || d.isEmpty()) return;
        e.setCancelled(true);
        plugin.sparks().use(p, p.isSneaking() ? PlayerData.SECONDARY : PlayerData.PRIMARY);
    }
}
