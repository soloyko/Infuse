package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * True Excalibur, the Blade of the Soulflame (Arc II faction weapon of the House of the Soulflame).
 * Passive Soulfire Ward, on-hit Soulbrand, F Soulfall, Sneak+F Excalibur's Oath.
 */
public final class ExcaliburWeapon extends SwordAbility {

    private static final int GOLD = 0xF2C94C;
    private static final int SOULFIRE = 0x2EE6C8;
    private static final int HOT = 0xB8FFF2;

    public ExcaliburWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.EXCALIBUR);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.FIRE_RESISTANCE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Location at = Combat.center(target);
        event.setDamage(event.getDamage() + 4);
        target.setFireTicks(Math.max(target.getFireTicks(), 80));
        fx().burst(at, SOULFIRE, 1.8f);
        fx().flash(at, HOT, 1.4f);
        Fx.particle(at, Particle.SOUL, 6, 0.3, 0.05);
        Fx.particle(at, Particle.SOUL_FIRE_FLAME, 15, 0.3, 0.05);
        Fx.sound(at, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        Fx.sound(at, Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.2f);
    }

    /**
     * Soulfall: a mass of soulfire gathers where you aim (an enemy or the ground). It crashes down
     * there at once, and for four seconds keeps raining on enemies beneath it.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 32, 0.8);
        Location ground;
        if (target != null) {
            ground = target.getLocation();
        } else {
            Location aim = combat().aimPoint(player, 32);
            if (aim == null) {
                tell(player, "Aim at an enemy or the ground to call the soulfire.");
                return false;
            }
            ground = Combat.ground(aim.clone().add(0, 0.5, 0));
        }
        Location sky = ground.clone().add(0, 9, 0);
        sky.setYaw(0);
        sky.setPitch(0);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        // the soulfire overhead: a churning mass of blue flame
        BlockDisplay heart = displays().block(sky, Material.SOUL_FIRE.createBlockData(), new Vector3f(1.8f),
                new Quaternionf().rotateXYZ(0.4f, 0.6f, 0.2f), ZONE_TICKS + 15);
        heart.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        fx().haze(sky, 0x1FB8A8, 3.4f, ZONE_TICKS + 10);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            fx().haze(sky.clone().add(Math.cos(a) * 2.2, r.nextDouble(-0.3, 0.3), Math.sin(a) * 2.2), 0x10302E, 2.6f, ZONE_TICKS + 10);
        }
        fx().castCircle(ground, SOULFIRE, (float) ZONE_RADIUS);
        Fx.particle(sky, Particle.LARGE_SMOKE, 30, 2.2, 0.01);
        Fx.sound(sky, Sound.PARTICLE_SOUL_ESCAPE, 2f, 0.6f);
        Fx.sound(sky, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.5f);
        fx().beam(dev.infuse.customs.ability.Fx3D.handOrigin(player), sky, SOULFIRE, 0.2f, 6);
        displays().later(4, () -> fall(player, sky, ground, target, 8));

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > ZONE_TICKS || !alive(player) || ground.getWorld() != player.getWorld()) {
                    displays().remove(heart);
                    Fx.particle(sky, Particle.LARGE_SMOKE, 20, 1.2, 0.02);
                    cancel();
                    return;
                }
                if (heart.isValid() && ticks % 2 == 0) {
                    dev.infuse.customs.ability.Displays.animateBlock(heart, new Vector3f(1.8f + (float) Math.sin(ticks * 0.3) * 0.15f),
                            new Quaternionf().rotateXYZ(0.4f + ticks * 0.05f, 0.6f + ticks * 0.08f, 0.2f), new Vector3f(), 2);
                }
                if (ticks % 3 == 0) {
                    // soul embers falling from it
                    Fx.particle(sky.clone().add(r.nextDouble(-1.5, 1.5), -0.8, r.nextDouble(-1.5, 1.5)), Particle.SOUL_FIRE_FLAME, 2, 0.3, 0.01);
                    double a = r.nextDouble(Math.PI * 2);
                    Fx.particle(ground.clone().add(Math.cos(a) * ZONE_RADIUS, 0.2, Math.sin(a) * ZONE_RADIUS), Particle.SOUL_FIRE_FLAME, 2, 0.1, 0.01);
                }
                if (ticks % 15 != 0) {
                    return;
                }
                List<LivingEntity> under = combat().nearby(player, ground, ZONE_RADIUS);
                if (under.isEmpty()) {
                    double a = r.nextDouble(Math.PI * 2);
                    double d = r.nextDouble(ZONE_RADIUS);
                    fall(player, sky, Combat.ground(ground.clone().add(Math.cos(a) * d, 1.5, Math.sin(a) * d)), null, 0);
                } else {
                    LivingEntity hit = under.get(r.nextInt(under.size()));
                    fall(player, sky, hit.getLocation(), hit, 5);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static final int ZONE_TICKS = 80;
    private static final double ZONE_RADIUS = 5;

    /** A ball of soulfire dropping onto {@code spot}, burning {@code victim} if there is one. */
    private void fall(Player player, Location sky, Location spot, LivingEntity victim, double damage) {
        Location from = sky.clone().add((Math.random() - 0.5) * 2, -0.6, (Math.random() - 0.5) * 2);
        from.setYaw(0);
        from.setPitch(0);
        BlockDisplay glob = displays().block(from, Material.SOUL_FIRE.createBlockData(), new Vector3f(0.9f), new Quaternionf(), 12);
        glob.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        glob.setTeleportDuration(5);
        Location land = spot.clone().add(0, 0.3, 0);
        land.setYaw(0);
        land.setPitch(0);
        displays().later(1, () -> {
            if (glob.isValid()) {
                glob.teleport(land);
            }
        });
        fx().beam(from, land, SOULFIRE, 0.35f, 5);
        displays().later(6, () -> {
            displays().remove(glob);
            fx().impact(spot.clone().add(0, 0.8, 0), SOULFIRE, 2.4f);
            fx().crack(spot, SOULFIRE, 2.2f, 40);
            fx().flamePillar(spot, 0.8f, 2.2f, 14, true);
            displays().eruptGround(spot, 0.5f, 0.5f);
            Fx.particle(spot.clone().add(0, 0.5, 0), Particle.SOUL, 10, 0.5, 0.05);
            Fx.particle(spot.clone().add(0, 0.5, 0), Particle.SOUL_FIRE_FLAME, 25, 0.6, 0.05);
            Fx.sound(spot, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
            Fx.sound(spot, Sound.BLOCK_SOUL_SAND_BREAK, 1.5f, 0.8f);
            if (victim != null && victim.isValid() && !victim.isDead() && alive(player)) {
                combat().damage(player, victim, damage, true);
                victim.setFireTicks(Math.max(victim.getFireTicks(), 60));
                applyEffect(victim, PotionEffectType.SLOWNESS, 20, 2);
            }
        });
    }

    /**
     * Excalibur's Oath: you drive the blade into the ground and swear the oath; runes of warding circle you and your
     * faction nearby (Resistance II, Absorption II) and enemies close by are thrown back.
     */
    @Override
    public boolean secondary(Player player) {
        Location at = player.getLocation();
        fx().flash(at.clone().add(0, 1.2, 0), GOLD, 3.2f);
        fx().shockwave(at, GOLD, 6.5f, 8);
        fx().shockwave(at, SOULFIRE, 4f, 6);
        fx().crack(at, GOLD, 3f, 40);
        displays().eruptGround(at, 0.9f, 0.8f);
        Fx.sound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f);
        Fx.sound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.8f);
        Fx.sound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
        for (Player p : at.getNearbyPlayers(10)) {
            if (p != player && !plugin.arcs().allies(player, p)) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1, false, false, true));
            ward(p);
        }
        for (LivingEntity enemy : combat().nearby(player, at, 6)) {
            Combat.knockback(enemy, at, 1.5, 0.5);
            applyEffect(enemy, PotionEffectType.SLOWNESS, 60, 1);
            combat().damage(player, enemy, 3, true);
            fx().burst(Combat.center(enemy), SOULFIRE, 1.4f);
            enemy.setFireTicks(Math.max(enemy.getFireTicks(), 40));
        }
        return true;
    }

    /** Three gold runes circling a player for a few seconds: the ward. */
    private void ward(Player p) {
        org.bukkit.entity.ItemDisplay[] runes = new org.bukkit.entity.ItemDisplay[3];
        for (int i = 0; i < 3; i++) {
            runes[i] = fx().sigil(p.getLocation().add(0, 1, 0), new Vector(0, 0, 1), GOLD, 0.7f, 70);
        }
        Fx.particle(p.getLocation().add(0, 1, 0), Particle.DUST, 12, 0.4, 0, Fx.dust(GOLD, 1.4f));
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (t > 60 || !p.isOnline() || p.isDead()) {
                    for (org.bukkit.entity.ItemDisplay r : runes) {
                        displays().remove(r);
                    }
                    cancel();
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.18 + i * Math.PI * 2 / 3;
                    Vector out = new Vector(Math.cos(a), 0, Math.sin(a));
                    Location spot = p.getLocation().add(out.clone().multiply(1.1)).add(0, 1.0 + Math.sin(t * 0.15 + i) * 0.15, 0);
                    spot.setDirection(out);
                    if (runes[i].isValid()) {
                        runes[i].teleport(spot);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
