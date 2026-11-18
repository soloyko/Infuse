package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Star's Edge, the Constellation Blade: stars on everything it touches, and the lines between them.
 * Passive Stargazer, on-hit Star Mark, F Constellation, Sneak+F Astral Projection.
 */
public final class StarsWeapon extends SwordAbility implements Listener {

    private static final int TEAL = 0x5CF2C2;
    private static final int VIOLET = 0x6A4BD6;
    private static final int WHITE = 0xF4FFFB;
    private static final int STAR_TICKS = 160;
    private static final int PROJECTION_TICKS = 100;

    /** A star over an enemy: who set it, its label, and when it fades. */
    private static final class Star {
        final UUID owner;
        final TextDisplay label;
        int until;

        Star(UUID owner, TextDisplay label, int until) {
            this.owner = owner;
            this.label = label;
            this.until = until;
        }
    }

    private final Map<LivingEntity, Star> stars = new HashMap<>();
    /** Projecting player -> their body. */
    private final Map<UUID, Mannequin> bodies = new HashMap<>();
    private final BukkitTask starTask;

    public StarsWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.STARS);
        this.starTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickStars, 2L, 2L);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        long time = player.getWorld().getTime();
        if (time > 13000 && time < 23000) {
            event.setDamage(event.getDamage() * 1.2);
        }
        star(player, target);
    }

    /** Sets (or renews) a star over an enemy. */
    private void star(Player owner, LivingEntity target) {
        int until = Bukkit.getCurrentTick() + STAR_TICKS;
        Star star = stars.get(target);
        if (star != null && star.label.isValid()) {
            star.until = until;
            return;
        }
        TextDisplay label = displays().text(target.getLocation().add(0, target.getHeight() + 0.5, 0),
                Component.text("✦", TextColor.color(TEAL)), 1.4f, 0);
        label.setTeleportDuration(2);
        stars.put(target, new Star(owner.getUniqueId(), label, until));
        Fx.particle(Combat.center(target), Particle.END_ROD, 4, 0.3, 0.03);
        Fx.sound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.8f);
    }

    private void tickStars() {
        int now = Bukkit.getCurrentTick();
        stars.entrySet().removeIf(e -> {
            LivingEntity t = e.getKey();
            Star s = e.getValue();
            if (!t.isValid() || t.isDead() || s.until < now || !s.label.isValid()) {
                displays().remove(s.label);
                return true;
            }
            s.label.teleport(t.getLocation().add(0, t.getHeight() + 0.5, 0));
            return false;
        });
    }

    private List<LivingEntity> starredBy(Player player, Location around, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Map.Entry<LivingEntity, Star> e : stars.entrySet()) {
            if (e.getValue().owner.equals(player.getUniqueId()) && e.getKey().getWorld() == around.getWorld()
                    && e.getKey().getLocation().distanceSquared(around) < radius * radius && combat().canHit(player, e.getKey())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // ---- Constellation ----

    /**
     * Constellation: every starred enemy within 16 blocks is joined to the next by lines of
     * starlight. A moment later the lines pull tight, dragging them all together into a collapsing
     * star that hurts more the more stars it holds.
     */
    @Override
    public boolean primary(Player player) {
        List<LivingEntity> linked = starredBy(player, player.getLocation(), 16);
        if (linked.isEmpty()) {
            tell(player, "No starred enemies nearby. Hit enemies to set stars over them.");
            return false;
        }
        Vector sum = new Vector();
        for (LivingEntity t : linked) {
            sum.add(Combat.center(t).toVector());
        }
        Location middle = sum.multiply(1.0 / linked.size()).toLocation(player.getWorld());
        // join them in order around the middle, closing the shape
        linked.sort(Comparator.comparingDouble(t -> Math.atan2(t.getLocation().getZ() - middle.getZ(), t.getLocation().getX() - middle.getX())));
        int count = linked.size();
        Fx.sound(middle, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.6f);
        Fx.sound(middle, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.4f, 1.4f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                linked.removeIf(t -> !t.isValid() || t.isDead());
                if (linked.isEmpty() || !alive(player)) {
                    cancel();
                    return;
                }
                if (ticks <= 24 && ticks % 4 == 1) {
                    for (int i = 0; i < linked.size(); i++) {
                        Location a = Combat.center(linked.get(i));
                        Location b = linked.size() > 1 ? Combat.center(linked.get((i + 1) % linked.size())) : middle;
                        fx().beam(a, b, TEAL, 0.2f, 5);
                        fx().beam(a, b, WHITE, 0.06f, 4);
                        Fx.particle(a, Particle.END_ROD, 2, 0.1, 0.02);
                    }
                }
                if (ticks > 14 && ticks <= 24) {
                    // the lines pull tight
                    for (LivingEntity t : linked) {
                        Vector in = middle.toVector().subtract(Combat.center(t).toVector());
                        double d = in.length();
                        if (d > 0.8) {
                            Combat.setVelocity(t, in.multiply(Math.min(1.2, d * 0.3) / d));
                        }
                    }
                }
                if (ticks == 26) {
                    cancel();
                    collapse(player, linked, middle, count);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void collapse(Player player, List<LivingEntity> linked, Location middle, int count) {
        double damage = 4 + 2.5 * count;
        fx().flash(middle, WHITE, 3.4f);
        fx().burst(middle, TEAL, 3f);
        fx().wave(middle, new Vector(0, 1, 0), VIOLET, 3.5f, 8);
        Fx.particle(middle, Particle.END_ROD, 40, 0.8, 0.15);
        Fx.sound(middle, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1f);
        Fx.sound(middle, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
        for (LivingEntity t : linked) {
            combat().damage(player, t, damage, true);
            Combat.knockback(t, middle, 0.9, 0.5);
            Star s = stars.remove(t);
            if (s != null) {
                displays().remove(s.label);
            }
        }
    }

    // ---- Astral Projection ----

    /**
     * Astral Projection: you step out of your body. For five seconds your spirit flies freely and
     * nothing can hurt it, and every enemy it passes through gets a star. Your body stays behind,
     * though, and anything that hits it hits you. Then you snap back into it.
     */
    @Override
    public boolean secondary(Player player) {
        UUID id = player.getUniqueId();
        if (bodies.containsKey(id)) {
            return false;
        }
        Location origin = player.getLocation();
        Mannequin body = player.getWorld().spawn(origin, Mannequin.class, m -> {
            m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            m.setDescription(Component.empty());
            m.setPersistent(false);
            m.setSilent(true);
            m.setImmovable(true);
            for (org.bukkit.inventory.EquipmentSlot slot : new org.bukkit.inventory.EquipmentSlot[]{org.bukkit.inventory.EquipmentSlot.HAND,
                    org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.CHEST,
                    org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET}) {
                m.getEquipment().setItem(slot, player.getEquipment().getItem(slot).clone());
            }
        });
        bodies.put(id, body);
        boolean couldFly = player.getAllowFlight();
        for (Player other : player.getWorld().getPlayers()) {
            if (other != player) {
                other.hidePlayer(plugin, player);
            }
        }
        applyEffect(player, PotionEffectType.INVISIBILITY, PROJECTION_TICKS + 2, 0);
        player.setAllowFlight(true);
        player.setFlying(true);
        Combat.setVelocity(player, new Vector(0, 0.5, 0));
        fx().flash(origin.clone().add(0, 1, 0), TEAL, 2.6f);
        Fx.sound(origin, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
        Fx.sound(origin, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.2f, 0.8f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || player.isDead() || ticks > PROJECTION_TICKS || !body.isValid()
                        || player.getWorld() != origin.getWorld()) {
                    snapBack();
                    return;
                }
                Location spirit = player.getLocation().add(0, 1, 0);
                Fx.particle(spirit, Particle.END_ROD, 2, 0.3, 0.01);
                Fx.particle(spirit, Particle.DUST, 3, 0.35, 0, Fx.dust(ticks % 2 == 0 ? TEAL : VIOLET, 1.1f));
                // a thread of starlight back to your body
                if (ticks % 3 == 0) {
                    fx().beam(body.getLocation().add(0, 1.2, 0), spirit, VIOLET, 0.06f, 3);
                }
                for (LivingEntity target : combat().nearby(player, spirit, 1.6)) {
                    if (!stars.containsKey(target)) {
                        star(player, target);
                        combat().damage(player, target, 2, true);
                    }
                }
            }

            private void snapBack() {
                cancel();
                bodies.remove(id);
                Location back = body.isValid() ? body.getLocation() : origin;
                if (body.isValid()) {
                    body.remove();
                }
                if (!player.isOnline()) {
                    return;
                }
                for (Player other : player.getWorld().getPlayers()) {
                    other.showPlayer(plugin, player);
                }
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                    player.setFlying(false);
                    player.setAllowFlight(couldFly);
                }
                back.setYaw(player.getLocation().getYaw());
                back.setPitch(player.getLocation().getPitch());
                player.teleport(back, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
                status().protectFromFall(player, 40);
                fx().flash(back.clone().add(0, 1, 0), WHITE, 2.4f);
                Fx.sound(back, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.8f);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** The spirit can't be hurt; the body can, and that hurts its owner. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && bodies.containsKey(player.getUniqueId())
                && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Mannequin m) {
            for (Map.Entry<UUID, Mannequin> e : bodies.entrySet()) {
                if (e.getValue() == m) {
                    event.setCancelled(true);
                    Player owner = Bukkit.getPlayer(e.getKey());
                    if (owner != null) {
                        bodies.remove(e.getKey());
                        owner.damage(event.getDamage());
                        bodies.put(e.getKey(), m);
                        Fx.particle(m.getLocation().add(0, 1, 0), Particle.DAMAGE_INDICATOR, 4, 0.3, 0.05);
                    }
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Mannequin body = bodies.remove(event.getPlayer().getUniqueId());
        if (body != null && body.isValid()) {
            event.getPlayer().teleport(body.getLocation());
            body.remove();
        }
    }

    @Override
    public void shutdown() {
        starTask.cancel();
        for (Star s : stars.values()) {
            displays().remove(s.label);
        }
        stars.clear();
        for (Map.Entry<UUID, Mannequin> e : bodies.entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player != null) {
                for (Player other : player.getWorld().getPlayers()) {
                    other.showPlayer(plugin, player);
                }
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                if (e.getValue().isValid()) {
                    player.teleport(e.getValue().getLocation());
                }
            }
            if (e.getValue().isValid()) {
                e.getValue().remove();
            }
        }
        bodies.clear();
    }
}
