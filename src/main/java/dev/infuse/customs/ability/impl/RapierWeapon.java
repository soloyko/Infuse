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
 * Royal Rapier, the Royal Rapier: precision and the perfect counter.
 * Passive Finesse, on-hit Precision, F Three Touches, Sneak+F Riposte.
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
        touch(player, target, event);
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

    private static final int TOUCH_TICKS = 100;

    /** A target's open weak points: who marked it, how many are left, the glints, and until when. */
    private static final class Touches {
        final UUID marker;
        final java.util.List<org.bukkit.entity.ItemDisplay> glints = new java.util.ArrayList<>();
        final double[] heights;
        final double[] angles;
        int left = 3;
        final int until;

        Touches(UUID marker, int until, double[] heights, double[] angles) {
            this.marker = marker;
            this.until = until;
            this.heights = heights;
            this.angles = angles;
        }
    }

    private final Map<UUID, Touches> touches = new HashMap<>();

    /**
     * Three Touches: you read the enemy you aim at and three weak points light up on it for five
     * seconds. Each of your hits on it strikes one for +4; land all three for a finishing
     * flourish that staggers it.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 16, 0.7);
        if (target == null) {
            tell(player, "Look at an enemy to read its guard.");
            return false;
        }
        Touches old = touches.remove(target.getUniqueId());
        if (old != null) {
            old.glints.forEach(displays()::remove);
        }
        double h = target.getHeight();
        Touches t = new Touches(player.getUniqueId(), org.bukkit.Bukkit.getCurrentTick() + TOUCH_TICKS,
                new double[]{h * 0.85, h * 0.55, h * 0.3}, new double[]{0.4, -0.5, 0.1});
        for (int i = 0; i < 3; i++) {
            org.bukkit.entity.ItemDisplay glint = fx().orb(target.getLocation().add(0, t.heights[i], 0), GOLD, 0.45f, TOUCH_TICKS + 10);
            glint.setTeleportDuration(1);
            t.glints.add(glint);
        }
        touches.put(target.getUniqueId(), t);
        Location eye = player.getEyeLocation();
        // the salute: the blade raised before the face, then the points appear one by one
        fx().afterimage(eye.clone().subtract(0, 0.3, 0).add(eye.getDirection().multiply(0.8)), plugin.items().create(type),
                new Vector(0, 1, 0), GOLD, 1.2f, 8);
        Fx.sound(eye, Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, 1.6f);
        Fx.sound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
        fx().beam(dev.infuse.customs.ability.Fx3D.handOrigin(player), Combat.center(target), IVORY, 0.12f, 6);

        new BukkitRunnable() {
            int tick;

            @Override
            public void run() {
                tick++;
                if (touches.get(target.getUniqueId()) != t || !target.isValid() || target.isDead()
                        || t.until < org.bukkit.Bukkit.getCurrentTick()) {
                    if (touches.get(target.getUniqueId()) == t) {
                        touches.remove(target.getUniqueId());
                    }
                    t.glints.forEach(displays()::remove);
                    cancel();
                    return;
                }
                // the points sit on the target's body, facing the duelist
                Player duelist = org.bukkit.Bukkit.getPlayer(t.marker);
                Vector toward = duelist == null ? new Vector(0, 0, 1)
                        : duelist.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                if (toward.lengthSquared() < 1.0E-4) {
                    toward = new Vector(0, 0, 1);
                }
                toward.normalize();
                Vector side = new Vector(-toward.getZ(), 0, toward.getX());
                for (int i = 0; i < t.glints.size(); i++) {
                    org.bukkit.entity.ItemDisplay g = t.glints.get(i);
                    if (!g.isValid()) {
                        continue;
                    }
                    int idx = 3 - t.glints.size() + i;
                    double sway = Math.sin(tick * 0.2 + idx) * 0.04;
                    Location spot = target.getLocation().add(toward.clone().multiply(target.getWidth() * 0.6 + 0.05))
                            .add(side.clone().multiply(t.angles[idx] * target.getWidth() + sway)).add(0, t.heights[idx], 0);
                    spot.setYaw(0);
                    spot.setPitch(0);
                    g.teleport(spot);
                }
                if (tick % 10 == 0) {
                    Fx.particle(target.getLocation().add(0, target.getHeight() * 0.6, 0), Particle.WAX_OFF, 2, 0.3, 0.02);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** A hit on a marked target strikes the next weak point. */
    private void touch(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Touches t = touches.get(target.getUniqueId());
        if (t == null || !t.marker.equals(player.getUniqueId()) || t.until < org.bukkit.Bukkit.getCurrentTick() || t.glints.isEmpty()) {
            return;
        }
        org.bukkit.entity.ItemDisplay glint = t.glints.remove(0);
        Location at = glint.isValid() ? glint.getLocation() : Combat.center(target);
        displays().remove(glint);
        t.left--;
        event.setDamage(event.getDamage() + 4);
        fx().burst(at, WHITE, 1.4f);
        fx().cut(at, at.toVector().subtract(player.getEyeLocation().toVector()), Math.random() * Math.PI, 0.3, GOLD, 1.0f, 2);
        Fx.particle(at, Particle.CRIT, 10, 0.15, 0.2);
        Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f + (3 - t.left) * 0.25f);
        if (t.left > 0) {
            return;
        }
        // all three: the finishing flourish
        touches.remove(target.getUniqueId());
        Location center = Combat.center(target);
        event.setDamage(event.getDamage() + 6);
        hold(target, 20);
        applyEffect(target, PotionEffectType.WEAKNESS, 80, 0);
        fx().impact(center, GOLD, 2.4f);
        for (int i = 0; i < 3; i++) {
            double roll = i * Math.PI / 3;
            displays().later(i * 2L, () -> fx().cut(Combat.center(target), center.toVector().subtract(player.getEyeLocation().toVector()),
                    roll, 0.4, IVORY, 1.6f, 2));
        }
        Fx.sound(center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.8f);
        Fx.sound(center, Sound.BLOCK_BELL_USE, 1f, 1.6f);
        tell(player, net.kyori.adventure.text.Component.text("Touché!", net.kyori.adventure.text.format.TextColor.color(GOLD)));
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
        for (Touches t : touches.values()) {
            t.glints.forEach(displays()::remove);
        }
        touches.clear();
    }
}
