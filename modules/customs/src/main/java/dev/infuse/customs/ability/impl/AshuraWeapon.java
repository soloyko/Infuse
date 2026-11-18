package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ashura's Blade, the Six-Armed Blade: a war god's fury, and a war god's size.
 * Passive Wrath, on-hit Echo Arm, F Thousand Hands, Sneak+F Wrathful Form.
 */
public final class AshuraWeapon extends SwordAbility implements Listener {

    private static final int RED = 0xE5482C;
    private static final int GOLD = 0xFFC94A;
    private static final int HANDS_TICKS = 160;
    private static final int FORM_TICKS = 160;
    private static final double ARM_DAMAGE = 3.5;

    /** Player -> the spectral arms behind them. */
    private final Map<UUID, List<ItemDisplay>> arms = new HashMap<>();
    /** Players in Wrathful Form. */
    private final Map<UUID, Integer> giants = new HashMap<>();
    private final NamespacedKey scaleKey;
    private final NamespacedKey reachKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey knockbackKey;

    public AshuraWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.ASHURA);
        scaleKey = new NamespacedKey(plugin, "wrathful_form_scale");
        reachKey = new NamespacedKey(plugin, "wrathful_form_reach");
        damageKey = new NamespacedKey(plugin, "wrathful_form_damage");
        knockbackKey = new NamespacedKey(plugin, "wrathful_form_knockback");
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        // Wrath: the more enemies around you, the harder you hit
        int around = Math.min(5, combat().nearby(player, player.getLocation(), 5).size());
        event.setDamage(event.getDamage() + around);
        Location at = Combat.center(target);
        if (chance(0.25)) {
            double extra = event.getDamage() * 0.5;
            Vector look = player.getEyeLocation().getDirection();
            displays().later(4, () -> {
                if (!target.isValid() || target.isDead() || !alive(player)) {
                    return;
                }
                fx().afterimage(Combat.center(target).subtract(look.clone().multiply(0.8)), plugin.items().create(type), look, GOLD, 1.0f, 6);
                combat().damage(player, target, extra, true);
                fx().cut(Combat.center(target), look, Math.PI - 0.5, 0.3, GOLD, 1.5f, 2);
                Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.8f);
            });
        }
        List<ItemDisplay> mine = arms.get(player.getUniqueId());
        if (mine != null) {
            armsStrike(player, target, mine);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shrink(event.getPlayer());
        List<ItemDisplay> mine = arms.remove(event.getPlayer().getUniqueId());
        if (mine != null) {
            mine.forEach(a -> displays().remove(a));
        }
    }

    // ---- Thousand Hands ----

    /** Where each of the four arms floats, relative to you: x right, y up, z back. */
    private static final double[][] ARM_SPOTS = {{0.9, 1.7, 0.5}, {-0.9, 1.7, 0.5}, {1.2, 1.0, 0.4}, {-1.2, 1.0, 0.4}};

    /**
     * Thousand Hands: four spectral arms, each gripping a copy of the blade, rise behind your
     * shoulders for eight seconds. Every time you land a hit, each arm lashes out at an enemy near
     * you (the one you hit if there's no one else).
     */
    @Override
    public boolean primary(Player player) {
        UUID id = player.getUniqueId();
        if (arms.containsKey(id)) {
            return false;
        }
        List<ItemDisplay> mine = new ArrayList<>();
        for (int i = 0; i < ARM_SPOTS.length; i++) {
            ItemDisplay arm = displays().item(armSpot(player, i), plugin.items().create(type), 0.05f, new Quaternionf(),
                    ItemDisplay.ItemDisplayTransform.NONE, HANDS_TICKS + 20);
            arm.setTeleportDuration(1);
            arm.setGlowing(true);
            arm.setGlowColorOverride(org.bukkit.Color.fromRGB(RED));
            arm.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            mine.add(arm);
        }
        arms.put(id, mine);
        Location at = player.getLocation().add(0, 1.2, 0);
        fx().flash(at, GOLD, 2.6f);
        Fx.sound(at, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.2f, 0.8f);
        Fx.sound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 0.7f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > HANDS_TICKS || arms.get(id) != mine) {
                    cancel();
                    arms.remove(id, mine);
                    for (ItemDisplay arm : mine) {
                        if (arm.isValid()) {
                            fx().flash(arm.getLocation(), RED, 1f);
                        }
                        displays().remove(arm);
                    }
                    return;
                }
                float yaw = (float) Math.toRadians(-player.getLocation().getYaw());
                for (int i = 0; i < mine.size(); i++) {
                    ItemDisplay arm = mine.get(i);
                    if (!arm.isValid()) {
                        continue;
                    }
                    arm.teleport(armSpot(player, i));
                    if (ticks == 2 || ticks % 10 == 0) {
                        // blades raised, fanned out like a halo of arms
                        Quaternionf pose = new Quaternionf().rotateY(yaw).rotateZ((float) ((i % 2 == 0 ? -1 : 1) * (0.4 + i / 2 * 0.5)));
                        Displays.animate(arm, new Transformation(new Vector3f(), pose, new Vector3f(1.3f), new Quaternionf()), 6);
                    }
                }
                if (ticks % 5 == 0) {
                    Fx.particle(player.getLocation().add(0, 1.4, 0), Particle.DUST, 3, 0.6, 0, Fx.dust(ticks % 10 == 0 ? RED : GOLD, 1.0f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static Location armSpot(Player player, int i) {
        double[] s = ARM_SPOTS[i];
        float yaw = (float) Math.toRadians(-player.getLocation().getYaw());
        Vector3f off = new Quaternionf().rotateY(yaw).transform(new Vector3f((float) -s[0], (float) s[1], (float) -s[2]));
        Location at = player.getLocation().add(off.x, off.y, off.z);
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    /** Each arm cuts at an enemy near you. */
    private void armsStrike(Player player, LivingEntity hit, List<ItemDisplay> mine) {
        List<LivingEntity> near = combat().nearby(player, player.getLocation(), 5);
        if (near.isEmpty()) {
            near = List.of(hit);
        }
        float yaw = (float) Math.toRadians(-player.getLocation().getYaw());
        for (int i = 0; i < mine.size(); i++) {
            ItemDisplay arm = mine.get(i);
            LivingEntity target = near.get(i % near.size());
            if (!arm.isValid() || !target.isValid()) {
                continue;
            }
            int n = i;
            Location at = Combat.center(target);
            // the arm swings down towards its target, then back up
            Vector to = at.toVector().subtract(arm.getLocation().toVector());
            Quaternionf swing = Displays.pointing(to.lengthSquared() < 1.0E-4 ? new Vector(0, -1, 0) : to);
            Displays.animate(arm, new Transformation(new Vector3f(), swing, new Vector3f(1.5f), new Quaternionf()), 2);
            displays().later(2 + n, () -> {
                if (!alive(player) || !target.isValid() || target.isDead()) {
                    return;
                }
                combat().damage(player, target, ARM_DAMAGE, true);
                fx().cut(Combat.center(target), to, n * 0.8, 0.3, n % 2 == 0 ? RED : GOLD, 1.4f, 2);
                Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.0f + n * 0.15f);
            });
            displays().later(6 + n, () -> {
                if (arm.isValid()) {
                    Quaternionf pose = new Quaternionf().rotateY(yaw).rotateZ((float) ((n % 2 == 0 ? -1 : 1) * (0.4 + n / 2 * 0.5)));
                    Displays.animate(arm, new Transformation(new Vector3f(), pose, new Vector3f(1.3f), new Quaternionf()), 4);
                }
            });
        }
    }

    // ---- Wrathful Form ----

    /**
     * Wrathful Form: you swell to twice your size for eight seconds. You reach further, hit five
     * harder, can't be knocked back, and every step you take shakes the ground under the enemies
     * around you.
     */
    @Override
    public boolean secondary(Player player) {
        UUID id = player.getUniqueId();
        if (giants.containsKey(id)) {
            return false;
        }
        giants.put(id, Bukkit.getCurrentTick());
        modify(player, Attribute.SCALE, scaleKey, 1.0);
        modify(player, Attribute.ENTITY_INTERACTION_RANGE, reachKey, 2.0);
        modify(player, Attribute.ATTACK_DAMAGE, damageKey, 5.0);
        modify(player, Attribute.KNOCKBACK_RESISTANCE, knockbackKey, 1.0);
        Location at = player.getLocation();
        fx().flash(at.clone().add(0, 1.5, 0), RED, 3.2f);
        fx().burst(at.clone().add(0, 1.5, 0), GOLD, 3f);
        Fx.sound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
        Fx.sound(at, Sound.ENTITY_WARDEN_EMERGE, 0.6f, 1.4f);

        new BukkitRunnable() {
            int ticks;
            Location lastStep = player.getLocation();

            @Override
            public void run() {
                ticks++;
                if (!player.isOnline() || player.isDead() || ticks > FORM_TICKS || !giants.containsKey(id)) {
                    shrink(player);
                    cancel();
                    return;
                }
                Location feet = player.getLocation();
                if (ticks % 3 == 0) {
                    Fx.particle(feet.clone().add(0, 2, 0), Particle.DUST, 3, 0.8, 0, Fx.dust(ticks % 6 == 0 ? RED : GOLD, 1.4f));
                }
                // a heavy footfall every couple of blocks you walk
                boolean grounded = !feet.clone().subtract(0, 0.1, 0).getBlock().isPassable();
                if (grounded && feet.distanceSquared(lastStep) > 4) {
                    lastStep = feet;
                    Fx.sound(feet, Sound.ENTITY_RAVAGER_STEP, 1.2f, 0.6f);
                    Fx.particle(feet.clone().add(0, 0.1, 0), Particle.BLOCK, 16, 0.8, 0.1, feet.clone().subtract(0, 0.5, 0).getBlock().getBlockData());
                    fx().crack(feet, RED, 1.8f, 16);
                    for (LivingEntity target : combat().nearby(player, feet, 3)) {
                        combat().damage(player, target, 2, true);
                        Combat.setVelocity(target, target.getVelocity().setY(0.35));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static void modify(Player player, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(key);
            instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void shrink(Player player) {
        if (giants.remove(player.getUniqueId()) == null) {
            return;
        }
        for (Attribute attribute : new Attribute[]{Attribute.SCALE, Attribute.ENTITY_INTERACTION_RANGE, Attribute.ATTACK_DAMAGE,
                Attribute.KNOCKBACK_RESISTANCE}) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                for (NamespacedKey key : new NamespacedKey[]{scaleKey, reachKey, damageKey, knockbackKey}) {
                    instance.removeModifier(key);
                }
            }
        }
        if (player.isOnline()) {
            Fx.particle(player.getLocation().add(0, 1, 0), Particle.CLOUD, 20, 0.6, 0.05);
            Fx.sound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1.2f, 0.6f);
        }
    }

    @Override
    public void shutdown() {
        for (UUID id : new ArrayList<>(giants.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                shrink(player);
            }
        }
        giants.clear();
        for (List<ItemDisplay> mine : arms.values()) {
            mine.forEach(a -> displays().remove(a));
        }
        arms.clear();
    }
}
