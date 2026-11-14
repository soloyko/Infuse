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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fallen God's Spear, the Spear of the Covenant (Arc II faction weapon of the Covenant of the Spear).
 * Passive Longreach, on-hit Skewer, F Godspear Lunge, Sneak+F War Banner.
 */
public final class GodSpearWeapon extends SwordAbility {

    private static final int RED = 0xD83A3A;
    private static final int DARK = 0x2A0E0E;
    private static final int BANNER_TICKS = 300;
    private static final double BANNER_RADIUS = 8;

    public GodSpearWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.GODSPEAR);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ENTITY_INTERACTION_RANGE, "longreach", 1.5, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (chance(0.2)) {
            hold(target, 20);
            Fx.particle(Combat.center(target), Particle.CRIT, 12, 0.3, 0.2);
            Fx.sound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.9f);
        }
    }

    /** Godspear Lunge: a long lunge; the first enemy is skewered and carried to the end of it. */
    @Override
    public boolean primary(Player player) {
        Vector dir = dev.infuse.customs.ability.Aim.flatLook(player);
        status().protectFromFall(player, 40);
        Fx.sound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 0.8f);

        new BukkitRunnable() {
            int ticks;
            LivingEntity skewered;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 9) {
                    finish();
                    return;
                }
                Combat.setVelocity(player, dir.clone().multiply(1.35).setY(Math.min(0.1, player.getVelocity().getY())));
                Location chest = player.getLocation().add(0, 1, 0);
                Fx.particle(chest, Particle.DUST, 4, 0.3, 0, Fx.dust(ticks % 2 == 0 ? RED : DARK, 1.3f));
                fx().beam(chest, chest.clone().add(dir.clone().multiply(2.5)), RED, 0.2f, 2);
                if (skewered == null) {
                    for (LivingEntity target : combat().nearby(player, chest.clone().add(dir.clone().multiply(1.8)), 1.4)) {
                        skewered = target;
                        combat().damage(player, target, 4, true);
                        Fx.sound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.2f, 0.7f);
                        break;
                    }
                } else if (skewered.isValid()) {
                    // carried along on the spear's point
                    Location tip = player.getLocation().add(dir.clone().multiply(1.8));
                    Combat.setVelocity(skewered, tip.toVector().subtract(skewered.getLocation().toVector()).multiply(0.8).setY(0.1));
                }
            }

            private void finish() {
                cancel();
                if (skewered != null && skewered.isValid() && !skewered.isDead() && alive(player)) {
                    combat().damage(player, skewered, 7, true);
                    Combat.setVelocity(skewered, dir.clone().multiply(0.6).setY(0.6));
                    fx().burst(Combat.center(skewered), RED, 2.2f);
                    fx().crack(Combat.ground(skewered.getLocation().add(0, 0.5, 0)), RED, 2f, 20);
                    Fx.sound(skewered.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /**
     * War Banner: you plant a war banner for fifteen seconds. Your faction near it regenerates and
     * hits harder; enemies near it are weakened.
     */
    @Override
    public boolean secondary(Player player) {
        Location ground = Combat.ground(player.getLocation().add(0, 0.5, 0));
        Location at = ground.clone().add(0, 0.5, 0);
        at.setYaw(0);
        at.setPitch(0);
        BlockDisplay pole = displays().block(at.clone().add(0, 0.9, 0), Material.DARK_OAK_FENCE.createBlockData(),
                new Vector3f(0.6f, 2.6f, 0.6f), new Quaternionf(), BANNER_TICKS + 10);
        BlockDisplay flag = displays().block(at.clone().add(0.45, 2.2, 0), Material.RED_WOOL.createBlockData(),
                new Vector3f(0.9f, 1.2f, 0.08f), new Quaternionf(), BANNER_TICKS + 10);
        flag.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        fx().castCircle(ground, RED, (float) BANNER_RADIUS);
        Fx.sound(at, Sound.BLOCK_WOOL_PLACE, 1.2f, 0.7f);
        Fx.sound(at, Sound.EVENT_RAID_HORN, 1f, 0.9f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > BANNER_TICKS || ground.getWorld() != player.getWorld()) {
                    displays().remove(pole);
                    displays().remove(flag);
                    Fx.particle(at.clone().add(0, 1, 0), Particle.CLOUD, 12, 0.4, 0.05);
                    cancel();
                    return;
                }
                if (ticks % 4 == 0) {
                    double a = ticks * 0.05;
                    for (int k = 0; k < 6; k++) {
                        double ang = a + k * Math.PI / 3;
                        Fx.particle(ground.clone().add(Math.cos(ang) * BANNER_RADIUS, 0.2, Math.sin(ang) * BANNER_RADIUS),
                                Particle.DUST, 1, 0, 0, Fx.dust(RED, 1.2f));
                    }
                }
                if (ticks % 20 != 0) {
                    return;
                }
                for (Player p : ground.getNearbyPlayers(BANNER_RADIUS)) {
                    if (p == player || plugin.arcs().allies(player, p)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false, true));
                    }
                }
                for (LivingEntity enemy : combat().nearby(player, ground, BANNER_RADIUS)) {
                    applyEffect(enemy, PotionEffectType.WEAKNESS, 40, 0);
                    Fx.particle(Combat.center(enemy), Particle.DUST, 3, 0.3, 0, Fx.dust(DARK, 1.0f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
