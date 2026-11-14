package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emerald Sword: sustain and support.
 * Passive Verdant Life (+ double XP), on-hit Thorned Edge, RC Nature's Grasp, Sneak+RC Verdant Bloom.
 */
public final class VerdantSword extends SwordAbility implements Listener {

    private static final int ROOT_TICKS = 50;

    private final NamespacedKey rootKey;
    /** Rooted entity -> server tick the root ends. */
    private final Map<UUID, Integer> rooted = new HashMap<>();
    private final BukkitTask rootTask;

    public VerdantSword(CustomsPlugin plugin) {
        super(plugin, SwordType.VERDANT);
        this.rootKey = new NamespacedKey(plugin, "emerald_root");
        this.rootTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRoots, 5L, 5L);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.REGENERATION, 0, 0));
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && plugin.items().heldType(killer) == type && plugin.canUse(killer, type)) {
            event.setDroppedExp(event.getDroppedExp() * 2);
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.25)) {
            return;
        }
        applyEffect(target, PotionEffectType.POISON, 60, 1);
        Location at = Combat.center(target);
        Fx.particle(at, Particle.HAPPY_VILLAGER, 12, 0.4, 0);
        Fx.particle(at, Particle.ITEM_SLIME, 8, 0.3, 0.05);
        Fx.sound(at, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1f, 0.8f);
    }

    /** Nature's Grasp: damage and root every enemy within 7 blocks. */
    @Override
    public boolean primary(Player player) {
        Location center = player.getLocation();
        List<LivingEntity> targets = combat().nearby(player, center, 7);
        if (targets.isEmpty()) {
            tell(player, "No enemies nearby.");
            return false;
        }
        for (double r = 1; r <= 7; r += 1.5) {
            Fx.ring(center.clone().add(0, 0.15, 0), r, (int) (r * 8), p -> {
                Fx.particle(p, Particle.HAPPY_VILLAGER, 1, 0.1, 0);
                Fx.particle(p, Particle.BLOCK, 1, 0.1, 0, Material.MOSS_BLOCK.createBlockData());
            });
        }
        Fx.sound(center, Sound.BLOCK_ROOTS_PLACE, 1.5f, 0.6f);
        Fx.sound(center, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 1.2f);
        fx().shockwave(center, 0x5BD16A, 7f, 10);
        fx().shockwave(center, 0xB8F07A, 4.5f, 7);
        Location hand = dev.infuse.customs.ability.Fx3D.handOrigin(player);
        for (LivingEntity target : targets) {
            combat().damage(player, target, 4);
            root(target);
            // a vine lashes out to each enemy, bursting into leaves when it wraps them
            fx().tendril(hand, () -> target.isValid() ? Combat.center(target) : null, 0x6FD45A, 1.0f, 9, () -> {
                Fx.particle(Combat.center(target), Particle.BLOCK, 12, 0.3, 0.1, Material.AZALEA_LEAVES.createBlockData());
                fx().burst(Combat.center(target), 0xB8F07A, 1.2f);
            });
        }
        return true;
    }

    private void root(LivingEntity target) {
        rooted.put(target.getUniqueId(), Bukkit.getCurrentTick() + ROOT_TICKS);
        growRoots(target);
        addRootModifier(target, Attribute.MOVEMENT_SPEED);
        addRootModifier(target, Attribute.JUMP_STRENGTH);
        Vector v = target.getVelocity();
        Combat.setVelocity(target, new Vector(0, Math.min(0, v.getY()), 0));
    }

    /** Mangrove roots curl up out of the ground around the target's feet, then sink back. */
    private void growRoots(LivingEntity target) {
        Location feet = target.getLocation();
        float reach = (float) target.getWidth() * 0.5f + 0.25f;
        for (int i = 0; i < 5; i++) {
            double a = i * 2 * Math.PI / 5;
            Location at = feet.clone().add(Math.cos(a) * reach, 0.35, Math.sin(a) * reach);
            at.setYaw(0);
            at.setPitch(0);
            // lean each root in towards the target
            Quaternionf lean = new Quaternionf().rotateY((float) -a).rotateZ((float) Math.toRadians(22));
            Vector3f full = new Vector3f(0.5f, 0.95f, 0.5f);
            BlockDisplay root = displays().block(at, (i % 2 == 0 ? Material.MANGROVE_ROOTS : Material.MUDDY_MANGROVE_ROOTS)
                    .createBlockData(), new Vector3f(0.5f, 0.05f, 0.5f), lean, ROOT_TICKS + 8);
            displays().later(1 + i, () -> Displays.animateBlock(root, full, lean, new Vector3f(), 4));
            displays().later(ROOT_TICKS, () -> Displays.animateBlock(root, new Vector3f(0.5f, 0.05f, 0.5f), lean,
                    new Vector3f(0, -0.3f, 0), 6));
        }
    }

    private void addRootModifier(LivingEntity target, Attribute attribute) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null && instance.getModifier(rootKey) == null) {
            // Transient: never saved to disk, so a crash can't leave anything stuck.
            instance.addTransientModifier(new AttributeModifier(rootKey, -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }

    private void unroot(LivingEntity target) {
        for (Attribute attribute : new Attribute[]{Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance != null && instance.getModifier(rootKey) != null) {
                instance.removeModifier(rootKey);
            }
        }
    }

    private void tickRoots() {
        if (rooted.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (UUID id : new ArrayList<>(rooted.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity living)) {
                rooted.remove(id);
                continue;
            }
            if (living.isDead() || rooted.get(id) <= now) {
                unroot(living);
                rooted.remove(id);
                continue;
            }
            Location feet = living.getLocation().add(0, 0.1, 0);
            Fx.ring(feet, 0.6, 6, p -> Fx.particle(p, Particle.BLOCK, 1, 0.05, 0, Material.OAK_LEAVES.createBlockData()));
            Fx.particle(feet.add(0, 0.4, 0), Particle.HAPPY_VILLAGER, 2, 0.3, 0);
        }
    }

    /** Verdant Bloom: heal, absorption and cleanse for you and your scoreboard teammates. */
    @Override
    public boolean secondary(Player player) {
        bloom(player, 10);
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
        if (team != null) {
            for (Player other : player.getLocation().getNearbyPlayers(8)) {
                if (other != player && team.hasEntry(other.getName())) {
                    bloom(other, 6);
                }
            }
        }
        Location at = player.getLocation().add(0, 1, 0);
        Fx.particle(at, Particle.TOTEM_OF_UNDYING, 60, 0.6, 0.4);
        Fx.ring(player.getLocation().add(0, 0.2, 0), 1.5, 24, p -> Fx.particle(p, Particle.HAPPY_VILLAGER, 1, 0, 0));
        Fx.sound(at, Sound.ITEM_TOTEM_USE, 0.5f, 1.5f);
        Fx.sound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
        fx().flash(at, 0xB8F07A, 3f);
        // a ring of azalea bushes blooms around you, then sinks back into the ground
        Location ground = player.getLocation();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Location spot = ground.clone().add(Math.cos(a) * 1.9, 0.35, Math.sin(a) * 1.9);
            spot.setYaw(0);
            spot.setPitch(0);
            org.bukkit.block.data.BlockData bush = (i % 2 == 0 ? Material.FLOWERING_AZALEA : Material.AZALEA).createBlockData();
            org.joml.Quaternionf turn = new org.joml.Quaternionf().rotateY((float) a);
            org.bukkit.entity.BlockDisplay d = displays().block(spot, bush, new org.joml.Vector3f(0.05f), turn, 50);
            int delay = i;
            displays().later(1 + delay, () -> dev.infuse.customs.ability.Displays.animateBlock(d, new org.joml.Vector3f(0.7f), turn,
                    new org.joml.Vector3f(), 6));
            displays().later(34, () -> dev.infuse.customs.ability.Displays.animateBlock(d, new org.joml.Vector3f(0.05f), turn,
                    new org.joml.Vector3f(0, -0.3f, 0), 10));
        }
        fx().burst(at, 0xFFF3A0, 2.2f);
        fx().shockwave(player.getLocation(), 0x5BD16A, 3.5f, 8);
        return true;
    }

    private void bloom(Player target, double heal) {
        combat().heal(target, heal);
        applyEffect(target, PotionEffectType.ABSORPTION, 300, 1);
        for (PotionEffect active : target.getActivePotionEffects()) {
            if (active.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                target.removePotionEffect(active.getType());
            }
        }
        target.setFireTicks(0);
        target.setFreezeTicks(0);
        status().clearBleed(target);
        Fx.particle(target.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 20, 0.5, 0);
    }

    @Override
    public void shutdown() {
        rootTask.cancel();
        for (UUID id : rooted.keySet()) {
            if (Bukkit.getEntity(id) instanceof LivingEntity living) {
                unroot(living);
            }
        }
        rooted.clear();
    }
}
