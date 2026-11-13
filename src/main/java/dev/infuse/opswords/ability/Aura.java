package dev.infuse.opswords.ability;

import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Faint themed particles around a held sword, shown to everyone except the holder (in first
 * person they would spawn right in front of the camera). Runs every 10 ticks.
 */
public final class Aura {

    private Aura() {
    }

    public static void play(Player player, SwordType type, long tick) {
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY) || player.isInvisible()
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return; // never give away an invisible player
        }
        emit(player, swordPoint(player), type, tick);
    }

    /** The same themed particles at a fixed spot (e.g. above an altar), visible to everyone. */
    public static void ambient(Location at, SwordType type) {
        emit(null, at, type, at.getWorld().getGameTime() / 10);
    }

    private static void emit(Player player, Location at, SwordType type, long tick) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (type) {
            case DRAGON -> show(player, Particle.DRAGON_BREATH, at, 1, 0.15, 0.25, 0.15, 0.005, 0.6f);
            case VOID -> show(player, Particle.REVERSE_PORTAL, at, 2, 0.15, 0.3, 0.15, 0.01);
            case PHOENIX -> show(player, Particle.SMALL_FLAME, at, 2, 0.12, 0.3, 0.12, 0.005);
            case FROST -> show(player, Particle.SNOWFLAKE, at, 1, 0.15, 0.3, 0.15, 0.005);
            case STORM -> {
                if (r.nextInt(3) == 0) {
                    show(player, Particle.ELECTRIC_SPARK, at, 2, 0.15, 0.3, 0.15, 0.05);
                }
            }
            case VERDANT -> {
                if (tick % 2 == 0) {
                    show(player, Particle.HAPPY_VILLAGER, at, 1, 0.15, 0.3, 0.15, 0);
                }
            }
            case CRIMSON -> show(player, Particle.DUST, at, 2, 0.12, 0.3, 0.12, 0, Fx.dust(0xB32E49, 0.7f));
            case SOLAR -> show(player, Particle.WAX_ON, at, 1, 0.15, 0.3, 0.15, 0);
            case SHADOW -> show(player, Particle.SMOKE, at, 2, 0.12, 0.3, 0.12, 0.005);
            case COSMIC -> show(player, r.nextBoolean() ? Particle.END_ROD : Particle.ENCHANT,
                    at, 1, 0.15, 0.3, 0.15, 0.005);
            case TIDE -> show(player, Particle.FALLING_WATER, at, 2, 0.15, 0.3, 0.15, 0);
            case PLAGUE -> show(player, Particle.ENTITY_EFFECT, at, 1, 0.15, 0.3, 0.15, 0, org.bukkit.Color.fromRGB(0x9CD64A));
            case CHRONO -> {
                if (tick % 2 == 0) {
                    show(player, Particle.ENCHANT, at, 3, 0.2, 0.3, 0.2, 0.3);
                }
            }
            case SOUL -> show(player, Particle.SOUL, at, 1, 0.15, 0.3, 0.15, 0.01);
            case ECHO -> show(player, Particle.SCULK_CHARGE_POP, at, 1, 0.15, 0.3, 0.15, 0.005);
            case HAMMER -> {
                if (tick % 2 == 0) {
                    show(player, Particle.SMALL_FLAME, at, 1, 0.12, 0.25, 0.12, 0.003);
                }
            }
            case SCYTHE -> show(player, Particle.SOUL_FIRE_FLAME, at, 1, 0.15, 0.3, 0.15, 0.003);
            case SPEAR -> show(player, Particle.CRIT, at, 1, 0.12, 0.35, 0.12, 0.05);
            case CHAKRAM -> show(player, Particle.SMALL_GUST, at, 1, 0.15, 0.3, 0.15, 0);
            case AXE -> show(player, Particle.DUST, at, 2, 0.12, 0.3, 0.12, 0, Fx.dust(0xC77DFF, 0.7f));
            case STAFF -> show(player, Particle.WITCH, at, 2, 0.15, 0.3, 0.15, 0);
            case SHURIKEN -> {
                if (tick % 2 == 0) {
                    show(player, Particle.CHERRY_LEAVES, at, 1, 0.15, 0.3, 0.15, 0);
                }
            }
            case HALBERD -> show(player, Particle.DUST, at, 1, 0.12, 0.35, 0.12, 0, Fx.dust(tick % 2 == 0 ? 0x3FBF7F : 0xE8C35A, 0.8f));
            case WARGLAIVE -> {
                show(player, Particle.DUST, at, 1, 0.12, 0.35, 0.12, 0, Fx.dust(0x2B2F6B, 0.9f));
                if (tick % 3 == 0) {
                    show(player, Particle.END_ROD, at, 1, 0.12, 0.35, 0.12, 0.005);
                }
            }
            case SAWBLADE -> {
                if (tick % 2 == 0) {
                    show(player, Particle.ELECTRIC_SPARK, at, 1, 0.1, 0.3, 0.1, 0.05);
                }
            }
            case RAPIER -> {
                if (tick % 2 == 0) {
                    show(player, Particle.DUST, at, 1, 0.08, 0.35, 0.08, 0, Fx.dust(0xF5E6B8, 0.7f));
                }
            }
            case KHOPESH -> show(player, Particle.FALLING_DUST, at, 1, 0.12, 0.3, 0.12, 0, org.bukkit.Material.SAND.createBlockData());
            case TRIDENT -> {
                show(player, Particle.DRIPPING_WATER, at, 1, 0.12, 0.35, 0.12, 0);
                if (tick % 3 == 0) {
                    show(player, Particle.NAUTILUS, at, 1, 0.15, 0.3, 0.15, 0.2);
                }
            }
        }
    }

    private static void show(Player holder, Particle particle, Location at, int count, double dx, double dy, double dz, double speed) {
        show(holder, particle, at, count, dx, dy, dz, speed, null);
    }

    private static <T> void show(Player holder, Particle particle, Location at, int count, double dx, double dy, double dz,
                                 double speed, T data) {
        for (Player viewer : at.getWorld().getPlayers()) {
            if (viewer != holder && (holder == null || viewer.canSee(holder))
                    && viewer.getLocation().distanceSquared(at) < 48 * 48) {
                viewer.spawnParticle(particle, at, count, dx, dy, dz, speed, data);
            }
        }
    }

    /** Roughly where the blade is when held in the main hand. */
    private static Location swordPoint(Player player) {
        Location base = player.getLocation();
        Vector forward = base.getDirection().setY(0);
        if (forward.lengthSquared() < 1.0E-4) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        if (player.getMainHand() == org.bukkit.inventory.MainHand.LEFT) {
            right.multiply(-1);
        }
        return base.add(right.multiply(0.45)).add(forward.multiply(0.35)).add(0, 1.0, 0);
    }
}
