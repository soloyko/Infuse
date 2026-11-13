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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
 * Hornet's Needle, the Hive Needle: venom, and the hive behind it.
 * Passive Gossamer, on-hit Venom, F Swarm Form, Sneak+F Hive Mind.
 */
public final class NeedleWeapon extends SwordAbility implements Listener {

    private static final int YELLOW = 0xF5C518;
    private static final int AMBER = 0xFFA51F;
    private static final int BLACK = 0x2A2A2A;
    private static final int MAX_VENOM = 6;
    /** How long venom stays on a target (ticks). */
    private static final int VENOM_TICKS = 200;
    private static final int SWARM_TICKS = 60;
    private static final int HIVE_TICKS = 120;
    private static final BlockData HONEY = Material.HONEY_BLOCK.createBlockData();
    private static final BlockData DARK = Material.BLACK_CONCRETE.createBlockData();

    private record Venom(UUID source, int stacks, int until) {
    }

    /** Entity -> its venom. */
    private final Map<UUID, Venom> venom = new HashMap<>();
    /** Players who are a swarm right now. */
    private final Set<UUID> swarming = new HashSet<>();

    public NeedleWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.NEEDLE);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.JUMP_BOOST, 0, 0));
    }

    /** Gossamer: sneaking in mid-air lets you drift down. */
    @Override
    public void tickHeld(Player player, long tick) {
        if (player.isSneaking() && player.getLocation().subtract(0, 0.2, 0).getBlock().isPassable() && player.getVelocity().getY() < 0) {
            applyEffect(player, PotionEffectType.SLOW_FALLING, 14, 0);
            Fx.particle(player.getLocation(), Particle.DUST, 2, 0.3, 0, Fx.dust(YELLOW, 0.8f));
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        sting(player, target);
    }

    /** Adds a stack of venom: poison that lasts longer with every stack. */
    private void sting(Player player, LivingEntity target) {
        int now = Bukkit.getCurrentTick();
        Venom old = venom.get(target.getUniqueId());
        int stacks = old == null || old.until < now ? 1 : Math.min(MAX_VENOM, old.stacks + 1);
        venom.put(target.getUniqueId(), new Venom(player.getUniqueId(), stacks, now + VENOM_TICKS));
        applyEffect(target, PotionEffectType.POISON, 30 + stacks * 15, stacks >= 4 ? 1 : 0);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.DUST, 3 + stacks, 0.3, 0, Fx.dust(stacks >= 4 ? AMBER : YELLOW, 1.0f));
        if (stacks == MAX_VENOM) {
            Fx.particle(at, Particle.ITEM_SLIME, 6, 0.3, 0.05);
        }
    }

    private int stacksOn(Player player, LivingEntity target) {
        Venom v = venom.get(target.getUniqueId());
        return v == null || v.until < Bukkit.getCurrentTick() || !v.source.equals(player.getUniqueId()) ? 0 : v.stacks;
    }

    // ---- Swarm Form ----

    /**
     * Swarm Form: you burst into a cloud of hornets for three seconds. The swarm flies wherever you
     * look, nothing can hurt it, and everything it passes through gets stung.
     */
    @Override
    public boolean primary(Player player) {
        UUID id = player.getUniqueId();
        if (!swarming.add(id)) {
            return false;
        }
        for (Player other : player.getWorld().getPlayers()) {
            if (other != player) {
                other.hidePlayer(plugin, player);
            }
        }
        status().protectFromFall(player, SWARM_TICKS + 40);
        applyEffect(player, PotionEffectType.INVISIBILITY, SWARM_TICKS + 2, 0);
        Location at = player.getLocation().add(0, 1, 0);
        Fx.sound(at, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.5f, 1.2f);
        Fx.sound(at, Sound.BLOCK_BEEHIVE_EXIT, 1.2f, 0.8f);
        fx().burst(at, YELLOW, 2.2f);
        // the hornets: tiny honey and black blocks buzzing around where you are
        List<BlockDisplay> hornets = new ArrayList<>();
        Location spawnAt = at.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        for (int i = 0; i < 14; i++) {
            BlockDisplay hornet = displays().block(spawnAt, i % 3 == 0 ? DARK : HONEY, new Vector3f(0.13f, 0.1f, 0.18f),
                    new Quaternionf(), SWARM_TICKS + 10);
            hornet.setTeleportDuration(2);
            hornet.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            hornets.add(hornet);
        }
        Map<UUID, Integer> stung = new HashMap<>();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > SWARM_TICKS) {
                    reform();
                    return;
                }
                Vector look = player.getEyeLocation().getDirection();
                Combat.setVelocity(player, look.clone().multiply(0.85));
                Location body = player.getLocation().add(0, 0.9, 0);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < hornets.size(); i++) {
                    BlockDisplay hornet = hornets.get(i);
                    if (!hornet.isValid()) {
                        continue;
                    }
                    double a = ticks * 0.7 + i * 2.3;
                    double radius = 0.6 + 0.4 * Math.sin(ticks * 0.3 + i);
                    Location p = body.clone().add(Math.cos(a) * radius + r.nextDouble(-0.15, 0.15),
                            Math.sin(ticks * 0.5 + i) * 0.7, Math.sin(a) * radius + r.nextDouble(-0.15, 0.15));
                    p.setYaw(0);
                    p.setPitch(0);
                    hornet.teleport(p);
                }
                Fx.particle(body, Particle.DUST, 6, 0.5, 0, Fx.dust(ticks % 2 == 0 ? YELLOW : BLACK, 0.7f));
                if (ticks % 10 == 0) {
                    Fx.sound(body, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1f, 1.3f);
                }
                for (LivingEntity target : combat().nearby(player, body, 1.8)) {
                    Integer last = stung.get(target.getUniqueId());
                    if (last != null && ticks - last < 6) {
                        continue;
                    }
                    stung.put(target.getUniqueId(), ticks);
                    combat().damage(player, target, 2, true);
                    sting(player, target);
                    Fx.sound(target.getLocation(), Sound.ENTITY_BEE_STING, 0.8f, 1.2f);
                }
            }

            private void reform() {
                cancel();
                swarming.remove(id);
                hornets.forEach(h -> displays().remove(h));
                for (Player other : player.getWorld().getPlayers()) {
                    other.showPlayer(plugin, player);
                }
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                if (alive(player)) {
                    Location here = player.getLocation().add(0, 1, 0);
                    Combat.setVelocity(player, player.getVelocity().multiply(0.3));
                    fx().burst(here, YELLOW, 2f);
                    Fx.particle(here, Particle.DUST, 30, 0.6, 0, Fx.dust(AMBER, 1.0f));
                    Fx.sound(here, Sound.BLOCK_BEEHIVE_ENTER, 1.2f, 1f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** The swarm can't be hurt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwarmHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && swarming.contains(player.getUniqueId())
                && event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (swarming.remove(player.getUniqueId())) {
            for (Player other : player.getWorld().getPlayers()) {
                other.showPlayer(plugin, player);
            }
        }
    }

    // ---- Hive Mind ----

    /**
     * Hive Mind: the venom in every poisoned enemy nearby flares up and turns them on each other for
     * six seconds. Creatures attack whoever the hive points them at; players are left reeling.
     */
    @Override
    public boolean secondary(Player player) {
        List<LivingEntity> thralls = new ArrayList<>();
        for (LivingEntity target : combat().nearby(player, player.getLocation(), 12)) {
            if (stacksOn(player, target) > 0) {
                thralls.add(target);
            }
        }
        if (thralls.isEmpty()) {
            tell(player, "No enemy nearby carries your venom.");
            return false;
        }
        Location at = player.getLocation().add(0, 1, 0);
        Fx.sound(at, Sound.BLOCK_BEEHIVE_WORK, 1.5f, 0.6f);
        Fx.sound(at, Sound.ENTITY_BEE_LOOP_AGGRESSIVE, 1.5f, 0.7f);
        fx().flash(at, AMBER, 2.4f);
        for (LivingEntity thrall : thralls) {
            int stacks = stacksOn(player, thrall);
            // the venom flares as the hive takes hold
            combat().damage(player, thrall, 1 + stacks, true);
            fx().beam(at, Combat.center(thrall), YELLOW, 0.15f, 8);
            fx().burst(Combat.center(thrall), AMBER, 1.4f);
            thrall.setGlowing(true);
            if (thrall instanceof Player victim) {
                applyEffect(victim, PotionEffectType.NAUSEA, 120, 0);
                applyEffect(victim, PotionEffectType.BLINDNESS, 50, 0);
            }
        }

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                thralls.removeIf(t -> !t.isValid() || t.isDead());
                if (ticks > HIVE_TICKS || thralls.isEmpty()) {
                    finish();
                    return;
                }
                for (LivingEntity thrall : thralls) {
                    Location head = thrall.getEyeLocation().add(0, 0.5, 0);
                    if (ticks % 3 == 0) {
                        double a = ticks * 0.4;
                        Fx.particle(head.clone().add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), Particle.DUST, 1, 0, 0, Fx.dust(YELLOW, 0.9f));
                    }
                    if (ticks % 10 != 0 || !(thrall instanceof Mob mob)) {
                        continue;
                    }
                    // point every creature at the nearest other thrall, or any enemy if it's the last one
                    LivingEntity prey = nearestOther(thrall, thralls);
                    if (prey == null) {
                        for (LivingEntity other : combat().nearby(player, thrall.getLocation(), 10)) {
                            if (other != thrall) {
                                prey = other;
                                break;
                            }
                        }
                    }
                    if (prey != null) {
                        mob.setTarget(prey);
                        if (ticks % 20 == 0) {
                            fx().beam(head, prey.getEyeLocation(), AMBER, 0.08f, 6);
                        }
                    }
                }
            }

            private void finish() {
                cancel();
                for (LivingEntity thrall : thralls) {
                    thrall.setGlowing(false);
                    if (thrall instanceof Mob mob && mob.getTarget() != null && !(mob.getTarget() instanceof Player)) {
                        mob.setTarget(null);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static LivingEntity nearestOther(LivingEntity from, List<LivingEntity> among) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity other : among) {
            double d = other.getLocation().distanceSquared(from.getLocation());
            if (other != from && d < bestDistance) {
                best = other;
                bestDistance = d;
            }
        }
        return best;
    }

    @Override
    public void shutdown() {
        for (UUID id : swarming) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                for (Player other : player.getWorld().getPlayers()) {
                    other.showPlayer(plugin, player);
                }
            }
        }
        swarming.clear();
        venom.clear();
    }
}
