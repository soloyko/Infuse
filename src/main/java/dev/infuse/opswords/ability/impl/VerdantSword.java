package dev.infuse.opswords.ability.impl;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.ability.Fx;
import dev.infuse.opswords.ability.SwordAbility;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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

    public VerdantSword(OPSwordsPlugin plugin) {
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
        for (LivingEntity target : targets) {
            combat().damage(player, target, 4);
            root(target);
        }
        return true;
    }

    private void root(LivingEntity target) {
        rooted.put(target.getUniqueId(), Bukkit.getCurrentTick() + ROOT_TICKS);
        addRootModifier(target, Attribute.MOVEMENT_SPEED);
        addRootModifier(target, Attribute.JUMP_STRENGTH);
        Vector v = target.getVelocity();
        Combat.setVelocity(target, new Vector(0, Math.min(0, v.getY()), 0));
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
