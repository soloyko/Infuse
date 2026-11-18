package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Brimstone Claymore, the Volcanic Claymore: magma, obsidian and eruptions.
 * Passive Magma Blood, on-hit Molten Splash, F Geyser Leap, Sneak+F Obsidian Skin.
 */
public final class ClaymoreWeapon extends SwordAbility implements Listener {

    private static final int MAGMA = 0xFF5A1F;
    private static final int GLOW = 0xFFB347;
    private static final int OBSIDIAN = 0x3B2A4A;
    private static final int POOL_TICKS = 100;
    private static final int SKIN_TICKS = 120;
    private static final BlockData MAGMA_BLOCK = Material.MAGMA_BLOCK.createBlockData();
    private static final BlockData MOLTEN = Material.ORANGE_STAINED_GLASS.createBlockData();
    private static final BlockData OBSIDIAN_BLOCK = Material.OBSIDIAN.createBlockData();
    private static final BlockData CRYING = Material.CRYING_OBSIDIAN.createBlockData();

    private final NamespacedKey skinKey;
    /** Players wearing obsidian right now. */
    private final Set<UUID> armored = new HashSet<>();

    public ClaymoreWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.CLAYMORE);
        this.skinKey = new NamespacedKey(plugin, "obsidian_skin");
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.FIRE_RESISTANCE, 0, 0));
    }

    /** Magma Blood: lava heals you. */
    @Override
    public void tickHeld(Player player, long tick) {
        Material feet = player.getLocation().getBlock().getType();
        if (feet == Material.LAVA || player.getLocation().add(0, 0.5, 0).getBlock().getType() == Material.LAVA) {
            combat().heal(player, 1);
            Fx.particle(player.getLocation().add(0, 1, 0), Particle.LAVA, 2, 0.3, 0);
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        Location at = Combat.center(target);
        target.setFireTicks(Math.max(target.getFireTicks(), 80));
        for (LivingEntity near : combat().nearby(player, at, 2.5)) {
            if (near != target) {
                near.setFireTicks(Math.max(near.getFireTicks(), 60));
                combat().damage(player, near, 3);
            }
        }
        fx().burst(at, MAGMA, 1.8f);
        Fx.particle(at, Particle.LAVA, 6, 0.4, 0);
        Fx.particle(at, Particle.BLOCK, 12, 0.4, 0.1, MAGMA_BLOCK);
        Fx.sound(at, Sound.BLOCK_LAVA_POP, 1.2f, 0.8f);
    }

    // ---- Geyser Leap ----

    /**
     * Geyser Leap: the ground under you rumbles and splits, then a geyser of lava bursts out and
     * hurls you high into the air. Enemies next to it are thrown up and set alight, and a molten
     * pool is left behind that burns anything standing in it for five seconds.
     */
    @Override
    public boolean primary(Player player) {
        Location spot = Combat.ground(player.getLocation().add(0, 0.5, 0));
        status().protectFromFall(player, 140);
        Fx.sound(spot, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 0.6f);
        Fx.sound(spot, Sound.ENTITY_WARDEN_DIG, 0.8f, 1.4f);
        fx().crack(spot, MAGMA, 2.6f, 30);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player)) {
                    cancel();
                    return;
                }
                if (ticks < 8) {
                    // the rumble
                    Fx.particle(spot.clone().add(0, 0.2, 0), Particle.LAVA, 2, 0.6, 0);
                    Fx.particle(spot.clone().add(0, 0.2, 0), Particle.LARGE_SMOKE, 2, 0.6, 0.02);
                    return;
                }
                cancel();
                erupt(player, spot);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void erupt(Player player, Location spot) {
        Vector look = player.getEyeLocation().getDirection().setY(0);
        Vector forward = look.lengthSquared() < 1.0E-4 ? new Vector() : look.normalize().multiply(0.45);
        Combat.setVelocity(player, forward.setY(1.75));
        Fx.sound(spot, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.7f);
        Fx.sound(spot, Sound.ITEM_BUCKET_EMPTY_LAVA, 1.5f, 0.6f);
        // the geyser: a column of molten rock that shoots up and falls back
        Location base = spot.clone();
        base.setYaw(0);
        base.setPitch(0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            float size = 1.1f - i * 0.12f;
            BlockData data = i % 2 == 0 ? MOLTEN : MAGMA_BLOCK;
            BlockDisplay chunk = displays().block(base.clone().add(0, 0.3, 0), data, new Vector3f(size), new Quaternionf(), 22);
            chunk.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            Quaternionf tumble = new Quaternionf().rotateXYZ((float) r.nextDouble(-0.5, 0.5), (float) r.nextDouble(3), 0);
            float height = 1.2f + i * 1.1f;
            displays().later(1 + i, () -> Displays.animateBlock(chunk, new Vector3f(size), tumble, new Vector3f(0, height, 0), 4));
            displays().later(10 + i, () -> Displays.animateBlock(chunk, new Vector3f(size * 0.4f), tumble, new Vector3f(0, 0.1f, 0), 8));
        }
        fx().flamePillar(spot, 1.6f, 5.5f, 16, false);
        fx().flash(spot.clone().add(0, 1.5, 0), GLOW, 3f);
        Fx.particle(spot.clone().add(0, 1, 0), Particle.LAVA, 30, 0.6, 0.1);
        Fx.particle(spot.clone().add(0, 2, 0), Particle.FLAME, 40, 0.5, 0.25);
        for (LivingEntity target : combat().nearby(player, spot.clone().add(0, 1, 0), 3)) {
            combat().damage(player, target, 7);
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            Combat.knockback(target, spot, 0.5, 1.0);
        }
        moltenPool(player, spot);
    }

    /** A pool of molten rock left where the geyser burst: it burns anything standing in it. */
    private void moltenPool(Player player, Location spot) {
        List<BlockDisplay> tiles = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 8;
            double d = i == 0 ? 0 : r.nextDouble(1.0, 2.0);
            Location p = Combat.ground(spot.clone().add(Math.cos(a) * d, 0.6, Math.sin(a) * d));
            p.setYaw(0);
            p.setPitch(0);
            Quaternionf turn = new Quaternionf().rotateY((float) r.nextDouble(Math.PI));
            float size = (float) r.nextDouble(1.0, 1.5);
            BlockDisplay tile = displays().block(p.add(0, 0.03, 0), MAGMA_BLOCK, new Vector3f(size, 0.06f, size), turn, POOL_TICKS + 10);
            tile.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            tiles.add(tile);
        }
        Map<UUID, Integer> burned = new HashMap<>();
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > POOL_TICKS || spot.getWorld() == null) {
                    tiles.forEach(t -> displays().remove(t));
                    Fx.particle(spot.clone().add(0, 0.3, 0), Particle.LARGE_SMOKE, 12, 1.2, 0.02);
                    cancel();
                    return;
                }
                if (ticks % 3 == 0) {
                    Fx.particle(spot.clone().add(0, 0.2, 0), Particle.LAVA, 1, 1.4, 0);
                    Fx.particle(spot.clone().add(0, 0.3, 0), Particle.SMOKE, 2, 1.4, 0.01);
                }
                if (ticks % 25 == 0) {
                    Fx.sound(spot, Sound.BLOCK_LAVA_POP, 1f, 0.8f);
                }
                for (LivingEntity target : combat().nearby(player, spot.clone().add(0, 0.5, 0), 2.4)) {
                    if (target.getLocation().getY() - spot.getY() > 1.2) {
                        continue;
                    }
                    Integer last = burned.get(target.getUniqueId());
                    if (last == null || ticks - last >= 10) {
                        burned.put(target.getUniqueId(), ticks);
                        combat().damage(player, target, 2, true);
                        target.setFireTicks(Math.max(target.getFireTicks(), 60));
                        Fx.particle(target.getLocation().add(0, 0.2, 0), Particle.FLAME, 6, 0.3, 0.03);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---- Obsidian Skin ----

    /**
     * Obsidian Skin: plates of obsidian lock over you for six seconds. You take 60% less damage,
     * can't be knocked back and move a little slower, and anyone who hits you is burned. When it
     * ends the obsidian shatters outwards, cutting everything around you.
     */
    @Override
    public boolean secondary(Player player) {
        UUID id = player.getUniqueId();
        if (!armored.add(id)) {
            return false;
        }
        AttributeInstance knockback = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.removeModifier(skinKey);
            knockback.addTransientModifier(new AttributeModifier(skinKey, 1.0, AttributeModifier.Operation.ADD_NUMBER));
        }
        applyEffect(player, PotionEffectType.SLOWNESS, SKIN_TICKS, 0);
        Location at = player.getLocation().add(0, 1, 0);
        Fx.sound(at, Sound.BLOCK_STONE_PLACE, 1.5f, 0.5f);
        Fx.sound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.6f);
        Fx.particle(at, Particle.BLOCK, 30, 0.5, 0.1, OBSIDIAN_BLOCK);
        // the plates: offsets around the body (x right, y up, z forward) and their sizes
        double[][] layout = {
                {0, 1.15, 0.28, 0.55, 0.6, 0.12}, {0, 1.15, -0.28, 0.55, 0.6, 0.12},   // chest and back
                {0.42, 1.45, 0, 0.3, 0.22, 0.4}, {-0.42, 1.45, 0, 0.3, 0.22, 0.4},       // shoulders
                {0.18, 0.45, 0.12, 0.22, 0.4, 0.22}, {-0.18, 0.45, 0.12, 0.22, 0.4, 0.22} // greaves
        };
        List<BlockDisplay> plates = new ArrayList<>();
        Location spawnAt = player.getLocation();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        for (int i = 0; i < layout.length; i++) {
            double[] l = layout[i];
            BlockDisplay plate = displays().block(spawnAt, i < 2 ? CRYING : OBSIDIAN_BLOCK,
                    new Vector3f((float) l[3], (float) l[4], (float) l[5]), new Quaternionf(), SKIN_TICKS + 10);
            plate.setTeleportDuration(1);
            plates.add(plate);
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > SKIN_TICKS) {
                    shatter();
                    return;
                }
                float yaw = (float) Math.toRadians(-player.getLocation().getYaw());
                Quaternionf turn = new Quaternionf().rotateY(yaw);
                Location feet = player.getLocation();
                for (int i = 0; i < plates.size(); i++) {
                    double[] l = layout[i];
                    Vector3f off = turn.transform(new Vector3f((float) -l[0], (float) l[1], (float) l[2]));
                    Location p = feet.clone().add(off.x, off.y, off.z);
                    p.setYaw(0);
                    p.setPitch(0);
                    BlockDisplay plate = plates.get(i);
                    if (plate.isValid()) {
                        plate.teleport(p);
                        if (ticks % 5 == 1) {
                            Displays.animateBlock(plate, new Vector3f((float) l[3], (float) l[4], (float) l[5]), turn, new Vector3f(), 5);
                        }
                    }
                }
                if (ticks % 6 == 0) {
                    Fx.particle(feet.clone().add(0, 1, 0), Particle.DUST, 2, 0.35, 0, Fx.dust(OBSIDIAN, 1.2f));
                    Fx.particle(feet.clone().add(0, 1.2, 0), Particle.FALLING_OBSIDIAN_TEAR, 1, 0.3, 0);
                }
            }

            private void shatter() {
                cancel();
                armored.remove(id);
                AttributeInstance kb = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                if (kb != null) {
                    kb.removeModifier(skinKey);
                }
                Location center = player.getLocation().add(0, 1, 0);
                // the plates fly off in every direction
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (BlockDisplay plate : plates) {
                    if (!plate.isValid()) {
                        continue;
                    }
                    Vector out = plate.getLocation().toVector().subtract(center.toVector()).setY(0.3);
                    if (out.lengthSquared() < 1.0E-4) {
                        out = new Vector(r.nextDouble(-1, 1), 0.3, r.nextDouble(-1, 1));
                    }
                    out.normalize().multiply(2.2);
                    Quaternionf spin = new Quaternionf().rotateXYZ((float) r.nextDouble(3), (float) r.nextDouble(3), 0);
                    Displays.animateBlock(plate, new Vector3f(0.2f), spin, new Vector3f((float) out.getX(), (float) out.getY(), (float) out.getZ()), 6);
                    displays().later(8, () -> displays().remove(plate));
                }
                if (!alive(player)) {
                    return;
                }
                Fx.sound(center, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
                Fx.sound(center, Sound.ENTITY_IRON_GOLEM_DAMAGE, 1f, 0.6f);
                Fx.particle(center, Particle.BLOCK, 50, 0.8, 0.2, OBSIDIAN_BLOCK);
                fx().burst(center, OBSIDIAN, 2.4f);
                for (LivingEntity target : combat().nearby(player, center, 4)) {
                    combat().damage(player, target, 7);
                    Combat.knockback(target, center, 1.0, 0.4);
                    fx().cut(Combat.center(target), Combat.center(target).toVector().subtract(center.toVector()),
                            r.nextDouble(Math.PI), 0.5, 0x8A5CF5, 1.4f, 2);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** The obsidian takes most of the blow, and burns whoever struck it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmoredHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !armored.contains(player.getUniqueId())) {
            return;
        }
        event.setDamage(event.getDamage() * 0.4);
        Fx.particle(player.getLocation().add(0, 1, 0), Particle.BLOCK, 8, 0.3, 0.05, OBSIDIAN_BLOCK);
        Fx.sound(player.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.7f);
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (damager instanceof LivingEntity attacker && attacker != player) {
                attacker.setFireTicks(Math.max(attacker.getFireTicks(), 80));
                Fx.particle(Combat.center(attacker), Particle.FLAME, 10, 0.3, 0.05);
            }
        }
    }

    @Override
    public void shutdown() {
        for (UUID id : armored) {
            Player player = org.bukkit.Bukkit.getPlayer(id);
            AttributeInstance kb = player == null ? null : player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.removeModifier(skinKey);
            }
        }
        armored.clear();
    }
}
