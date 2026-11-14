package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Death Knight's Sword, the Runed Greatsword: an oath that outlived its king.
 * Passive Frost Presence, on-hit Death Coil, F Death Grip, Sneak+F Runeforge.
 */
public final class DeathKnightWeapon extends SwordAbility implements Listener {

    private static final int FROST = 0x5DA9E9;
    private static final int PALE = 0xCFE8FF;
    private static final int BLOOD = 0xC4203A;
    private static final int UNHOLY = 0x7BE05A;
    private static final int RUNE_TICKS = 200;

    /** The three runes of Runeforge, used up in this order. */
    private enum Rune {
        FROST(DeathKnightWeapon.FROST), BLOOD(DeathKnightWeapon.BLOOD), UNHOLY(DeathKnightWeapon.UNHOLY);

        final int color;

        Rune(int color) {
            this.color = color;
        }
    }

    /** A player's forged runes: the ones left, and the sigils circling them. */
    private static final class Forged {
        final Deque<Rune> left = new ArrayDeque<>(List.of(Rune.values()));
        final Map<Rune, ItemDisplay> sigils = new HashMap<>();
        int until;
    }

    private final Map<UUID, Forged> forged = new HashMap<>();

    public DeathKnightWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.DEATHKNIGHT);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Forged f = forged.get(player.getUniqueId());
        if (f != null && !f.left.isEmpty() && f.until >= Bukkit.getCurrentTick()) {
            unleash(player, target, event, f, f.left.pollFirst());
            if (f.left.isEmpty()) {
                forged.remove(player.getUniqueId());
            }
        }
        if (!chance(0.2)) {
            return;
        }
        // Death Coil
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
        if (damager instanceof LivingEntity attacker && attacker != player) {
            applyEffect(attacker, PotionEffectType.SLOWNESS, 40, 1);
            Fx.particle(Combat.center(attacker), Particle.SNOWFLAKE, 8, 0.3, 0.02);
        }
    }

    // ---- Death Grip ----

    /**
     * Death Grip: a hand of bone bursts out of the ground under the enemy you aim at, closes
     * around it and drags it through the frost to your feet, where it is held for a moment.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 20, 0.8);
        if (target == null) {
            tell(player, "Look at an enemy to seize it.");
            return false;
        }
        Hand hand = new Hand(target.getLocation());
        hand.rise();
        Fx.sound(target.getLocation(), Sound.ENTITY_SKELETON_AMBIENT, 1.2f, 0.5f);
        Fx.sound(target.getLocation(), Sound.BLOCK_BONE_BLOCK_BREAK, 1.4f, 0.6f);
        Fx.sound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.5f, 1.6f);
        fx().crack(Combat.ground(target.getLocation().add(0, 0.5, 0)), FROST, 1.8f, 30);
        applyEffect(target, PotionEffectType.SLOWNESS, 40, 4);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || !target.isValid() || target.isDead() || target.getWorld() != player.getWorld()) {
                    hand.sink();
                    cancel();
                    return;
                }
                if (ticks < 6) {
                    hand.follow(target.getLocation(), (float) ticks / 6);
                    return; // the fingers close
                }
                Vector look = player.getLocation().getDirection().setY(0);
                look = look.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : look.normalize();
                Location dest = player.getLocation().add(look.multiply(1.6));
                Vector pull = dest.toVector().subtract(target.getLocation().toVector()).setY(0);
                double distance = pull.length();
                // delivered, or as close as it can be dragged (a wall, something rooted in place)
                if (distance < 0.8 || ticks > 36) {
                    // delivered: held there, frozen
                    hand.follow(target.getLocation(), 1);
                    hold(target, 30);
                    combat().damage(player, target, 4, true);
                    target.setFreezeTicks(Math.max(target.getFreezeTicks(), 120));
                    fx().burst(Combat.center(target), PALE, 1.8f);
                    Fx.particle(Combat.center(target), Particle.SNOWFLAKE, 25, 0.4, 0.05);
                    Fx.sound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
                    Fx.sound(target.getLocation(), Sound.ENTITY_SKELETON_HURT, 1f, 0.6f);
                    displays().later(30, hand::sink);
                    cancel();
                    return;
                }
                Combat.setVelocity(target, pull.multiply(Math.min(1.1, 0.35 + distance * 0.12) / distance).setY(0.02));
                hand.follow(target.getLocation(), 1);
                Location feet = target.getLocation();
                Fx.particle(feet.clone().add(0, 0.1, 0), Particle.SNOWFLAKE, 3, 0.2, 0.01);
                Fx.particle(feet.clone().add(0, 0.1, 0), Particle.BLOCK, 4, 0.2, 0.02, Material.PACKED_ICE.createBlockData());
                if (ticks % 3 == 0) {
                    Fx.sound(feet, Sound.BLOCK_GRAVEL_STEP, 1f, 0.6f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Five bone fingers and a palm, closing around a target and moving with it. */
    private final class Hand {
        private static final int FINGERS = 5;
        private final List<BlockDisplay> bones = new ArrayList<>();
        private final BlockData bone = Material.BONE_BLOCK.createBlockData();
        private Location at;

        Hand(Location feet) {
            at = Combat.ground(feet.clone().add(0, 0.5, 0));
            at.setYaw(0);
            at.setPitch(0);
            for (int i = 0; i <= FINGERS; i++) {
                BlockDisplay b = displays().block(at, bone, new Vector3f(0.01f), new Quaternionf(), 90);
                b.setGlowing(true);
                b.setGlowColorOverride(Color.fromRGB(PALE));
                b.setBrightness(new Display.Brightness(12, 12));
                b.setTeleportDuration(1);
                bones.add(b);
            }
        }

        /** Bursts up out of the ground, fingers spread. */
        void rise() {
            Displays.animateBlock(bones.get(FINGERS), new Vector3f(1.3f, 0.22f, 1.3f), new Quaternionf(), new Vector3f(0, 0.05f, 0), 3);
            for (int i = 0; i < FINGERS; i++) {
                Displays.animateBlock(bones.get(i), new Vector3f(0.2f, 1.6f, 0.2f), finger(i, 0), offset(i, 0), 3);
            }
            Fx.particle(at.clone().add(0, 0.2, 0), Particle.BLOCK, 30, 0.6, 0.1, Material.BONE_BLOCK.createBlockData());
        }

        /** Follows the target, the fingers curling in by {@code close} (0 open, 1 shut). */
        void follow(Location feet, float close) {
            at = feet.clone();
            at.setYaw(0);
            at.setPitch(0);
            for (int i = 0; i <= FINGERS; i++) {
                BlockDisplay b = bones.get(i);
                if (!b.isValid()) {
                    continue;
                }
                b.teleport(at);
                if (i < FINGERS) {
                    Displays.animateBlock(b, new Vector3f(0.2f, 1.6f, 0.2f), finger(i, close), offset(i, close), 1);
                }
            }
        }

        void sink() {
            for (BlockDisplay b : bones) {
                if (b.isValid()) {
                    Displays.animateBlock(b, new Vector3f(0.05f, 0.05f, 0.05f), new Quaternionf(), new Vector3f(0, -0.4f, 0), 6);
                }
            }
            Fx.particle(at.clone().add(0, 0.3, 0), Particle.SOUL, 8, 0.4, 0.03);
            displays().later(7, () -> bones.forEach(displays()::remove));
        }

        private static Quaternionf finger(int i, float close) {
            double a = i * Math.PI * 2 / FINGERS;
            // lean outward when open, inward over the target when closed
            float lean = (float) (0.35 - close * 0.85);
            return new Quaternionf().rotateY((float) -a).rotateZ(-lean);
        }

        private static Vector3f offset(int i, float close) {
            double a = i * Math.PI * 2 / FINGERS;
            double r = 0.75 - close * 0.15;
            return new Vector3f((float) (Math.cos(a) * r), 0.8f, (float) (Math.sin(a) * r));
        }
    }

    // ---- Runeforge ----

    /**
     * Runeforge: three runes are carved into the blade and circle you for ten seconds. Your next
     * three hits each spend one: Frost encases the target in ice, Blood drinks half the blow back
     * as health, Unholy tears the target's soul out in a burst that hurts everything around it.
     */
    @Override
    public boolean secondary(Player player) {
        clearRunes(player);
        Forged f = new Forged();
        f.until = Bukkit.getCurrentTick() + RUNE_TICKS;
        forged.put(player.getUniqueId(), f);
        Location at = player.getLocation().add(0, 1, 0);
        for (Rune r : Rune.values()) {
            f.sigils.put(r, fx().sigil(at, new Vector(0, 0, 1), r.color, 0.7f, RUNE_TICKS + 10));
        }
        fx().castCircle(player.getLocation(), FROST, 2.2f);
        fx().flash(at, PALE, 2.4f);
        Fx.sound(at, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.2f, 0.6f);
        Fx.sound(at, Sound.BLOCK_ANVIL_USE, 0.6f, 0.6f);
        Fx.sound(at, Sound.ENTITY_WITHER_AMBIENT, 0.4f, 1.6f);

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                Forged now = forged.get(player.getUniqueId());
                if (now != f || !alive(player) || f.until < Bukkit.getCurrentTick()) {
                    if (forged.get(player.getUniqueId()) == f) {
                        clearRunes(player);
                    } else {
                        f.sigils.values().forEach(displays()::remove);
                    }
                    cancel();
                    return;
                }
                int n = f.sigils.size();
                int k = 0;
                for (Map.Entry<Rune, ItemDisplay> e : f.sigils.entrySet()) {
                    double a = t * 0.12 + k++ * Math.PI * 2 / Math.max(1, n);
                    Vector out = new Vector(Math.cos(a), 0, Math.sin(a));
                    Location spot = player.getLocation().add(out.clone().multiply(1.15)).add(0, 1.1 + Math.sin(t * 0.1 + k) * 0.12, 0);
                    spot.setDirection(out);
                    if (e.getValue().isValid()) {
                        e.getValue().teleport(spot);
                    }
                    if (t % 6 == 0) {
                        Fx.particle(spot, Particle.DUST, 1, 0.05, 0, Fx.dust(e.getKey().color, 0.8f));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Spends one rune on a hit. */
    private void unleash(Player player, LivingEntity target, EntityDamageByEntityEvent event, Forged f, Rune rune) {
        ItemDisplay sigil = f.sigils.remove(rune);
        Location at = Combat.center(target);
        if (sigil != null) {
            fx().beam(sigil.getLocation(), at, rune.color, 0.25f, 5);
            displays().remove(sigil);
        }
        fx().burst(at, rune.color, 2f);
        switch (rune) {
            case FROST -> {
                event.setDamage(event.getDamage() + 3);
                hold(target, 30);
                target.setFreezeTicks(Math.max(target.getFreezeTicks(), 140));
                Location base = target.getLocation();
                base.setYaw(0);
                base.setPitch(0);
                float w = (float) Math.max(1.0, target.getWidth() + 0.5);
                float h = (float) target.getHeight() + 0.3f;
                BlockDisplay ice = displays().block(base.clone().add(0, h / 2, 0), Material.ICE.createBlockData(),
                        new Vector3f(w, h, w), new Quaternionf(), 32);
                ice.setBrightness(new Display.Brightness(12, 15));
                displays().later(30, () -> {
                    Fx.particle(base.clone().add(0, 1, 0), Particle.BLOCK, 30, 0.5, 0.1, Material.ICE.createBlockData());
                    Fx.sound(base, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
                });
                Fx.sound(at, Sound.BLOCK_GLASS_PLACE, 1.2f, 0.6f);
                Fx.sound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
            }
            case BLOOD -> {
                double drink = Math.max(2, event.getDamage() * 0.5);
                combat().heal(player, drink);
                fx().drift(at, player.getLocation().add(0, 1, 0).toVector().subtract(at.toVector()), BLOOD, 0.7f, 8);
                Fx.particle(at, Particle.DAMAGE_INDICATOR, 6, 0.3, 0.1);
                Fx.particle(player.getLocation().add(0, 1, 0), Particle.DUST, 15, 0.4, 0, Fx.dust(BLOOD, 1.3f));
                Fx.sound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 1.3f);
            }
            case UNHOLY -> {
                // the soul torn out: a skull bursts from the target and everything around is hit
                fx().spectralHead(at.clone().add(0, 0.4, 0), Material.SKELETON_SKULL, UNHOLY, 1.1f,
                        player.getLocation().toVector().subtract(at.toVector()).setY(0), 20);
                fx().drift(at, new Vector(0, 2.4, 0), UNHOLY, 0.8f, 16);
                fx().shockwave(target.getLocation().add(0, 0.05, 0), UNHOLY, 3.2f, 7);
                Fx.particle(at, Particle.SOUL, 20, 0.5, 0.06);
                Fx.particle(at, Particle.DUST, 25, 0.6, 0, Fx.dust(UNHOLY, 1.3f));
                for (LivingEntity near : combat().nearby(player, target.getLocation(), 3.2)) {
                    combat().damage(player, near, 5, true);
                    applyEffect(near, PotionEffectType.WITHER, 60, 0);
                }
                Fx.sound(at, Sound.PARTICLE_SOUL_ESCAPE, 2f, 0.6f);
                Fx.sound(at, Sound.ENTITY_WITHER_HURT, 0.8f, 1.2f);
            }
        }
        if (f.left.isEmpty()) {
            tell(player, net.kyori.adventure.text.Component.text("The runes are spent.", net.kyori.adventure.text.format.TextColor.color(PALE)));
        }
    }

    private void clearRunes(Player player) {
        Forged f = forged.remove(player.getUniqueId());
        if (f != null) {
            for (ItemDisplay d : f.sigils.values()) {
                Fx.particle(d.getLocation(), Particle.DUST, 6, 0.1, 0, Fx.dust(PALE, 0.8f));
                displays().remove(d);
            }
            f.sigils.clear();
        }
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(forged.keySet())) {
            Forged f = forged.remove(id);
            if (f != null) {
                f.sigils.values().forEach(displays()::remove);
            }
        }
    }
}
