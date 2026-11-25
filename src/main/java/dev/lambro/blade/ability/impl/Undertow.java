package dev.lambro.blade.ability.impl;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.SettingDef;
import dev.lambro.blade.ability.Trigger;
import dev.lambro.blade.util.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tier 1 — every hit stacks a tide mark; the final mark drags the target under. */
public final class Undertow extends Ability {

    private record Mark(int stacks, long at) {}

    private final Map<UUID, Map<UUID, Mark>> marks = new HashMap<>();

    public Undertow(LambroPlugin plugin) {
        super(plugin, "undertow", "Undertow", 1, Trigger.PASSIVE, Material.HEART_OF_THE_SEA,
                "hits stack tide marks that burst",
                List.of("Each strike brands your foe with a tide mark.",
                        "At full marks the current erupts: bonus damage",
                        "and a crushing slow as the abyss drags them down."));
        setting(SettingDef.whole("hits", "Hits To Burst", Material.PRISMARINE_SHARD, 3, 1, 2, 10, "hits"));
        setting(SettingDef.num("bonus-damage", "Burst Damage", Material.IRON_SWORD, 4, 0.5, 0, 40, "hp"));
        setting(SettingDef.num("slow-seconds", "Slow Duration", Material.SOUL_SAND, 2, 0.5, 0, 10, "s"));
        setting(SettingDef.whole("slow-level", "Slow Level", Material.COBWEB, 2, 1, 1, 5, ""));
        setting(SettingDef.num("mark-expire", "Mark Lifetime", Material.CLOCK, 5, 0.5, 1, 30, "s"));
    }

    @Override
    public void onMeleeHit(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Map<UUID, Mark> mine = marks.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        long now = System.currentTimeMillis();
        Mark m = mine.get(target.getUniqueId());
        int stacks = (m == null || now - m.at() > get("mark-expire") * 1000) ? 1 : m.stacks() + 1;
        int need = getInt("hits");

        if (stacks >= need) {
            mine.remove(target.getUniqueId());
            event.setDamage(event.getDamage() + get("bonus-damage"));
            int ticks = (int) (get("slow-seconds") * 20);
            if (ticks > 0) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, getInt("slow-level") - 1, false, false, true));
            burst(target);
        } else {
            mine.put(target.getUniqueId(), new Mark(stacks, now));
            pips(target, stacks, need);
        }
        mine.values().removeIf(x -> now - x.at() > 30_000);
    }

    /** Small orbiting pips above the head showing marks. */
    private void pips(LivingEntity t, int stacks, int need) {
        Location head = t.getLocation().add(0, t.getHeight() + 0.45, 0);
        for (int i = 0; i < need; i++) {
            double a = Math.PI * 2 * i / need;
            Location l = head.clone().add(Math.cos(a) * 0.35, 0, Math.sin(a) * 0.35);
            Fx.dust(l, i < stacks ? Fx.CYAN : Fx.VOID, i < stacks ? 0.8f : 0.6f);
        }
        t.getWorld().playSound(t.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f + stacks * 0.2f);
    }

    /** A rising blue→black whirlpool around the target. */
    private void burst(LivingEntity t) {
        Location base = t.getLocation();
        t.getWorld().playSound(base, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.9f, 0.8f);
        t.getWorld().playSound(base, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        t.getWorld().spawnParticle(Particle.SQUID_INK, base.clone().add(0, t.getHeight() / 2, 0), 18, 0.3, 0.4, 0.3, 0.05);
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i++ >= 8) { cancel(); return; }
                for (int arm = 0; arm < 2; arm++) {
                    for (int s = 0; s < 4; s++) {
                        double prog = (i * 4 + s) / 32.0;
                        double ang = prog * Math.PI * 5 + arm * Math.PI;
                        double r = 1.1 - prog * 0.7;
                        Location l = base.clone().add(Math.cos(ang) * r, prog * t.getHeight() * 1.3, Math.sin(ang) * r);
                        Fx.fade(l, Fx.ICE, Fx.VOID, 1.1f, 1, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void shutdown() {
        marks.clear();
    }
}
