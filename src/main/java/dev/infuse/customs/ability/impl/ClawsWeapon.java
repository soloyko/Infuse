package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soul Claws, the Spectral Claws: a predator's speed and a hunter's focus.
 * Passive Predator, on-hit Frenzy, F Pounce, Sneak+F The Hunt.
 */
public final class ClawsWeapon extends SwordAbility implements Listener {

    private static final int SPECTRAL = 0x2EE6D6;
    private static final int PALE = 0xB8FFF7;
    private static final int DEEP = 0x0B3B4A;
    private static final int FRENZY_TICKS = 60;
    private static final int MAX_FRENZY = 5;
    private static final int HUNT_TICKS = 240;

    private record Frenzy(int stacks, int last) {
    }

    private final Map<UUID, Frenzy> frenzy = new HashMap<>();
    /** Hunter -> prey. */
    private final Map<UUID, LivingEntity> hunts = new HashMap<>();

    public ClawsWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.CLAWS);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.SPEED, 0, 0));
    }

    /** Predator: wounded enemies near you glow. */
    @Override
    public void tickHeld(Player player, long tick) {
        for (LivingEntity near : combat().nearby(player, player.getLocation(), 12)) {
            AttributeInstance max = near.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && near.getHealth() < max.getValue() * 0.5) {
                near.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 16, 0, false, false, false));
            }
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        int now = Bukkit.getCurrentTick();
        Frenzy old = frenzy.get(player.getUniqueId());
        int stacks = old == null || now - old.last > FRENZY_TICKS ? 1 : Math.min(MAX_FRENZY, old.stacks + 1);
        frenzy.put(player.getUniqueId(), new Frenzy(stacks, now));
        event.setDamage(event.getDamage() + stacks);
        applyEffect(player, PotionEffectType.HASTE, FRENZY_TICKS, Math.min(2, stacks - 1));
        if (target == hunts.get(player.getUniqueId())) {
            event.setDamage(event.getDamage() + 5);
        }
        rake(player, target, stacks >= MAX_FRENZY ? PALE : SPECTRAL);
        if (stacks == MAX_FRENZY) {
            Fx.particle(player.getLocation().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, 4, 0.3, 0.02);
        }
    }

    /** Three parallel claw marks across the target. */
    private void rake(Player player, LivingEntity target, int rgb) {
        Location at = Combat.center(target);
        Vector look = player.getEyeLocation().getDirection();
        Vector up = new Vector(0, 1, 0);
        for (int i = -1; i <= 1; i++) {
            fx().cut(at.clone().add(up.clone().multiply(i * 0.28)), look, 0.35, 0.3, rgb, 1.2f, 2);
        }
        Fx.particle(at, Particle.DUST, 6, 0.3, 0, Fx.dust(rgb, 1.0f));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        frenzy.remove(event.getPlayer().getUniqueId());
        hunts.remove(event.getPlayer().getUniqueId());
    }

    // ---- Pounce ----

    /**
     * Pounce: you spring at the enemy under your crosshair in a long arc. When you land on it, it is
     * pinned to the ground while you rake it three times.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity prey = combat().lookTarget(player, 18, 0.6);
        if (prey == null) {
            tell(player, "Look at an enemy to pounce on.");
            return false;
        }
        status().protectFromFall(player, 80);
        Vector toward = prey.getLocation().toVector().subtract(player.getLocation().toVector());
        Combat.setVelocity(player, toward.clone().setY(0).normalize().multiply(0.9).setY(0.75));
        Location from = player.getLocation().add(0, 1, 0);
        Fx.sound(from, Sound.ENTITY_WOLF_GROWL, 1f, 1.2f);
        Fx.sound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        fx().afterimage(from, plugin.items().create(type), toward, SPECTRAL, 1.0f, 8);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !prey.isValid() || prey.isDead() || prey.getWorld() != player.getWorld() || ticks > 24) {
                    cancel();
                    return;
                }
                Vector gap = Combat.center(prey).toVector().subtract(player.getLocation().add(0, 1, 0).toVector());
                if (gap.length() < 1.8) {
                    cancel();
                    land(player, prey);
                    return;
                }
                // steer the arc onto the target, keeping the leap's lift for the first few ticks
                Vector v = gap.clone().normalize().multiply(Math.min(1.3, 0.6 + gap.length() * 0.1));
                v.setY(ticks < 5 ? Math.max(v.getY(), 0.45 - ticks * 0.08) : v.getY());
                Combat.setVelocity(player, v);
                Fx.particle(player.getLocation().add(0, 1, 0), Particle.DUST, 3, 0.2, 0, Fx.dust(SPECTRAL, 1.2f));
                if (ticks % 2 == 0) {
                    fx().afterimage(player.getLocation().add(0, 1, 0), plugin.items().create(type), gap, SPECTRAL, 0.9f, 6);
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
        return true;
    }

    private void land(Player player, LivingEntity prey) {
        Combat.setVelocity(player, new Vector(0, -0.3, 0));
        hold(prey, 20);
        combat().damage(player, prey, 4, true);
        Location at = Combat.center(prey);
        fx().burst(at, SPECTRAL, 2.2f);
        fx().crack(Combat.ground(prey.getLocation().add(0, 0.5, 0)), DEEP, 2f, 20);
        Fx.sound(at, Sound.ENTITY_WOLF_GROWL, 1.2f, 0.8f);
        Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
        for (int i = 1; i <= 3; i++) {
            int n = i;
            displays().later(i * 5L, () -> {
                if (!alive(player) || !prey.isValid() || prey.isDead()) {
                    return;
                }
                combat().damage(player, prey, 3, true);
                rake(player, prey, n == 3 ? PALE : SPECTRAL);
                Fx.sound(prey.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.4f + n * 0.1f);
            });
        }
    }

    // ---- The Hunt ----

    /**
     * The Hunt: the enemy under your crosshair becomes your prey for twelve seconds. It glows, a
     * trail of spectral pawprints leads you to it, you run faster while you close in, and your hits
     * on it deal +5 damage. Kill it before the hunt ends and you heal fully.
     */
    @Override
    public boolean secondary(Player player) {
        LivingEntity prey = combat().lookTarget(player, 32, 0.8);
        if (prey == null) {
            tell(player, "Look at an enemy to hunt.");
            return false;
        }
        hunts.put(player.getUniqueId(), prey);
        prey.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, HUNT_TICKS, 0, false, false, false));
        combat().damage(player, prey, 2, true);
        Location at = Combat.center(prey);
        fx().sigil(prey.getLocation().add(0, 0.1, 0), new Vector(0, 1, 0), SPECTRAL, 1.8f, 30);
        fx().beam(player.getEyeLocation(), at, SPECTRAL, 0.15f, 8);
        Fx.sound(player.getLocation(), Sound.ENTITY_WOLF_ANGRY_AMBIENT, 1f, 1.1f);
        Fx.sound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.2f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || hunts.get(player.getUniqueId()) != prey || !prey.isValid() || prey.isDead()
                        || prey.getWorld() != player.getWorld() || ticks > HUNT_TICKS) {
                    if (hunts.get(player.getUniqueId()) == prey) {
                        hunts.remove(player.getUniqueId());
                    }
                    cancel();
                    return;
                }
                Vector toPrey = prey.getLocation().toVector().subtract(player.getLocation().toVector());
                double distance = toPrey.length();
                // the trail: pawprints on the ground from you towards it
                if (ticks % 6 == 0 && distance > 2) {
                    Vector step = toPrey.clone().setY(0).normalize();
                    Vector side = new Vector(-step.getZ(), 0, step.getX()).multiply(0.25);
                    for (int i = 1; i <= Math.min(8, (int) distance - 1); i++) {
                        Location paw = Combat.ground(player.getLocation().add(step.clone().multiply(i * 1.1))
                                .add(side.clone().multiply(i % 2 == 0 ? 1 : -1)).add(0, 0.6, 0)).add(0, 0.08, 0);
                        Fx.particle(paw, Particle.DUST, 2, 0.06, 0, Fx.dust(i < 3 ? PALE : SPECTRAL, 1.0f));
                    }
                }
                // closing in on it: faster
                Vector moving = player.getVelocity().setY(0);
                if (moving.lengthSquared() > 0.005 && moving.normalize().dot(toPrey.clone().setY(0).normalize()) > 0.7) {
                    applyEffect(player, PotionEffectType.SPEED, 10, 1);
                }
                if (ticks % 20 == 0) {
                    Fx.particle(prey.getEyeLocation().add(0, 0.6, 0), Particle.SOUL_FIRE_FLAME, 4, 0.15, 0.01);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Bringing the prey down ends the hunt in a feast. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreyDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || hunts.get(killer.getUniqueId()) != event.getEntity()) {
            return;
        }
        hunts.remove(killer.getUniqueId());
        AttributeInstance max = killer.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            killer.setHealth(max.getValue());
        }
        Location at = killer.getLocation().add(0, 1, 0);
        fx().flash(at, SPECTRAL, 2.6f);
        Fx.particle(at, Particle.HEART, 6, 0.5, 0);
        Fx.sound(at, Sound.ENTITY_WOLF_ANGRY_AMBIENT, 1.2f, 0.9f);
        Fx.sound(at, Sound.ENTITY_PLAYER_BURP, 0.8f, 0.8f);
    }

    @Override
    public void shutdown() {
        frenzy.clear();
        hunts.clear();
    }
}
