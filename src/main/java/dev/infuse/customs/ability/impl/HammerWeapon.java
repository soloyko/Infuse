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
 * Earthshaker, the Titan Hammer (mace): the earth itself as a weapon.
 * Passive Unshakable, on-hit Quake, F Seismic Slam, Sneak+F Earthen Bulwark.
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
    }

    /** Seismic Slam: a ring of erupting earth rolls out to 8 blocks. */
    @Override
    public boolean primary(Player player) {
        Location center = player.getLocation();
        if (Displays.groundBelow(center.clone().subtract(0, 0.3, 0)) == null) {
            tell(player, "You need solid ground to slam.");
            return false;
        }
        Fx.sound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        Fx.sound(center, Sound.BLOCK_ANVIL_LAND, 1f, 0.5f);
        fx().shockwave(center, 0xE8822A, 8.5f, 12);
        fx().shockwave(center, 0xFFC27A, 5f, 8);
        fx().impact(center.clone().add(0, 0.6, 0), 0xFF9A3C, 2.4f);
        Fx.particle(center.clone().add(0, 0.3, 0), Particle.EXPLOSION, 2, 0.4, 0);
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            double radius = 1.2;

            @Override
            public void run() {
                if (radius > 8 || player.getWorld() != center.getWorld()) {
                    cancel();
                    return;
                }
                int points = (int) (radius * 2.6);
                double offset = radius * 0.7; // stagger rings so they don't line up in spokes
                for (int i = 0; i < points; i++) {
                    double a = offset + i * 2 * Math.PI / points;
                    Location p = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                    displays().eruptGround(p, 0.75f, 0.9f);
                    Block ground = Displays.groundBelow(p);
                    if (ground != null) {
                        Fx.particle(ground.getLocation().add(0.5, 1.1, 0.5), Particle.BLOCK, 4, 0.3, 0.05, ground.getBlockData());
                    }
                }
                for (LivingEntity target : combat().nearby(player, center, radius + 0.5)) {
                    if (hit.add(target.getUniqueId())) {
                        combat().damage(player, target, 8);
                        Combat.knockback(target, center, 0.6, 0.9);
                    }
                }
                if (((int) (radius * 10)) % 3 == 0) {
                    Fx.sound(center, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.6f);
                }
                radius += 0.85;
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
