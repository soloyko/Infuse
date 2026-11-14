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
 * Passive Vanguard, on-hit Shieldbreaker, F Heroic Charge, Sneak+F Rally.
 */
public final class HeroSwordWeapon extends SwordAbility {

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

    /** Heroic Charge: a short charge behind a shield of light; the first enemy in the way is bowled over and stunned. */
    @Override
    public boolean primary(Player player) {
        Vector dir = dev.infuse.customs.ability.Aim.flatLook(player);
        status().protectFromFall(player, 40);
        Fx.sound(player.getLocation(), Sound.ENTITY_HORSE_GALLOP, 1.2f, 0.8f);
        Fx.sound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.2f);
        Fx.sound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.4f);
        // a shield sigil leads the charge
        org.bukkit.entity.ItemDisplay shield = fx().sigil(player.getLocation().add(0, 1.1, 0).add(dir.clone().multiply(1.1)), dir, GOLD, 1.5f, 14);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 10) {
                    displays().remove(shield);
                    cancel();
                    return;
                }
                Combat.setVelocity(player, dir.clone().multiply(1.25).setY(Math.min(0.1, player.getVelocity().getY())));
                Location chest = player.getLocation().add(0, 1, 0);
                if (shield.isValid()) {
                    Location front = chest.clone().add(0, 0.1, 0).add(dir.clone().multiply(1.1));
                    front.setYaw(0);
                    front.setPitch(0);
                    shield.teleport(front);
                }
                Fx.particle(chest, Particle.DUST, 4, 0.3, 0, Fx.dust(ticks % 2 == 0 ? BLUE : GOLD, 1.3f));
                Location feet = player.getLocation();
                org.bukkit.block.Block below = feet.clone().subtract(0, 0.2, 0).getBlock();
                if (below.getType().isSolid()) {
                    Fx.particle(feet.clone().add(0, 0.1, 0), Particle.BLOCK, 6, 0.3, 0.05, below.getBlockData());
                }
                if (ticks % 2 == 0) {
                    fx().afterimage(chest, plugin.items().create(type), dir, BLUE, 1.0f, 6);
                }
                for (LivingEntity target : combat().nearby(player, chest.clone().add(dir.clone().multiply(0.8)), 1.6)) {
                    combat().damage(player, target, 6, true);
                    Combat.setVelocity(target, dir.clone().multiply(1.4).setY(0.5));
                    hold(target, 30);
                    applyEffect(target, PotionEffectType.SLOWNESS, 40, 4);
                    Location at = Combat.center(target);
                    fx().impact(at, GOLD, 2.2f);
                    fx().wave(at, dir, BLUE, 2.6f, 6);
                    fx().cut(at, dir, Math.PI / 2, 0.4, BLUE, 1.8f, 2);
                    fx().crack(Combat.ground(target.getLocation().add(0, 0.5, 0)), GOLD, 1.8f, 30);
                    // stunned: little stars over the head
                    for (int i = 0; i < 3; i++) {
                        int k = i;
                        displays().later(4 + i * 6, () -> {
                            if (target.isValid()) {
                                Fx.particle(target.getEyeLocation().add(0, 0.6, 0), Particle.WAX_OFF, 4, 0.3, 0.02);
                                if (k == 0) {
                                    Fx.sound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
                                }
                            }
                        });
                    }
                    Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
                    Fx.sound(at, Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
                    Fx.sound(at, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.4f);
                    Combat.setVelocity(player, new Vector());
                    displays().remove(shield);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
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
