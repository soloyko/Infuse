package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Displays;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Void Sword: mobility and control.
 * Passive Void Walker (night vision + void rescue, see CombatListener), on-hit Void Drain,
 * RC Void Step, Sneak+RC Singularity.
 */
public final class VoidSword extends SwordAbility {

    private static final int PURPLE = 0x845ECC;
    private static final int DARK = 0x1B1030;

    public VoidSword(OPSwordsPlugin plugin) {
        super(plugin, SwordType.VOID);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.NIGHT_VISION, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        applyEffect(target, PotionEffectType.WITHER, 60, 1);
        combat().heal(player, 2);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.REVERSE_PORTAL, 30, 0.3, 0.05);
        Fx.particle(at, Particle.SQUID_INK, 8, 0.3, 0.02);
        Fx.sound(at, Sound.ENTITY_ENDERMAN_HURT, 0.6f, 0.6f);
    }

    /** Void Step: blink forward up to 15 blocks without passing through walls. */
    @Override
    public boolean primary(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        double max = Math.max(0, Combat.clearDistance(player, 15) - 0.6);
        double eyeHeight = player.getEyeHeight();
        Location destination = null;
        for (double d = max; d >= 1.5; d -= 0.5) {
            Location feet = eye.clone().add(dir.clone().multiply(d)).subtract(0, eyeHeight, 0);
            Location safe = Combat.safeSpot(feet);
            if (safe != null) {
                destination = safe;
                break;
            }
        }
        if (destination == null) {
            tell(player, "No room to step there.");
            return false;
        }
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());

        Location from = player.getLocation();
        Fx.particle(from.clone().add(0, 1, 0), Particle.PORTAL, 60, 0.4, 0.5);
        Fx.particle(from.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 30, 0.3, 0.05);
        Fx.line(from.clone().add(0, 1, 0), destination.clone().add(0, 1, 0), 0.4,
                p -> Fx.particle(p, Particle.DUST, 1, 0.05, 0, Fx.dust(PURPLE, 1.2f)));
        Fx.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);

        Vector velocity = player.getVelocity();
        if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            return false;
        }
        player.setFallDistance(0);
        player.setVelocity(velocity.multiply(0.5));

        Location landing = destination.clone().add(0, 1, 0);
        Fx.particle(landing, Particle.REVERSE_PORTAL, 50, 0.5, 0.1);
        Fx.sound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        for (LivingEntity target : combat().nearby(player, landing, 2.5)) {
            combat().damage(player, target, 4);
            applyEffect(target, PotionEffectType.BLINDNESS, 20, 0);
        }
        return true;
    }

    /** Singularity: a 4 second black hole that pulls enemies in, then implodes. */
    @Override
    public boolean secondary(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        Location aim = combat().aimPoint(player, 25);
        Location center;
        if (aim == null) {
            center = eye.clone().add(dir.clone().multiply(15));
        } else {
            center = aim.clone().subtract(dir.clone().multiply(0.8)).add(0, 1.0, 0);
        }
        Fx.sound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        Fx.sound(center, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 0.6f);
        BlockDisplay core = displays().block(center, Material.BLACK_CONCRETE.createBlockData(), new Vector3f(0.1f),
                new Quaternionf(), 90);
        core.setBrightness(new org.bukkit.entity.Display.Brightness(0, 0));

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player) || player.getWorld() != center.getWorld()) {
                    displays().remove(core);
                    cancel();
                    return;
                }
                ticks++;
                if (ticks % 5 == 1) {
                    float pulse = 0.75f + (float) Math.sin(ticks * 0.3) * 0.12f;
                    Displays.animateBlock(core, new Vector3f(pulse), new Quaternionf().rotateXYZ(ticks * 0.11f, ticks * 0.17f, ticks * 0.07f),
                            new Vector3f(), 5);
                }
                drawVortex(center, ticks);
                if (ticks % 2 == 0) {
                    pull(player, center);
                }
                if (ticks % 10 == 0) {
                    for (LivingEntity target : combat().nearby(player, center, 2.5)) {
                        combat().damage(player, target, 2.0, true);
                    }
                    Fx.sound(center, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.5f);
                }
                if (ticks >= 80) {
                    Displays.animateBlock(core, new Vector3f(0.02f), new Quaternionf(), new Vector3f(), 2);
                    displays().later(3, () -> displays().remove(core));
                    implode(player, center);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void drawVortex(Location center, int ticks) {
        Fx.particle(center, Particle.DUST, 6, 0.25, 0, Fx.dust(DARK, 2.2f));
        Fx.particle(center, Particle.SQUID_INK, 2, 0.15, 0.01);
        for (int arm = 0; arm < 4; arm++) {
            double phase = (ticks % 20) / 20.0;
            double radius = 4.0 * (1 - phase) + 0.3;
            double angle = ticks * 0.35 + arm * Math.PI / 2;
            Location p = center.clone().add(Math.cos(angle) * radius, Math.sin(angle * 0.5) * 0.6, Math.sin(angle) * radius);
            Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(PURPLE, 1.4f));
            Fx.particle(p, Particle.REVERSE_PORTAL, 1, 0, 0);
        }
        Fx.ring(center, 1.2 + Math.sin(ticks * 0.3) * 0.2, 12,
                p -> Fx.particle(p, Particle.PORTAL, 1, 0, 0.3));
    }

    private void pull(Player player, Location center) {
        for (LivingEntity target : combat().nearby(player, center, 9)) {
            Vector pull = center.toVector().subtract(Combat.center(target).toVector());
            double distance = pull.length();
            if (distance < 0.6) {
                Combat.setVelocity(target, target.getVelocity().multiply(0.2));
                continue;
            }
            pull.normalize().multiply(0.25 + 0.2 * (1 - distance / 9));
            Combat.setVelocity(target, target.getVelocity().multiply(0.5).add(pull));
        }
    }

    private void implode(Player player, Location center) {
        Fx.particle(center, Particle.SONIC_BOOM, 1, 0, 0);
        Fx.burst(center, Particle.REVERSE_PORTAL, 120, 0.6);
        Fx.burst(center, Particle.SQUID_INK, 40, 0.4);
        Fx.sound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.8f);
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        for (LivingEntity target : combat().nearby(player, center, 6)) {
            combat().damage(player, target, 7, true);
            Combat.knockback(target, center, 1.6, 0.6);
        }
    }
}
