package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cyber Mantis Blade, the Cyber Arm-Blade: reach, patience and a sudden strike.
 * Passive Servo Legs, on-hit Serrated, F Blade Extension, Sneak+F Ambush.
 */
public final class MantisWeapon extends SwordAbility implements Listener {

    private static final int ORANGE = 0xFF7A00;
    private static final int HOT = 0xFFD08A;
    private static final int DARK = 0x1C1C1C;
    private static final int SHRED_TICKS = 80;
    private static final int EXTEND_TICKS = 120;
    private static final int AMBUSH_TICKS = 100;
    private static final double AMBUSH_RANGE = 6;

    private final NamespacedKey reachKey;
    /** Shredded entity -> tick it wears off. */
    private final Map<UUID, Integer> shredded = new HashMap<>();
    /** Player -> tick their extended blade retracts. */
    private final Map<UUID, Integer> extended = new HashMap<>();
    /** Players lying in wait. */
    private final Set<UUID> waiting = new HashSet<>();

    public MantisWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.MANTIS);
        this.reachKey = new NamespacedKey(plugin, "blade_extension");
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.JUMP_BOOST, 0, 1));
    }

    /** Servo Legs: short falls don't hurt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && plugin.items().heldType(player) == type && plugin.canUse(player, type) && player.getFallDistance() < 12) {
            event.setCancelled(true);
            Fx.particle(player.getLocation(), Particle.ELECTRIC_SPARK, 6, 0.3, 0.05);
            Fx.sound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.5f, 1.6f);
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        int now = Bukkit.getCurrentTick();
        Location at = Combat.center(target);
        Integer shred = shredded.get(target.getUniqueId());
        if (shred != null && shred >= now) {
            event.setDamage(event.getDamage() * 1.3);
        }
        if (chance(0.25)) {
            shredded.put(target.getUniqueId(), now + SHRED_TICKS);
            Fx.particle(at, Particle.BLOCK, 12, 0.3, 0.1, Material.IRON_BLOCK.createBlockData());
            Fx.particle(at, Particle.ELECTRIC_SPARK, 10, 0.3, 0.15);
            Fx.sound(at, Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
        }
        // with the blade extended, every swing cuts everything in front of you
        Integer ext = extended.get(player.getUniqueId());
        if (ext != null && ext >= now) {
            Location eye = player.getEyeLocation();
            Vector look = eye.getDirection();
            double damage = event.getDamage() * 0.6;
            for (LivingEntity other : combat().nearby(player, eye.clone().add(look.clone().multiply(2.5)), 3)) {
                Vector to = Combat.center(other).toVector().subtract(eye.toVector());
                if (other != target && to.length() < 6 && to.normalize().dot(look) > 0.5) {
                    combat().damage(player, other, damage);
                    fx().cut(Combat.center(other), look, 0.2, 0.2, ORANGE, 1.4f, 2);
                }
            }
            fx().slash(eye.clone().subtract(0, 0.4, 0).add(look.clone().setY(0).multiply(0.5)), look.clone().setY(0), 0.1, ORANGE, 4.2f, 2);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        retract(event.getPlayer());
        waiting.remove(event.getPlayer().getUniqueId());
    }

    // ---- Blade Extension ----

    /**
     * Blade Extension: the blade telescopes out to twice its length for six seconds: three more
     * blocks of reach, and every swing sweeps through everything in front of you.
     */
    @Override
    public boolean primary(Player player) {
        AttributeInstance reach = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            reach.removeModifier(reachKey);
            reach.addTransientModifier(new AttributeModifier(reachKey, 3, AttributeModifier.Operation.ADD_NUMBER));
        }
        extended.put(player.getUniqueId(), Bukkit.getCurrentTick() + EXTEND_TICKS);
        Location hand = dev.infuse.customs.ability.Fx3D.handOrigin(player);
        Fx.sound(hand, Sound.BLOCK_PISTON_EXTEND, 1.2f, 1.4f);
        Fx.sound(hand, Sound.ITEM_CROSSBOW_LOADING_END, 1f, 0.8f);
        Fx.particle(hand, Particle.ELECTRIC_SPARK, 12, 0.2, 0.1);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                Integer until = extended.get(player.getUniqueId());
                if (!alive(player) || until == null || until < Bukkit.getCurrentTick() || plugin.items().heldType(player) != type) {
                    retract(player);
                    cancel();
                    return;
                }
                // the extended edge: a line of hot light out past the blade
                if (ticks % 2 == 0) {
                    Location from = dev.infuse.customs.ability.Fx3D.handOrigin(player);
                    Vector look = player.getEyeLocation().getDirection();
                    Location tip = from.clone().add(look.clone().multiply(3.2));
                    fx().beam(from.clone().add(look.clone().multiply(1.2)), tip, ORANGE, 0.28f, 3);
                    fx().beam(from.clone().add(look.clone().multiply(1.2)), tip, HOT, 0.1f, 2);
                    Fx.particle(tip, Particle.ELECTRIC_SPARK, 1, 0.02, 0.02);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void retract(Player player) {
        if (extended.remove(player.getUniqueId()) == null) {
            return;
        }
        AttributeInstance reach = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            reach.removeModifier(reachKey);
        }
        if (player.isOnline()) {
            Fx.sound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 1f, 1.4f);
        }
    }

    // ---- Ambush ----

    /**
     * Ambush: you crouch and go still for up to five seconds, almost invisible. The first enemy that
     * comes within six blocks is snatched to you by the arm-blade and struck four times. Moving
     * away from your spot gives the ambush away.
     */
    @Override
    public boolean secondary(Player player) {
        UUID id = player.getUniqueId();
        if (!waiting.add(id)) {
            return false;
        }
        Location spot = player.getLocation();
        applyEffect(player, PotionEffectType.INVISIBILITY, AMBUSH_TICKS, 0);
        Fx.sound(spot, Sound.BLOCK_PISTON_CONTRACT, 0.8f, 0.6f);
        Fx.sound(spot, Sound.ENTITY_ENDERMAN_AMBIENT, 0.5f, 1.6f);
        Fx.particle(spot.clone().add(0, 1, 0), Particle.SMOKE, 12, 0.4, 0.02);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > AMBUSH_TICKS || player.getWorld() != spot.getWorld()
                        || player.getLocation().distanceSquared(spot) > 2.25) {
                    end(false);
                    return;
                }
                // two faint orange eyes in the dark
                if (ticks % 10 == 0) {
                    Location eye = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.3));
                    Fx.particle(eye, Particle.DUST, 2, 0.08, 0, Fx.dust(ORANGE, 0.6f));
                }
                for (LivingEntity target : combat().nearby(player, player.getLocation(), AMBUSH_RANGE)) {
                    end(true);
                    snatch(player, target);
                    return;
                }
            }

            private void end(boolean sprung) {
                cancel();
                waiting.remove(id);
                if (!sprung && alive(player)) {
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    Fx.sound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.6f, 0.8f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void snatch(Player player, LivingEntity target) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        Location hand = dev.infuse.customs.ability.Fx3D.handOrigin(player);
        Fx.sound(hand, Sound.BLOCK_PISTON_EXTEND, 1.4f, 1.8f);
        Fx.sound(hand, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 1.4f);
        applyEffect(target, PotionEffectType.SLOWNESS, 40, 3);

        new BukkitRunnable() {
            int ticks;
            int strikes;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !target.isValid() || target.isDead() || ticks > 30) {
                    cancel();
                    return;
                }
                Location front = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.5));
                Vector pull = front.toVector().subtract(target.getLocation().toVector());
                Location at = Combat.center(target);
                if (ticks <= 8) {
                    // the arm-blade lashes out, hooks it and reels it in
                    fx().beam(dev.infuse.customs.ability.Fx3D.handOrigin(player), at, ORANGE, 0.16f, 2);
                    if (pull.length() > 0.8) {
                        Combat.setVelocity(target, pull.multiply(Math.min(1.4, pull.length() * 0.35) / pull.length()).setY(0.15));
                    }
                    return;
                }
                if (ticks % 4 == 0 && strikes < 4) {
                    strikes++;
                    combat().damage(player, target, strikes == 4 ? 6 : 3.5, true);
                    Vector look = at.toVector().subtract(player.getEyeLocation().toVector());
                    fx().cut(at, look, strikes * 0.9, 0.3, strikes == 4 ? HOT : ORANGE, 1.5f, 2);
                    Fx.particle(at, Particle.ELECTRIC_SPARK, 8, 0.3, 0.1);
                    Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f + strikes * 0.15f);
                    if (strikes == 4) {
                        Combat.knockback(target, player.getLocation(), 1.1, 0.4);
                        fx().burst(at, ORANGE, 2.2f);
                        Fx.particle(at, Particle.DUST, 12, 0.4, 0, Fx.dust(DARK, 1.4f));
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void shutdown() {
        for (UUID id : new HashSet<>(extended.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                retract(player);
            }
        }
        extended.clear();
        shredded.clear();
        waiting.clear();
    }
}
