package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
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
 * Passive Arcane Ward, on-hit Arcane Spark, F Arcane Missiles, Sneak+F Crystal Sentinel.
 */
public final class StaffWeapon extends SwordAbility implements Listener {

    private static final int AMETHYST = 0xB57EDC;
    private static final int PALE = 0xF3D9FF;
    private static final int WARD_RECHARGE = 240;

    /** Player -> server tick their ward broke (absent while the ward is up). */
    private final Map<UUID, Integer> wardBroken = new HashMap<>();

    public StaffWeapon(CustomsPlugin plugin) {
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
        // a rune portal opens above your shoulder as the missiles leave
        Vector look = player.getEyeLocation().getDirection();
        Location portal = player.getEyeLocation().add(0, 0.9, 0).subtract(look.clone().setY(0).multiply(0.4));
        org.bukkit.entity.ItemDisplay rune = fx().sigil(portal, look, 0xC48BFF, 1.6f, 22);
        displays().later(14, () -> fx().fade(rune, dev.infuse.customs.ability.Fx3D.Shape.RUNE, 0xC48BFF, 1, 2));
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
        org.bukkit.entity.ItemDisplay glow = fx().orb(pos, 0xC48BFF, 1.3f, 50);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !shard.isValid() || ticks > 45 || pos.getWorld() != player.getWorld()) {
                    displays().remove(shard);
                    displays().remove(glow);
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
                    displays().remove(glow);
                    cancel();
                    return;
                }
                pos.add(velocity);
                shard.teleport(pos);
                glow.teleport(pos);
                Fx.particle(pos, Particle.DUST, 2, 0.05, 0, Fx.dust(AMETHYST, 1.3f));
                Fx.particle(pos, Particle.WITCH, 1, 0.05, 0);
                for (LivingEntity hit : combat().nearby(player, pos, 0.9)) {
                    combat().damage(player, hit, 3.5, true);
                    Fx.burst(pos, Particle.END_ROD, 10, 0.15);
                    Fx.particle(pos, Particle.DUST, 10, 0.25, 0, Fx.dust(PALE, 1.2f));
                    Fx.sound(pos, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.4f);
                    fx().burst(pos, 0xE6C8FF, 1.4f);
                    displays().remove(shard);
                    displays().remove(glow);
                    cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** How long the sentinel stands, how often it fires and how often it mends. */
    private static final int SENTINEL_TICKS = 160;
    private static final int SENTINEL_FIRE = 10;
    private static final int SENTINEL_MEND = 40;
    private static final double SENTINEL_RANGE = 11;

    /**
     * Crystal Sentinel: a floating amethyst crystal forms where you aim. For eight seconds it fires
     * bolts at the nearest enemy it can see and, every two seconds, mends you and your teammates
     * standing near it.
     */
    @Override
    public boolean secondary(Player player) {
        Location aim = combat().aimPoint(player, 18);
        if (aim == null) {
            Location eye = player.getEyeLocation();
            aim = eye.clone().add(eye.getDirection().setY(0).normalize().multiply(4));
        }
        Location ground = Combat.ground(aim.clone().add(0, 0.5, 0));
        Location core = ground.clone().add(0, 1.7, 0);
        core.setYaw(0);
        core.setPitch(0);
        // the crystal: a tall amethyst diamond, two smaller shards orbiting it, a rune on the ground
        Quaternionf diamond = new Quaternionf().rotateY((float) (Math.PI / 4)).rotateX((float) (Math.PI / 4)).rotateZ((float) (Math.PI / 4));
        Vector3f size = new Vector3f(0.55f, 0.9f, 0.55f);
        BlockDisplay crystal = displays().block(core, Material.AMETHYST_BLOCK.createBlockData(), new Vector3f(0.05f), diamond,
                SENTINEL_TICKS + 10);
        crystal.setGlowing(true);
        crystal.setGlowColorOverride(org.bukkit.Color.fromRGB(AMETHYST));
        crystal.setBrightness(new Display.Brightness(15, 15));
        displays().later(1, () -> Displays.animateBlock(crystal, size, diamond, new Vector3f(), 6));
        ItemDisplay glow = fx().orb(core, AMETHYST, 2.4f, SENTINEL_TICKS + 10);
        ItemDisplay rune = fx().sigil(ground.clone().add(0, 0.08, 0), new Vector(0, 1, 0), AMETHYST, 3.2f, SENTINEL_TICKS + 10);
        BlockDisplay[] shards = new BlockDisplay[2];
        for (int i = 0; i < shards.length; i++) {
            shards[i] = displays().block(core, Material.AMETHYST_CLUSTER.createBlockData(), new Vector3f(0.4f), new Quaternionf(),
                    SENTINEL_TICKS + 10);
            shards[i].setBrightness(new Display.Brightness(15, 15));
            shards[i].setTeleportDuration(2);
        }
        fx().flash(core, PALE, 2.6f);
        Fx.particle(core, Particle.BLOCK, 30, 0.4, 0.1, Material.AMETHYST_BLOCK.createBlockData());
        Fx.sound(core, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 0.6f);
        Fx.sound(core, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || player.getWorld() != core.getWorld() || ticks > SENTINEL_TICKS || !crystal.isValid()) {
                    shatter();
                    return;
                }
                // a slow spin and a gentle bob
                float spin = ticks * 0.08f;
                float bob = (float) Math.sin(ticks * 0.12) * 0.12f;
                if (ticks > 6 && ticks % 4 == 0) {
                    Quaternionf turn = new Quaternionf().rotateY(spin).mul(diamond);
                    Displays.animateBlock(crystal, size, turn, new Vector3f(0, bob, 0), 4);
                }
                for (int i = 0; i < shards.length; i++) {
                    double a = ticks * 0.15 + i * Math.PI;
                    shards[i].teleport(core.clone().add(Math.cos(a) * 1.0, 0.2 + Math.sin(ticks * 0.1 + i) * 0.3, Math.sin(a) * 1.0));
                }
                if (ticks % 3 == 0) {
                    Fx.particle(core, Particle.END_ROD, 1, 0.3, 0.01);
                }
                if (ticks % SENTINEL_FIRE == 0) {
                    fire();
                }
                if (ticks % SENTINEL_MEND == 0) {
                    mend();
                }
            }

            private void fire() {
                LivingEntity best = null;
                double bestDistance = SENTINEL_RANGE * SENTINEL_RANGE;
                for (LivingEntity target : combat().nearby(player, core, SENTINEL_RANGE)) {
                    double d = Combat.center(target).distanceSquared(core);
                    if (d < bestDistance && core.getWorld().rayTraceBlocks(core, Combat.center(target).toVector().subtract(core.toVector()),
                            Math.sqrt(d), FluidCollisionMode.NEVER, true) == null) {
                        best = target;
                        bestDistance = d;
                    }
                }
                if (best == null) {
                    return;
                }
                Location at = Combat.center(best);
                fx().beam(core, at, AMETHYST, 0.45f, 5);
                fx().beam(core, at, PALE, 0.15f, 3);
                fx().burst(at, PALE, 1.3f);
                Fx.particle(at, Particle.BLOCK, 6, 0.2, 0.1, Material.AMETHYST_BLOCK.createBlockData());
                combat().damage(player, best, 3.5, true);
                applyEffect(best, PotionEffectType.SLOWNESS, 20, 0);
                Fx.sound(core, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
                Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.2f);
            }

            private void mend() {
                org.bukkit.scoreboard.Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
                for (Player near : core.getNearbyPlayers(5.5)) {
                    if (near != player && (team == null || !team.hasEntry(near.getName()))) {
                        continue;
                    }
                    combat().heal(near, 2);
                    Location at = near.getLocation().add(0, 1, 0);
                    fx().beam(core, at, 0xF3D9FF, 0.3f, 6);
                    Fx.particle(at, Particle.HEART, 2, 0.3, 0);
                }
                fx().helix(ground.clone().add(0, 0.1, 0), 1.2, 2.2, 3, Particle.WITCH, null, 14);
                Fx.sound(core, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
            }

            private void shatter() {
                cancel();
                if (crystal.isValid()) {
                    Fx.particle(core, Particle.BLOCK, 40, 0.5, 0.2, Material.AMETHYST_BLOCK.createBlockData());
                    fx().burst(core, PALE, 2.2f);
                    Fx.sound(core, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.4f, 0.7f);
                }
                displays().remove(crystal);
                displays().remove(glow);
                displays().remove(rune);
                for (BlockDisplay shard : shards) {
                    displays().remove(shard);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @Override
    public void shutdown() {
        wardBroken.clear();
    }
}
