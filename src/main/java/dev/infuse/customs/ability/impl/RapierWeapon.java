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
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Valiance, the Royal Rapier: precision and the perfect counter.
 * Passive Finesse, on-hit Precision, F Flèche, Sneak+F Riposte.
 */
public final class RapierWeapon extends SwordAbility implements Listener {

    private static final int IVORY = 0xF5E6B8;
    private static final int GOLD = 0xD4A017;
    private static final int WHITE = 0xFFFDF0;

    /** The shield sigil shown during each player's stance. */
    private final Map<UUID, org.bukkit.entity.ItemDisplay> wards = new HashMap<>();

    private static Location wardSpot(Player player) {
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        Location at = player.getLocation().add(0, 1.1, 0).add(look.normalize().multiply(1.1));
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    /** Players in a parry stance, until this tick. */
    private final Map<UUID, Integer> stance = new HashMap<>();

    public RapierWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.RAPIER);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ATTACK_SPEED, "finesse", 0.25, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        addModifier(meta, Attribute.MOVEMENT_SPEED, "footwork", 0.10, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && target.getHealth() >= max.getValue() - 0.01) {
            event.setDamage(event.getDamage() * 1.5);
            Location at = Combat.center(target);
            fx().burst(at, GOLD, 1.6f);
            Fx.particle(at, Particle.CRIT, 12, 0.3, 0.2);
            Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.4f);
            Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.8f);
        }
    }

    private void thrust(Player player, LivingEntity target, double damage) {
        Location from = dev.infuse.customs.ability.Fx3D.handOrigin(player);
        Location at = Combat.center(target);
        fx().beam(from, at, IVORY, 0.35f, 4);
        fx().burst(at, WHITE, 1.2f);
        combat().damage(player, target, damage, true);
        Fx.particle(at, Particle.CRIT, 6, 0.2, 0.2);
        Fx.sound(at, Sound.ITEM_TRIDENT_HIT, 0.8f, 1.6f);
    }

    /** Flèche: lunge forward and run the first enemy in your path through. */
    @Override
    public boolean primary(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double reach = Combat.clearDistance(player, 8);
        LivingEntity target = combat().lookTarget(player, reach, 0.8);
        Vector flat = dir.clone().setY(0);
        if (flat.lengthSquared() < 1.0E-4) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        Combat.setVelocity(player, flat.clone().multiply(target == null ? 1.8 : Math.min(2.4, eye.distance(target.getLocation()) * 0.35))
                .setY(0.15));
        status().protectFromFall(player, 30);
        fx().shockwave(player.getLocation(), GOLD, 1.8f, 5);
        Fx.sound(eye, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.6f);
        Fx.sound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.9f);
        // golden afterimages of the rapier trail the lunge
        org.bukkit.inventory.ItemStack blade = plugin.items().create(type);
        for (int i = 0; i < 4; i++) {
            displays().later(1 + i, () -> {
                if (alive(player)) {
                    Location at = player.getEyeLocation().subtract(0, 0.5, 0).subtract(dir.clone().multiply(0.6));
                    fx().afterimage(at, blade, dir, GOLD, 1.5f, 4);
                }
            });
        }
        if (target != null) {
            displays().later(5, () -> {
                if (target.isValid() && alive(player)) {
                    thrust(player, target, 9);
                    Combat.knockback(target, player.getLocation(), 0.6, 0.2);
                    fx().impact(Combat.center(target), GOLD, 2f);
                }
            });
        }
        return true;
    }

    /** Riposte: 1.5 s parry stance; the next attack is blocked and answered with five thrusts. */
    @Override
    public boolean secondary(Player player) {
        int until = org.bukkit.Bukkit.getCurrentTick() + 30;
        stance.put(player.getUniqueId(), until);
        applyEffect(player, PotionEffectType.SLOWNESS, 30, 1);
        Fx.sound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.6f);
        Fx.sound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        fx().shockwave(player.getLocation(), GOLD, 2f, 6);
        // a golden shield sigil hovers in front of you for the whole stance
        org.bukkit.entity.ItemDisplay ward = fx().sigil(wardSpot(player), player.getLocation().getDirection().setY(0), GOLD, 1.5f, 40);
        wards.put(player.getUniqueId(), ward);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                Integer end = stance.get(player.getUniqueId());
                if (ward.isValid() && end != null && end == until) {
                    ward.teleport(wardSpot(player));
                }
                if (!alive(player) || end == null || end != until || ticks > 30) {
                    if (wards.remove(player.getUniqueId(), ward)) {
                        fx().fade(ward, dev.infuse.customs.ability.Fx3D.Shape.RUNE, GOLD, 1, 1);
                    }
                    if (end != null && end == until) {
                        stance.remove(player.getUniqueId());
                    }
                    cancel();
                    return;
                }
                Location c = player.getLocation().add(0, 1, 0);
                double a = ticks * 0.6;
                Fx.particle(c.clone().add(Math.cos(a) * 1.1, Math.sin(ticks * 0.4) * 0.4, Math.sin(a) * 1.1),
                        Particle.DUST, 2, 0, 0, Fx.dust(GOLD, 1.3f));
                Fx.particle(c.clone().add(Math.cos(a + Math.PI) * 1.1, -Math.sin(ticks * 0.4) * 0.4, Math.sin(a + Math.PI) * 1.1),
                        Particle.END_ROD, 1, 0, 0);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || combat().isAbilityDamage()) {
            return;
        }
        Integer until = stance.get(player.getUniqueId());
        if (until == null || until < org.bukkit.Bukkit.getCurrentTick()) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (!(damager instanceof LivingEntity attacker) || attacker == player) {
            return;
        }
        event.setCancelled(true);
        stance.remove(player.getUniqueId());
        org.bukkit.entity.ItemDisplay ward = wards.remove(player.getUniqueId());
        if (ward != null && ward.isValid()) {
            fx().burst(ward.getLocation(), GOLD, 2.6f);
            Fx.particle(ward.getLocation(), Particle.BLOCK, 20, 0.4, 0.1, org.bukkit.Material.GOLD_BLOCK.createBlockData());
            displays().remove(ward);
        }
        Location c = Combat.center(player);
        fx().burst(c, WHITE, 2.4f);
        fx().shockwave(player.getLocation(), GOLD, 3f, 6);
        Fx.sound(c, Sound.ITEM_SHIELD_BLOCK, 1.2f, 1.2f);
        Fx.sound(c, Sound.BLOCK_ANVIL_LAND, 0.5f, 2f);
        if (!combat().canHit(player, attacker)) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            displays().later(2 + i * 3L, () -> {
                if (attacker.isValid() && !attacker.isDead() && alive(player)) {
                    thrust(player, attacker, 4);
                }
            });
        }
    }

    @Override
    public void shutdown() {
        stance.clear();
    }
}
