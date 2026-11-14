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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.meta.ItemMeta;
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
 * Hearthflame, the Titan Hammer (mace): the earth itself as a weapon.
 * Passive Unshakable, on-hit Quake, F Landslide, Sneak+F Earthen Bulwark.
 */
public final class HammerWeapon extends SwordAbility implements Listener {

    private static final int BULWARK_TICKS = 160;

    /** Bulwarked player -> server tick it ends. */
    private final Map<UUID, Integer> bulwark = new HashMap<>();

    public HammerWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.HAMMER);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.KNOCKBACK_RESISTANCE, "knockback_resistance", 1.0, AttributeModifier.Operation.ADD_NUMBER);
        addModifier(meta, Attribute.ARMOR, "armor", 3, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Location at = target.getLocation();
        for (LivingEntity near : combat().nearby(player, at, 3)) {
            applyEffect(near, PotionEffectType.SLOWNESS, 20, 3);
            applyEffect(near, PotionEffectType.MINING_FATIGUE, 20, 2);
            if (near != target) {
                combat().damage(player, near, 3);
            }
            Combat.setVelocity(near, near.getVelocity().setY(0.35));
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            displays().eruptGround(at.clone().add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4), 0.5f, 0.5f);
        }
        Fx.sound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.9f, 0.6f);
        fx().crack(Combat.ground(target.getLocation()), 0xFF8A2A, 3f, 30);
    }

    /**
     * Landslide: you slam the hammer down and the ground under the enemy you aim at gives way: a
     * slide of rubble carries it six blocks away from you and buries it to the waist, held fast
     * for two seconds.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 20, 0.8);
        if (target == null) {
            tell(player, "Look at an enemy to bring the ground down under.");
            return false;
        }
        Location slam = player.getLocation();
        fx().crack(slam, 0xFF8A2A, 2f, 30);
        displays().eruptGround(slam, 0.6f, 0.5f);
        Fx.sound(slam, org.bukkit.Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.7f);
        Vector away = target.getLocation().toVector().subtract(slam.toVector()).setY(0);
        away = away.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : away.normalize();
        Vector push = away;
        org.bukkit.block.data.BlockData[] rubble = {org.bukkit.Material.GRAVEL.createBlockData(),
                org.bukkit.Material.COBBLESTONE.createBlockData(), org.bukkit.Material.COARSE_DIRT.createBlockData()};
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        Fx.sound(target.getLocation(), org.bukkit.Sound.BLOCK_GRAVEL_BREAK, 1.5f, 0.6f);
        Fx.sound(target.getLocation(), org.bukkit.Sound.ENTITY_RAVAGER_STEP, 1.2f, 0.5f);

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                if (!target.isValid() || target.isDead() || !alive(player) || target.getWorld() != player.getWorld()) {
                    cancel();
                    return;
                }
                Location feet = target.getLocation();
                if (t <= 12) {
                    // carried along on the slide
                    Combat.setVelocity(target, push.clone().multiply(0.55).setY(Math.min(0.05, target.getVelocity().getY())));
                    for (int i = 0; i < 3; i++) {
                        Location chunk = feet.clone().add(r.nextDouble(-0.8, 0.8), 0.1, r.nextDouble(-0.8, 0.8));
                        chunk.setYaw(0);
                        chunk.setPitch(0);
                        float size = (float) r.nextDouble(0.25, 0.45);
                        org.bukkit.entity.BlockDisplay rock = displays().block(chunk, rubble[r.nextInt(rubble.length)], new Vector3f(size),
                                new Quaternionf().rotateXYZ(r.nextFloat(), r.nextFloat(), r.nextFloat()), 14);
                        Displays.animateBlock(rock, new Vector3f(0.05f), new Quaternionf(), new Vector3f(0, -0.3f, 0), 12);
                    }
                    Fx.particle(feet.clone().add(0, 0.2, 0), Particle.BLOCK, 10, 0.5, 0.1, rubble[0]);
                    if (t % 3 == 0) {
                        Fx.sound(feet, org.bukkit.Sound.BLOCK_GRAVEL_STEP, 1.2f, 0.6f);
                    }
                    return;
                }
                // buried to the waist
                cancel();
                Location ground = Combat.ground(feet.clone().add(0, 0.5, 0));
                ground.setYaw(0);
                ground.setPitch(0);
                combat().damage(player, target, 6, true);
                hold(target, 40);
                fx().crack(ground, 0xC08A4A, 2f, 40);
                Fx.sound(ground, org.bukkit.Sound.BLOCK_ROOTED_DIRT_PLACE, 1.5f, 0.6f);
                Fx.sound(ground, org.bukkit.Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 0.6f);
                java.util.List<org.bukkit.entity.BlockDisplay> mound = new java.util.ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Location piece = ground.clone().add(Math.cos(a) * 0.55, 0.35, Math.sin(a) * 0.55);
                    org.bukkit.entity.BlockDisplay b = displays().block(piece, rubble[i % rubble.length], new Vector3f(0.05f),
                            new Quaternionf().rotateY((float) a).rotateX(0.25f), 50);
                    Displays.animateBlock(b, new Vector3f(0.55f, 0.75f, 0.55f), new Quaternionf().rotateY((float) a).rotateX(0.25f),
                            new Vector3f(), 3);
                    mound.add(b);
                }
                displays().later(40, () -> {
                    for (org.bukkit.entity.BlockDisplay b : mound) {
                        if (b.isValid()) {
                            Displays.animateBlock(b, new Vector3f(0.05f), new Quaternionf(), new Vector3f(0, -0.4f, 0), 6);
                        }
                    }
                    Fx.particle(ground.clone().add(0, 0.5, 0), Particle.BLOCK, 20, 0.5, 0.1, rubble[2]);
                });
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Earthen Bulwark: stone pillars circle you for 8 seconds; attackers are hurt and thrown back. */
    @Override
    public boolean secondary(Player player) {
        if (bulwark.containsKey(player.getUniqueId())) {
            tell(player, "Your bulwark is already up.");
            return false;
        }
        bulwark.put(player.getUniqueId(), Bukkit.getCurrentTick() + BULWARK_TICKS);
        applyEffect(player, PotionEffectType.RESISTANCE, BULWARK_TICKS, 1);
        Fx.sound(player.getLocation(), Sound.BLOCK_DEEPSLATE_PLACE, 1.5f, 0.5f);
        Fx.sound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 0.6f);
        fx().shockwave(player.getLocation(), 0xB08A5A, 2.6f, 8);
        // stone spikes burst up in a ring as the bulwark rises
        Location bulwarkAt = player.getLocation();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            Location spot = Combat.ground(bulwarkAt.clone().add(Math.cos(a) * 3.2, 1.5, Math.sin(a) * 3.2));
            int delay = i % 3;
            displays().later(1 + delay, () -> fx().spike(spot, Material.COBBLED_DEEPSLATE.createBlockData(), 1.3f, 0.6f, 0.45, 24));
        }
        fx().crack(bulwarkAt, 0xB08A5A, 4.5f, 40);

        BlockData[] stone = {Material.DEEPSLATE_BRICKS.createBlockData(), Material.CHISELED_DEEPSLATE.createBlockData(),
                Material.POLISHED_DEEPSLATE.createBlockData()};
        List<BlockDisplay> pillars = new ArrayList<>();
        Vector3f size = new Vector3f(0.5f, 1.05f, 0.5f); // waist high: stays out of the first-person view
        for (int i = 0; i < 6; i++) {
            Location spot = pillarSpot(player, i, 0);
            BlockDisplay pillar = displays().block(spot, stone[i % 3], new Vector3f(size.x, 0.05f, size.z), new Quaternionf(), 0);
            pillars.add(pillar);
            displays().later(1 + i, () -> Displays.animateBlock(pillar, size, new Quaternionf(), new Vector3f(), 5));
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                Integer until = bulwark.get(player.getUniqueId());
                if (!alive(player) || until == null || Bukkit.getCurrentTick() >= until) {
                    bulwark.remove(player.getUniqueId());
                    for (BlockDisplay pillar : pillars) {
                        Displays.animateBlock(pillar, new Vector3f(size.x, 0.05f, size.z), new Quaternionf(), new Vector3f(0, -0.5f, 0), 5);
                        displays().later(6, () -> displays().remove(pillar));
                        if (pillar.isValid()) {
                            Fx.particle(pillar.getLocation(), Particle.BLOCK, 12, 0.3, 0.05, Material.DEEPSLATE.createBlockData());
                        }
                    }
                    Fx.sound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.7f);
                    cancel();
                    return;
                }
                for (int i = 0; i < pillars.size(); i++) {
                    BlockDisplay pillar = pillars.get(i);
                    if (pillar.isValid()) {
                        pillar.teleport(pillarSpot(player, i, ticks));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static Location pillarSpot(Player player, int index, int ticks) {
        double a = index * Math.PI / 3 + ticks * 0.03;
        Location base = player.getLocation();
        base.setYaw(0);
        base.setPitch(0);
        return base.add(Math.cos(a) * 1.9, 0.52, Math.sin(a) * 1.9);
    }

    /** Attackers of a bulwarked player take 4 damage and are thrown back. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBulwarkHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !bulwark.containsKey(player.getUniqueId())
                || combat().isAbilityDamage()) {
            return;
        }
        LivingEntity attacker = null;
        if (event.getDamager() instanceof LivingEntity living) {
            attacker = living;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) {
            attacker = living;
        }
        if (attacker == null || attacker == player) {
            return;
        }
        combat().damage(player, attacker, 4);
        Combat.knockback(attacker, player.getLocation(), 1.1, 0.4);
        Fx.particle(Combat.center(attacker), Particle.BLOCK, 15, 0.3, 0.1, Material.DEEPSLATE.createBlockData());
        Fx.sound(attacker.getLocation(), Sound.BLOCK_DEEPSLATE_HIT, 1.2f, 0.6f);
        fx().burst(Combat.center(attacker), 0xE8822A, 1.4f);
    }

    @Override
    public void shutdown() {
        bulwark.clear();
    }
}
