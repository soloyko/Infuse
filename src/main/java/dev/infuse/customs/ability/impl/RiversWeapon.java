package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Rivers of Blood, the Blade of the Court (Arc II faction weapon of the Court of Blood).
 * Passive Bloodborne, on-hit Hemorrhage, F Corpse Piler, Sneak+F Sanguine Tithe.
 */
public final class RiversWeapon extends SwordAbility {

    private static final int BLOOD = 0xC8102E;
    private static final int DARK = 0x3A0008;
    private static final int GLOW = 0xFF3048;

    public RiversWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.RIVERS);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        combat().heal(player, event.getDamage() * 0.1);
        if (chance(0.25)) {
            status().bleed(player, target, 4);
            Location at = Combat.center(target);
            Fx.particle(at, Particle.DUST, 15, 0.3, 0, Fx.dust(BLOOD, 1.2f));
            Fx.sound(at, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.7f);
        }
    }

    /**
     * Corpse Piler: a flurry of four blood slashes sweeps the arc in front of you over a second,
     * each from a different angle. Every slash cuts every enemy in the arc and makes it bleed.
     */
    @Override
    public boolean primary(Player player) {
        double[] rolls = {0.35, -0.5, 1.2, Math.PI / 2};
        Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        for (int i = 0; i < rolls.length; i++) {
            int n = i;
            displays().later(i * 5L, () -> {
                if (!alive(player)) {
                    return;
                }
                Vector look = player.getLocation().getDirection().setY(0);
                look = look.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : look.normalize();
                Location center = player.getLocation().add(0, 1.1, 0).add(look.clone().multiply(1.6));
                boolean last = n == rolls.length - 1;
                fx().slash(center, look, rolls[n], n % 2 == 0 ? GLOW : BLOOD, last ? 3.4f : 2.8f, 3);
                Fx.particle(center, Particle.DUST, 25, 1.2, 0, Fx.dust(n % 2 == 0 ? BLOOD : DARK, 1.4f));
                Fx.particle(center, Particle.SWEEP_ATTACK, 2, 0.8, 0);
                Fx.sound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f + n * 0.15f);
                Fx.sound(center, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.7f, 0.6f);
                for (LivingEntity e : combat().nearby(player, center, 3.2)) {
                    Vector to = e.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() > 1.0E-4 && to.normalize().dot(look) < 0.2) {
                        continue; // only the arc in front of you
                    }
                    combat().damage(player, e, last ? 5 : 3, true);
                    status().bleed(player, e, 3);
                    Fx.particle(Combat.center(e), Particle.DAMAGE_INDICATOR, 3, 0.2, 0.05);
                    if (last) {
                        Combat.knockback(e, player.getLocation(), 0.7, 0.25);
                    }
                }
            });
        }
        return true;
    }

    /**
     * Sanguine Tithe: blood is torn out of every enemy within eight blocks and streams through the
     * air to you and your faction nearby, healing you for what was taken (shared between you).
     */
    @Override
    public boolean secondary(Player player) {
        Location at = player.getLocation();
        List<LivingEntity> donors = combat().nearby(player, at, 8);
        if (donors.isEmpty()) {
            tell(player, "No enemies near enough to tithe.");
            return false;
        }
        List<Player> court = new ArrayList<>();
        court.add(player);
        for (Player p : at.getNearbyPlayers(10)) {
            if (p != player && plugin.arcs().allies(player, p)) {
                court.add(p);
            }
        }
        fx().castCircle(at, BLOOD, 8f);
        fx().flash(at.clone().add(0, 1, 0), GLOW, 3f);
        Fx.sound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.7f);
        Fx.sound(at, Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
        double taken = 0;
        for (LivingEntity donor : donors) {
            double before = donor.getHealth();
            combat().damage(player, donor, 3, true);
            taken += Math.max(0, before - donor.getHealth());
            Location from = Combat.center(donor);
            Fx.particle(from, Particle.DAMAGE_INDICATOR, 6, 0.3, 0.1);
            Fx.particle(from, Particle.DUST, 15, 0.3, 0, Fx.dust(BLOOD, 1.3f));
            // a stream of blood to each member of the court
            for (Player p : court) {
                Location to = p.getLocation().add(0, 1.2, 0);
                fx().drift(from, to.toVector().subtract(from.toVector()), GLOW, 0.5f, 12);
                Fx.arc(from, to, q -> Fx.particle(q, Particle.DUST, 1, 0.02, 0, Fx.dust(BLOOD, 1.0f)));
            }
        }
        double share = Math.max(2, taken * 1.2 / court.size());
        displays().later(12, () -> {
            for (Player p : court) {
                if (p.isOnline() && !p.isDead()) {
                    combat().heal(p, share);
                    Fx.particle(p.getLocation().add(0, 1.2, 0), Particle.DUST, 12, 0.4, 0, Fx.dust(GLOW, 1.2f));
                    Fx.sound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.7f);
                }
            }
        });
        return true;
    }
}
