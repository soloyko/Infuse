package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Displays;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Arcanum, the Arcane Staff: a battle mage's focus.
 * Passive Arcane Ward, on-hit Arcane Spark, F Arcane Missiles, Sneak+F Crystal Nova.
 */
public final class StaffWeapon extends SwordAbility implements Listener {

    private static final int AMETHYST = 0xB57EDC;
    private static final int PALE = 0xF3D9FF;
    private static final int WARD_RECHARGE = 240;

    /** Player -> server tick their ward broke (absent while the ward is up). */
    private final Map<UUID, Integer> wardBroken = new HashMap<>();

    public StaffWeapon(OPSwordsPlugin plugin) {
        super(plugin, SwordType.STAFF);
    }

    /** Arcane Ward: 2 absorption hearts that come back 12 seconds after they're gone. */
    @Override
    public void tickHeld(Player player, long tick) {
        int now = Bukkit.getCurrentTick();
        if (player.getAbsorptionAmount() > 0.01) {
            wardBroken.remove(player.getUniqueId());
            return;
        }
        Integer broke = wardBroken.putIfAbsent(player.getUniqueId(), now);
        if (broke != null && now - broke >= WARD_RECHARGE) {
            PotionEffect old = player.getPotionEffect(PotionEffectType.ABSORPTION);
            if (old == null || old.getAmplifier() == 0) {
                player.removePotionEffect(PotionEffectType.ABSORPTION);
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 1200, 0, true, false, true));
                wardBroken.remove(player.getUniqueId());
                Fx.ring(player.getLocation().add(0, 0.2, 0), 0.9, 14, p -> Fx.particle(p, Particle.WITCH, 1, 0, 0));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        wardBroken.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        for (LivingEntity near : combat().nearby(player, target.getLocation(), 6)) {
            if (near != target) {
                Fx.arc(Combat.center(target), Combat.center(near), p -> Fx.particle(p, Particle.DUST, 1, 0.02, 0, Fx.dust(AMETHYST, 1.0f)));
                Fx.particle(Combat.center(near), Particle.END_ROD, 8, 0.2, 0.05);
                Fx.sound(near.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.6f);
                combat().damage(player, near, 3);
                return;
            }
        }
    }

    /** Arcane Missiles: five glowing shards that home in on enemies ahead. */
    @Override
    public boolean primary(Player player) {
        Fx.sound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.4f);
        for (int i = 0; i < 5; i++) {
            int index = i;
            displays().later(1 + i * 2L, () -> missile(player, index));
        }
        return true;
    }

    private LivingEntity pickTarget(Player player, Location from, Vector look) {
        LivingEntity aimed = combat().lookTarget(player, 24, 1.2);
        if (aimed != null) {
            return aimed;
        }
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : combat().nearby(player, from, 18)) {
            Vector to = Combat.center(candidate).toVector().subtract(from.toVector());
            if (to.lengthSquared() < 1.0E-4 || look.angle(to) > Math.toRadians(55)) {
                continue;
            }
            double score = to.length() * (1 + look.angle(to));
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private void missile(Player player, int index) {
        if (!alive(player)) {
            return;
        }
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector right = Fx.right(look);
        double spread = (index - 2) * 0.35;
        Location pos = eye.clone().subtract(0, 0.3, 0).add(right.clone().multiply(spread));
        Vector velocity = look.clone().add(right.clone().multiply(spread * 0.6)).add(new Vector(0, 0.15, 0)).normalize().multiply(0.9);
        LivingEntity target = pickTarget(player, eye, look);
        ItemDisplay shard = displays().item(pos, new ItemStack(Material.AMETHYST_SHARD), 0.6f, new Quaternionf(),
                ItemDisplay.ItemDisplayTransform.FIXED, 50);
        shard.setBillboard(Display.Billboard.CENTER);
        shard.setBrightness(new Display.Brightness(15, 15));
        Fx.sound(pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f + index * 0.1f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !shard.isValid() || ticks > 45 || pos.getWorld() != player.getWorld()) {
                    displays().remove(shard);
                    cancel();
                    return;
                }
                if (target != null && target.isValid() && !target.isDead()) {
                    Vector want = Combat.center(target).toVector().subtract(pos.toVector()).normalize().multiply(0.95);
                    velocity.multiply(0.72).add(want.multiply(0.28)).normalize().multiply(0.95);
                }
                RayTraceResult block = pos.getWorld().rayTraceBlocks(pos, velocity, velocity.length(), FluidCollisionMode.NEVER, true);
                if (block != null) {
                    Fx.particle(pos, Particle.END_ROD, 8, 0.1, 0.05);
                    displays().remove(shard);
                    cancel();
                    return;
                }
                pos.add(velocity);
                shard.teleport(pos);
                Fx.particle(pos, Particle.DUST, 2, 0.05, 0, Fx.dust(AMETHYST, 1.3f));
                Fx.particle(pos, Particle.WITCH, 1, 0.05, 0);
                for (LivingEntity hit : combat().nearby(player, pos, 0.9)) {
                    combat().damage(player, hit, 3.5, true);
                    Fx.burst(pos, Particle.END_ROD, 10, 0.15);
                    Fx.particle(pos, Particle.DUST, 10, 0.25, 0, Fx.dust(PALE, 1.2f));
                    Fx.sound(pos, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.4f);
                    displays().remove(shard);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Crystal Nova: four rings of amethyst clusters burst out of the ground. */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        Fx.sound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 0.8f);
        Fx.sound(center, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
        Set<UUID> hit = new HashSet<>();
        double[] radii = {1.6, 3.0, 4.4, 5.8};
        for (int ring = 0; ring < radii.length; ring++) {
            double radius = radii[ring];
            displays().later(1 + ring * 3L, () -> crystalRing(player, center, radius, hit));
        }
        return true;
    }

    private void crystalRing(Player player, Location center, double radius, Set<UUID> hit) {
        if (!alive(player) || player.getWorld() != center.getWorld()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int count = (int) (radius * 3.2);
        BlockData cluster = Material.AMETHYST_CLUSTER.createBlockData();
        if (cluster instanceof Directional directional) {
            directional.setFacing(BlockFace.UP);
        }
        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count + radius;
            Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            Block ground = Displays.groundBelow(p.clone().add(0, 1, 0));
            if (ground == null) {
                continue;
            }
            Location at = ground.getLocation().add(0.5, 1.0, 0.5);
            float size = (float) r.nextDouble(0.9, 1.4);
            Quaternionf tilt = new Quaternionf().rotateY((float) r.nextDouble(Math.PI * 2))
                    .rotateX((float) r.nextDouble(-0.35, 0.35));
            BlockDisplay crystal = displays().block(at, cluster, new Vector3f(size, 0.05f, size), tilt, 22);
            crystal.setBrightness(new Display.Brightness(13, 13));
            Vector3f full = new Vector3f(size);
            displays().later(1, () -> Displays.animateBlock(crystal, full, tilt, new Vector3f(0, size / 2 - 0.1f, 0), 3));
            displays().later(16, () -> {
                if (crystal.isValid()) {
                    Fx.particle(crystal.getLocation().add(0, 0.5, 0), Particle.BLOCK, 10, 0.3, 0.1, Material.AMETHYST_BLOCK.createBlockData());
                }
                Displays.animateBlock(crystal, new Vector3f(size, 0.05f, size), tilt, new Vector3f(), 4);
            });
        }
        Fx.sound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.4f, 0.6f + (float) radius * 0.1f);
        for (LivingEntity target : combat().nearby(player, center, radius + 0.9)) {
            double d = Combat.distanceToBody(target, center);
            if (d >= radius - 1.3 && hit.add(target.getUniqueId())) {
                combat().damage(player, target, 6);
                applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
                Combat.setVelocity(target, target.getVelocity().setY(0.7));
            }
        }
    }

    @Override
    public void shutdown() {
        wardBroken.clear();
    }
}
