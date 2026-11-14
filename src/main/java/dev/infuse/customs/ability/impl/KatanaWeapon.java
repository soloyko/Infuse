package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Katana, the Ronin's Katana: patience, then one perfect stroke.
 * Passive Iaido, on-hit Bleeding Edge, F Draw Cut, Sneak+F Moonlit Duel.
 */
public final class KatanaWeapon extends SwordAbility implements Listener {

    private static final int STEEL = 0xE6E9F0;
    private static final int RED = 0xD32F2F;
    private static final int MOON = 0xCFD8FF;
    /** Ticks without attacking before the next swing counts as a draw. */
    private static final int IAIDO_TICKS = 60;
    private static final double DRAW_RANGE = 10;
    private static final int DUEL_TICKS = 160;
    private static final double DUEL_RADIUS = 7;

    /** Server tick of each player's last swing. */
    private final Map<UUID, Integer> lastSwing = new HashMap<>();
    /** Duelling player -> their opponent. */
    private final Map<UUID, UUID> duels = new HashMap<>();

    public KatanaWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.KATANA);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        int now = Bukkit.getCurrentTick();
        Integer last = lastSwing.put(player.getUniqueId(), now);
        Location at = Combat.center(target);
        if (last == null || now - last >= IAIDO_TICKS) {
            // a drawn cut: the blade leaves the scabbard faster than the eye can follow
            event.setDamage(event.getDamage() * 1.6);
            Vector look = player.getEyeLocation().getDirection();
            fx().cut(at, look, 0.15, 0.2, STEEL, 2.4f, 2);
            fx().flash(at, MOON, 1.6f);
            Fx.particle(at, Particle.SWEEP_ATTACK, 1, 0, 0);
            Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
            Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2f);
        }
        if (target.getUniqueId().equals(duels.get(player.getUniqueId()))) {
            event.setDamage(event.getDamage() * 1.4);
            Fx.particle(at, Particle.END_ROD, 6, 0.3, 0.05);
        }
        if (chance(0.25)) {
            status().bleed(player, target, 4);
            Fx.particle(at, Particle.DUST, 10, 0.3, 0, Fx.dust(RED, 1.2f));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSwing.remove(event.getPlayer().getUniqueId());
        duels.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Draw Cut: you flash forward along your aim. Nothing seems to happen to the enemies you passed
     * until the blade clicks back into its sheath, and then they are all cut at once.
     */
    @Override
    public boolean primary(Player player) {
        Location start = player.getLocation();
        Vector dir = player.getEyeLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        // stop short of walls, on a spot you can stand
        Location chest = start.clone().add(0, 1, 0);
        RayTraceResult wall = player.getWorld().rayTraceBlocks(chest, dir, DRAW_RANGE, FluidCollisionMode.NEVER, true);
        double reach = wall == null ? DRAW_RANGE : Math.max(0, wall.getHitPosition().distance(chest.toVector()) - 0.7);
        Location destination = null;
        for (double d = reach; d >= 1.5; d -= 0.5) {
            Location spot = Combat.safeSpot(start.clone().add(dir.clone().multiply(d)));
            if (spot != null) {
                destination = spot;
                reach = d;
                break;
            }
        }
        if (destination == null) {
            tell(player, "No room to move.");
            return false;
        }
        // everyone along the path is marked
        List<LivingEntity> marked = new ArrayList<>();
        Location mid = start.clone().add(dir.clone().multiply(reach / 2));
        for (LivingEntity target : combat().nearby(player, mid, reach / 2 + 2)) {
            if (target.getBoundingBox().expand(0.9).rayTrace(chest.toVector(), dir, reach) != null) {
                marked.add(target);
            }
        }
        destination.setYaw(start.getYaw());
        destination.setPitch(start.getPitch());
        Location end = destination.clone().add(0, 1, 0);
        fx().afterimage(chest, plugin.items().create(type), dir, STEEL, 1.0f, 10);
        player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        status().protectFromFall(player, 40);
        // a single hairline of light where you went
        fx().beam(chest, end, STEEL, 0.14f, 10);
        fx().beam(chest, end, RED, 0.05f, 6);
        Fx.particle(end, Particle.CLOUD, 8, 0.3, 0.02);
        Fx.sound(start, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 2f);
        Fx.sound(destination, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.8f);
        for (LivingEntity target : marked) {
            Fx.particle(Combat.center(target), Particle.END_ROD, 3, 0.2, 0.01);
        }

        // ...then the sheath clicks shut
        Vector cut = dir.clone();
        displays().later(14, () -> {
            if (!alive(player)) {
                return;
            }
            Fx.sound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.9f, 2f);
            Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
            for (LivingEntity target : marked) {
                if (!target.isValid() || target.isDead()) {
                    continue;
                }
                Location at = Combat.center(target);
                combat().damage(player, target, 9, true);
                status().bleed(player, target, 3);
                fx().cut(at, cut, 0.6, 0.4, STEEL, 2.0f, 2);
                fx().cut(at, cut, Math.PI - 0.6, 0.4, RED, 2.0f, 2);
                fx().burst(at, MOON, 1.6f);
                Fx.particle(at, Particle.DUST, 16, 0.35, 0, Fx.dust(RED, 1.4f));
            }
        });
        return true;
    }

    /**
     * Moonlit Duel: a ring of moonlight closes around you and the enemy under your crosshair. For
     * eight seconds neither of you can leave it and nobody else can come in, and your hits on your
     * opponent deal +40% damage. Win, and you heal.
     */
    @Override
    public boolean secondary(Player player) {
        LivingEntity foe = combat().lookTarget(player, 20, 0.5);
        if (foe == null) {
            tell(player, "Look at an enemy to challenge it.");
            return false;
        }
        Location center = Combat.ground(player.getLocation().add(foe.getLocation()).multiply(0.5).add(0, 1, 0));
        center.setWorld(player.getWorld());
        duels.put(player.getUniqueId(), foe.getUniqueId());
        fx().castCircle(center, MOON, (float) DUEL_RADIUS);
        fx().flash(Combat.center(foe), MOON, 2.4f);
        fx().beam(player.getEyeLocation(), Combat.center(foe), MOON, 0.2f, 8);
        Fx.sound(center, Sound.BLOCK_BELL_USE, 1.2f, 0.7f);
        Fx.sound(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                boolean foeGone = !foe.isValid() || foe.isDead() || foe.getWorld() != center.getWorld();
                if (!alive(player) || player.getWorld() != center.getWorld() || foeGone || ticks > DUEL_TICKS) {
                    finish(foeGone && alive(player) && foe.isDead());
                    return;
                }
                // the ring itself: a band of moonlight on the ground and pillars rising from it
                if (ticks % 4 == 0) {
                    Fx.ring(center.clone().add(0, 0.15, 0), DUEL_RADIUS, 56, p -> Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(MOON, 1.4f)));
                }
                if (ticks % 8 == 0) {
                    double a = ticks * 0.11;
                    for (int k = 0; k < 4; k++) {
                        double ang = a + k * Math.PI / 2;
                        Location p = center.clone().add(Math.cos(ang) * DUEL_RADIUS, 0.1, Math.sin(ang) * DUEL_RADIUS);
                        for (double y = 0; y < 2.4; y += 0.4) {
                            Fx.particle(p.clone().add(0, y, 0), Particle.END_ROD, 1, 0, 0);
                        }
                    }
                }
                // the two of you stay in, everyone else stays out
                keepInside(player);
                keepInside(foe);
                if (ticks % 2 == 0) {
                    for (LivingEntity other : combat().nearby(player, center, DUEL_RADIUS + 1)) {
                        if (other == foe) {
                            continue;
                        }
                        Vector out = other.getLocation().toVector().subtract(center.toVector()).setY(0);
                        if (out.lengthSquared() < 1.0E-4) {
                            out = new Vector(1, 0, 0);
                        }
                        Combat.setVelocity(other, out.normalize().multiply(0.9).setY(0.3));
                        if (ticks % 10 == 0) {
                            Fx.particle(Combat.center(other), Particle.END_ROD, 4, 0.3, 0.02);
                        }
                    }
                }
            }

            private void keepInside(LivingEntity who) {
                Vector off = who.getLocation().toVector().subtract(center.toVector()).setY(0);
                double distance = off.length();
                if (distance > DUEL_RADIUS - 0.6) {
                    Combat.setVelocity(who, off.multiply(-0.6 / Math.max(0.01, distance)).setY(0.2));
                    Fx.particle(who.getLocation().add(0, 1, 0), Particle.END_ROD, 3, 0.2, 0.02);
                    Fx.sound(who.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.6f);
                }
            }

            private void finish(boolean won) {
                cancel();
                duels.remove(player.getUniqueId());
                Fx.sound(center, Sound.BLOCK_BELL_RESONATE, 0.8f, 1.2f);
                Fx.ring(center.clone().add(0, 0.3, 0), DUEL_RADIUS, 40, p -> Fx.particle(p, Particle.CLOUD, 1, 0.05, 0.02));
                if (won) {
                    combat().heal(player, 8);
                    fx().flash(player.getLocation().add(0, 1, 0), MOON, 2.4f);
                    Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @Override
    public void shutdown() {
        lastSwing.clear();
        duels.clear();
    }
}
