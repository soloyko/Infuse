package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Death Knight's Sword, the Runed Greatsword: an oath that outlived its king, and the dead who keep it.
 * Passive Frost Presence, on-hit Death Coil, F Raise Dead, Sneak+F Deathcharger.
 */
public final class DeathKnightWeapon extends SwordAbility implements Listener {

    private static final int FROST = 0x5DA9E9;
    private static final int PALE = 0xCFE8FF;
    private static final int DARK = 0x0D1B2A;
    private static final int MINION_TICKS = 400;
    private static final int MAX_RAISED = 3;
    private static final int CORPSE_TICKS = 600;
    private static final int HORSE_TICKS = 240;

    private record Corpse(Location at, int tick) {
    }

    /** Recent deaths, newest first: the bodies Raise Dead can use. */
    private final Deque<Corpse> corpses = new ArrayDeque<>();
    /** Raised skeleton -> the player it serves. */
    private final Map<UUID, Player> minions = new HashMap<>();
    /** Rider -> their deathcharger. */
    private final Map<UUID, SkeletonHorse> horses = new HashMap<>();

    public DeathKnightWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.DEATHKNIGHT);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Location at = Combat.center(target);
        combat().damage(player, target, 2, true);
        combat().heal(player, 2);
        fx().drift(at, player.getLocation().add(0, 1, 0).toVector().subtract(at.toVector()), FROST, 0.6f, 8);
        Fx.particle(at, Particle.SOUL, 6, 0.3, 0.03);
        Fx.sound(at, Sound.PARTICLE_SOUL_ESCAPE, 1.2f, 0.8f);
    }

    /** Frost Presence: whoever strikes you is chilled. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStruck(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player) || plugin.items().heldType(player) != type || !plugin.canUse(player, type)) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof LivingEntity attacker && attacker != player && !minions.containsKey(attacker.getUniqueId())) {
            applyEffect(attacker, PotionEffectType.SLOWNESS, 40, 1);
            Fx.particle(Combat.center(attacker), Particle.SNOWFLAKE, 8, 0.3, 0.02);
        }
    }

    /** Remembers where things die, for Raise Dead; raised skeletons leave nothing behind. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (minions.remove(dead.getUniqueId()) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            Fx.particle(dead.getLocation().add(0, 1, 0), Particle.SOUL, 10, 0.3, 0.05);
            return;
        }
        if (dead instanceof org.bukkit.entity.ArmorStand || dead instanceof org.bukkit.entity.Mannequin) {
            return;
        }
        corpses.addFirst(new Corpse(dead.getLocation(), Bukkit.getCurrentTick()));
        while (corpses.size() > 40) {
            corpses.removeLast();
        }
    }

    /** Raised skeletons never turn on their master or on each other. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Player master = minions.get(event.getEntity().getUniqueId());
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }
        if (master != null && (target == master || minions.containsKey(target.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    // ---- Raise Dead ----

    /**
     * Raise Dead: up to three of the creatures slain near you in the last thirty seconds claw
     * their way back up as skeleton warriors (if nothing died nearby, one rises from the ground in
     * front of you). They hunt your enemies for twenty seconds, then crumble.
     */
    @Override
    public boolean primary(Player player) {
        int now = Bukkit.getCurrentTick();
        List<Location> graves = new ArrayList<>();
        for (Corpse c : corpses) {
            if (now - c.tick > CORPSE_TICKS) {
                break;
            }
            if (c.at.getWorld() == player.getWorld() && c.at.distanceSquared(player.getLocation()) < 14 * 14) {
                graves.add(c.at);
            }
            if (graves.size() >= MAX_RAISED) {
                break;
            }
        }
        corpses.removeIf(c -> graves.contains(c.at));
        if (graves.isEmpty()) {
            graves.add(player.getLocation().add(dev.infuse.customs.ability.Aim.flatLook(player).multiply(2.5)));
        }
        Fx.sound(player.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.2f, 0.6f);
        Fx.sound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
        fx().sigil(player.getLocation().add(0, 0.08, 0), new Vector(0, 1, 0), FROST, 3f, 30);
        List<Skeleton> raised = new ArrayList<>();
        for (Location grave : graves) {
            Location at = Combat.safeSpot(grave);
            if (at == null) {
                at = grave;
            }
            Location spot = at;
            fx().crack(spot, FROST, 2f, 30);
            Fx.particle(spot.clone().add(0, 0.2, 0), Particle.BLOCK, 20, 0.4, 0.1, spot.clone().subtract(0, 0.5, 0).getBlock().getBlockData());
            Fx.particle(spot.clone().add(0, 0.5, 0), Particle.SOUL, 14, 0.3, 0.1);
            Skeleton skeleton = spot.getWorld().spawn(spot, Skeleton.class, s -> {
                s.setPersistent(false);
                s.setShouldBurnInDay(false);
                s.getPersistentDataContainer().set(Combat.OWNER_KEY, org.bukkit.persistence.PersistentDataType.STRING,
                        player.getUniqueId().toString());
                s.setGlowing(true);
                s.setCanPickupItems(false);
                s.getEquipment().setItem(EquipmentSlot.HAND, ItemStack.of(Material.STONE_SWORD));
                s.getEquipment().setItem(EquipmentSlot.HEAD, ItemStack.of(Material.CHAINMAIL_HELMET));
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.HEAD}) {
                    s.getEquipment().setDropChance(slot, 0f);
                }
                AttributeInstance speed = s.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.setBaseValue(0.3);
                }
            });
            minions.put(skeleton.getUniqueId(), player);
            raised.add(skeleton);
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                raised.removeIf(s -> !s.isValid() || s.isDead());
                if (raised.isEmpty() || ticks > MINION_TICKS || !player.isOnline()) {
                    for (Skeleton s : raised) {
                        minions.remove(s.getUniqueId());
                        Fx.particle(s.getLocation().add(0, 1, 0), Particle.BLOCK, 16, 0.3, 0.1, Material.BONE_BLOCK.createBlockData());
                        Fx.sound(s.getLocation(), Sound.ENTITY_SKELETON_DEATH, 0.8f, 1.2f);
                        s.remove();
                    }
                    cancel();
                    return;
                }
                if (ticks % 10 != 0) {
                    return;
                }
                for (Skeleton s : raised) {
                    LivingEntity current = s.getTarget();
                    if (current == null || current.isDead() || current == player || !combat().canHit(player, current)) {
                        LivingEntity foe = null;
                        for (LivingEntity near : combat().nearby(player, s.getLocation(), 16)) {
                            if (!minions.containsKey(near.getUniqueId())) {
                                foe = near;
                                break;
                            }
                        }
                        s.setTarget(foe);
                        if (foe == null && s.getLocation().distanceSquared(player.getLocation()) > 25) {
                            s.getPathfinder().moveTo(player.getLocation(), 1.2);
                        }
                    }
                    Fx.particle(s.getEyeLocation(), Particle.DUST, 2, 0.15, 0, Fx.dust(FROST, 0.8f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ---- Deathcharger ----

    /**
     * Deathcharger: a skeletal horse wreathed in cold soulfire bursts out of the ground beneath
     * you and you ride it for twelve seconds. At a gallop it tramples every enemy in its path.
     * Get off and it crumbles back into the earth.
     */
    @Override
    public boolean secondary(Player player) {
        if (horses.containsKey(player.getUniqueId()) || player.isInsideVehicle()) {
            tell(player, "You are already riding.");
            return false;
        }
        Location at = player.getLocation();
        SkeletonHorse horse = at.getWorld().spawn(at, SkeletonHorse.class, h -> {
            h.setPersistent(false);
            h.setTamed(true);
            h.setOwner(player);
            h.setAdult();
            h.setInvulnerable(true);
            h.setGlowing(true);
            h.getInventory().setSaddle(ItemStack.of(Material.SADDLE));
            AttributeInstance speed = h.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.38);
            }
            AttributeInstance jump = h.getAttribute(Attribute.JUMP_STRENGTH);
            if (jump != null) {
                jump.setBaseValue(0.8);
            }
        });
        horse.addPassenger(player);
        horses.put(player.getUniqueId(), horse);
        fx().flash(at.clone().add(0, 1, 0), FROST, 2.6f);
        Fx.particle(at.clone().add(0, 0.5, 0), Particle.SOUL_FIRE_FLAME, 30, 0.6, 0.05);
        Fx.sound(at, Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1.5f, 0.6f);
        Fx.sound(at, Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
        Map<UUID, Integer> trampled = new HashMap<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!horse.isValid() || ticks > HORSE_TICKS || !player.isOnline() || player.getVehicle() != horse) {
                    vanish(player, horse);
                    cancel();
                    return;
                }
                Location hooves = horse.getLocation();
                if (ticks % 2 == 0) {
                    Fx.particle(hooves.clone().add(0, 0.2, 0), Particle.SOUL_FIRE_FLAME, 2, 0.4, 0.01);
                    Fx.particle(hooves.clone().add(0, 1.2, 0), Particle.DUST, 2, 0.5, 0, Fx.dust(ticks % 4 == 0 ? FROST : PALE, 1.1f));
                }
                Vector moving = horse.getVelocity().setY(0);
                if (moving.lengthSquared() < 0.02) {
                    return;
                }
                Location front = hooves.clone().add(moving.clone().normalize().multiply(1.3)).add(0, 0.8, 0);
                for (LivingEntity target : combat().nearby(player, front, 1.6)) {
                    if (target == horse || minions.containsKey(target.getUniqueId())) {
                        continue;
                    }
                    Integer last = trampled.get(target.getUniqueId());
                    if (last != null && ticks - last < 15) {
                        continue;
                    }
                    trampled.put(target.getUniqueId(), ticks);
                    combat().damage(player, target, 5, true);
                    Combat.setVelocity(target, moving.clone().normalize().multiply(1.1).setY(0.5));
                    fx().burst(Combat.center(target), FROST, 1.6f);
                    Fx.sound(target.getLocation(), Sound.ENTITY_HORSE_STEP_WOOD, 1.2f, 0.6f);
                    Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void vanish(Player player, SkeletonHorse horse) {
        horses.remove(player.getUniqueId(), horse);
        if (!horse.isValid()) {
            return;
        }
        Location at = horse.getLocation();
        horse.eject();
        horse.remove();
        Fx.particle(at.clone().add(0, 1, 0), Particle.SOUL, 20, 0.6, 0.05);
        Fx.particle(at.clone().add(0, 0.3, 0), Particle.BLOCK, 20, 0.5, 0.1, Material.BONE_BLOCK.createBlockData());
        Fx.sound(at, Sound.ENTITY_SKELETON_HORSE_DEATH, 1f, 0.8f);
        status().protectFromFall(player, 40);
    }

    /** Getting off the deathcharger ends the ride. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.getDismounted() instanceof SkeletonHorse horse
                && horses.get(player.getUniqueId()) == horse) {
            Bukkit.getScheduler().runTask(plugin, () -> vanish(player, horse));
        }
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(minions.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        minions.clear();
        for (SkeletonHorse horse : horses.values()) {
            if (horse.isValid()) {
                horse.eject();
                horse.remove();
            }
        }
        horses.clear();
        corpses.clear();
    }
}
