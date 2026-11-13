package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Displays;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sandreaver, the Pharaoh's Khopesh: the desert and its curses.
 * Passive Sun-Blessed, on-hit Pharaoh's Curse, F Sandstorm, Sneak+F Tomb of Sands.
 */
public final class KhopeshWeapon extends SwordAbility implements Listener {

    private static final int GOLD = 0xE3B341;
    private static final int SAND = 0xE8D3A0;
    private static final int LAPIS = 0x1FA3A3;
    private static final BlockData SAND_BLOCK = Material.SAND.createBlockData();
    private static final BlockData SANDSTONE = Material.CUT_SANDSTONE.createBlockData();
    private static final BlockData CHISELED = Material.CHISELED_SANDSTONE.createBlockData();
    private static final BlockData LAPIS_BLOCK = Material.LAPIS_BLOCK.createBlockData();

    /** Cursed targets: who cursed them and until when (ms). */
    private final Map<UUID, Map.Entry<UUID, Long>> curses = new HashMap<>();

    public KhopeshWeapon(OPSwordsPlugin plugin) {
        super(plugin, SwordType.KHOPESH);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        long time = player.getWorld().getTime();
        boolean day = time < 12300 || time > 23850;
        if (day && player.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL
                && player.getLocation().getBlock().getLightFromSky() > 10) {
            out.add(effect(PotionEffectType.REGENERATION, 0, 0));
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        curses.put(target.getUniqueId(), Map.entry(player.getUniqueId(), System.currentTimeMillis() + 5000));
        applyEffect(target, PotionEffectType.WEAKNESS, 100, 0);
        applyEffect(target, PotionEffectType.SLOWNESS, 100, 0);
        Location at = Combat.center(target);
        fx().castCircle(target.getLocation(), LAPIS, 1.4f);
        fx().burst(at, GOLD, 1.4f);
        Fx.particle(at, Particle.SOUL, 8, 0.3, 0.02);
        Fx.sound(at, Sound.ENTITY_HUSK_AMBIENT, 1f, 0.6f);
        Fx.sound(at, Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 0.6f, 1.4f);
    }

    /** Cursed targets take 20% more damage from whoever cursed them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCursedHit(EntityDamageByEntityEvent event) {
        Map.Entry<UUID, Long> curse = curses.get(event.getEntity().getUniqueId());
        if (curse == null) {
            return;
        }
        if (curse.getValue() < System.currentTimeMillis()) {
            curses.remove(event.getEntity().getUniqueId());
            return;
        }
        UUID attacker = event.getDamager().getUniqueId();
        if (event.getDamager() instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof org.bukkit.entity.Entity e) {
            attacker = e.getUniqueId();
        }
        if (curse.getKey().equals(attacker)) {
            event.setDamage(event.getDamage() * 1.2);
        }
    }

    /** Sandstorm: a sand tornado travels forward, lifting, blinding and grinding enemies. */
    @Override
    public boolean primary(Player player) {
        Location start = player.getLocation();
        Vector dir = start.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 0, 1);
        }
        Vector heading = dir.normalize();
        Location spawnAt = start.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        List<BlockDisplay> grains = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            BlockData data = i % 4 == 0 ? SANDSTONE : SAND_BLOCK;
            BlockDisplay grain = displays().block(spawnAt, data, new Vector3f(0.32f), new Quaternionf(), 70);
            grain.setTeleportDuration(2);
            grains.add(grain);
        }
        Set<UUID> caught = new HashSet<>();
        Fx.sound(start, Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 0.5f);
        Fx.sound(start, Sound.WEATHER_RAIN_ABOVE, 1.5f, 0.5f);

        new BukkitRunnable() {
            int ticks;
            final Location base = Combat.ground(start.clone().add(heading.clone().multiply(1.5)).add(0, 1, 0));

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > 60 || base.getWorld() != player.getWorld()) {
                    grains.forEach(g -> displays().remove(g));
                    Fx.particle(base.clone().add(0, 1, 0), Particle.BLOCK, 40, 1, 0.1, SAND_BLOCK);
                    cancel();
                    return;
                }
                Location next = Combat.ground(base.clone().add(heading.clone().multiply(0.3)).add(0, 1.2, 0));
                base.setX(next.getX());
                base.setY(next.getY());
                base.setZ(next.getZ());
                // the funnel: rings that widen with height, turning fast
                for (int ring = 0; ring < 5; ring++) {
                    double y = ring * 0.8;
                    double radius = 0.4 + ring * 0.35;
                    double a = ticks * 0.6 + ring;
                    for (int k = 0; k < 3; k++) {
                        double ang = a + k * Math.PI * 2 / 3;
                        Location p = base.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
                        Fx.particle(p, Particle.FALLING_DUST, 1, 0.05, 0, SAND_BLOCK);
                        if (ring % 2 == 0) {
                            Fx.particle(p, Particle.DUST, 1, 0.05, 0, Fx.dust(k == 0 ? GOLD : SAND, 1.5f));
                        }
                    }
                }
                if (ticks % 2 == 0) {
                    for (int i = 0; i < grains.size(); i++) {
                        double y = (i * 0.37 + ticks * 0.05) % 3.6;
                        double radius = 0.45 + y * 0.42;
                        double ang = ticks * 0.5 + i * 2.4;
                        Location p = base.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius);
                        p.setYaw(0);
                        p.setPitch(0);
                        if (grains.get(i).isValid()) {
                            grains.get(i).teleport(p);
                            Displays.animateBlock(grains.get(i), new Vector3f(0.32f),
                                    new Quaternionf().rotateXYZ(ticks * 0.3f, ticks * 0.5f + i, 0), new Vector3f(), 2);
                        }
                    }
                }
                if (ticks % 10 == 0) {
                    fx().shockwave(base, SAND, 1.8f, 8);
                    Fx.sound(base, Sound.ENTITY_BREEZE_IDLE_GROUND, 1f, 0.6f);
                }
                for (LivingEntity target : combat().nearby(player, base.clone().add(0, 1, 0), 2.2)) {
                    if (caught.add(target.getUniqueId())) {
                        applyEffect(target, PotionEffectType.BLINDNESS, 50, 0);
                    }
                    Vector in = base.toVector().subtract(target.getLocation().toVector()).setY(0);
                    Combat.setVelocity(target, heading.clone().multiply(0.3).add(in.multiply(0.25)).setY(0.28));
                    if (ticks % 5 == 0) {
                        combat().damage(player, target, 2, true);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Tomb of Sands: enemies nearby are sealed in sandstone tombs, which then collapse on them. */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> targets = combat().nearby(player, player.getLocation(), 7);
        if (targets.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        Fx.sound(player.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 1.5f, 0.6f);
        Fx.sound(player.getLocation(), Sound.ENTITY_WARDEN_DIG, 1f, 1.2f);
        fx().castCircle(player.getLocation(), GOLD, 7.5f);
        fx().shockwave(player.getLocation(), SAND, 7f, 12);
        for (LivingEntity target : targets) {
            entomb(player, target);
        }
        return true;
    }

    private void entomb(Player player, LivingEntity target) {
        Location feet = target.getLocation();
        feet.setYaw(0);
        feet.setPitch(0);
        double w = Math.max(0.7, target.getWidth() / 2 + 0.25);
        double h = target.getHeight() + 0.3;
        List<BlockDisplay> walls = new ArrayList<>();
        // four walls rise out of the sand around the target, with a lapis band and a carved lid
        double[][] sides = {{w, 0}, {-w, 0}, {0, w}, {0, -w}};
        for (double[] side : sides) {
            boolean alongX = side[0] == 0;
            Vector3f size = alongX ? new Vector3f((float) (w * 2 + 0.2), (float) h, 0.18f) : new Vector3f(0.18f, (float) h, (float) (w * 2 + 0.2));
            Location at = feet.clone().add(side[0], h / 2, side[1]);
            BlockDisplay wall = displays().block(at, SANDSTONE, new Vector3f(size.x, 0.05f, size.z), new Quaternionf(), 70);
            walls.add(wall);
            displays().later(1, () -> Displays.animateBlock(wall, size, new Quaternionf(), new Vector3f(), 6));
            Location band = feet.clone().add(side[0] * 1.02, h * 0.62, side[1] * 1.02);
            BlockDisplay stripe = displays().block(band, LAPIS_BLOCK,
                    new Vector3f(alongX ? size.x : 0.2f, 0.01f, alongX ? 0.2f : size.z), new Quaternionf(), 70);
            walls.add(stripe);
            displays().later(6, () -> Displays.animateBlock(stripe,
                    new Vector3f(alongX ? size.x : 0.2f, 0.14f, alongX ? 0.2f : size.z), new Quaternionf(), new Vector3f(), 3));
        }
        BlockDisplay lid = displays().block(feet.clone().add(0, h + 0.12, 0), CHISELED,
                new Vector3f((float) (w * 2 + 0.4), 0.01f, (float) (w * 2 + 0.4)), new Quaternionf(), 70);
        walls.add(lid);
        displays().later(7, () -> Displays.animateBlock(lid, new Vector3f((float) (w * 2 + 0.4), 0.24f, (float) (w * 2 + 0.4)),
                new Quaternionf(), new Vector3f(), 3));
        hold(target, 60);
        applyEffect(target, PotionEffectType.BLINDNESS, 60, 0);
        Fx.particle(feet.clone().add(0, 0.2, 0), Particle.BLOCK, 30, w, 0.1, SAND_BLOCK);
        Fx.sound(feet, Sound.BLOCK_SAND_PLACE, 1.2f, 0.6f);

        displays().later(60, () -> {
            Location c = feet.clone().add(0, h / 2, 0);
            for (BlockDisplay wall : walls) {
                if (wall.isValid()) {
                    Fx.particle(wall.getLocation(), Particle.BLOCK, 14, 0.3, 0.1, SANDSTONE);
                }
                displays().remove(wall);
            }
            fx().burst(c, GOLD, 2.6f);
            fx().shockwave(feet, SAND, 2.6f, 7);
            Fx.particle(c, Particle.BLOCK, 50, 0.6, 0.2, SAND_BLOCK);
            Fx.sound(c, Sound.BLOCK_STONE_BREAK, 1.5f, 0.6f);
            Fx.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
            if (target.isValid() && alive(player)) {
                combat().damage(player, target, 10, true);
                Combat.setVelocity(target, new Vector(0, 0.6, 0));
            }
        });
    }

    @Override
    public void shutdown() {
        curses.clear();
    }
}
