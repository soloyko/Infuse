package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.Fx3D;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Divine Punisher, the Holy Mace: judgement for the dead, mercy for the living.
 * Passive Dawnward, on-hit Smite, F Sanctuary, Sneak+F Martyr's Vow.
 */
public final class MaceWeapon extends SwordAbility implements Listener {

    private static final int GOLD = 0xFFD966;
    private static final int WHITE = 0xFFF8E1;
    private static final int SANCTUARY_TICKS = 120;
    private static final double SANCTUARY_RADIUS = 4.5;
    private static final int VOW_TICKS = 160;

    /** Players under a Martyr's Vow -> the damage they've endured so far. */
    private final Map<UUID, Double> vows = new HashMap<>();

    public MaceWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.MACE);
    }

    private static boolean undead(Entity entity) {
        return Tag.ENTITY_TYPES_UNDEAD.isTagged(entity.getType());
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Location at = Combat.center(target);
        if (undead(target)) {
            event.setDamage(event.getDamage() * 1.5);
            Fx.particle(at, Particle.END_ROD, 6, 0.3, 0.05);
        }
        if (chance(0.2)) {
            event.setDamage(event.getDamage() + 4);
            fx().beam(target.getLocation().add(0, 8, 0), target.getLocation(), WHITE, 0.7f, 8);
            fx().beam(target.getLocation().add(0, 8, 0), target.getLocation(), GOLD, 1.3f, 6);
            fx().flash(at, GOLD, 2f);
            Fx.sound(at, Sound.BLOCK_BELL_USE, 0.8f, 1.6f);
            Fx.sound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.8f);
        }
    }

    /** Dawnward: the dead strike you more weakly. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUndeadHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || plugin.items().heldType(player) != type || !plugin.canUse(player, type)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (undead(damager)) {
            event.setDamage(event.getDamage() * 0.8);
        }
    }

    private boolean ally(Player caster, Player other) {
        if (other == caster) {
            return true;
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(caster.getName());
        return team != null && team.hasEntry(other.getName());
    }

    // ---- Sanctuary ----

    /**
     * Sanctuary: a dome of light rises over where you stand for six seconds. Enemies are pushed
     * out of it and can't get back in, arrows and other projectiles stop dead at its edge, and you
     * and your teammates inside regenerate.
     */
    @Override
    public boolean primary(Player player) {
        Location center = player.getLocation();
        ItemDisplay rune = fx().sigil(center.clone().add(0, 0.08, 0), new Vector(0, 1, 0), GOLD, (float) SANCTUARY_RADIUS * 2, SANCTUARY_TICKS);
        fx().castCircle(center, GOLD, (float) SANCTUARY_RADIUS);
        fx().flash(center.clone().add(0, 1.5, 0), WHITE, 3f);
        Fx.sound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        Fx.sound(center, Sound.BLOCK_BELL_RESONATE, 1.2f, 1.4f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > SANCTUARY_TICKS || center.getWorld() == null) {
                    displays().remove(rune);
                    Fx.sound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.2f);
                    cancel();
                    return;
                }
                // the dome: rings of light at a few heights, turning slowly
                if (ticks % 3 == 0) {
                    for (int ring = 0; ring < 5; ring++) {
                        double lat = ring * Math.PI / 10;
                        double r = SANCTUARY_RADIUS * Math.cos(lat);
                        double y = SANCTUARY_RADIUS * Math.sin(lat);
                        int points = Math.max(6, (int) (r * 6));
                        double turn = ticks * 0.03 + ring;
                        for (int k = 0; k < points; k++) {
                            if ((k + ticks / 3) % 3 != 0) {
                                continue;
                            }
                            double a = turn + k * Math.PI * 2 / points;
                            Location p = center.clone().add(Math.cos(a) * r, y + 0.1, Math.sin(a) * r);
                            Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(ring % 2 == 0 ? GOLD : WHITE, 1.1f));
                        }
                    }
                    Fx.particle(center.clone().add(0, SANCTUARY_RADIUS, 0), Particle.END_ROD, 1, 0.2, 0.01);
                }
                for (Entity e : center.getWorld().getNearbyEntities(center, SANCTUARY_RADIUS + 1.5, SANCTUARY_RADIUS + 1.5, SANCTUARY_RADIUS + 1.5)) {
                    Vector off = e.getLocation().toVector().subtract(center.toVector());
                    double distance = off.length();
                    if (e instanceof Projectile projectile) {
                        boolean friendly = projectile.getShooter() instanceof Player shooter && ally(player, shooter);
                        if (!friendly && distance < SANCTUARY_RADIUS + 0.8) {
                            Fx.particle(e.getLocation(), Particle.END_ROD, 6, 0.1, 0.05);
                            Fx.sound(e.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.8f);
                            e.remove();
                        }
                        continue;
                    }
                    if (e instanceof Player other && ally(player, other)) {
                        if (distance < SANCTUARY_RADIUS && ticks % 10 == 0) {
                            applyEffect(other, PotionEffectType.REGENERATION, 30, 1);
                        }
                        continue;
                    }
                    if (!(e instanceof LivingEntity target) || !combat().canHit(player, target) || distance > SANCTUARY_RADIUS + 0.3) {
                        continue;
                    }
                    Vector out = off.setY(0);
                    if (out.lengthSquared() < 1.0E-4) {
                        out = new Vector(1, 0, 0);
                    }
                    Combat.setVelocity(target, out.normalize().multiply(0.8).setY(0.25));
                    if (ticks % 10 == 0) {
                        combat().damage(player, target, 1.5, true);
                        Fx.particle(Combat.center(target), Particle.END_ROD, 5, 0.3, 0.03);
                        if (undead(target)) {
                            target.setFireTicks(Math.max(target.getFireTicks(), 40));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ---- Martyr's Vow ----

    /**
     * Martyr's Vow: a halo settles over your head for eight seconds. Nothing can take you below half
     * a heart while it lasts, and when it lifts you heal half of all the damage you endured.
     */
    @Override
    public boolean secondary(Player player) {
        UUID id = player.getUniqueId();
        if (vows.containsKey(id)) {
            return false;
        }
        vows.put(id, 0.0);
        Location head = player.getEyeLocation().add(0, 0.55, 0);
        ItemDisplay halo = fx().spawn(head, Fx3D.Shape.RING, GOLD,
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f), new Quaternionf()), Display.Billboard.FIXED,
                VOW_TICKS + 10);
        halo.setTeleportDuration(1);
        fx().beam(player.getLocation().add(0, 10, 0), player.getLocation(), WHITE, 1.2f, 12);
        fx().flash(player.getLocation().add(0, 1, 0), GOLD, 2.4f);
        Fx.sound(head, Sound.BLOCK_BELL_USE, 1.2f, 0.8f);
        Fx.sound(head, Sound.ITEM_TOTEM_USE, 0.4f, 1.6f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || player.isDead() || ticks > VOW_TICKS) {
                    end();
                    return;
                }
                Location above = player.getEyeLocation().add(0, 0.55, 0);
                above.setYaw(0);
                above.setPitch(0);
                halo.teleport(above);
                if (ticks % 4 == 0) {
                    Fx.particle(player.getLocation().add(0, 1, 0), Particle.DUST, 2, 0.35, 0.02, Fx.dust(GOLD, 0.9f));
                }
            }

            private void end() {
                cancel();
                displays().remove(halo);
                Double endured = vows.remove(id);
                if (endured == null || !alive(player)) {
                    return;
                }
                combat().heal(player, endured * 0.5);
                Location at = player.getLocation().add(0, 1, 0);
                fx().flash(at, WHITE, 2.6f);
                fx().beam(player.getLocation().add(0, 8, 0), player.getLocation(), GOLD, 0.9f, 10);
                Fx.particle(at, Particle.TOTEM_OF_UNDYING, 30, 0.5, 0.3);
                Fx.sound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Under the vow, nothing takes you below half a heart; everything endured is remembered. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVowedHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Double endured = vows.get(player.getUniqueId());
        if (endured == null || event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        double damage = event.getFinalDamage();
        vows.put(player.getUniqueId(), endured + damage);
        if (player.getHealth() - damage < 1) {
            event.setCancelled(true);
            player.setHealth(Math.max(1, Math.min(player.getHealth(), 1)));
            player.playHurtAnimation(0);
            Location at = player.getLocation().add(0, 1, 0);
            Fx.particle(at, Particle.END_ROD, 8, 0.4, 0.05);
            Fx.sound(at, Sound.BLOCK_ANVIL_LAND, 0.4f, 2f);
        }
    }

    @Override
    public void shutdown() {
        vows.clear();
    }
}
