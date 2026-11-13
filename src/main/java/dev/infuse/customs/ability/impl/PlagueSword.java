package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pestilence, the Plague Sword: an infection that stacks up, then bursts.
 * Passive Plaguebearer, on-hit Infection, RC Miasma, Sneak+RC Pandemic.
 * <p>
 * The plague is its own status (1-5 stacks, 6 seconds, refreshed on every new stack) rather than
 * vanilla poison, so it also works on undead mobs, which are immune to poison.
 */
public final class PlagueSword extends SwordAbility {

    private static final int TOXIC = 0x8DC63F;
    private static final int ROT = 0x3E5A1E;
    private static final Color SICK = Color.fromRGB(0x9CD64A);
    private static final int MAX_STACKS = 5;
    private static final int INFECTION_TICKS = 120;
    private static final PotionEffectType[] IMMUNE = {
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.HUNGER, PotionEffectType.NAUSEA
    };

    private record Infection(UUID source, int stacks, int expires) {
    }

    private final Map<UUID, Infection> infected = new HashMap<>();
    private final BukkitTask plagueTask;

    public PlagueSword(CustomsPlugin plugin) {
        super(plugin, SwordType.PLAGUE);
        this.plagueTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPlague, 20L, 20L);
    }

    @Override
    public void tickHeld(Player player, long tick) {
        for (PotionEffectType effect : IMMUNE) {
            if (player.hasPotionEffect(effect)) {
                player.removePotionEffect(effect);
            }
        }
    }

    private void infect(Player source, LivingEntity target, int stacks) {
        if (!combat().canHit(source, target)) {
            return;
        }
        Infection current = infected.get(target.getUniqueId());
        int total = Math.min(MAX_STACKS, (current == null ? 0 : current.stacks()) + stacks);
        infected.put(target.getUniqueId(), new Infection(source.getUniqueId(), total, Bukkit.getCurrentTick() + INFECTION_TICKS));
        // green hearts on anything that can be poisoned; undead simply ignore it
        applyEffect(target, PotionEffectType.POISON, 40, 0);
    }

    private int stacks(LivingEntity entity) {
        Infection infection = infected.get(entity.getUniqueId());
        return infection == null || infection.expires() < Bukkit.getCurrentTick() ? 0 : infection.stacks();
    }

    /** Every second: the plague eats 0.5 health per stack. */
    private void tickPlague() {
        if (infected.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (UUID id : new ArrayList<>(infected.keySet())) {
            Infection infection = infected.get(id);
            Entity entity = Bukkit.getEntity(id);
            if (infection == null) {
                continue;
            }
            if (!(entity instanceof LivingEntity living) || living.isDead() || infection.expires() < now) {
                infected.remove(id);
                continue;
            }
            Location at = Combat.center(living);
            Fx.particle(at, Particle.ENTITY_EFFECT, 2 + infection.stacks() * 2, 0.3, 0, SICK);
            Player source = Bukkit.getPlayer(infection.source());
            if (source != null) {
                combat().damage(source, living, 0.5 * infection.stacks());
            }
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        boolean wasInfected = stacks(target) > 0;
        infect(player, target, 1);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.ENTITY_EFFECT, 8, 0.3, 0, SICK);
        if (wasInfected) {
            event.setDamage(event.getDamage() + 2);
            applyEffect(target, PotionEffectType.WITHER, 60, 0);
            Fx.particle(at, Particle.DUST, 12, 0.35, 0, Fx.dust(ROT, 1.4f));
            Fx.sound(at, Sound.ENTITY_SLIME_SQUISH, 0.7f, 0.6f);
        }
    }

    /** Miasma: a 6 second plague cloud at the target point; one stack per second inside it. */
    @Override
    public boolean primary(Player player) {
        Location eye = player.getEyeLocation();
        Location center = combat().aimPoint(player, 20);
        if (center == null) {
            center = Combat.ground(eye.clone().add(eye.getDirection().multiply(10)));
        }
        Location cloud = center.clone().add(0, 0.2, 0);
        Fx.line(eye.clone().subtract(0, 0.3, 0), cloud, 0.5, p -> Fx.particle(p, Particle.DUST, 1, 0.05, 0, Fx.dust(TOXIC, 1.1f)));
        Fx.sound(eye, Sound.ENTITY_SPLASH_POTION_THROW, 1f, 0.6f);
        Fx.sound(cloud, Sound.ENTITY_SPLASH_POTION_BREAK, 1.2f, 0.5f);
        fx().shockwave(cloud, 0x9CD64A, 4.2f, 9);
        fx().flash(cloud.clone().add(0, 0.8, 0), 0x7FB83A, 3f);
        double radius = 4;
        ThreadLocalRandom rr = ThreadLocalRandom.current();
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5 + rr.nextDouble(-0.4, 0.4);
            double d = i == 0 ? 0 : rr.nextDouble(1.2, 2.6);
            Location p = cloud.clone().add(Math.cos(a) * d, rr.nextDouble(0.5, 1.3), Math.sin(a) * d);
            int delay = i * 3;
            int color = i % 2 == 0 ? 0x6FA830 : 0x9CD64A;
            displays().later(delay + 1, () -> fx().haze(p, color, 3.8f, 118 - delay));
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!alive(player) || player.getWorld() != cloud.getWorld() || ticks++ >= 120) {
                    cancel();
                    return;
                }
                if (ticks % 2 == 0) {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    for (int i = 0; i < 18; i++) {
                        double a = r.nextDouble(Math.PI * 2);
                        double d = Math.sqrt(r.nextDouble()) * radius;
                        Location p = cloud.clone().add(Math.cos(a) * d, r.nextDouble(0, 1.6), Math.sin(a) * d);
                        if (i % 3 == 0) {
                            Fx.particle(p, Particle.ENTITY_EFFECT, 1, 0, 0, SICK);
                        } else {
                            Fx.particle(p, Particle.DUST, 1, 0.1, 0, Fx.dust(i % 2 == 0 ? TOXIC : ROT, 1.8f));
                        }
                    }
                }
                if (ticks % 20 == 0) {
                    Fx.sound(cloud, Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 0.6f);
                    // toxic bubbles swell up out of the cloud and pop
                    ThreadLocalRandom br = ThreadLocalRandom.current();
                    for (int b = 0; b < 2; b++) {
                        double a = br.nextDouble(Math.PI * 2);
                        double d = br.nextDouble() * radius * 0.8;
                        Location bubble = cloud.clone().add(Math.cos(a) * d, 0.2, Math.sin(a) * d);
                        fx().drift(bubble, new org.bukkit.util.Vector(0, br.nextDouble(1.2, 2.2), 0), 0xB4E85A, 0.7f, 12);
                        displays().later(12, () -> {
                            fx().burst(bubble.clone().add(0, 1.6, 0), 0xB4E85A, 1.0f);
                            Fx.particle(bubble.clone().add(0, 1.6, 0), Particle.ITEM_SLIME, 6, 0.2, 0.05);
                            Fx.sound(bubble, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 1f, 0.7f);
                        });
                    }
                    for (LivingEntity target : combat().nearby(player, cloud, radius)) {
                        if (Math.abs(target.getLocation().getY() - cloud.getY()) > 2.5) {
                            continue;
                        }
                        infect(player, target, 1);
                        applyEffect(target, PotionEffectType.WEAKNESS, 60, 0);
                        combat().damage(player, target, 2);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Pandemic: every infected enemy nearby erupts for 3 + 2.5 damage per stack. */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity target : combat().nearby(player, player.getLocation(), 16)) {
            if (stacks(target) > 0) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            tell(player, "No infected enemies nearby.");
            return false;
        }
        Location eye = player.getEyeLocation();
        Fx.sound(eye, Sound.ENTITY_SPLASH_POTION_BREAK, 1.2f, 0.6f);
        for (LivingEntity target : targets) {
            int stacks = stacks(target);
            infected.remove(target.getUniqueId());
            Location at = Combat.center(target);
            Fx.line(eye.clone().subtract(0, 0.3, 0), at, 0.6, p -> Fx.particle(p, Particle.DUST, 1, 0.03, 0, Fx.dust(TOXIC, 0.9f)));
            Fx.particle(at, Particle.ENTITY_EFFECT, 15 + stacks * 8, 0.5, 0, SICK);
            Fx.particle(at, Particle.ITEM_SLIME, 20, 0.4, 0.1);
            Fx.particle(at, Particle.BLOCK, 20, 0.4, 0.1, Material.MOSS_BLOCK.createBlockData());
            Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
            fx().burst(at, 0xB4E85A, 1.6f + stacks * 0.3f);
            fx().tendril(dev.infuse.customs.ability.Fx3D.handOrigin(player), () -> at, 0x9CD64A, 0.9f, 7, null);
            combat().damage(player, target, 3 + 2.5 * stacks, true);
            // the burst infects anyone standing next to it
            for (LivingEntity near : combat().nearby(player, at, 3)) {
                if (!targets.contains(near)) {
                    infect(player, near, 2);
                }
            }
        }
        return true;
    }

    @Override
    public void shutdown() {
        plagueTask.cancel();
        infected.clear();
    }
}
