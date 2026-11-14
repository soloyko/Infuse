package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Terra Blade, the Blade of the Living Earth: the balance between what lives and what dies.
 * Passive Verdance, on-hit Lifebloom, F Equilibrium, Sneak+F Overgrowth.
 */
public final class TerraWeapon extends SwordAbility {

    private static final int GREEN = 0x5BE35B;
    private static final int LEAF = 0x2E9E3E;
    private static final int GOLD = 0xF2E26A;
    private static final double HEDGE_RADIUS = 7;
    private static final double MAX_SHIFT = 10;

    public TerraWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.TERRA);
    }

    @Override
    public void tickHeld(Player player, long tick) {
        Block below = player.getLocation().subtract(0, 0.2, 0).getBlock();
        Material m = below.getType();
        if (m == Material.GRASS_BLOCK || m == Material.MOSS_BLOCK || m == Material.MOSS_CARPET || Tag.LEAVES.isTagged(m)
                || m == Material.PODZOL || m == Material.MYCELIUM || m == Material.ROOTED_DIRT) {
            applyEffect(player, PotionEffectType.REGENERATION, 40, 0);
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        combat().heal(player, 2);
        Location at = Combat.center(target);
        fx().burst(at, GREEN, 1.4f);
        Fx.particle(at, Particle.CHERRY_LEAVES, 10, 0.4, 0.02);
        Fx.particle(player.getLocation().add(0, 1.8, 0), Particle.HAPPY_VILLAGER, 4, 0.3, 0);
        Fx.sound(at, Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 1.2f);
    }

    /**
     * Equilibrium: you and the enemy you aim at share what life you have between you: both move
     * toward the average of your health, by up to five hearts each. It never kills.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 20, 0.8);
        if (target == null) {
            tell(player, "Look at an enemy to balance against.");
            return false;
        }
        double mine = player.getHealth();
        double theirs = target.getHealth();
        // each side moves toward the average by at most five hearts, so a boss can't be halved
        double shift = Math.min(MAX_SHIFT, Math.abs(mine - theirs) / 2);
        double newMine = Math.max(1, Math.min(max(player), mine + Math.signum(theirs - mine) * shift));
        double newTheirs = Math.max(1, Math.min(max(target), theirs - Math.signum(theirs - mine) * shift));

        Location a = player.getLocation().add(0, 1.1, 0);
        Location b = Combat.center(target);
        // two motes of life cross between you: green from the healthier, gold from the weaker
        boolean iGive = mine > theirs;
        fx().beam(a, b, LEAF, 0.18f, 14);
        fx().drift(iGive ? a : b, (iGive ? b : a).toVector().subtract((iGive ? a : b).toVector()), GREEN, 0.8f, 10);
        fx().drift(iGive ? b : a, (iGive ? a : b).toVector().subtract((iGive ? b : a).toVector()), GOLD, 0.6f, 10);
        fx().castCircle(player.getLocation(), GREEN, 1.6f);
        fx().castCircle(target.getLocation(), GOLD, 1.6f);
        Fx.sound(a, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.8f);
        Fx.sound(b, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.6f);

        displays().later(10, () -> {
            if (!alive(player) || !target.isValid() || target.isDead()) {
                return;
            }
            player.setHealth(Math.min(max(player), newMine));
            boolean drained = newTheirs < target.getHealth();
            target.setHealth(Math.max(1, Math.min(max(target), newTheirs)));
            if (drained) {
                target.playHurtAnimation(0);
            }
            fx().flash(Combat.center(player), iGive ? GOLD : GREEN, 2f);
            fx().flash(Combat.center(target), iGive ? GREEN : GOLD, 2f);
            Fx.particle(Combat.center(player), Particle.HAPPY_VILLAGER, 10, 0.4, 0);
            Fx.particle(Combat.center(target), Particle.DUST, 12, 0.4, 0, Fx.dust(GOLD, 1.2f));
            Fx.sound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 1.4f);
        });
        return true;
    }

    private static double max(LivingEntity e) {
        AttributeInstance attr = e.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20 : attr.getValue();
    }

    /**
     * Overgrowth: a thorny hedge grows outward from you to seven blocks over two seconds. Enemies
     * it passes are rooted for a moment and poisoned; you and your allies inside are healed.
     */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        Location ground = Combat.ground(center.clone().add(0, 0.5, 0));
        ground.setYaw(0);
        ground.setPitch(0);
        Set<UUID> caught = new HashSet<>();
        List<BlockDisplay> hedge = new ArrayList<>();
        BlockData[] leaves = {Material.AZALEA_LEAVES.createBlockData(), Material.FLOWERING_AZALEA_LEAVES.createBlockData(),
                Material.OAK_LEAVES.createBlockData()};
        fx().castCircle(ground, GREEN, 2.4f);
        Fx.sound(ground, Sound.BLOCK_AZALEA_LEAVES_PLACE, 1.4f, 0.6f);
        Fx.sound(ground, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.4f, 0.6f);

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (t > 40 || !alive(player) || ground.getWorld() != player.getWorld()) {
                    finish();
                    return;
                }
                double radius = HEDGE_RADIUS * t / 40.0;
                if (t % 3 == 0) {
                    // a new ring of bushes bursts up at the hedge's edge
                    int count = (int) Math.max(6, radius * 3.2);
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < count; i++) {
                        double a = i * Math.PI * 2 / count + r.nextDouble(0.2);
                        Location spot = Combat.ground(ground.clone().add(Math.cos(a) * radius, 1.5, Math.sin(a) * radius));
                        spot.setYaw(0);
                        spot.setPitch(0);
                        float size = (float) r.nextDouble(0.45, 0.8);
                        BlockDisplay bush = displays().block(spot, leaves[r.nextInt(leaves.length)], new Vector3f(0.05f),
                                new Quaternionf().rotateY((float) r.nextDouble(Math.PI)), 60);
                        Displays.animateBlock(bush, new Vector3f(size, size * 1.2f, size), new Quaternionf().rotateY((float) a),
                                new Vector3f(0, size * 0.55f, 0), 3);
                        hedge.add(bush);
                    }
                    Fx.sound(ground, Sound.BLOCK_GRASS_PLACE, 1f, 0.7f + t * 0.01f);
                }
                for (LivingEntity e : combat().nearby(player, ground, radius + 0.6)) {
                    if (caught.add(e.getUniqueId())) {
                        hold(e, 30);
                        applyEffect(e, PotionEffectType.POISON, 80, 1);
                        combat().damage(player, e, 3, true);
                        Fx.particle(Combat.center(e), Particle.BLOCK, 15, 0.3, 0.1, Material.OAK_LEAVES.createBlockData());
                        Fx.sound(e.getLocation(), Sound.ENCHANT_THORNS_HIT, 1f, 0.8f);
                    }
                }
                if (t % 10 == 0) {
                    for (Player p : ground.getNearbyPlayers(radius)) {
                        if (p == player || plugin.arcs().allies(player, p)) {
                            combat().heal(p, 2);
                            Fx.particle(p.getLocation().add(0, 2, 0), Particle.HAPPY_VILLAGER, 4, 0.3, 0);
                        }
                    }
                }
            }

            private void finish() {
                cancel();
                // the hedge withers away
                for (BlockDisplay bush : hedge) {
                    if (bush.isValid()) {
                        Displays.animateBlock(bush, new Vector3f(0.05f), new Quaternionf(), new Vector3f(0, -0.2f, 0), 10);
                    }
                }
                displays().later(11, () -> hedge.forEach(displays()::remove));
                Fx.particle(ground.clone().add(0, 0.5, 0), Particle.CHERRY_LEAVES, 40, HEDGE_RADIUS * 0.6, 0.02);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
