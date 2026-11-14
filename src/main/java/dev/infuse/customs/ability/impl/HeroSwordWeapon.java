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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Hero Sword, the Blade of the Order (Arc II faction weapon of the Order of the Blade).
 * Passive Vanguard, on-hit Shieldbreaker, F Aegis, Sneak+F Rally.
 */
public final class HeroSwordWeapon extends SwordAbility implements org.bukkit.event.Listener {

    private static final int BLUE = 0x4DA3FF;
    private static final int GOLD = 0xF2C94C;

    public HeroSwordWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.HEROSWORD);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.RESISTANCE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        applyEffect(target, PotionEffectType.WEAKNESS, 60, 0);
        applyEffect(target, PotionEffectType.SLOWNESS, 60, 1);
        Location at = Combat.center(target);
        fx().burst(at, GOLD, 1.6f);
        Fx.sound(at, Sound.ITEM_SHIELD_BREAK, 1f, 1f);
    }

    private static final double AEGIS_ABSORB = 8;
    private static final int AEGIS_TICKS = 200;

    /** A shield of light on a player: how much it still soaks up, and until when. */
    private static final class Aegis {
        final Player caster;
        double left = AEGIS_ABSORB;
        final int until;
        final List<org.bukkit.entity.ItemDisplay> plates = new java.util.ArrayList<>();

        Aegis(Player caster, int until) {
            this.caster = caster;
            this.until = until;
        }
    }

    private final java.util.Map<java.util.UUID, Aegis> shields = new java.util.HashMap<>();

    /**
     * Aegis: a shield of light settles on the ally you aim at (or on you). It soaks up the next 8
     * damage for ten seconds; when it breaks it bursts, blinding the enemies around them.
     */
    @Override
    public boolean primary(Player player) {
        Player ally = allyInSight(player);
        Player warded = ally == null ? player : ally;
        Aegis old = shields.remove(warded.getUniqueId());
        if (old != null) {
            old.plates.forEach(displays()::remove);
        }
        Aegis aegis = new Aegis(player, org.bukkit.Bukkit.getCurrentTick() + AEGIS_TICKS);
        shields.put(warded.getUniqueId(), aegis);
        Location at = warded.getLocation().add(0, 1, 0);
        for (int i = 0; i < 3; i++) {
            aegis.plates.add(fx().sigil(at, new Vector(0, 0, 1), i == 1 ? BLUE : GOLD, 0.85f, AEGIS_TICKS + 10));
        }
        if (ally != null) {
            fx().beam(player.getEyeLocation().subtract(0, 0.3, 0), at, GOLD, 0.25f, 8);
        }
        fx().flash(at, 0xFFF3C4, 2.6f);
        fx().castCircle(warded.getLocation(), GOLD, 1.6f);
        Fx.sound(at, Sound.ITEM_ARMOR_EQUIP_GOLD, 1.2f, 0.8f);
        Fx.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);
        if (ally != null) {
            tell(ally, net.kyori.adventure.text.Component.text(player.getName() + " shields you with Aegis.",
                    net.kyori.adventure.text.format.TextColor.color(GOLD)));
        }

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (shields.get(warded.getUniqueId()) != aegis || !warded.isOnline() || warded.isDead()
                        || aegis.until < org.bukkit.Bukkit.getCurrentTick()) {
                    if (shields.get(warded.getUniqueId()) == aegis) {
                        shields.remove(warded.getUniqueId());
                        fade(warded, aegis);
                    }
                    cancel();
                    return;
                }
                // three plates of light circle them, closing in as the shield wears down
                double radius = 0.75 + 0.35 * (aegis.left / AEGIS_ABSORB);
                for (int i = 0; i < aegis.plates.size(); i++) {
                    org.bukkit.entity.ItemDisplay plate = aegis.plates.get(i);
                    double a = t * 0.14 + i * Math.PI * 2 / 3;
                    Vector out = new Vector(Math.cos(a), 0, Math.sin(a));
                    Location spot = warded.getLocation().add(out.clone().multiply(radius)).add(0, 1.0, 0);
                    spot.setDirection(out);
                    if (plate.isValid()) {
                        plate.teleport(spot);
                    }
                }
                if (t % 8 == 0) {
                    Fx.particle(warded.getLocation().add(0, 1, 0), Particle.DUST, 3, 0.4, 0, Fx.dust(GOLD, 0.8f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** The faction ally (or any player while there are no factions) under the crosshair, if close. */
    private Player allyInSight(Player player) {
        org.bukkit.util.RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), 20, 0.6, e -> e instanceof Player p && p != player
                        && p.getGameMode() != org.bukkit.GameMode.SPECTATOR);
        if (hit != null && hit.getHitEntity() instanceof Player other) {
            boolean warring = plugin.arcs().factions().active();
            if (!warring || plugin.arcs().allies(player, other)) {
                return other;
            }
        }
        return null;
    }

    /** The shield soaks up damage; when it runs out it bursts. */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player warded)) {
            return;
        }
        Aegis aegis = shields.get(warded.getUniqueId());
        if (aegis == null || aegis.until < org.bukkit.Bukkit.getCurrentTick()) {
            return;
        }
        double damage = event.getDamage();
        double soaked = Math.min(damage, aegis.left);
        aegis.left -= soaked;
        event.setDamage(damage - soaked);
        Location at = warded.getLocation().add(0, 1, 0);
        fx().burst(at, GOLD, 1.4f);
        Fx.sound(at, Sound.ITEM_SHIELD_BLOCK, 1f, 1.4f);
        if (aegis.left <= 0.01) {
            shields.remove(warded.getUniqueId());
            shatter(warded, aegis);
        }
    }

    /** Broken: a blinding burst of light around the warded player. */
    private void shatter(Player warded, Aegis aegis) {
        Location at = warded.getLocation().add(0, 1, 0);
        aegis.plates.forEach(displays()::remove);
        fx().flash(at, 0xFFF3C4, 4.5f);
        fx().shockwave(warded.getLocation().add(0, 0.05, 0), GOLD, 4.5f, 8);
        fx().wave(at, new Vector(0, 1, 0), BLUE, 3.5f, 6);
        Fx.particle(at, Particle.END_ROD, 30, 0.5, 0.15);
        Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.4f);
        Fx.sound(at, Sound.ITEM_TOTEM_USE, 0.4f, 1.8f);
        for (LivingEntity enemy : combat().nearby(aegis.caster, warded.getLocation(), 4.5)) {
            if (enemy == warded) {
                continue;
            }
            applyEffect(enemy, PotionEffectType.BLINDNESS, 60, 0);
            applyEffect(enemy, PotionEffectType.SLOWNESS, 40, 1);
            Combat.knockback(enemy, warded.getLocation(), 0.6, 0.25);
        }
    }

    /** Ran out of time unbroken: the plates just fade. */
    private void fade(Player warded, Aegis aegis) {
        for (org.bukkit.entity.ItemDisplay plate : aegis.plates) {
            Fx.particle(plate.getLocation(), Particle.DUST, 5, 0.1, 0, Fx.dust(GOLD, 0.8f));
            displays().remove(plate);
        }
    }

    @Override
    public void shutdown() {
        for (Aegis a : shields.values()) {
            a.plates.forEach(displays()::remove);
        }
        shields.clear();
    }

    /**
     * Rally: a giant spectral Hero Sword plunges into the ground before you; you and your faction
     * nearby gain Strength, Speed and a quick heal, and enemies close by are thrown back.
     */
    @Override
    public boolean secondary(Player player) {
        Location at = player.getLocation();
        Vector facing = dev.infuse.customs.ability.Aim.flatLook(player);
        // ahead and a little to the right, so it doesn't stand in the middle of your view
        Location plant = Combat.ground(at.clone().add(facing.clone().multiply(2.4)).add(Fx.right(facing).multiply(1.3)).add(0, 0.5, 0));
        Location sky = plant.clone().add(0, 9, 0);
        sky.setYaw(0);
        sky.setPitch(0);
        // the sword, point down, falling from the sky
        org.joml.Quaternionf down = new org.joml.Quaternionf().rotateY((float) Math.atan2(-facing.getX(), facing.getZ())).rotateZ((float) Math.PI);
        org.bukkit.entity.ItemDisplay sword = displays().item(sky, plugin.items().create(type), 4.2f, down,
                org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE, 70);
        sword.setGlowing(true);
        sword.setGlowColorOverride(org.bukkit.Color.fromRGB(GOLD));
        sword.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        sword.setTeleportDuration(4);
        Fx.sound(at, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
        displays().later(1, () -> {
            if (sword.isValid()) {
                sword.teleport(plant.clone().add(0, 1.6, 0).setDirection(new Vector(0, 0, 1)));
            }
        });
        displays().later(5, () -> {
            if (!alive(player)) {
                return;
            }
            rally(player, plant);
        });
        // it lingers, then sinks into the ground in a shower of light
        displays().later(50, () -> {
            if (sword.isValid()) {
                sword.setGlowing(false); // the outline would show through the ground
                sword.setTeleportDuration(12);
                sword.teleport(plant.clone().add(0, -1.5, 0).setDirection(new Vector(0, 0, 1)));
                Fx.particle(plant.clone().add(0, 0.3, 0), Particle.END_ROD, 20, 0.4, 0.08);
            }
        });
        return true;
    }

    private void rally(Player player, Location plant) {
        Location up = plant.clone().add(0, 0.2, 0);
        fx().beam(plant.clone().add(0, 14, 0), plant, GOLD, 1.2f, 16);
        fx().flash(plant.clone().add(0, 1.4, 0), 0xFFF3C4, 4f);
        fx().shockwave(up, GOLD, 7f, 10);
        fx().shockwave(up, BLUE, 4.5f, 7);
        fx().crack(plant, GOLD, 3.2f, 50);
        fx().castCircle(plant, BLUE, 3.5f);
        displays().eruptGround(plant, 0.6f, 0.7f);
        Fx.sound(plant, Sound.EVENT_RAID_HORN, 1.2f, 1.4f);
        Fx.sound(plant, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
        Fx.sound(plant, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        Fx.sound(plant, Sound.ITEM_TOTEM_USE, 0.4f, 1.5f);
        for (Player p : plant.getNearbyPlayers(12)) {
            if (p != player && !plugin.arcs().allies(player, p)) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, false, false, true));
            combat().heal(p, 4);
            fx().beam(plant.clone().add(0, 2.2, 0), p.getLocation().add(0, 1, 0), GOLD, 0.15f, 8);
            fx().castCircle(p.getLocation(), GOLD, 1.2f);
            Fx.particle(p.getLocation().add(0, 2, 0), Particle.HEART, 2, 0.3, 0);
            fx().helix(p.getLocation(), 0.7, 2.2, 2, Particle.DUST, Fx.dust(GOLD, 1.0f), 16);
        }
        for (LivingEntity enemy : combat().nearby(player, plant, 5)) {
            Combat.knockback(enemy, plant, 1.0, 0.4);
            Fx.particle(Combat.center(enemy), Particle.CRIT, 10, 0.3, 0.2);
        }
    }
}
