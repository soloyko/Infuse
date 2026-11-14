package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crimson Sword: lifesteal.
 * Passive Blood Reserve, on-hit Bloodthirst, F Blood Crescent, Sneak+F Blood Bond.
 */
public final class CrimsonSword extends SwordAbility {

    private static final int BLOOD_GLOW = 0xFF3048;

    private static final int RED = 0xC4415B;
    private static final int DARK_RED = 0x6B0F1A;

    public CrimsonSword(CustomsPlugin plugin) {
        super(plugin, SwordType.CRIMSON);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.MAX_HEALTH, "max_health", 6, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        combat().heal(player, event.getFinalDamage() * 0.15);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.DUST, 8, 0.3, 0, Fx.dust(RED, 1.2f));
        if (chance(0.25)) {
            status().bleed(player, target, 4);
            Fx.sound(at, Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.8f);
        }
    }

    /** Blood Crescent: a wide slash that travels forward and cuts through everything. */
    @Override
    public boolean primary(Player player) {
        Location start = player.getEyeLocation().subtract(0, 0.4, 0);
        // flies level, so aiming a little down doesn't drive it into the ground
        Vector flat = start.getDirection().setY(0);
        Vector dir = flat.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : flat.normalize();
        Vector right = Fx.right(dir);
        Fx.sound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        Fx.sound(start, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
        Set<UUID> hit = new HashSet<>();
        float size = 2.4f;
        // tilted diagonally so it reads as a crescent from behind, not a flat line
        org.bukkit.entity.ItemDisplay crescent = fx().movingSlash(start.clone().subtract(dir.clone().multiply(0.94 * size)),
                dir, -0.6, BLOOD_GLOW, size, 40);

        new BukkitRunnable() {
            final Location center = start.clone();
            int steps;

            @Override
            public void run() {
                if (!alive(player) || center.getWorld() != player.getWorld() || steps++ >= 14) {
                    fx().fade(crescent, dev.infuse.customs.ability.Fx3D.Shape.SLASH, BLOOD_GLOW, 1, 1);
                    cancel();
                    return;
                }
                center.add(dir.clone().multiply(1.2));
                if (!center.getBlock().isPassable()) {
                    Fx.particle(center, Particle.DUST, 20, 0.5, 0, Fx.dust(DARK_RED, 1.5f));
                    fx().burst(center, 0xD3122E, 1.8f);
                    fx().fade(crescent, dev.infuse.customs.ability.Fx3D.Shape.SLASH, BLOOD_GLOW, 1, 1);
                    cancel();
                    return;
                }
                if (crescent.isValid()) {
                    Location at = center.clone().subtract(dir.clone().multiply(0.94 * size));
                    at.setYaw(0);
                    at.setPitch(0);
                    crescent.teleport(at);
                }
                for (double s = -1.6; s <= 1.6; s += 0.2) {
                    Location p = center.clone().add(right.clone().multiply(s)).subtract(dir.clone().multiply(s * s * 0.25));
                    Fx.particle(p, Particle.DUST, 1, 0.02, 0, Fx.dust(Math.abs(s) > 1.1 ? DARK_RED : RED, 1.4f));
                }
                if (steps % 2 == 0 && steps >= 4) { // not right in the caster's face
                    Fx.particle(center, Particle.SWEEP_ATTACK, 1, 0, 0);
                }
                for (LivingEntity target : combat().nearby(player, center, 2.0)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 7);
                        status().bleed(player, target, 4);
                        Combat.knockback(target, center.clone().subtract(dir), 0.5, 0.2);
                        Fx.particle(Combat.center(target), Particle.DUST, 15, 0.3, 0, Fx.dust(DARK_RED, 1.3f));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** How long the chains hold, how far they let an enemy get, and how many enemies they catch. */
    private static final int BOND_TICKS = 120;
    private static final double BOND_LENGTH = 6.5;
    private static final int BOND_MAX = 4;

    /**
     * Blood Bond: chains of blood lash out to up to four nearby enemies and bind them to you for six
     * seconds. They can't get further than the chain allows, every second drains them and heals
     * you, and when the chains snap they tear them open.
     */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> bound = new java.util.ArrayList<>();
        for (LivingEntity target : combat().nearby(player, player.getLocation(), 10)) {
            if (player.hasLineOfSight(target)) {
                bound.add(target);
            }
            if (bound.size() >= BOND_MAX) {
                break;
            }
        }
        if (bound.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        Location chest = player.getLocation().add(0, 1.1, 0);
        Fx.sound(chest, Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.6f);
        Fx.sound(chest, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.2f);
        fx().flash(chest, BLOOD_GLOW, 2.2f);
        for (LivingEntity target : bound) {
            // the chain lashes out
            fx().beam(chest, Combat.center(target), BLOOD_GLOW, 0.5f, 6);
            fx().burst(Combat.center(target), 0xD3122E, 1.6f);
            Fx.sound(target.getLocation(), Sound.BLOCK_CHAIN_HIT, 1f, 0.8f);
            applyEffect(target, PotionEffectType.SLOWNESS, BOND_TICKS, 0);
        }

        java.util.Map<LivingEntity, BloodChain> chains = new java.util.HashMap<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                bound.removeIf(t -> {
                    boolean gone = !t.isValid() || t.isDead() || t.getWorld() != player.getWorld();
                    if (gone && chains.containsKey(t)) {
                        chains.remove(t).remove();
                    }
                    return gone;
                });
                if (!alive(player) || bound.isEmpty() || ticks > BOND_TICKS) {
                    snap();
                    return;
                }
                Location from = player.getLocation().add(0, 1.1, 0);
                for (LivingEntity target : bound) {
                    Location to = Combat.center(target);
                    chains.computeIfAbsent(target, k -> new BloodChain()).update(from, to, ticks);
                    // the chain only lets them get so far: past it, they're yanked back towards you
                    Vector away = to.toVector().subtract(from.toVector());
                    double distance = away.length();
                    if (distance > BOND_LENGTH) {
                        Vector pull = away.multiply(-1 / distance).multiply(Math.min(1.2, 0.35 + (distance - BOND_LENGTH) * 0.3));
                        Combat.setVelocity(target, pull.setY(Math.max(0.15, pull.getY())));
                        if (ticks % 6 == 0) {
                            Fx.sound(to, Sound.BLOCK_CHAIN_STEP, 1f, 0.6f);
                        }
                    }
                    if (ticks % 20 == 0) {
                        combat().damage(player, target, 2.5, true);
                        combat().heal(player, 1.5);
                        // a pulse of blood runs down the chain to you
                        fx().drift(to, from.toVector().subtract(to.toVector()), BLOOD_GLOW, 0.6f, 6);
                        Fx.particle(to, Particle.DAMAGE_INDICATOR, 3, 0.2, 0.05);
                    }
                }
                if (ticks % 20 == 0) {
                    Fx.sound(from, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.4f);
                    Fx.particle(from, Particle.DUST, 8, 0.3, 0, Fx.dust(RED, 1.2f));
                }
            }

            private void snap() {
                cancel();
                chains.values().forEach(BloodChain::remove);
                chains.clear();
                for (LivingEntity target : bound) {
                    if (!target.isValid() || target.isDead()) {
                        continue;
                    }
                    Location at = Combat.center(target);
                    combat().damage(player, target, 5, true);
                    status().bleed(player, target, 4);
                    fx().burst(at, 0xD3122E, 2f);
                    fx().cut(at, at.toVector().subtract(player.getLocation().toVector()), Math.random() * Math.PI, 0.5, BLOOD_GLOW, 1.6f, 2);
                    Fx.particle(at, Particle.DUST, 25, 0.4, 0, Fx.dust(DARK_RED, 1.6f));
                    Fx.particle(at, Particle.BLOCK, 10, 0.2, 0.1, org.bukkit.Material.REDSTONE_BLOCK.createBlockData());
                }
                if (alive(player)) {
                    Fx.sound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.4f, 0.6f);
                    Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /**
     * A chain between you and a bound enemy: real iron chain links outlined in blood red, sagging
     * in the middle, with a bright pulse running along it.
     */
    private final class BloodChain {
        private final List<org.bukkit.entity.BlockDisplay> links = new java.util.ArrayList<>();

        void update(Location from, Location to, int tick) {
            Vector span = to.toVector().subtract(from.toVector());
            double length = span.length();
            int n = Math.max(1, (int) Math.ceil(length / 0.85));
            while (links.size() < n) {
                org.bukkit.entity.BlockDisplay link = displays().block(from, org.bukkit.Material.IRON_CHAIN.createBlockData(),
                        new org.joml.Vector3f(0.6f), new org.joml.Quaternionf(), BOND_TICKS + 40);
                link.setGlowing(true);
                link.setGlowColorOverride(org.bukkit.Color.fromRGB(BLOOD_GLOW));
                link.setBrightness(new org.bukkit.entity.Display.Brightness(12, 12));
                link.setTeleportDuration(1);
                links.add(link);
            }
            while (links.size() > n) {
                displays().remove(links.remove(links.size() - 1));
            }
            double sagMax = Math.min(0.7, length * 0.07);
            for (int i = 0; i < n; i++) {
                double t0 = i / (double) n;
                double t1 = (i + 1) / (double) n;
                Vector a = from.toVector().add(span.clone().multiply(t0)).subtract(new Vector(0, Math.sin(t0 * Math.PI) * sagMax, 0));
                Vector b = from.toVector().add(span.clone().multiply(t1)).subtract(new Vector(0, Math.sin(t1 * Math.PI) * sagMax, 0));
                Vector seg = b.clone().subtract(a);
                double len = seg.length();
                if (len < 1.0E-3) {
                    continue;
                }
                Vector mid = a.clone().add(b).multiply(0.5);
                org.joml.Quaternionf rot = new org.joml.Quaternionf().rotationTo(0, 1, 0,
                        (float) (seg.getX() / len), (float) (seg.getY() / len), (float) (seg.getZ() / len));
                // every other link turned a quarter, like a real chain
                if (i % 2 == 1) {
                    rot.rotateY((float) (Math.PI / 2));
                }
                org.bukkit.entity.BlockDisplay link = links.get(i);
                if (!link.isValid()) {
                    continue;
                }
                Location at = mid.toLocation(from.getWorld());
                link.teleport(at);
                link.setInterpolationDelay(0);
                link.setInterpolationDuration(1);
                link.setTransformation(dev.infuse.customs.ability.Displays.centered(new org.joml.Vector3f(0.55f, (float) len * 1.05f, 0.55f), rot));
            }
            // the pulse of blood running along it
            double pulse = (tick * 0.35) % 1.0;
            Vector p = from.toVector().add(span.clone().multiply(pulse)).subtract(new Vector(0, Math.sin(pulse * Math.PI) * sagMax, 0));
            Fx.particle(p.toLocation(from.getWorld()), Particle.DUST, 3, 0.05, 0, Fx.dust(BLOOD_GLOW, 1.4f));
        }

        void remove() {
            for (org.bukkit.entity.BlockDisplay link : links) {
                displays().remove(link);
            }
            links.clear();
        }
    }
}
