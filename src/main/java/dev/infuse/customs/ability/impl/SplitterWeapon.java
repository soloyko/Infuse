package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creation Splitter, the Worldsplitter: cuts in the world itself.
 * Passive Fault Line, on-hit Fracture, F Sunder, Sneak+F Banish.
 */
public final class SplitterWeapon extends SwordAbility implements Listener {

    private static final int RED = 0xC2183A;
    private static final int HOT = 0xFF5A6E;
    private static final int VOID = 0x1A0A0A;
    private static final double SUNDER_LENGTH = 12;
    private static final int BANISH_TICKS = 60;

    /** Hits each player has landed, for Fault Line. */
    private final Map<UUID, Integer> hits = new HashMap<>();

    /** Something taken out of the world, and how to put it back. */
    private static final class Banished {
        final LivingEntity entity;
        final Location at;
        final boolean hadAi;
        final boolean hadGravity;
        final boolean wasInvisible;

        Banished(LivingEntity entity) {
            this.entity = entity;
            this.at = entity.getLocation();
            this.hadAi = !(entity instanceof Mob mob) || mob.hasAI();
            this.hadGravity = entity.hasGravity();
            this.wasInvisible = entity.isInvisible();
        }
    }

    private final Map<UUID, Banished> banished = new HashMap<>();

    public SplitterWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.SPLITTER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Location at = Combat.center(target);
        int count = hits.merge(player.getUniqueId(), 1, Integer::sum);
        if (count % 5 == 0) {
            event.setDamage(event.getDamage() * 2);
            fx().cut(at, player.getEyeLocation().getDirection(), 0.1, 0.1, HOT, 2.4f, 2);
            fx().flash(at, RED, 1.8f);
            Fx.sound(at, Sound.ENTITY_ENDER_DRAGON_HURT, 0.6f, 1.6f);
        }
        if (chance(0.2)) {
            Location ground = Combat.ground(target.getLocation().add(0, 0.5, 0));
            fx().crack(ground, RED, 2.2f, 20);
            Fx.sound(ground, Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
            displays().later(6, () -> {
                if (!alive(player) || !target.isValid() || target.isDead()) {
                    return;
                }
                combat().damage(player, target, 4, true);
                Combat.setVelocity(target, target.getVelocity().setY(0.55));
                fx().burst(Combat.center(target), HOT, 1.6f);
                Fx.particle(ground.clone().add(0, 0.2, 0), Particle.REVERSE_PORTAL, 20, 0.4, 0.1);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hits.remove(event.getPlayer().getUniqueId());
    }

    // ---- Sunder ----

    /**
     * Sunder: you cut the world open along your aim. A glowing rift twelve blocks long splits
     * the ground; everything near it is thrown out to one side or the other, and whatever stood
     * right on the line takes the full force of the cut.
     */
    @Override
    public boolean primary(Player player) {
        Vector dir = dev.infuse.customs.ability.Aim.flatLook(player);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location start = player.getLocation().add(dir.clone().multiply(1.2));
        Fx.sound(start, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.6f);
        Fx.sound(start, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.4f);

        // the rift opens from you outwards, a little at a time
        for (int i = 0; i < SUNDER_LENGTH; i += 2) {
            int step = i;
            displays().later(1 + i / 2, () -> {
                Location a = Combat.ground(start.clone().add(dir.clone().multiply(step)).add(0, 1, 0)).add(0, 0.12, 0);
                Location b = Combat.ground(start.clone().add(dir.clone().multiply(step + 2)).add(0, 1, 0)).add(0, 0.12, 0);
                fx().beam(a, b, RED, 0.7f, 22);
                fx().beam(a, b, HOT, 0.2f, 18);
                fx().crack(a, RED, 2f, 26);
                Fx.particle(a.clone().add(0, 0.3, 0), Particle.REVERSE_PORTAL, 8, 0.4, 0.1);
                Fx.particle(a.clone().add(0, 0.2, 0), Particle.BLOCK, 8, 0.4, 0.1, a.clone().subtract(0, 0.6, 0).getBlock().getBlockData());
            });
        }
        displays().later(4, () -> {
            Location middle = start.clone().add(dir.clone().multiply(SUNDER_LENGTH / 2)).add(0, 1.2, 0);
            fx().rift(middle, side, RED, 2.6f, 20);
            if (!alive(player)) {
                return;
            }
            for (LivingEntity target : combat().nearby(player, middle, SUNDER_LENGTH / 2 + 2.5)) {
                Vector rel = target.getLocation().toVector().subtract(start.toVector());
                double along = rel.dot(dir);
                double across = rel.dot(side);
                if (along < -1 || along > SUNDER_LENGTH + 1 || Math.abs(across) > 3) {
                    continue;
                }
                boolean onLine = Math.abs(across) < 1.0;
                combat().damage(player, target, onLine ? 11 : 5, true);
                Vector shove = side.clone().multiply(across >= 0 ? 1 : -1).multiply(onLine ? 1.6 : 1.2).setY(0.45);
                Combat.setVelocity(target, shove);
                fx().burst(Combat.center(target), onLine ? HOT : RED, onLine ? 2.2f : 1.4f);
            }
            Fx.sound(middle, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.7f);
        });
        return true;
    }

    // ---- Banish ----

    /**
     * Banish: a rift swallows the enemy under your crosshair and it simply isn't there for three
     * seconds: it can't be seen, can't act and can't be hurt. Then it falls back out of a rift
     * high above where it stood, hurt by the trip.
     */
    @Override
    public boolean secondary(Player player) {
        LivingEntity target = combat().lookTarget(player, 24, 0.6);
        if (target == null) {
            tell(player, "Look at an enemy to banish it.");
            return false;
        }
        if (banished.containsKey(target.getUniqueId())) {
            return false;
        }
        Banished b = new Banished(target);
        banished.put(target.getUniqueId(), b);
        Location at = Combat.center(target);
        fx().rift(at, player.getEyeLocation().getDirection(), RED, 2.4f, 16);
        fx().flash(at, VOID, 2.6f);
        Fx.particle(at, Particle.REVERSE_PORTAL, 40, 0.4, 0.2);
        Fx.sound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
        Fx.sound(at, Sound.BLOCK_END_PORTAL_SPAWN, 0.4f, 2f);
        hide(b);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!target.isValid()) {
                    banished.remove(target.getUniqueId());
                    cancel();
                    return;
                }
                if (ticks < BANISH_TICKS) {
                    // held outside the world: frozen where it vanished
                    Combat.setVelocity(target, new Vector());
                    if (target.getLocation().distanceSquared(b.at) > 0.25) {
                        target.teleport(b.at);
                    }
                    if (ticks % 5 == 0) {
                        Fx.particle(b.at.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, 3, 0.3, 0.05);
                    }
                    return;
                }
                cancel();
                Location above = b.at.clone().add(0, 3.5, 0);
                restore(b);
                banished.remove(target.getUniqueId());
                target.teleport(above);
                fx().rift(above.clone().add(0, 1, 0), new Vector(0, 1, 0), RED, 2.2f, 14);
                fx().flash(above.clone().add(0, 1, 0), HOT, 2.2f);
                Fx.sound(above, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.8f);
                if (alive(player)) {
                    combat().damage(player, target, 8, true);
                    applyEffect(target, PotionEffectType.NAUSEA, 80, 0);
                    applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void hide(Banished b) {
        LivingEntity e = b.entity;
        e.setInvulnerable(true);
        e.setGravity(false);
        if (e instanceof Mob mob) {
            mob.setAI(false);
        }
        if (e instanceof Player p) {
            for (Player other : p.getWorld().getPlayers()) {
                if (other != p) {
                    other.hidePlayer(plugin, p);
                }
            }
            applyEffect(p, PotionEffectType.BLINDNESS, BANISH_TICKS + 10, 0);
        } else {
            e.setInvisible(true);
            for (Player other : e.getWorld().getPlayers()) {
                other.hideEntity(plugin, e);
            }
        }
    }

    private void restore(Banished b) {
        LivingEntity e = b.entity;
        e.setInvulnerable(false);
        e.setGravity(b.hadGravity);
        if (e instanceof Mob mob) {
            mob.setAI(b.hadAi);
        }
        if (e instanceof Player p) {
            for (Player other : p.getWorld().getPlayers()) {
                other.showPlayer(plugin, p);
            }
        } else {
            e.setInvisible(b.wasInvisible);
            for (Player other : e.getWorld().getPlayers()) {
                other.showEntity(plugin, e);
            }
        }
    }

    @Override
    public void shutdown() {
        for (Banished b : banished.values()) {
            if (b.entity.isValid()) {
                restore(b);
                b.entity.teleport(b.at);
            }
        }
        banished.clear();
        hits.clear();
    }
}
