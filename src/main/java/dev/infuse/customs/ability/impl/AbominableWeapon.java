package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.ability.Tether;
import dev.infuse.customs.sword.SwordType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abominable Great Saber, the Blade of the Host (Arc II faction weapon of the Abominable Host).
 * Passive Abominable Hide, on-hit Rend, F Spawn the Brood, Sneak+F Writhing Mass.
 */
public final class AbominableWeapon extends SwordAbility implements Listener {

    private static final int VIOLET = 0xB04CFF;
    private static final int DARK = 0x2A0A3A;
    private static final int BROOD_TICKS = 240;
    private static final int MASS_TICKS = 40;

    /** Broodling -> the player it serves. */
    private final Map<UUID, Player> brood = new HashMap<>();

    public AbominableWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.ABOMINABLE);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ARMOR, "hide", 4, AttributeModifier.Operation.ADD_NUMBER);
        addModifier(meta, Attribute.KNOCKBACK_RESISTANCE, "bulk", 0.5, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        applyEffect(target, PotionEffectType.WITHER, 60, 1);
        Location at = Combat.center(target);
        fx().burst(at, VIOLET, 1.6f);
        Fx.particle(at, Particle.SQUID_INK, 10, 0.3, 0.05);
        Fx.sound(at, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.8f, 1.2f);
    }

    // ---- Spawn the Brood ----

    /**
     * Spawn the Brood: three violet broodlings claw their way out of the ground around you and
     * hunt the enemies of your faction for twelve seconds. They never touch you or your faction.
     */
    @Override
    public boolean primary(Player player) {
        Location at = player.getLocation();
        fx().castCircle(at, VIOLET, 2.4f);
        Fx.sound(at, Sound.ENTITY_SILVERFISH_AMBIENT, 1.5f, 0.6f);
        Fx.sound(at, Sound.BLOCK_SCULK_SPREAD, 1.5f, 0.6f);
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3;
            Location spot = Combat.safeSpot(at.clone().add(Math.cos(a) * 1.6, 0, Math.sin(a) * 1.6));
            if (spot == null) {
                spot = at.clone();
            }
            Location ground = spot;
            fx().crack(Combat.ground(ground.clone().add(0, 0.5, 0)), VIOLET, 1.2f, 30);
            Fx.particle(ground.clone().add(0, 0.2, 0), Particle.BLOCK, 20, 0.4, 0.1, Material.SCULK.createBlockData());
            Endermite mite = ground.getWorld().spawn(ground, Endermite.class, m -> {
                m.customName(Component.text("Broodling", TextColor.color(VIOLET)));
                m.setCustomNameVisible(false);
                m.setPersistent(false);
                m.setRemoveWhenFarAway(true);
                m.setGlowing(true);
                m.getPersistentDataContainer().set(Combat.OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                scale(m, Attribute.MAX_HEALTH, 12);
                scale(m, Attribute.ATTACK_DAMAGE, 4);
                scale(m, Attribute.MOVEMENT_SPEED, 0.36);
                scale(m, Attribute.SCALE, 1.6);
                m.setHealth(12);
            });
            brood.put(mite.getUniqueId(), player);
        }

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t += 10;
                List<Endermite> mine = new ArrayList<>();
                for (Map.Entry<UUID, Player> e : brood.entrySet()) {
                    if (e.getValue() == player && Bukkit.getEntity(e.getKey()) instanceof Endermite m && m.isValid()) {
                        mine.add(m);
                    }
                }
                if (t > BROOD_TICKS || !alive(player) || mine.isEmpty()) {
                    mine.forEach(m -> crumble(m));
                    cancel();
                    return;
                }
                for (Endermite m : mine) {
                    // each hunts the nearest enemy of its master
                    LivingEntity prey = null;
                    double best = 20 * 20;
                    for (LivingEntity e : combat().nearby(player, m.getLocation(), 20)) {
                        if (brood.containsKey(e.getUniqueId())) {
                            continue;
                        }
                        double d = e.getLocation().distanceSquared(m.getLocation());
                        if (d < best) {
                            best = d;
                            prey = e;
                        }
                    }
                    m.setTarget(prey);
                    Fx.particle(m.getLocation().add(0, 0.3, 0), Particle.DUST, 3, 0.2, 0, Fx.dust(VIOLET, 0.9f));
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
        return true;
    }

    private static void scale(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance a = e.getAttribute(attribute);
        if (a != null) {
            a.setBaseValue(value);
        }
    }

    private void crumble(Endermite m) {
        brood.remove(m.getUniqueId());
        if (m.isValid()) {
            Fx.particle(m.getLocation().add(0, 0.3, 0), Particle.SQUID_INK, 10, 0.3, 0.05);
            Fx.particle(m.getLocation().add(0, 0.3, 0), Particle.DUST, 10, 0.3, 0, Fx.dust(VIOLET, 1f));
            m.remove();
        }
    }

    /** Broodlings never turn on their master, their master's faction or each other. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Player master = brood.get(event.getEntity().getUniqueId());
        Entity target = event.getTarget();
        if (master == null || target == null) {
            return;
        }
        if (target == master || brood.containsKey(target.getUniqueId())
                || target instanceof Player p && plugin.arcs().allies(master, p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBroodDeath(EntityDeathEvent event) {
        if (brood.remove(event.getEntity().getUniqueId()) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            Fx.particle(event.getEntity().getLocation().add(0, 0.3, 0), Particle.SQUID_INK, 10, 0.3, 0.05);
        }
    }

    // ---- Writhing Mass ----

    /**
     * Writhing Mass: violet tendrils burst out of you and seize every enemy within seven blocks,
     * dragging them in close and holding them for two seconds while the mass pulses; your faction
     * nearby gains Strength.
     */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> seized = new ArrayList<>(combat().nearby(player, player.getLocation(), 7));
        if (seized.isEmpty()) {
            tell(player, "No enemies near enough to seize.");
            return false;
        }
        BlockData[] flesh = {Material.CRYING_OBSIDIAN.createBlockData(), Material.PURPLE_CONCRETE.createBlockData()};
        Map<LivingEntity, Tether> tendrils = new HashMap<>();
        for (LivingEntity e : seized) {
            tendrils.put(e, new Tether(displays(), flesh, 0.32f, 0.32f, 0.55, VIOLET, false, MASS_TICKS + 30));
        }
        Location at = player.getLocation();
        fx().flash(at.clone().add(0, 1, 0), VIOLET, 3f);
        fx().crack(at, VIOLET, 3f, 50);
        Fx.sound(at, Sound.ENTITY_WARDEN_ROAR, 0.8f, 1.2f);
        Fx.sound(at, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 0.6f);
        for (Player p : at.getNearbyPlayers(10)) {
            if (p == player || plugin.arcs().allies(player, p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 0, false, false, true));
            }
        }

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                seized.removeIf(e -> {
                    boolean gone = !e.isValid() || e.isDead() || e.getWorld() != player.getWorld();
                    if (gone && tendrils.containsKey(e)) {
                        tendrils.remove(e).remove();
                    }
                    return gone;
                });
                if (t > MASS_TICKS + 8 || !alive(player) || seized.isEmpty()) {
                    tendrils.values().forEach(Tether::remove);
                    tendrils.clear();
                    for (LivingEntity e : seized) {
                        Combat.knockback(e, player.getLocation(), 0.6, 0.3);
                    }
                    Fx.particle(player.getLocation().add(0, 1, 0), Particle.SQUID_INK, 20, 0.6, 0.05);
                    cancel();
                    return;
                }
                Location core = player.getLocation().add(0, 1, 0);
                for (LivingEntity e : seized) {
                    Location body = Combat.center(e);
                    tendrils.get(e).update(core, body, 0.3, 0.25, t);
                    Vector in = core.toVector().subtract(body.toVector());
                    double distance = in.length();
                    if (t <= 8 && distance > 1.8) {
                        // the tendrils reel them in
                        Combat.setVelocity(e, in.multiply(Math.min(1.2, distance * 0.18) / distance).setY(0.1));
                    } else {
                        Combat.setVelocity(e, new Vector(0, Math.min(0, e.getVelocity().getY()), 0));
                    }
                    if (t > 8 && t % 10 == 0) {
                        combat().damage(player, e, 2, true);
                        Fx.particle(body, Particle.DUST, 6, 0.3, 0, Fx.dust(DARK, 1.1f));
                    }
                }
                if (t % 10 == 0) {
                    fx().shockwave(player.getLocation().add(0, 0.05, 0), VIOLET, 2.6f, 6);
                    Fx.sound(core, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.8f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(brood.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        brood.clear();
    }
}
