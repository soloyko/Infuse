package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gravecaller, the Soul Sword: necromancy.
 * Passive Soul Siphon, on-hit Soul Rend, RC Spectral Hounds, Sneak+RC Wail of the Damned.
 * <p>
 * Hounds are tamed wolves tagged with {@code customs:hound}; they never outlive their 15 seconds,
 * their owner's session, a chunk unload or a restart.
 */
public final class SoulSword extends SwordAbility implements Listener {

    private static final int HOUND_TICKS = 300;
    private static final int SOUL_BLUE = 0x6FD8F0;

    private final NamespacedKey houndKey;
    private final Map<UUID, List<UUID>> hounds = new HashMap<>();

    public SoulSword(CustomsPlugin plugin) {
        super(plugin, SwordType.SOUL);
        this.houndKey = new NamespacedKey(plugin, "hound");
    }

    private boolean isHound(Entity entity) {
        return entity.getPersistentDataContainer().has(houndKey);
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (isHound(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || plugin.items().heldType(killer) != type || !plugin.canUse(killer, type)) {
            return;
        }
        combat().heal(killer, 4);
        applyEffect(killer, PotionEffectType.ABSORPTION, 200, 0);
        Location from = Combat.center(event.getEntity());
        Fx.line(from, Combat.center(killer), 0.4, p -> Fx.particle(p, Particle.SOUL, 1, 0.05, 0.01));
        Fx.sound(from, Sound.PARTICLE_SOUL_ESCAPE, 1.2f, 1f);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        event.setDamage(event.getDamage() + 4);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.SOUL, 12, 0.3, 0.04);
        Fx.particle(at, Particle.SCULK_SOUL, 6, 0.3, 0.02);
        Fx.sound(at, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
    }

    /** Spectral Hounds: three soul wolves that hunt your target for 15 seconds. */
    @Override
    public boolean primary(Player player) {
        LivingEntity prey = combat().lookTarget(player, 24, 0.8);
        if (prey == null) {
            List<LivingEntity> near = combat().nearby(player, player.getLocation(), 16);
            prey = near.isEmpty() ? null : near.get(0);
        }
        dismiss(player);
        List<UUID> pack = new ArrayList<>();
        Location base = player.getLocation();
        for (int i = 0; i < 3; i++) {
            double a = i * 2 * Math.PI / 3;
            Location spot = Combat.safeSpot(base.clone().add(Math.cos(a) * 1.5, 0, Math.sin(a) * 1.5));
            if (spot == null) {
                spot = base.clone();
            }
            LivingEntity huntTarget = prey;
            Wolf wolf = base.getWorld().spawn(spot, Wolf.class, w -> {
                w.setTamed(true);
                w.setOwner(player);
                w.setCollarColor(DyeColor.LIGHT_BLUE);
                w.customName(Text.gradient("Spectral Hound", type.color(), type.accent()));
                w.setCustomNameVisible(false);
                w.setGlowing(true);
                w.setPersistent(false);
                w.setSitting(false);
                w.getPersistentDataContainer().set(houndKey, PersistentDataType.STRING, player.getUniqueId().toString());
                setBase(w, Attribute.MAX_HEALTH, 30);
                setBase(w, Attribute.ATTACK_DAMAGE, 4);
                setBase(w, Attribute.MOVEMENT_SPEED, 0.42);
                w.setHealth(30);
                if (huntTarget != null) {
                    w.setTarget(huntTarget);
                }
            });
            pack.add(wolf.getUniqueId());
            fx().castCircle(spot, 0x4FE3E0, 1.4f);
            fx().beam(spot, spot.clone().add(0, 2.6, 0), 0x7FFFF5, 0.9f, 8);
            fx().flash(spot.clone().add(0, 0.6, 0), 0x4FE3E0, 1.8f);
            fx().follow(wolf, 0x4FE3E0, 1.4f, 300);
            Fx.particle(spot.clone().add(0, 0.5, 0), Particle.SOUL, 20, 0.3, 0.05);
            Fx.particle(spot.clone().add(0, 0.5, 0), Particle.SCULK_SOUL, 10, 0.3, 0.02);
        }
        hounds.put(player.getUniqueId(), pack);
        Fx.sound(base, Sound.ENTITY_WOLF_GROWL, 1.2f, 0.6f);
        Fx.sound(base, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.7f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks += 5;
                List<UUID> current = hounds.get(player.getUniqueId());
                if (current != pack) {
                    cancel(); // dismissed or replaced
                    return;
                }
                if (ticks >= HOUND_TICKS || !player.isOnline()) {
                    dismiss(player);
                    cancel();
                    return;
                }
                for (UUID id : pack) {
                    if (Bukkit.getEntity(id) instanceof Wolf wolf && wolf.isValid()) {
                        Fx.particle(wolf.getLocation().add(0, 0.6, 0), Particle.SOUL, 1, 0.25, 0.01);
                    }
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
        return true;
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Removes a player's hounds with a puff of souls. */
    private void dismiss(Player player) {
        List<UUID> pack = hounds.remove(player.getUniqueId());
        if (pack == null) {
            return;
        }
        for (UUID id : pack) {
            if (Bukkit.getEntity(id) instanceof Wolf wolf) {
                Fx.particle(wolf.getLocation().add(0, 0.5, 0), Particle.SOUL, 15, 0.3, 0.05);
                wolf.remove();
            }
        }
    }

    /** Hounds respect abilities-hit-players. */
    @EventHandler(ignoreCancelled = true)
    public void onHoundTarget(EntityTargetLivingEntityEvent event) {
        if (isHound(event.getEntity()) && event.getTarget() instanceof Player && !plugin.settings().abilitiesHitPlayers) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dismiss(event.getPlayer());
    }

    @EventHandler
    public void onOwnerDeath(PlayerDeathEvent event) {
        dismiss(event.getPlayer());
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isHound(entity)) {
                entity.remove(); // never saved with the chunk
            }
        }
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isHound(entity)) {
                entity.remove(); // leftovers from a crash
            }
        }
    }

    /** Wail of the Damned: wither, weaken and scatter every enemy around you. */
    @Override
    public boolean secondary(Player player) {
        Location center = player.getLocation();
        List<LivingEntity> targets = combat().nearby(player, center, 10);
        if (targets.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        Fx.sound(center, Sound.ENTITY_GHAST_HURT, 1.5f, 0.5f);
        Fx.sound(center, Sound.ENTITY_VEX_CHARGE, 1.2f, 0.5f);
        Fx.sound(center, Sound.PARTICLE_SOUL_ESCAPE, 2f, 0.6f);
        fx().shockwave(center, 0x4FE3E0, 10f, 12);
        fx().shockwave(center, 0x1E8C8A, 6f, 9);
        fx().flash(center.clone().add(0, 1, 0), 0x7FFFF5, 2.6f);
        // spectral skulls burst out of you screaming, circle once, then hunt down an enemy each
        for (int i = 0; i < 6; i++) {
            double a0 = i * Math.PI * 2 / 6;
            LivingEntity prey = targets.get(i % targets.size());
            Location start = center.clone().add(0, 1.4, 0);
            org.bukkit.entity.ItemDisplay skull = fx().spectralHead(start, org.bukkit.Material.WITHER_SKELETON_SKULL, 0x4FE3E0, 0.9f,
                    new org.bukkit.util.Vector(Math.cos(a0), 0, Math.sin(a0)), 50);
            new BukkitRunnable() {
                int t;
                Location pos = start.clone();

                @Override
                public void run() {
                    t++;
                    if (!skull.isValid() || t > 45 || !alive(player)) {
                        displays().remove(skull);
                        cancel();
                        return;
                    }
                    Location next;
                    if (t <= 8) {
                        double a = a0 + t * 0.25;
                        next = center.clone().add(Math.cos(a) * (0.6 + t * 0.25), 1.4 + t * 0.08, Math.sin(a) * (0.6 + t * 0.25));
                    } else {
                        if (!prey.isValid() || prey.isDead()) {
                            fx().burst(pos, 0x7FFFF5, 1.2f);
                            displays().remove(skull);
                            cancel();
                            return;
                        }
                        Location aim = Combat.center(prey);
                        org.bukkit.util.Vector to = aim.toVector().subtract(pos.toVector());
                        double d = to.length();
                        if (d < 0.9) {
                            combat().damage(player, prey, 3, true);
                            applyEffect(prey, PotionEffectType.WITHER, 60, 0);
                            fx().burst(aim, 0x7FFFF5, 1.6f);
                            Fx.particle(aim, Particle.SOUL, 10, 0.3, 0.04);
                            Fx.sound(aim, Sound.ENTITY_VEX_HURT, 0.8f, 0.6f);
                            displays().remove(skull);
                            cancel();
                            return;
                        }
                        next = pos.clone().add(to.multiply(Math.min(1.1, d) / d));
                    }
                    org.bukkit.util.Vector heading = next.toVector().subtract(pos.toVector());
                    pos = next;
                    skull.teleport(next);
                    if (heading.lengthSquared() > 1.0E-4) {
                        skull.setInterpolationDelay(0);
                        skull.setInterpolationDuration(2);
                        skull.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                                dev.infuse.customs.ability.Fx3D.headRotation(heading), new org.joml.Vector3f(0.9f), new org.joml.Quaternionf()));
                    }
                    if (t % 2 == 0) {
                        Fx.particle(next, Particle.SOUL_FIRE_FLAME, 2, 0.1, 0.01);
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
        new BukkitRunnable() {
            double radius = 1;

            @Override
            public void run() {
                if (radius > 10 || player.getWorld() != center.getWorld()) {
                    cancel();
                    return;
                }
                Location ring = center.clone().add(0, 0.3, 0);
                Fx.ring(ring, radius, (int) (radius * 9), p -> Fx.particle(p, Particle.SCULK_SOUL, 1, 0.05, 0.02));
                Fx.ring(ring.clone().add(0, 0.8, 0), radius, (int) (radius * 4), p -> Fx.particle(p, Particle.SOUL, 1, 0.05, 0.02));
                radius += 1;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        for (LivingEntity target : targets) {
            combat().damage(player, target, 5);
            applyEffect(target, PotionEffectType.WITHER, 60, 0);
            applyEffect(target, PotionEffectType.WEAKNESS, 80, 1);
            if (target instanceof Player) {
                applyEffect(target, PotionEffectType.BLINDNESS, 40, 0);
            }
            if (target instanceof Mob mob && mob.getTarget() == player) {
                mob.setTarget(null);
            }
            Combat.knockback(target, center, 1.2, 0.4);
            Fx.particle(Combat.center(target), Particle.SOUL, 10, 0.3, 0.05);
        }
        Fx.particle(center.clone().add(0, 1, 0), Particle.DUST, 20, 0.6, 0, Fx.dust(SOUL_BLUE, 1.5f));
        return true;
    }

    @Override
    public void shutdown() {
        for (UUID owner : new ArrayList<>(hounds.keySet())) {
            for (UUID id : hounds.getOrDefault(owner, List.of())) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            }
        }
        hounds.clear();
    }
}
