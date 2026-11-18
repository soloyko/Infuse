package dev.bloodbound.ability;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.ability.kits.CrimsonBladeKit;
import dev.bloodbound.ability.kits.EarthshakerMaceKit;
import dev.bloodbound.ability.kits.PhantomBowKit;
import dev.bloodbound.ability.kits.RavagerAxeKit;
import dev.bloodbound.ability.kits.TempestTridentKit;
import dev.bloodbound.config.Messages;
import dev.bloodbound.config.WeaponSettings;
import dev.bloodbound.data.PlayerData;
import dev.bloodbound.weapon.WeaponType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static dev.bloodbound.config.Messages.text;

/** Cooldowns, tier gates, triggering, fall immunity and the cooldown action bar. */
public final class AbilityManager implements Runnable {
    private record Cooldowns(long activeReadyAt, long ultimateReadyAt) {}

    private final BloodboundPlugin plugin;
    private final Map<WeaponType, WeaponKit> kits = new EnumMap<>(WeaponType.class);
    private final Map<UUID, Cooldowns> cooldowns = new HashMap<>();
    private final Map<UUID, Long> fallImmuneUntil = new HashMap<>();
    private final Map<UUID, Long> barPausedUntil = new HashMap<>();

    public AbilityManager(BloodboundPlugin plugin) {
        this.plugin = plugin;
        register(new CrimsonBladeKit());
        register(new RavagerAxeKit());
        register(new TempestTridentKit());
        register(new EarthshakerMaceKit());
        register(new PhantomBowKit());
    }

    private void register(WeaponKit kit) {
        kits.put(kit.type(), kit);
    }

    public WeaponKit kit(WeaponType type) {
        return kits.get(type);
    }

    /** Context for a player holding (or having fired) their own valid weapon. */
    public Ctx ctx(Player player) {
        PlayerData data = plugin.players().get(player);
        WeaponSettings cfg = plugin.settings().weapon(data.type());
        return new Ctx(plugin, player, plugin.settings().tiers().tierFor(data.blood()), cfg);
    }

    public void trigger(Player player, boolean ultimate) {
        PlayerData data = plugin.players().get(player);
        if (data.type() == null) return;
        Messages m = plugin.messages();
        String abilityName = m.raw("weapon.types." + data.type().key() + "." + (ultimate ? "ultimate" : "active"));
        Ctx ctx = ctx(player);
        int needed = ultimate ? plugin.settings().ultimateTier() : plugin.settings().activeTier();
        if (ctx.tier() < needed) {
            m.send(player, "weapon.locked", text("ability", abilityName), text("tier", needed));
            return;
        }
        long now = System.currentTimeMillis();
        Cooldowns cd = cooldowns.getOrDefault(player.getUniqueId(), new Cooldowns(0, 0));
        long readyAt = ultimate ? cd.ultimateReadyAt() : cd.activeReadyAt();
        if (now < readyAt) {
            m.send(player, "weapon.cooldown", text("ability", abilityName), text("seconds", (readyAt - now + 999) / 1000));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 0.6f);
            return;
        }
        WeaponKit kit = kits.get(data.type());
        boolean fired = ultimate ? kit.ultimate(ctx) : kit.active(ctx);
        if (!fired) return;
        long base = ultimate ? ctx.cfg().ultimateCooldownMillis() : ctx.cfg().activeCooldownMillis();
        long next = now + Math.round(base * plugin.events().cooldownMultiplier());
        cooldowns.put(player.getUniqueId(), ultimate
                ? new Cooldowns(cd.activeReadyAt(), next)
                : new Cooldowns(next, cd.ultimateReadyAt()));
    }

    public void grantFallImmunity(Player player, long millis) {
        fallImmuneUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    /** Consumes fall immunity if active. */
    public boolean consumeFallImmunity(Player player) {
        Long until = fallImmuneUntil.remove(player.getUniqueId());
        return until != null && System.currentTimeMillis() <= until;
    }

    /** Lets another action-bar message (e.g. a blood gain) stay visible for a moment. */
    public void pauseBar(Player player, long millis) {
        barPausedUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public void clear(Player player) {
        cooldowns.remove(player.getUniqueId());
        barPausedUntil.remove(player.getUniqueId());
        fallImmuneUntil.remove(player.getUniqueId());
        kits.values().forEach(k -> k.clear(player));
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        cooldowns.clear();
    }

    /** Action bar with cooldowns, every 10 ticks, for players holding their weapon during a session. */
    @Override
    public void run() {
        if (!plugin.sessions().isActive()) return;
        Messages m = plugin.messages();
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.weapons().heldWeapon(player).isEmpty()) continue;
            Long paused = barPausedUntil.get(player.getUniqueId());
            if (paused != null && now < paused) continue;
            PlayerData data = plugin.players().get(player);
            int tier = plugin.settings().tiers().tierFor(data.blood());
            Cooldowns cd = cooldowns.getOrDefault(player.getUniqueId(), new Cooldowns(0, 0));
            String base = "weapon.types." + data.type().key() + ".";
            Component bar = segment(m, m.raw(base + "active"), tier, plugin.settings().activeTier(), cd.activeReadyAt(), now)
                    .append(m.get("weapon.actionbar-separator"))
                    .append(segment(m, m.raw(base + "ultimate"), tier, plugin.settings().ultimateTier(), cd.ultimateReadyAt(), now));
            player.sendActionBar(bar);
        }
    }

    private Component segment(Messages m, String name, int tier, int needed, long readyAt, long now) {
        if (tier < needed) return m.get("weapon.actionbar-locked", text("ability", name), text("tier", needed));
        if (now >= readyAt) return m.get("weapon.actionbar-ready", text("ability", name));
        return m.get("weapon.actionbar-cooldown", text("ability", name), text("seconds", (readyAt - now + 999) / 1000));
    }
}
