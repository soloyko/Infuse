package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Silverine Blade, the Silver Mirror Blade: illusions and reflections.
 * Passive Glass Skin, on-hit Shatter, F Mirror Images, Sneak+F Reflection.
 */
public final class MirrorWeapon extends SwordAbility implements Listener {

    private static final int SILVER = 0xC9D6E3;
    private static final int SHEEN = 0x8FB8FF;
    private static final int IMAGE_TICKS = 160;
    private static final int IMAGE_HITS = 3;
    private static final int REFLECT_TICKS = 60;
    private static final BlockData GLASS = Material.GLASS.createBlockData();
    private static final BlockData PANE = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData();

    /** Mirror image -> hits it can still take. */
    private final Map<Mannequin, Integer> images = new HashMap<>();
    /** Players reflecting damage right now. */
    private final Set<UUID> reflecting = new HashSet<>();
    /** Guards against two reflecting players bouncing a hit back and forth forever. */
    private boolean reflectingNow;

    public MirrorWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.MIRROR);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        Location at = Combat.center(target);
        for (LivingEntity near : combat().nearby(player, at, 2.8)) {
            if (near != target) {
                combat().damage(player, near, 4);
                fx().cut(Combat.center(near), Combat.center(near).toVector().subtract(at.toVector()), Math.random() * Math.PI, 0.4,
                        SILVER, 1.2f, 2);
            }
        }
        shards(at, 1f);
    }

    private void shards(Location at, float size) {
        Fx.particle(at, Particle.BLOCK, (int) (24 * size), 0.4 * size, 0.15, GLASS);
        Fx.particle(at, Particle.END_ROD, (int) (6 * size), 0.3 * size, 0.05);
        fx().burst(at, SHEEN, 1.6f * size);
        Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.3f);
    }

    /** Glass Skin and Reflection: both decide what happens when the player is hit. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        LivingEntity attacker = attacker(event);
        if (reflecting.contains(player.getUniqueId())) {
            event.setCancelled(true);
            Location at = player.getLocation().add(0, 1.1, 0);
            Fx.sound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.8f);
            Fx.sound(at, Sound.BLOCK_GLASS_HIT, 1.2f, 1.6f);
            fx().flash(at, SHEEN, 1.6f);
            if (attacker != null && attacker != player && !reflectingNow) {
                reflectingNow = true;
                try {
                    combat().damage(player, attacker, event.getDamage(), true);
                } finally {
                    reflectingNow = false;
                }
                fx().beam(at, Combat.center(attacker), SILVER, 0.2f, 6);
                shards(Combat.center(attacker), 0.8f);
            }
            return;
        }
        if (plugin.items().heldType(player) == type && plugin.canUse(player, type) && chance(0.15)) {
            // it was a reflection they hit
            event.setCancelled(true);
            Location at = player.getLocation().add(0, 1, 0);
            Vector side = new Vector(-player.getLocation().getDirection().getZ(), 0, player.getLocation().getDirection().getX());
            fx().afterimage(at.clone().add(side.normalize().multiply(0.4)), plugin.items().create(type),
                    player.getLocation().getDirection(), SILVER, 1.0f, 8);
            shards(at, 0.6f);
        }
    }

    private static LivingEntity attacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }

    // ---- Mirror Images ----

    /**
     * Mirror Images: two copies of you step out of the blade, one to each side. They copy every
     * move you make, creatures go after them instead of you, and each breaks into a burst of
     * cutting glass after three hits or when the eight seconds are up.
     */
    @Override
    public boolean primary(Player player) {
        Location feet = player.getLocation();
        Vector look = feet.getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-4) {
            look = new Vector(0, 0, 1);
        }
        Vector side = new Vector(-look.getZ(), 0, look.getX()).normalize();
        List<Mannequin> pair = new ArrayList<>();
        for (int s = -1; s <= 1; s += 2) {
            Location at = Combat.safeSpot(feet.clone().add(side.clone().multiply(1.8 * s)));
            if (at == null) {
                at = feet.clone();
            }
            at.setYaw(feet.getYaw());
            at.setPitch(feet.getPitch());
            Mannequin image = player.getWorld().spawn(at, Mannequin.class, m -> {
                m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
                m.setDescription(Component.empty());
                m.setPersistent(false);
                m.setSilent(true);
                m.setGlowing(true);
                EntityEquipment from = player.getEquipment();
                EntityEquipment to = m.getEquipment();
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    to.setItem(slot, from.getItem(slot).clone());
                }
            });
            images.put(image, IMAGE_HITS);
            pair.add(image);
            Location chest = at.clone().add(0, 1, 0);
            shards(chest, 0.8f);
            fx().beam(feet.clone().add(0, 1, 0), chest, SILVER, 0.12f, 6);
        }
        Fx.sound(feet, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.4f, 1f);
        Fx.sound(feet, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.4f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                pair.removeIf(m -> !m.isValid() || !images.containsKey(m));
                if (!alive(player) || pair.isEmpty() || ticks > IMAGE_TICKS) {
                    for (Mannequin m : pair) {
                        breakImage(player, m);
                    }
                    cancel();
                    return;
                }
                // they move as you move and look where you look
                Vector velocity = player.getVelocity();
                for (Mannequin m : pair) {
                    Combat.setVelocity(m, new Vector(velocity.getX(), Math.max(velocity.getY(), m.getVelocity().getY()), velocity.getZ()));
                    m.setRotation(player.getLocation().getYaw(), player.getLocation().getPitch());
                    if (ticks % 8 == 0) {
                        Fx.particle(m.getLocation().add(0, 1, 0), Particle.END_ROD, 1, 0.3, 0.01);
                    }
                }
                // creatures hunting you hunt a reflection instead
                if (ticks % 10 == 1) {
                    for (Entity e : player.getNearbyEntities(16, 8, 16)) {
                        if (e instanceof Mob mob && mob.getTarget() == player) {
                            mob.setTarget(pair.get(ThreadLocalRandom.current().nextInt(pair.size())));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Mirror images don't really get hurt: each hit cracks them, the third breaks them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onImageHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mannequin m) || !images.containsKey(m)) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        int left = images.get(m) - 1;
        Fx.particle(m.getLocation().add(0, 1, 0), Particle.BLOCK, 10, 0.3, 0.1, GLASS);
        Fx.sound(m.getLocation(), Sound.BLOCK_GLASS_HIT, 1f, 1.4f);
        if (left > 0) {
            images.put(m, left);
            return;
        }
        Player owner = null;
        for (Player p : m.getWorld().getPlayers()) {
            if (p.getPlayerProfile().getId() != null && m.getProfile().uuid() != null
                    && p.getPlayerProfile().getId().equals(m.getProfile().uuid())) {
                owner = p;
            }
        }
        breakImage(owner, m);
    }

    /** A mirror image shatters: the glass cuts every enemy around it. */
    private void breakImage(Player owner, Mannequin m) {
        if (images.remove(m) == null || !m.isValid()) {
            return;
        }
        Location at = m.getLocation().add(0, 1, 0);
        m.remove();
        shards(at, 1.4f);
        Fx.sound(at, Sound.BLOCK_GLASS_BREAK, 1.4f, 0.8f);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        for (LivingEntity near : combat().nearby(owner, at, 3)) {
            combat().damage(owner, near, 5, true);
            Combat.knockback(near, at, 0.6, 0.3);
            fx().cut(Combat.center(near), Combat.center(near).toVector().subtract(at.toVector()), Math.random() * Math.PI, 0.4,
                    SILVER, 1.4f, 2);
        }
    }

    // ---- Reflection ----

    /**
     * Reflection: four mirrors circle you for three seconds. Every hit you take in that time is
     * turned back on whoever dealt it, and you take none of it.
     */
    @Override
    public boolean secondary(Player player) {
        UUID id = player.getUniqueId();
        if (!reflecting.add(id)) {
            return false;
        }
        Location at = player.getLocation().add(0, 1, 0);
        Fx.sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
        Fx.sound(at, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.2f, 1.2f);
        fx().flash(at, SHEEN, 2.2f);
        List<BlockDisplay> mirrors = new ArrayList<>();
        Location spawnAt = at.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        for (int i = 0; i < 4; i++) {
            BlockDisplay mirror = displays().block(spawnAt, i % 2 == 0 ? GLASS : PANE, new Vector3f(0.75f, 1.2f, 0.06f),
                    new Quaternionf(), REFLECT_TICKS + 10);
            mirror.setTeleportDuration(1);
            mirror.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            mirror.setGlowing(true);
            mirror.setGlowColorOverride(org.bukkit.Color.fromRGB(SILVER));
            mirrors.add(mirror);
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > REFLECT_TICKS) {
                    cancel();
                    reflecting.remove(id);
                    for (BlockDisplay mirror : mirrors) {
                        if (mirror.isValid()) {
                            Fx.particle(mirror.getLocation(), Particle.BLOCK, 8, 0.3, 0.1, GLASS);
                        }
                        displays().remove(mirror);
                    }
                    if (alive(player)) {
                        Fx.sound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
                    }
                    return;
                }
                Location center = player.getLocation().add(0, 1.0, 0);
                for (int i = 0; i < mirrors.size(); i++) {
                    double a = ticks * 0.12 + i * Math.PI / 2;
                    Location p = center.clone().add(Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3);
                    p.setYaw(0);
                    p.setPitch(0);
                    BlockDisplay mirror = mirrors.get(i);
                    if (mirror.isValid()) {
                        mirror.teleport(p);
                        // face outwards
                        Quaternionf face = new Quaternionf().rotateY((float) (-a + Math.PI / 2));
                        dev.infuse.customs.ability.Displays.animateBlock(mirror, new Vector3f(0.75f, 1.2f, 0.06f), face, new Vector3f(), 1);
                    }
                }
                if (ticks % 5 == 0) {
                    Fx.particle(center, Particle.END_ROD, 2, 0.8, 0.01);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @Override
    public void shutdown() {
        for (Mannequin m : images.keySet()) {
            if (m.isValid()) {
                m.remove();
            }
        }
        images.clear();
        reflecting.clear();
    }
}
