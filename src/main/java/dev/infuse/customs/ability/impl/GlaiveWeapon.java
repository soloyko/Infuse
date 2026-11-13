package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Forest Guardian's Glaive, the Guardian's Glaive: the old forest, its wind and its seasons.
 * Passive Forest Kin, on-hit Thornwisp, F Leaf Glide, Sneak+F Turning Seasons.
 */
public final class GlaiveWeapon extends SwordAbility {

    private static final int TEAL = 0x2FBF9F;
    private static final int LEAF = 0x7BC86C;
    private static final int GLIDE_TICKS = 80;
    private static final int SEASON_TICKS = 40;
    private static final double SEASON_RADIUS = 6;
    private static final BlockData WING = Material.AZALEA_LEAVES.createBlockData();
    private static final BlockData WING_BLOOM = Material.FLOWERING_AZALEA_LEAVES.createBlockData();

    private enum Season {
        SPRING(0x8FE07A, "spring"), SUMMER(0xFFB347, "summer"), AUTUMN(0xD9822B, "autumn"), WINTER(0xDDF3FF, "winter");

        final int rgb;
        final String label;

        Season(int rgb, String label) {
            this.rgb = rgb;
            this.label = label;
        }
    }

    private final Set<UUID> gliding = new HashSet<>();

    public GlaiveWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.GLAIVE);
    }

    /** Forest Kin: standing on living ground mends you. */
    @Override
    public void tickHeld(Player player, long tick) {
        Material under = player.getLocation().subtract(0, 0.1, 0).getBlock().getType();
        if (under == Material.GRASS_BLOCK || under == Material.MOSS_BLOCK || under == Material.MOSS_CARPET
                || under == Material.PODZOL || under == Material.ROOTED_DIRT || Tag.LEAVES.isTagged(under)) {
            applyEffect(player, PotionEffectType.REGENERATION, 30, 0);
            if (tick % 4 == 0) {
                Fx.particle(player.getLocation().add(0, 0.2, 0), Particle.HAPPY_VILLAGER, 1, 0.3, 0);
            }
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        // a wisp of thorns latches on and bites every second
        ItemDisplay wisp = fx().orb(Combat.center(target), LEAF, 0.6f, 90);
        Fx.sound(target.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PLACE, 1f, 1.2f);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!target.isValid() || target.isDead() || ticks > 80 || !alive(player)) {
                    displays().remove(wisp);
                    cancel();
                    return;
                }
                double a = ticks * 0.3;
                wisp.teleport(Combat.center(target).add(Math.cos(a) * 0.6, 0.3, Math.sin(a) * 0.6));
                if (ticks % 20 == 0) {
                    combat().damage(player, target, 1.5, true);
                    Fx.particle(Combat.center(target), Particle.BLOCK, 6, 0.3, 0.05, Material.SWEET_BERRY_BUSH.createBlockData());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- Leaf Glide ----

    /**
     * Leaf Glide: a gust lifts you and wings of leaves unfold. For four seconds you glide wherever
     * you look, sinking slowly; look down to dive, and diving into an enemy strikes it hard.
     * Landing or sneaking folds the wings.
     */
    @Override
    public boolean primary(Player player) {
        UUID id = player.getUniqueId();
        if (!gliding.add(id)) {
            return false;
        }
        status().protectFromFall(player, GLIDE_TICKS + 60);
        Combat.setVelocity(player, player.getVelocity().setY(1.15));
        Location feet = player.getLocation();
        Fx.sound(feet, Sound.ENTITY_BREEZE_JUMP, 1f, 0.9f);
        Fx.sound(feet, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.2f, 0.8f);
        Fx.particle(feet.clone().add(0, 0.3, 0), Particle.CHERRY_LEAVES, 30, 0.8, 0.05);
        fx().wave(feet.clone().add(0, 0.2, 0), new Vector(0, 1, 0), TEAL, 1.8f, 6);
        BlockDisplay[] wings = new BlockDisplay[2];
        Location spawnAt = feet.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        for (int i = 0; i < 2; i++) {
            wings[i] = displays().block(spawnAt, i == 0 ? WING : WING_BLOOM, new Vector3f(0.05f), new Quaternionf(), GLIDE_TICKS + 30);
            wings[i].setTeleportDuration(1);
        }

        new BukkitRunnable() {
            int ticks;
            boolean struck;

            @Override
            public void run() {
                ticks++;
                Location at = player.getLocation();
                boolean grounded = ticks > 12 && !at.clone().subtract(0, 0.15, 0).getBlock().isPassable();
                if (!alive(player) || ticks > GLIDE_TICKS + 8 || grounded || (ticks > 10 && player.isSneaking()) || struck) {
                    fold();
                    return;
                }
                Vector look = player.getEyeLocation().getDirection();
                if (ticks > 8) {
                    // glide: forward along your look, sinking gently; looking steeply down dives
                    double y = look.getY() < -0.55 ? look.getY() * 1.1 : Math.max(-0.12, look.getY() * 0.5);
                    Combat.setVelocity(player, look.clone().setY(0).multiply(0.75).setY(y));
                }
                // the wings: two leafy planes out to your sides, flapping slowly
                float yaw = (float) Math.toRadians(-at.getYaw());
                float flap = (float) Math.sin(ticks * 0.35) * 0.25f;
                for (int s = 0; s < 2; s++) {
                    int side = s == 0 ? 1 : -1;
                    Quaternionf body = new Quaternionf().rotateY(yaw);
                    Vector3f off = body.transform(new Vector3f(-side * 0.9f, 1.25f, -0.15f));
                    Location p = at.clone().add(off.x, off.y, off.z);
                    p.setYaw(0);
                    p.setPitch(0);
                    if (wings[s].isValid()) {
                        wings[s].teleport(p);
                        Quaternionf turn = new Quaternionf(body).rotateZ(side * (0.25f + flap));
                        Displays.animateBlock(wings[s], new Vector3f(1.3f, 0.08f, 0.7f), turn, new Vector3f(), 2);
                    }
                }
                if (ticks % 2 == 0) {
                    Fx.particle(at.clone().add(0, 1, 0), Particle.CHERRY_LEAVES, 2, 0.6, 0.02);
                    Fx.particle(at.clone().add(0, 1, 0), Particle.DUST, 2, 0.5, 0, Fx.dust(LEAF, 1.0f));
                }
                // diving into an enemy strikes it
                if (player.getVelocity().getY() < -0.45) {
                    for (LivingEntity target : combat().nearby(player, at.clone().add(0, 0.5, 0), 1.8)) {
                        combat().damage(player, target, 9);
                        Combat.knockback(target, at, 1.0, 0.5);
                        fx().burst(Combat.center(target), TEAL, 2.2f);
                        fx().crack(Combat.ground(target.getLocation().add(0, 0.5, 0)), LEAF, 2f, 20);
                        Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
                        struck = true;
                    }
                }
            }

            private void fold() {
                cancel();
                gliding.remove(id);
                for (BlockDisplay wing : wings) {
                    if (wing.isValid()) {
                        Fx.particle(wing.getLocation(), Particle.CHERRY_LEAVES, 10, 0.4, 0.02);
                    }
                    displays().remove(wing);
                }
                if (alive(player)) {
                    Fx.sound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_FALL, 1.2f, 1f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ---- Turning Seasons ----

    /**
     * Turning Seasons: the year turns in eight seconds around where you stand. Spring mends you and
     * your teammates, summer scorches enemies, autumn's falling leaves blind and slow them, and
     * winter freezes them.
     */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        Fx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 0.8f);
        Fx.sound(center, Sound.ITEM_BONE_MEAL_USE, 1.4f, 0.8f);
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (center.getWorld() != player.getWorld() || ticks >= SEASON_TICKS * 4) {
                    cancel();
                    return;
                }
                Season season = Season.values()[ticks / SEASON_TICKS];
                int phase = ticks % SEASON_TICKS;
                ticks++;
                if (phase == 0) {
                    fx().castCircle(center, season.rgb, (float) SEASON_RADIUS);
                    fx().flash(center.clone().add(0, 1.5, 0), season.rgb, 2.6f);
                    Fx.sound(center, switch (season) {
                        case SPRING -> Sound.BLOCK_SPORE_BLOSSOM_PLACE;
                        case SUMMER -> Sound.ITEM_FIRECHARGE_USE;
                        case AUTUMN -> Sound.BLOCK_AZALEA_LEAVES_FALL;
                        case WINTER -> Sound.BLOCK_POWDER_SNOW_PLACE;
                    }, 1.5f, 0.8f);
                    if (player.isOnline()) {
                        player.sendActionBar(dev.infuse.customs.util.Text.caps(season.label, net.kyori.adventure.text.format.TextColor.color(season.rgb)));
                    }
                }
                // the weather of the season over the whole circle
                java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
                for (int i = 0; i < 4; i++) {
                    double a = r.nextDouble(Math.PI * 2);
                    double d = Math.sqrt(r.nextDouble()) * SEASON_RADIUS;
                    Location p = center.clone().add(Math.cos(a) * d, r.nextDouble(0.3, 3), Math.sin(a) * d);
                    switch (season) {
                        case SPRING -> Fx.particle(p, Particle.CHERRY_LEAVES, 1, 0, 0);
                        case SUMMER -> Fx.particle(p.clone().subtract(0, p.getY() - center.getY() - 0.2, 0), Particle.SMALL_FLAME, 1, 0.1, 0.02);
                        case AUTUMN -> Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(i % 2 == 0 ? 0xD9822B : 0xA0522D, 1.4f));
                        case WINTER -> Fx.particle(p, Particle.SNOWFLAKE, 1, 0, 0.01);
                    }
                }
                if (phase % 10 != 5 || !alive(player)) {
                    return;
                }
                if (season == Season.SPRING) {
                    for (Player near : center.getNearbyPlayers(SEASON_RADIUS)) {
                        if (near == player || (team != null && team.hasEntry(near.getName()))) {
                            combat().heal(near, 2);
                            Fx.particle(near.getLocation().add(0, 1.8, 0), Particle.HEART, 1, 0.2, 0);
                        }
                    }
                    return;
                }
                for (LivingEntity target : combat().nearby(player, center, SEASON_RADIUS)) {
                    Location at = Combat.center(target);
                    switch (season) {
                        case SUMMER -> {
                            combat().damage(player, target, 2, true);
                            target.setFireTicks(Math.max(target.getFireTicks(), 40));
                            Fx.particle(at, Particle.FLAME, 6, 0.3, 0.03);
                        }
                        case AUTUMN -> {
                            combat().damage(player, target, 1, true);
                            applyEffect(target, PotionEffectType.BLINDNESS, 30, 0);
                            applyEffect(target, PotionEffectType.SLOWNESS, 30, 1);
                            Fx.particle(target.getEyeLocation(), Particle.DUST, 6, 0.3, 0, Fx.dust(0xD9822B, 1.3f));
                        }
                        case WINTER -> {
                            combat().damage(player, target, 1.5, true);
                            applyEffect(target, PotionEffectType.SLOWNESS, 30, 2);
                            target.setFreezeTicks(Math.min(target.getMaxFreezeTicks() + 60, target.getFreezeTicks() + 50));
                            Fx.particle(at, Particle.SNOWFLAKE, 8, 0.3, 0.02);
                        }
                        default -> {
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @Override
    public void shutdown() {
        gliding.clear();
    }
}
