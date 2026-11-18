package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Holy Moonlight Sword, the Blade of the Moonlight (Arc II faction weapon of the Order of the Moonlight).
 * Passive Moonblessed, on-hit Moonlit Arc, F Gather the Order, Sneak+F Full Moon.
 */
public final class MoonlightWeapon extends SwordAbility {

    private static final int MOON = 0x8FF0E8;
    private static final int PALE = 0xE8FFFF;
    private static final int NIGHT = 0x2B3A6B;
    private static final int FULL_MOON_TICKS = 160;
    private static final double MOON_RADIUS = 12;

    public MoonlightWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.MOONLIGHT);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.NIGHT_VISION, 0, 0));
    }

    @Override
    public void tickHeld(Player player, long tick) {
        long time = player.getWorld().getTime();
        if (time > 13000 && time < 23000) {
            applyEffect(player, PotionEffectType.REGENERATION, 40, 0);
        }
    }

    /** Moonlit Arc: a crescent sweeps out behind the target and cuts whoever stands there. */
    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Vector through = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (through.lengthSquared() < 1.0E-4) {
            return;
        }
        through.normalize();
        Location behind = Combat.center(target).add(through.clone().multiply(1.6));
        fx().slash(behind, through, Math.PI / 2, MOON, 2.2f, 3);
        Fx.particle(behind, Particle.END_ROD, 10, 0.6, 0.02);
        Fx.sound(behind, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
        for (LivingEntity e : combat().nearby(player, behind, 2.2)) {
            if (e != target) {
                combat().damage(player, e, 4, true);
            }
        }
    }

    /**
     * Gather the Order: you raise the blade to the moon for a moment, then every member of your
     * faction within 40 blocks is drawn to your side in a pillar of moonlight and healed. With
     * nobody to gather, the moon shields you instead (Absorption II).
     */
    @Override
    public boolean primary(Player player) {
        Location at = player.getLocation();
        fx().castCircle(at, MOON, 2.6f);
        fx().beam(at.clone().add(0, 20, 0), at, PALE, 0.6f, 30);
        Fx.sound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
        Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false, false));
        List<Player> order = new ArrayList<>();
        for (Player p : player.getWorld().getPlayers()) {
            if (p != player && plugin.arcs().allies(player, p) && !p.isDead() && p.getLocation().distanceSquared(at) < 40 * 40) {
                order.add(p);
                // the summoned see the moon call them first
                fx().beam(p.getLocation().add(0, 14, 0), p.getLocation(), MOON, 0.4f, 30);
                tell(p, net.kyori.adventure.text.Component.text("☾ The moon calls you to " + player.getName() + ".",
                        net.kyori.adventure.text.format.TextColor.color(MOON)));
            }
        }
        displays().later(30, () -> {
            if (!alive(player)) {
                return;
            }
            Location here = player.getLocation();
            if (order.isEmpty()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 1, false, false, true));
                fx().flash(here.clone().add(0, 1, 0), PALE, 3f);
                Fx.sound(here, Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1f, 1.4f);
                return;
            }
            for (int i = 0; i < order.size(); i++) {
                Player p = order.get(i);
                if (!p.isOnline() || p.isDead() || p.getWorld() != here.getWorld()) {
                    continue;
                }
                double a = i * Math.PI * 2 / order.size();
                Location spot = Combat.safeSpot(here.clone().add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8));
                if (spot == null) {
                    spot = here.clone();
                }
                spot.setYaw(p.getLocation().getYaw());
                spot.setPitch(p.getLocation().getPitch());
                Fx.particle(p.getLocation().add(0, 1, 0), Particle.END_ROD, 20, 0.4, 0.1);
                p.teleport(spot, PlayerTeleportEvent.TeleportCause.PLUGIN);
                combat().heal(p, 4);
                fx().beam(spot.clone().add(0, 16, 0), spot, PALE, 0.7f, 16);
                fx().castCircle(spot, MOON, 1.2f);
                Fx.sound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.6f);
            }
            fx().shockwave(here.clone().add(0, 0.05, 0), MOON, 4f, 8);
        });
        return true;
    }

    /**
     * Full Moon: a full moon rises over you for eight seconds. Your faction within twelve blocks
     * regenerates and runs faster; enemies within twelve blocks are revealed (they glow and lose
     * invisibility) and slowed.
     */
    @Override
    public boolean secondary(Player player) {
        Location at = player.getLocation();
        // up and ahead of you, where you can see it rise
        Vector ahead = at.getDirection().setY(0);
        ahead = ahead.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : ahead.normalize();
        Location sky = at.clone().add(ahead.multiply(4)).add(0, 5.5, 0);
        sky.setYaw(0);
        sky.setPitch(0);
        ItemDisplay moon = fx().orb(sky.clone().subtract(0, 5, 0), PALE, 0.6f, FULL_MOON_TICKS + 20);
        moon.setTeleportDuration(20);
        ItemDisplay halo = fx().sigil(sky, at.toVector().subtract(sky.toVector()), MOON, 3.2f, FULL_MOON_TICKS + 20);
        halo.setTeleportDuration(20);
        displays().later(1, () -> {
            if (moon.isValid()) {
                moon.teleport(sky);
                moon.setInterpolationDelay(0);
                moon.setInterpolationDuration(20);
                moon.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(4.5f), new Quaternionf()));
            }
        });
        fx().castCircle(at, MOON, (float) MOON_RADIUS);
        Fx.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f);
        Fx.sound(at, Sound.ENTITY_WOLF_ANGRY_AMBIENT, 1f, 0.7f);

        List<LivingEntity> revealed = new ArrayList<>();
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (t > FULL_MOON_TICKS || !alive(player)) {
                    displays().remove(moon);
                    displays().remove(halo);
                    for (LivingEntity e : revealed) {
                        if (e.isValid() && !(e instanceof Player p && plugin.arcs().factions().isLeader(p))) {
                            e.setGlowing(false);
                        }
                    }
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                if (t % 4 == 0) {
                    // moonlight falling
                    java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
                    for (int i = 0; i < 4; i++) {
                        double a = r.nextDouble(Math.PI * 2);
                        double d = r.nextDouble(MOON_RADIUS);
                        Fx.particle(center.clone().add(Math.cos(a) * d, 4 + r.nextDouble(2), Math.sin(a) * d), Particle.END_ROD, 1, 0, 0.02);
                    }
                }
                if (t % 20 != 0) {
                    return;
                }
                for (Player p : center.getNearbyPlayers(MOON_RADIUS)) {
                    if (p == player || plugin.arcs().allies(player, p)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, true));
                        Fx.particle(p.getLocation().add(0, 2, 0), Particle.DUST, 4, 0.3, 0, Fx.dust(MOON, 0.9f));
                    }
                }
                for (LivingEntity e : combat().nearby(player, center, MOON_RADIUS)) {
                    e.removePotionEffect(PotionEffectType.INVISIBILITY);
                    applyEffect(e, PotionEffectType.SLOWNESS, 40, 0);
                    if (!e.isGlowing()) {
                        e.setGlowing(true);
                        revealed.add(e);
                    }
                    Fx.particle(Combat.center(e), Particle.DUST, 4, 0.3, 0, Fx.dust(NIGHT, 1f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
