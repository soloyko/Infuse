package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demonic Blade, the Blade of the Pact: every edge a broken promise, and it still collects.
 * Passive Infernal Heart, on-hit Hellscorch, F Devil's Bargain, Sneak+F Abyssal Maw.
 */
public final class DemonicWeapon extends SwordAbility {

    private static final int RED = 0xB01020;
    private static final int EMBER = 0xFF4A1A;
    private static final int DARK = 0x14040A;

    /** Players who paid the price: their next hit deals +12. */
    private final Map<UUID, Integer> paid = new HashMap<>();

    public DemonicWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.DEMONIC);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.FIRE_RESISTANCE, 0, 0));
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && player.getHealth() < max.getValue() / 2) {
            event.setDamage(event.getDamage() * 1.2);
        }
        Location at = Combat.center(target);
        if (paid.remove(player.getUniqueId()) != null) {
            event.setDamage(event.getDamage() + 12);
            fx().impact(at, RED, 2.6f);
            fx().crack(Combat.ground(target.getLocation().add(0, 0.5, 0)), EMBER, 2.4f, 40);
            Fx.particle(at, Particle.LAVA, 12, 0.4, 0.1);
            Fx.sound(at, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.8f, 0.8f);
            tell(player, Component.text("The debt is collected.", TextColor.color(RED)));
        }
        if (chance(0.2)) {
            event.setDamage(event.getDamage() + 3);
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            fx().burst(at, EMBER, 1.6f);
            Fx.particle(at, Particle.SOUL_FIRE_FLAME, 12, 0.3, 0.05);
            Fx.particle(at, Particle.LARGE_SMOKE, 8, 0.3, 0.02);
            Fx.sound(at, Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        }
    }

    /** The five fates the devil's dice can show. */
    private enum Fate {
        FEAST("FEAST", "the devil heals you", 0x5BE35B),
        FURY("FURY", "strength of the pit", 0xFF4A1A),
        HELLFIRE("HELLFIRE", "the ground burns", 0xFF7A1A),
        TITHE("TITHE", "the devil takes from them", 0xB01020),
        PRICE("THE PRICE", "your next hit is paid for in blood", 0x6A0010);

        final String title;
        final String text;
        final int color;

        Fate(String title, String text, int color) {
            this.title = title;
            this.text = text;
            this.color = color;
        }
    }

    /**
     * Devil's Bargain: a demon's skull spins over your hand and settles on one of five fates —
     * Feast (a full heal), Fury (Strength II for 8 seconds), Hellfire (a burning nova around
     * you), Tithe (the enemy you aim at, or the nearest, loses 6 health to you) or the Price (lose
     * 3 hearts, and your next hit deals +12).
     */
    @Override
    public boolean primary(Player player) {
        Location hand = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.2)).subtract(0, 0.2, 0);
        org.bukkit.entity.ItemDisplay skull = fx().spectralHead(hand, Material.WITHER_SKELETON_SKULL, RED, 0.9f,
                player.getLocation().toVector().subtract(hand.toVector()).setY(0), 26);
        skull.setTeleportDuration(1);
        Fx.sound(hand, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
        Fx.sound(hand, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.4f);
        Fate[] fates = Fate.values();
        Fate fate = fates[ThreadLocalRandom.current().nextInt(fates.length)];
        // the dice: the skull spins, each turn flashing a fate, slowing down onto the real one
        int[] beats = {0, 2, 4, 7, 10, 14, 19};
        for (int i = 0; i < beats.length; i++) {
            int step = i;
            displays().later(beats[i], () -> {
                if (!alive(player)) {
                    return;
                }
                Fate shown = step == beats.length - 1 ? fate : fates[(step * 3 + fate.ordinal() + 1) % fates.length];
                player.sendActionBar(Component.text("☠ " + shown.title + " ☠", TextColor.color(shown.color)).decorate(TextDecoration.BOLD));
                Location at = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(1.2)).subtract(0, 0.2, 0);
                if (skull.isValid()) {
                    Location turn = at.clone();
                    turn.setYaw(at.getYaw() + step * 70f);
                    turn.setPitch(0);
                    skull.teleport(turn);
                }
                Fx.particle(at, Particle.DUST, 6, 0.15, 0, Fx.dust(shown.color, 1f));
                Fx.sound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f + step * 0.1f);
            });
        }
        displays().later(beats[beats.length - 1] + 2, () -> {
            displays().remove(skull);
            if (alive(player)) {
                resolve(player, fate);
            }
        });
        return true;
    }

    private void resolve(Player player, Fate fate) {
        Location at = player.getLocation().add(0, 1, 0);
        player.showTitle(Title.title(Component.text("☠ " + fate.title + " ☠", TextColor.color(fate.color)).decorate(TextDecoration.BOLD),
                Component.text(fate.text, NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1400), Duration.ofMillis(500))));
        fx().flash(at, fate.color, 2.6f);
        Fx.sound(at, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 0.8f, 0.6f);
        switch (fate) {
            case FEAST -> {
                AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    player.setHealth(max.getValue());
                }
                fx().helix(player.getLocation(), 0.8, 2.4, 2, Particle.DUST, Fx.dust(fate.color, 1.1f), 20);
                Fx.particle(at, Particle.HEART, 6, 0.5, 0);
                Fx.sound(at, Sound.ENTITY_PLAYER_BURP, 1f, 0.6f);
            }
            case FURY -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 1, false, false, true));
                fx().castCircle(player.getLocation(), EMBER, 2f);
                Fx.particle(at, Particle.ANGRY_VILLAGER, 6, 0.5, 0.1);
                Fx.sound(at, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
            }
            case HELLFIRE -> {
                Location ground = player.getLocation();
                fx().shockwave(ground.clone().add(0, 0.05, 0), EMBER, 6f, 10);
                fx().crack(ground, EMBER, 3f, 50);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    fx().flamePillar(Combat.ground(ground.clone().add(Math.cos(a) * 3, 1, Math.sin(a) * 3)), 0.7f, 2.4f, 16, false);
                }
                for (LivingEntity e : combat().nearby(player, ground, 6)) {
                    combat().damage(player, e, 6, true);
                    e.setFireTicks(Math.max(e.getFireTicks(), 100));
                    Combat.knockback(e, ground, 0.8, 0.4);
                }
                Fx.sound(at, Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.5f);
                Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
            }
            case TITHE -> {
                LivingEntity victim = combat().lookTarget(player, 24, 0.8);
                if (victim == null) {
                    List<LivingEntity> near = combat().nearby(player, player.getLocation(), 12);
                    victim = near.isEmpty() ? null : near.get(0);
                }
                if (victim == null) {
                    // nobody to take it from: the devil takes it from you instead
                    player.damage(2);
                    Fx.sound(at, Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.6f);
                    return;
                }
                Location from = Combat.center(victim);
                double before = victim.getHealth();
                combat().damage(player, victim, 6, true);
                double taken = Math.max(0, before - victim.getHealth());
                combat().heal(player, Math.max(2, taken));
                fx().beam(from, at, fate.color, 0.3f, 10);
                fx().drift(from, at.toVector().subtract(from.toVector()), fate.color, 0.8f, 10);
                Fx.particle(from, Particle.DAMAGE_INDICATOR, 8, 0.3, 0.1);
                Fx.sound(from, Sound.ENTITY_WARDEN_HEARTBEAT, 1.4f, 0.8f);
            }
            case PRICE -> {
                player.setHealth(Math.max(1, player.getHealth() - 6));
                paid.put(player.getUniqueId(), 1);
                player.playHurtAnimation(0);
                Fx.particle(at, Particle.DUST, 25, 0.4, 0, Fx.dust(fate.color, 1.4f));
                Fx.sound(at, Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
                Fx.sound(at, Sound.BLOCK_CHAIN_BREAK, 1f, 0.6f);
            }
        }
    }

    /**
     * Abyssal Maw: a giant jaw of jagged teeth bursts out of the ground under the enemy you aim at
     * and bites shut, holding it for a moment; enemies beside it are bitten too.
     */
    @Override
    public boolean secondary(Player player) {
        LivingEntity target = combat().lookTarget(player, 22, 0.8);
        Location ground;
        if (target != null) {
            ground = target.getLocation();
        } else {
            Location aim = combat().aimPoint(player, 22);
            if (aim == null) {
                tell(player, "Aim at an enemy or the ground to open the maw.");
                return false;
            }
            ground = aim;
        }
        Location base = Combat.ground(ground.clone().add(0, 0.5, 0));
        base.setYaw(0);
        base.setPitch(0);
        Vector facing = player.getLocation().toVector().subtract(base.toVector()).setY(0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(0, 0, 1);
        }
        float yaw = (float) Math.atan2(-facing.getX(), facing.getZ());
        BlockData tooth = Material.BONE_BLOCK.createBlockData();
        BlockData gum = Material.NETHER_WART_BLOCK.createBlockData();
        List<BlockDisplay> jaw = new ArrayList<>();
        // two arcs of teeth (upper and lower jaw) that rise open, then snap shut over the target
        int teeth = 7;
        for (int side = -1; side <= 1; side += 2) {
            BlockDisplay g = displays().block(base, gum, new Vector3f(0.05f), new Quaternionf(), 40);
            g.setBrightness(new Display.Brightness(10, 12));
            jaw.add(g);
            for (int i = 0; i < teeth; i++) {
                BlockDisplay t = displays().block(base, tooth, new Vector3f(0.05f), new Quaternionf(), 40);
                t.setGlowing(true);
                t.setGlowColorOverride(org.bukkit.Color.fromRGB(RED));
                jaw.add(t);
            }
        }
        fx().crack(base, RED, 2.6f, 40);
        fx().castCircle(base, DARK, 2.2f);
        Fx.particle(base.clone().add(0, 0.3, 0), Particle.LARGE_SMOKE, 20, 1.2, 0.02);
        Fx.sound(base, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.5f);
        Fx.sound(base, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        pose(jaw, teeth, yaw, 1f, 3);
        displays().later(7, () -> {
            pose(jaw, teeth, yaw, 0f, 2);
            Fx.sound(base, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.6f, 0.5f);
            Fx.sound(base, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.5f);
            fx().burst(base.clone().add(0, 1, 0), RED, 2.6f);
            Fx.particle(base.clone().add(0, 1, 0), Particle.DAMAGE_INDICATOR, 12, 0.6, 0.1);
            if (!alive(player)) {
                return;
            }
            for (LivingEntity e : combat().nearby(player, base, 2.6)) {
                boolean main = e == target || target == null && e.getLocation().distanceSquared(base) < 1.5;
                combat().damage(player, e, main ? 10 : 5, true);
                hold(e, main ? 24 : 10);
                status().bleed(player, e, 3);
            }
        });
        displays().later(30, () -> {
            for (BlockDisplay b : jaw) {
                if (b.isValid()) {
                    Displays.animateBlock(b, new Vector3f(0.05f), new Quaternionf(), new Vector3f(0, -0.6f, 0), 8);
                }
            }
            Fx.particle(base.clone().add(0, 0.3, 0), Particle.BLOCK, 20, 0.8, 0.1, Material.NETHERRACK.createBlockData());
        });
        displays().later(39, () -> jaw.forEach(displays()::remove));
        return true;
    }

    /** Opens the jaw by {@code open} (1 wide open, 0 shut), facing {@code yaw}. */
    private static void pose(List<BlockDisplay> jaw, int teeth, float yaw, float open, int ticks) {
        int k = 0;
        for (int side = -1; side <= 1; side += 2) {
            // each half hinges at the ground and swings up; open, they lean apart; shut, they meet
            float tilt = side * (0.15f + open * 0.95f);
            Quaternionf hinge = new Quaternionf().rotateY(yaw).rotateX(tilt);
            BlockDisplay gum = jaw.get(k++);
            Displays.animateBlock(gum, new Vector3f(2.6f, 0.35f, 0.5f), hinge, hinge.transform(new Vector3f(0, 0.2f, side * 0.95f)), ticks);
            for (int i = 0; i < teeth; i++) {
                float x = -1.1f + i * (2.2f / (teeth - 1));
                float h = 0.9f + (i % 2 == 0 ? 0.35f : 0f) - Math.abs(x) * 0.2f;
                Vector3f offset = hinge.transform(new Vector3f(x, 0.35f + h / 2, side * 0.95f - side * 0.18f));
                Quaternionf point = new Quaternionf(hinge).rotateY((float) Math.PI / 4);
                Displays.animateBlock(jaw.get(k++), new Vector3f(0.16f, h, 0.16f), point, offset, ticks);
            }
        }
    }

    @Override
    public void shutdown() {
        paid.clear();
    }
}
