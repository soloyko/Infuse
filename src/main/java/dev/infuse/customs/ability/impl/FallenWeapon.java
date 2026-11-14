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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Fallen God's Sword, the Blade of the Covenant (Arc II faction weapon of the Covenant of the Fallen).
 * Passive Longreach, on-hit Pinning Strike, F Godfall Lunge, Sneak+F War Banner.
 */
public final class FallenWeapon extends SwordAbility {

    private static final int RED = 0xD83A3A;
    private static final int DARK = 0x2A0E0E;
    private static final int BANNER_TICKS = 300;
    private static final double BANNER_RADIUS = 8;

    public FallenWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.FALLEN);
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
            Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.8f);
        }
    }

    /** Godfall Lunge: a long lunge; the first enemy is run through, carried to the end of it and pinned by crimson spikes. */
    @Override
    public boolean primary(Player player) {
        Vector dir = dev.infuse.customs.ability.Aim.flatLook(player);
        status().protectFromFall(player, 40);
        Fx.sound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 0.8f);
        Fx.sound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.6f, 0.7f);
        fx().wave(player.getLocation().add(0, 1, 0), dir, RED, 1.8f, 5);

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
                if (ticks % 3 == 0) {
                    fx().afterimage(chest, plugin.items().create(type), dir, RED, 1.0f, 5);
                }
                if (skewered == null) {
                    for (LivingEntity target : combat().nearby(player, chest.clone().add(dir.clone().multiply(1.8)), 1.4)) {
                        skewered = target;
                        combat().damage(player, target, 4, true);
                        fx().burst(Combat.center(target), RED, 1.6f);
                        Fx.particle(Combat.center(target), Particle.DAMAGE_INDICATOR, 6, 0.3, 0.1);
                        Fx.sound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.2f, 0.7f);
                        break;
                    }
                } else if (skewered.isValid()) {
                    // carried along on the blade's point
                    Location tip = player.getLocation().add(dir.clone().multiply(1.8));
                    Combat.setVelocity(skewered, tip.toVector().subtract(skewered.getLocation().toVector()).multiply(0.8).setY(0.1));
                    Fx.particle(Combat.center(skewered), Particle.DUST, 3, 0.2, 0, Fx.dust(RED, 1.1f));
                }
            }

            private void finish() {
                cancel();
                if (skewered != null && skewered.isValid() && !skewered.isDead() && alive(player)) {
                    combat().damage(player, skewered, 7, true);
                    Combat.setVelocity(skewered, dir.clone().multiply(0.6).setY(0.6));
                    Location ground = Combat.ground(skewered.getLocation().add(0, 0.5, 0));
                    fx().impact(Combat.center(skewered), RED, 2.2f);
                    fx().crack(ground, RED, 2.2f, 30);
                    // crimson spikes erupt around the victim
                    org.bukkit.block.data.BlockData blood = Material.RED_STAINED_GLASS.createBlockData();
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2 + Math.PI / 4;
                        fx().spike(ground.clone().add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9), blood, 1.6f, 0.45f, 0.35, 18);
                    }
                    Fx.sound(skewered.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
                    Fx.sound(skewered.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1f, 0.6f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** The Covenant's war banner: red, a black border and a gold sword down the middle. */
    private static ItemStack banner() {
        ItemStack item = ItemStack.of(Material.RED_BANNER);
        item.editMeta(org.bukkit.inventory.meta.BannerMeta.class, meta -> {
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.BLACK, org.bukkit.block.banner.PatternType.GRADIENT_UP));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.YELLOW, org.bukkit.block.banner.PatternType.STRIPE_CENTER));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.YELLOW, org.bukkit.block.banner.PatternType.TRIANGLE_TOP));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.RED, org.bukkit.block.banner.PatternType.CURLY_BORDER));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.BLACK, org.bukkit.block.banner.PatternType.BORDER));
        });
        return item;
    }

    /**
     * War Banner: you plant the Covenant's war banner for fifteen seconds. Your faction near it
     * regenerates and hits harder; enemies near it are weakened.
     */
    @Override
    public boolean secondary(Player player) {
        Vector facing = dev.infuse.customs.ability.Aim.flatLook(player);
        // ahead and a little to the right, so it doesn't stand in the middle of your view
        Location ground = Combat.ground(player.getLocation().add(facing.clone().multiply(1.8)).add(Fx.right(facing).multiply(1.3)).add(0, 0.5, 0));
        Location at = ground.clone().add(0, 2.1, 0);
        at.setYaw(0);
        at.setPitch(0);
        float yaw = (float) Math.atan2(-facing.getX(), facing.getZ()) + (float) Math.PI;
        org.bukkit.entity.ItemDisplay flag = displays().item(at.clone().add(0, 4, 0), banner(), 2.0f,
                new org.joml.Quaternionf().rotateY(yaw), org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED, BANNER_TICKS + 20);
        flag.setTeleportDuration(4);
        displays().later(1, () -> {
            if (flag.isValid()) {
                flag.teleport(at);
            }
        });
        // the standard: a dark pole from the ground and a gold crossbar the flag hangs from
        org.joml.Quaternionf turn = new org.joml.Quaternionf().rotateY(yaw);
        Vector back = facing.clone().multiply(0.12);
        org.bukkit.entity.BlockDisplay[] standard = new org.bukkit.entity.BlockDisplay[2];
        displays().later(5, () -> {
            Location pole = ground.clone().add(back).add(0, 1.7, 0);
            pole.setYaw(0);
            pole.setPitch(0);
            standard[0] = displays().block(pole, Material.DARK_OAK_LOG.createBlockData(), new org.joml.Vector3f(0.14f, 3.4f, 0.14f),
                    turn, BANNER_TICKS + 10);
            Location bar = ground.clone().add(0, 3.22, 0);
            bar.setYaw(0);
            bar.setPitch(0);
            standard[1] = displays().block(bar, Material.GOLD_BLOCK.createBlockData(), new org.joml.Vector3f(1.15f, 0.1f, 0.1f),
                    turn, BANNER_TICKS + 10);
        });
        displays().later(5, () -> {
            fx().shockwave(ground.clone().add(0, 0.05, 0), RED, (float) BANNER_RADIUS, 10);
            fx().crack(ground, RED, 2.4f, 40);
            displays().eruptGround(ground, 0.6f, 0.6f);
            fx().flash(ground.clone().add(0, 1.5, 0), RED, 3f);
            Fx.sound(ground, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
        });
        fx().castCircle(ground, RED, (float) BANNER_RADIUS);
        Fx.sound(at, Sound.BLOCK_WOOL_PLACE, 1.2f, 0.7f);
        Fx.sound(at, Sound.EVENT_RAID_HORN, 1f, 0.9f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > BANNER_TICKS || ground.getWorld() != player.getWorld()) {
                    if (flag.isValid()) {
                        flag.setTeleportDuration(10);
                        flag.teleport(at.clone().add(0, -3.5, 0));
                    }
                    displays().later(11, () -> displays().remove(flag));
                    for (org.bukkit.entity.BlockDisplay part : standard) {
                        if (part != null) {
                            displays().remove(part);
                        }
                    }
                    Fx.particle(ground.clone().add(0, 1, 0), Particle.CLOUD, 12, 0.4, 0.05);
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
                    // embers rising from the banner
                    Fx.particle(at.clone().add(0, 0.5, 0), Particle.DUST, 2, 0.4, 0, Fx.dust(ticks % 8 == 0 ? 0xF2C94C : RED, 0.9f));
                }
                if (ticks % 20 != 0) {
                    return;
                }
                fx().shockwave(ground.clone().add(0, 0.05, 0), RED, (float) BANNER_RADIUS, 14);
                for (Player p : ground.getNearbyPlayers(BANNER_RADIUS)) {
                    if (p == player || plugin.arcs().allies(player, p)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false, true));
                        Fx.particle(p.getLocation().add(0, 0.2, 0), Particle.DUST, 8, 0.35, 0, Fx.dust(RED, 1.0f));
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
