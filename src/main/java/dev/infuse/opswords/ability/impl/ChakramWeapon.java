package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Zephyr, the Wind Chakram: a thrown blade riding the wind.
 * Passive Windborne (double jump), on-hit Gust, F Halo Throw, Sneak+F Cyclone.
 */
public final class ChakramWeapon extends SwordAbility implements Listener {

    private static final float MODEL_SCALE = 2.6f;

    /** Players we gave the ability to "fly" (used to detect a jump in mid-air). */
    private final Set<UUID> doubleJump = new HashSet<>();
    private final BukkitTask jumpTask;

    public ChakramWeapon(OPSwordsPlugin plugin) {
        super(plugin, SwordType.CHAKRAM);
        this.jumpTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickJumps, 2L, 2L);
    }

    // ---- Windborne: double jump ----

    private static boolean survival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private static boolean grounded(Player player) {
        return player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isSolid();
    }

    @Override
    public void tickHeld(Player player, long tick) {
        if (survival(player) && !player.getAllowFlight() && grounded(player)) {
            player.setAllowFlight(true);
            doubleJump.add(player.getUniqueId());
        }
    }

    private void tickJumps() {
        for (UUID id : new ArrayList<>(doubleJump)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                doubleJump.remove(id);
                continue;
            }
            if (!survival(player) || plugin.items().heldType(player) != type || !plugin.canUse(player, type)) {
                revoke(player);
                continue;
            }
            if (!player.getAllowFlight() && grounded(player)) {
                player.setAllowFlight(true); // landed: the double jump is ready again
            }
        }
    }

    private void revoke(Player player) {
        doubleJump.remove(player.getUniqueId());
        if (survival(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || !doubleJump.contains(player.getUniqueId()) || !survival(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);
        player.setAllowFlight(false);
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() > 1.0E-4) {
            look.normalize().multiply(0.55);
        }
        Combat.setVelocity(player, look.setY(0.85));
        status().protectFromFall(player, 80);
        Location feet = player.getLocation();
        Fx.particle(feet, Particle.GUST, 1, 0, 0);
        Fx.particle(feet, Particle.SMALL_GUST, 8, 0.4, 0.02);
        Fx.sound(feet, Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
        fx().shockwave(feet, 0xBFF5D8, 1.6f, 5);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        revoke(event.getPlayer());
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Combat.knockback(target, player.getLocation(), 1.4, 0.5);
        Fx.particle(Combat.center(target), Particle.GUST, 1, 0, 0);
        Fx.sound(target.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 0.8f, 1.2f);
    }

    // ---- abilities ----

    private ItemDisplay spawnDisc(Location at, Vector facing, int life) {
        ItemDisplay disc = displays().item(at, plugin.items().create(type), MODEL_SCALE, facing(facing, 0),
                ItemDisplay.ItemDisplayTransform.NONE, life);
        disc.setInterpolationDuration(1);
        return disc;
    }

    /**
     * The model is a flat ring in its XY plane, so turn its Z axis towards {@code normal} (the ring's
     * face then points that way) and spin it in its own plane.
     */
    private static Quaternionf facing(Vector normal, double spin) {
        Vector n = normal.clone().normalize();
        return new Quaternionf().rotationTo(0, 0, 1, (float) n.getX(), (float) n.getY(), (float) n.getZ())
                .rotateZ((float) spin);
    }

    private static void spin(ItemDisplay disc, Vector normal, double angle) {
        disc.setInterpolationDelay(0);
        disc.setInterpolationDuration(1);
        disc.setTransformation(new Transformation(new Vector3f(), facing(normal, angle), new Vector3f(MODEL_SCALE), new Quaternionf()));
    }

    /** Halo Throw: out to 16 blocks and back, cutting enemies both ways. */
    @Override
    public boolean primary(Player player) {
        Location start = player.getEyeLocation().subtract(0, 0.4, 0);
        Vector dir = start.getDirection().normalize();
        ItemDisplay disc = spawnDisc(start, dir, 80); // ring faces the thrower as it flies away
        ItemDisplay discGlow = fx().orb(start, 0x6FE3A8, 1.6f, 80);
        Fx.sound(start, Sound.ITEM_TRIDENT_THROW, 1f, 1.6f);
        Set<UUID> outbound = new HashSet<>();
        Set<UUID> inbound = new HashSet<>();

        new BukkitRunnable() {
            final Location pos = start.clone();
            int ticks;
            boolean back;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !disc.isValid() || ticks > 60 || player.getWorld() != pos.getWorld()) {
                    displays().remove(disc);
                    displays().remove(discGlow);
                    cancel();
                    return;
                }
                spin(disc, dir, ticks * 0.9);
                if (!back) {
                    RayTraceResult block = pos.getWorld().rayTraceBlocks(pos, dir, 1.6, FluidCollisionMode.NEVER, true);
                    if (block != null || ticks >= 10) {
                        back = true;
                        if (block != null) {
                            Fx.sound(pos, Sound.ITEM_TRIDENT_HIT_GROUND, 0.8f, 1.6f);
                        }
                    } else {
                        pos.add(dir.clone().multiply(1.6));
                    }
                } else {
                    Location home = player.getEyeLocation().subtract(0, 0.4, 0);
                    Vector toHome = home.toVector().subtract(pos.toVector());
                    double distance = toHome.length();
                    if (distance < 1.3) {
                        Fx.sound(home, Sound.ENTITY_ITEM_PICKUP, 1f, 1.4f);
                        displays().remove(disc);
                        displays().remove(discGlow);
                        cancel();
                        return;
                    }
                    pos.add(toHome.multiply(Math.min(1.8, distance) / distance));
                }
                disc.teleport(pos);
                discGlow.teleport(pos);
                Fx.particle(pos, Particle.SMALL_GUST, 2, 0.2, 0.01);
                if (ticks % 4 == 0) {
                    Fx.sound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.9f);
                }
                for (LivingEntity target : combat().nearby(player, pos, 1.3)) {
                    if ((back ? inbound : outbound).add(target.getUniqueId())) {
                        combat().damage(player, target, 6, true);
                        Fx.particle(Combat.center(target), Particle.SWEEP_ATTACK, 1, 0, 0);
                        Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
                        fx().cut(Combat.center(target), dir, Math.random() * Math.PI * 2, 0.5, 0x4CC38A, 1.2f, 2);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Cyclone: three chakrams orbit you for 6 seconds; they cut enemies and bat arrows away. */
    @Override
    public boolean secondary(Player player) {
        List<ItemDisplay> discs = new ArrayList<>();
        List<ItemDisplay> glows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            discs.add(spawnDisc(orbit(player, i, 0), outward(i, 0), 130));
            glows.add(fx().orb(orbit(player, i, 0), i == 1 ? 0xE8C64A : 0x6FE3A8, 1.3f, 130));
        }
        Fx.sound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.8f);
        fx().shockwave(player.getLocation(), 0x4CC38A, 4f, 8);
        fx().shockwave(player.getLocation().add(0, 1, 0), 0xE8C64A, 3f, 10);
        Set<UUID> deflected = new HashSet<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 120) {
                    discs.forEach(d -> displays().remove(d));
                    glows.forEach(d -> displays().remove(d));
                    cancel();
                    return;
                }
                for (int i = 0; i < discs.size(); i++) {
                    ItemDisplay disc = discs.get(i);
                    if (disc.isValid()) {
                        disc.teleport(orbit(player, i, ticks));
                        spin(disc, outward(i, ticks), ticks * 0.8 + i);
                    }
                    ItemDisplay glow = glows.get(i);
                    if (glow.isValid()) {
                        Location g = orbit(player, i, ticks);
                        g.setYaw(0);
                        g.setPitch(0);
                        glow.teleport(g);
                    }
                }
                Location center = player.getLocation().add(0, 1, 0);
                if (ticks % 2 == 0) {
                    double a = ticks * 0.35;
                    Fx.particle(center.clone().add(Math.cos(a) * 2.6, 0, Math.sin(a) * 2.6), Particle.SMALL_GUST, 1, 0.1, 0);
                }
                if (ticks % 10 == 0) {
                    for (LivingEntity target : combat().nearby(player, center, 3.0)) {
                        combat().damage(player, target, 3);
                        Combat.knockback(target, center, 0.5, 0.2);
                    }
                }
                if (ticks % 20 == 0) {
                    Fx.sound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
                }
                for (Entity e : player.getNearbyEntities(3, 3, 3)) {
                    if (e instanceof Projectile projectile && projectile.getShooter() != player
                            && deflected.add(projectile.getUniqueId())) {
                        projectile.setVelocity(projectile.getVelocity().multiply(-0.6).setY(0.2));
                        projectile.setShooter(player);
                        Fx.particle(projectile.getLocation(), Particle.GUST, 1, 0, 0);
                        Fx.sound(projectile.getLocation(), Sound.ENTITY_BREEZE_DEFLECT, 1f, 1f);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Orbiting rings face outwards (tilted up a little) so they read clearly from the middle. */
    private static Vector outward(int index, int ticks) {
        double a = index * 2 * Math.PI / 3 + ticks * 0.32;
        return new Vector(Math.cos(a), 0.35, Math.sin(a));
    }

    private static Location orbit(Player player, int index, int ticks) {
        double a = index * 2 * Math.PI / 3 + ticks * 0.32;
        Location base = player.getLocation();
        base.setYaw(0);
        base.setPitch(0);
        return base.add(Math.cos(a) * 2.2, 1.0 + Math.sin(ticks * 0.2 + index) * 0.2, Math.sin(a) * 2.2);
    }

    @Override
    public void shutdown() {
        jumpTask.cancel();
        for (UUID id : new ArrayList<>(doubleJump)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                revoke(player);
            }
        }
        doubleJump.clear();
    }
}
