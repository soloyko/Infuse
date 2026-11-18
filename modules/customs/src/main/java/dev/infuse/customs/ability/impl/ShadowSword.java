package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shadow Sword: assassin.
 * Passive Umbral Cloak, on-hit Backstab, RC Shadow Strike, Sneak+RC Shadow Veil.
 * <p>
 * Shadow Veil = Invisibility + other players are sent empty equipment for the caster, so
 * armour and held items disappear too. Mobs stop targeting the caster.
 */
public final class ShadowSword extends SwordAbility implements Listener {

    private static final int VEIL_TICKS = 120;
    private static final EquipmentSlot[] VISIBLE_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };

    /** Veiled player -> server tick the veil ends. */
    private final Map<UUID, Integer> veiled = new HashMap<>();
    private final BukkitTask veilTask;

    public ShadowSword(CustomsPlugin plugin) {
        super(plugin, SwordType.SHADOW);
        this.veilTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickVeils, 5L, 5L);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.SNEAKING_SPEED, "sneaking_speed", 0.4, AttributeModifier.Operation.ADD_NUMBER);
        addModifier(meta, Attribute.MOVEMENT_SPEED, "speed", 0.1, AttributeModifier.Operation.ADD_SCALAR);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        if (player.isSneaking()) {
            out.add(effect(PotionEffectType.INVISIBILITY, 0, 0));
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (plugin.items().heldType(player) == type) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    plugin.passives().refresh(player);
                }
            });
        }
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        Vector facing = target.getLocation().getDirection().setY(0);
        Vector approach = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() > 1.0E-4 && approach.lengthSquared() > 1.0E-4
                && facing.angle(approach) < Math.toRadians(60)) {
            event.setDamage(event.getDamage() * 1.6);
            Location at = Combat.center(target);
            Fx.particle(at, Particle.CRIT, 20, 0.3, 0.2);
            Fx.particle(at, Particle.SQUID_INK, 8, 0.2, 0.02);
            Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            tell(player, Text.plain("☠ Backstab!", type.color()));
        }
        if (chance(0.2)) {
            applyEffect(target, PotionEffectType.BLINDNESS, 40, 0);
        }
    }

    /** Shadow Strike: teleport behind the targeted enemy and hit them. */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 18, 0.8);
        if (target == null) {
            tell(player, "No target in sight.");
            return false;
        }
        Location destination = behind(player, target);
        if (destination == null) {
            tell(player, "No room behind your target.");
            return false;
        }
        Location from = player.getLocation();
        Fx.particle(from.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 25, 0.3, 0.02);
        Fx.particle(from.clone().add(0, 1, 0), Particle.SQUID_INK, 15, 0.3, 0.05);
        Fx.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        if (!player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            return false;
        }
        player.setFallDistance(0);
        Fx.particle(destination.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 25, 0.3, 0.02);
        Fx.sound(destination, Sound.ENTITY_PHANTOM_BITE, 1f, 0.8f);
        fx().cut(Combat.center(target), destination.getDirection(), 2.4, 0.5, 0x6A4CA8, 1.6f, 3);
        // you step out of one shadow rift and into another; shadows rise out of the ground and grab the target
        org.bukkit.util.Vector across = destination.toVector().subtract(from.toVector());
        fx().rift(from.clone().add(0, 1, 0), across, 0x6A4CA8, 2.0f, 20);
        fx().rift(destination.clone().add(0, 1, 0), across, 0x6A4CA8, 1.6f, 16);
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3;
            Location root = target.getLocation().add(Math.cos(a) * 1.3, 0.1, Math.sin(a) * 1.3);
            fx().tendril(root, () -> target.isValid() ? Combat.center(target) : null, 0x3B2766, 0.7f, 6, null);
        }
        fx().burst(Combat.center(target), 0x9D7BFF, 1.6f);
        combat().damage(player, target, 6);
        applyEffect(target, PotionEffectType.BLINDNESS, 40, 0);
        if (target instanceof Player) {
            applyEffect(target, PotionEffectType.DARKNESS, 60, 0);
        }
        return true;
    }

    private Location behind(Player player, LivingEntity target) {
        Location base = target.getLocation();
        Vector facing = base.getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = base.toVector().subtract(player.getLocation().toVector()).setY(0);
        }
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        double distance = 0.9 + target.getWidth() / 2;
        // Try directly behind, then the sides, then in front.
        for (double angle : new double[]{0, 45, -45, 90, -90, 180}) {
            Vector offset = facing.clone().rotateAroundY(Math.toRadians(angle)).multiply(-distance);
            Location spot = Combat.safeSpot(base.clone().add(offset));
            if (spot != null && player.getWorld() == spot.getWorld()) {
                Vector look = base.toVector().subtract(spot.toVector());
                spot.setDirection(look.lengthSquared() < 1.0E-4 ? facing : look);
                return spot;
            }
        }
        return null;
    }

    /** Shadow Veil: total invisibility for 6s; the first attack out of it is an ambush. */
    @Override
    public boolean secondary(Player player) {
        if (veiled.containsKey(player.getUniqueId())) {
            tell(player, "You're already veiled.");
            return false;
        }
        veiled.put(player.getUniqueId(), Bukkit.getCurrentTick() + VEIL_TICKS);
        PotionEffect sneakInvis = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (sneakInvis != null && sneakInvis.isAmbient()) {
            // Swap the Umbral Cloak effect for the veil's own so the two don't merge.
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, VEIL_TICKS, 0, false, false, true));
        applyEffect(player, PotionEffectType.SPEED, VEIL_TICKS, 1);
        hideGear(player);
        for (Entity nearby : player.getNearbyEntities(32, 16, 32)) {
            if (nearby instanceof Mob mob && mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
        Location at = player.getLocation().add(0, 1, 0);
        Fx.particle(at, Particle.LARGE_SMOKE, 40, 0.4, 0.03);
        Fx.particle(at, Particle.SQUID_INK, 20, 0.4, 0.05);
        Fx.sound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
        // a burst of shadow where you were: others see you dissolve into it
        fx().haze(at, 0x2A1B47, 3.2f, 40);
        fx().haze(at.clone().add(0, 0.6, 0), 0x6A4CA8, 2.2f, 30);
        fx().shockwave(player.getLocation(), 0x3B2766, 3.5f, 10);
        fx().burst(at, 0x9D7BFF, 2f);
        tell(player, Text.plain("🌑 You vanish into the shadows...", type.color()));
        return true;
    }

    public boolean isVeiled(Player player) {
        return veiled.containsKey(player.getUniqueId());
    }

    private void tickVeils() {
        if (veiled.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        for (UUID id : new ArrayList<>(veiled.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                veiled.remove(id);
                continue;
            }
            if (veiled.get(id) <= now || player.isDead()) {
                endVeil(player, false);
                continue;
            }
            hideGear(player); // the server re-sends real gear whenever it changes
            int secondsLeft = (veiled.get(id) - now + 19) / 20;
            tell(player, Text.plain("🌑 Veiled ", type.color()).append(Text.plain(secondsLeft + "s", NamedTextColor.GRAY)));
        }
    }

    private void hideGear(Player player) {
        Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : VISIBLE_SLOTS) {
            empty.put(slot, ItemStack.empty());
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer != player && viewer.canSee(player)) {
                viewer.sendEquipmentChange(player, empty);
            }
        }
    }

    private void showGear(Player player) {
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : VISIBLE_SLOTS) {
            real.put(slot, player.getInventory().getItem(slot));
        }
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer != player && viewer.canSee(player)) {
                viewer.sendEquipmentChange(player, real);
            }
        }
    }

    private void endVeil(Player player, boolean ambush) {
        if (veiled.remove(player.getUniqueId()) == null) {
            return;
        }
        PotionEffect invis = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (invis != null && !invis.isAmbient() && invis.getDuration() <= VEIL_TICKS) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        if (player.isOnline()) {
            showGear(player);
            Location at = player.getLocation().add(0, 1, 0);
            Fx.particle(at, Particle.LARGE_SMOKE, 30, 0.4, 0.03);
            if (!ambush) {
                Fx.sound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);
            }
            plugin.passives().refresh(player);
        }
    }

    /** Attacking out of the veil ends it; a melee hit with the Shadow Sword is an ambush. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVeiledAttack(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || !isVeiled(attacker)) {
            return;
        }
        boolean ambush = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && !plugin.combat().isAbilityDamage()
                && event.getDamager() == attacker
                && plugin.items().heldType(attacker) == type;
        if (ambush && event.getEntity() instanceof LivingEntity target) {
            event.setDamage(event.getDamage() + 8);
            applyEffect(target, PotionEffectType.BLINDNESS, 60, 0);
            Location at = Combat.center(target);
            Fx.particle(at, Particle.SQUID_INK, 30, 0.4, 0.1);
            Fx.particle(at, Particle.CRIT, 30, 0.4, 0.3);
            Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
            Fx.sound(at, Sound.ENTITY_PHANTOM_BITE, 1f, 0.6f);
            tell(attacker, Text.plain("☠ Ambush!", NamedTextColor.DARK_PURPLE));
        }
        endVeil(attacker, ambush);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isVeiled(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : veiled.keySet()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && joined.isOnline()) {
                    hideGear(player);
                }
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endVeil(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        endVeil(event.getPlayer(), false);
    }

    @Override
    public void shutdown() {
        veilTask.cancel();
        for (UUID id : new ArrayList<>(veiled.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                endVeil(player, false);
            }
        }
        veiled.clear();
    }
}
