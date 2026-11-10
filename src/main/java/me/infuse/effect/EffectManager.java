package me.infuse.effect;

import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.util.Glyphs;
import me.infuse.util.Msg;
import me.infuse.util.Status;
import me.infuse.util.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Equipping, passive upkeep (potions, attributes, auras, movement) and the HUD. */
public final class EffectManager {
    private static final List<PotionEffectType> MANAGED = List.of(
            PotionEffectType.LUCK, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.SPEED,
            PotionEffectType.INVISIBILITY, PotionEffectType.DOLPHINS_GRACE, PotionEffectType.WATER_BREATHING,
            PotionEffectType.WEAVING, PotionEffectType.RESISTANCE, PotionEffectType.JUMP_BOOST);

    private final InfusePlugin plugin;
    private final Status status;
    public final NamespacedKey heartHealth;
    public final NamespacedKey heartAbsorption;
    public final NamespacedKey hasteMining;
    public final NamespacedKey hasteSpark;
    public final NamespacedKey frostJump;
    public final NamespacedKey resistanceAbsorption;
    private BukkitTask tickTask;
    private final Set<UUID> vanishedFrom = new HashSet<>();
    /** viewer -> targets this plugin unlisted from that viewer's tablist (so we only re-list our own). */
    private final Map<UUID, Set<UUID>> unlistedBy = new HashMap<>();
    private long tick;

    public EffectManager(InfusePlugin plugin, Status status) {
        this.plugin = plugin;
        this.status = status;
        this.heartHealth = new NamespacedKey(plugin, "heart_health");
        this.heartAbsorption = new NamespacedKey(plugin, "heart_absorption");
        this.hasteMining = new NamespacedKey(plugin, "haste_mining");
        this.hasteSpark = new NamespacedKey(plugin, "haste_spark");
        this.frostJump = new NamespacedKey(plugin, "frost_jump");
        this.resistanceAbsorption = new NamespacedKey(plugin, "resistance_absorption");
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Set<UUID> ours = unlistedBy.getOrDefault(viewer.getUniqueId(), Set.of());
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (ours.contains(target.getUniqueId()) && !viewer.isListed(target)) viewer.listPlayer(target);
                if (vanishedFrom.contains(target.getUniqueId())) viewer.showEntity(plugin, target);
            }
        }
        vanishedFrom.clear();
        unlistedBy.clear();
    }

    /** Forget tablist state for a player who left (they get a fresh tablist on rejoin). */
    public void forget(UUID id) {
        unlistedBy.remove(id);
        unlistedBy.values().forEach(set -> set.remove(id));
        vanishedFrom.remove(id);
    }

    /**
     * Thief: holders are unlisted from non-allies' tablists; while the spark is active they are
     * hidden from non-allies entirely.
     */
    public void syncThieves() {
        var online = Bukkit.getOnlinePlayers();
        for (Player target : online) {
            PlayerData td = plugin.data().get(target);
            boolean thief = td.hasPassive(EffectType.THIEF);
            boolean vanish = thief && td.isSparkActive(EffectType.THIEF);
            for (Player viewer : online) {
                if (viewer.equals(target)) continue;
                boolean enemy = !Util.isAlly(viewer, target);
                Set<UUID> ours = unlistedBy.computeIfAbsent(viewer.getUniqueId(), k -> new HashSet<>());
                if (thief && enemy) {
                    if (viewer.isListed(target) && viewer.unlistPlayer(target)) ours.add(target.getUniqueId());
                } else if (ours.remove(target.getUniqueId()) && !viewer.isListed(target)) {
                    viewer.listPlayer(target);
                }
                if (vanish && enemy) viewer.hideEntity(plugin, target);
                else if (vanishedFrom.contains(target.getUniqueId())) viewer.showEntity(plugin, target);
            }
            if (vanish) vanishedFrom.add(target.getUniqueId());
            else vanishedFrom.remove(target.getUniqueId());
        }
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    // ------------------------------------------------------------------ equip

    public enum EquipResult { PRIMARY, SECONDARY, DUPLICATE, FULL }

    public EquipResult equip(Player p, Equipped e) {
        PlayerData d = plugin.data().get(p);
        if (d.hasEquipped(e.type())) return EquipResult.DUPLICATE;
        EquipResult result;
        if (d.get(PlayerData.PRIMARY) == null) {
            d.set(PlayerData.PRIMARY, e);
            result = EquipResult.PRIMARY;
        } else if (d.get(PlayerData.SECONDARY) == null) {
            d.set(PlayerData.SECONDARY, e);
            result = EquipResult.SECONDARY;
        } else {
            return EquipResult.FULL;
        }
        d.setXp(result == EquipResult.PRIMARY ? PlayerData.PRIMARY : PlayerData.SECONDARY, 0);
        refresh(p);
        plugin.data().save(d);
        return result;
    }

    /** Removes the effect in a slot and returns it (or null). */
    public Equipped unequip(Player p, int slot) {
        PlayerData d = plugin.data().get(p);
        Equipped e = d.get(slot);
        if (e == null) return null;
        d.set(slot, null);
        d.setXp(slot, 0);
        d.setSlotActive(slot, 0);
        d.setHits(0);
        d.setSpeedStacks(0);
        refresh(p);
        plugin.data().save(d);
        return e;
    }

    public void clearAll(Player p) {
        PlayerData d = plugin.data().get(p);
        d.set(PlayerData.PRIMARY, null);
        d.set(PlayerData.SECONDARY, null);
        d.resetCooldowns();
        d.clearSparks();
        d.setHits(0);
        d.setSpeedStacks(0);
        refresh(p);
        plugin.data().save(d);
    }

    /** Re-applies everything that depends on what is equipped. */
    public void refresh(Player p) {
        PlayerData d = plugin.data().get(p);
        applyAttributes(p, d);
        applyPotions(p, d);
        applyNametag(p, d);
    }

    // ------------------------------------------------------------------ passives

    private void applyAttributes(Player p, PlayerData d) {
        boolean heart = d.hasPassive(EffectType.HEART);
        Util.modifier(p, Attribute.MAX_HEALTH, heartHealth, me.infuse.effect.Tune.d(p, "effects.heart.bonus-health", 10),
                AttributeModifier.Operation.ADD_NUMBER, heart);
        Util.modifier(p, Attribute.MAX_ABSORPTION, heartAbsorption, cfg().getDouble("effects.heart.max-absorption", 40),
                AttributeModifier.Operation.ADD_NUMBER, heart);
        Util.modifier(p, Attribute.MAX_ABSORPTION, resistanceAbsorption,
                cfg().getDouble("effects.resistance.max-absorption", 16), AttributeModifier.Operation.ADD_NUMBER,
                d.hasPassive(EffectType.RESISTANCE));
        double max = Util.maxHealth(p);
        if (p.getHealth() > max) p.setHealth(max);

        boolean mining = d.hasPassive(EffectType.HASTE) && Util.isPickaxe(p.getInventory().getItemInMainHand());
        Util.modifier(p, Attribute.MINING_EFFICIENCY, hasteMining, 101, AttributeModifier.Operation.ADD_NUMBER, mining);
        Util.modifier(p, Attribute.ATTACK_SPEED, hasteSpark, cfg().getDouble("effects.haste.spark-attack-speed", 4),
                AttributeModifier.Operation.ADD_NUMBER, d.isSparkActive(EffectType.HASTE));
    }

    public void applyPotions(Player p, PlayerData d) {
        Map<PotionEffectType, Integer> want = new HashMap<>();
        if (d.hasPassive(EffectType.EMERALD)) want.put(PotionEffectType.LUCK, 9);
        if (d.hasPassive(EffectType.FIRE)) want.put(PotionEffectType.FIRE_RESISTANCE, 0);
        if (d.hasPassive(EffectType.INVISIBILITY)) want.put(PotionEffectType.INVISIBILITY, 0);
        if (d.hasPassive(EffectType.OCEAN)) {
            want.put(PotionEffectType.DOLPHINS_GRACE, 0);
            want.put(PotionEffectType.WATER_BREATHING, 0);
        }
        int speed = -1;
        if (d.hasPassive(EffectType.SPEED)) speed = d.speedStacks() + (d.isAugmented(EffectType.SPEED) ? 1 : 0);
        if (d.hasPassive(EffectType.FROST) && onIceOrSnow(p)) speed = Math.max(speed, 2);
        if (d.hasPassive(EffectType.WEAVING)) {
            want.put(PotionEffectType.WEAVING, 0);
            if (nearCobweb(p)) speed = Math.max(speed, 1);
        }
        if (d.hasPassive(EffectType.RESISTANCE)) want.put(PotionEffectType.RESISTANCE, 0);
        if (d.isSparkActive(EffectType.OOZING)) want.put(PotionEffectType.JUMP_BOOST, cfg().getInt("effects.oozing.jump-boost", 2));
        if (speed >= 0) want.put(PotionEffectType.SPEED, speed);

        // Extra potions from config: effects.<id>.extra-potions: ["STRENGTH:0", "night_vision:0"]
        java.util.Set<PotionEffectType> managed = new java.util.HashSet<>(MANAGED);
        for (EffectType t : EffectType.values()) {
            for (String entry : cfg().getStringList("effects." + t.id() + ".extra-potions")) {
                String[] parts = entry.split(":");
                PotionEffectType pt = org.bukkit.Registry.EFFECT.get(
                        NamespacedKey.minecraft(parts[0].trim().toLowerCase(java.util.Locale.ROOT)));
                if (pt == null) continue;
                managed.add(pt);
                if (!d.hasPassive(t)) continue;
                int amp = 0;
                try { if (parts.length > 1) amp = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) { }
                want.merge(pt, amp, Math::max);
            }
        }

        for (Map.Entry<PotionEffectType, Integer> e : want.entrySet()) {
            PotionEffect existing = p.getPotionEffect(e.getKey());
            if (existing != null && !existing.isAmbient() && existing.getAmplifier() >= e.getValue()) continue;
            if (existing != null && existing.isAmbient() && existing.getAmplifier() != e.getValue()) {
                p.removePotionEffect(e.getKey());
            }
            p.addPotionEffect(new PotionEffect(e.getKey(), 70, e.getValue(), true, false, true));
        }
        for (PotionEffectType type : managed) {
            if (want.containsKey(type)) continue;
            PotionEffect existing = p.getPotionEffect(type);
            if (existing != null && existing.isAmbient() && existing.getDuration() <= 100) p.removePotionEffect(type);
        }
    }

    private void applyNametag(Player p, PlayerData d) {
        if (d.hasEquipped(EffectType.APOPHIS)) {
            Component tag = Msg.parse(cfg().getString("effects.apophis.nametag-prefix", "<dark_purple>☠ </dark_purple>"))
                    .append(Component.text(p.getName(), EffectType.APOPHIS.color()));
            p.displayName(tag);
            p.playerListName(tag);
        } else {
            p.displayName(null);
            p.playerListName(null);
        }
    }

    private static boolean nearCobweb(Player p) {
        Block feet = p.getLocation().getBlock();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (feet.getRelative(x, y, z).getType() == Material.COBWEB) return true;
        }
        return false;
    }

    private static boolean onIceOrSnow(Player p) {
        Block feet = p.getLocation().getBlock();
        Block below = feet.getRelative(0, -1, 0);
        return isIceOrSnow(feet.getType()) || isIceOrSnow(below.getType());
    }

    private static boolean isIceOrSnow(Material m) {
        String n = m.name();
        return n.contains("ICE") || n.contains("SNOW");
    }

    // ------------------------------------------------------------------ ticking

    private void tick() {
        tick++;
        int hudEvery = Math.max(1, cfg().getInt("hud.update-ticks", 4));
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            if (d.isEmpty()) continue;
            movement(p, d);
            Aura.tick(p, d, tick, cfg());
            if (tick % 5 == 0) {
                applyAttributes(p, d);
            }
            if (tick % 20 == 0) {
                second(p, d);
            }
            for (int slot = 0; slot < 2; slot++) {
                if (d.get(slot) != null && d.consumeReady(slot)) me.infuse.util.Fx.sparkReady(p, d.get(slot).type());
            }
            if (tick % hudEvery == 0 && cfg().getBoolean("hud.enabled", true)) {
                hud(p, d);
            }
        }
        if (tick % 10 == 0) syncThieves();
        if (tick % 20 == 0) {
            status.cleanup();
            cleanupJumpDebuffs();
        }
    }

    private void movement(Player p, PlayerData d) {
        if (p.getGameMode() == GameMode.SPECTATOR || p.isFlying()) return;
        if (d.isSparkActive(EffectType.OOZING)) {
            //noinspection deprecation
            if (!p.isOnGround()) {
                d.setAirFall(Math.max(d.airFall(), p.getFallDistance()));
            } else {
                if (d.airFall() > 1.2f) plugin.sparks().oozingSlam(p);
                d.setAirFall(0);
            }
        }
        boolean lava = d.hasPassive(EffectType.FIRE) && p.isInLava();
        boolean snow = d.hasPassive(EffectType.FROST) && inPowderSnow(p);
        if (snow) p.setFreezeTicks(0);
        if (!lava && !snow) return;
        double speed = lava ? cfg().getDouble("effects.fire.lava-swim-speed", 0.35)
                : cfg().getDouble("effects.frost.snow-swim-speed", 0.3);
        Input in = p.getCurrentInput();
        Vector look = p.getLocation().getDirection();
        Vector v = new Vector();
        if (in.isForward()) v.add(look.clone().multiply(speed));
        if (in.isBackward()) v.subtract(look.clone().multiply(speed * 0.5));
        if (in.isJump()) v.setY(v.getY() + 0.25);
        if (in.isSneak()) v.setY(-0.25);
        if (v.lengthSquared() > 0) p.setVelocity(v);
    }

    private static boolean inPowderSnow(Player p) {
        return p.getLocation().getBlock().getType() == Material.POWDER_SNOW
                || p.getEyeLocation().getBlock().getType() == Material.POWDER_SNOW;
    }

    private void second(Player p, PlayerData d) {
        long now = System.currentTimeMillis();
        if (d.speedStacks() > 0 && now - d.lastHitAt() > cfg().getLong("effects.speed.stack-reset-seconds", 3) * 1000) {
            d.setSpeedStacks(0);
        }
        applyPotions(p, d);

        if (d.hasPassive(EffectType.ENDER)) {
            double r = me.infuse.effect.Tune.d(p, "effects.ender.glow-radius", 16);
            for (LivingEntity le : Util.enemiesNear(p, p.getLocation(), r)) {
                if (le instanceof Player) Util.potion(le, PotionEffectType.GLOWING, 0, 30, false);
            }
        }

        if (d.hasPassive(EffectType.OCEAN)) {
            double r = cfg().getDouble("effects.ocean.drown-radius", 6);
            boolean onlyWater = cfg().getBoolean("effects.ocean.drown-only-in-water", true);
            for (LivingEntity le : Util.enemiesNear(p, p.getLocation(), r)) {
                if (!(le instanceof Player target)) continue;
                if (onlyWater && !target.isInWater()) continue;
                PlayerData td = plugin.data().get(target);
                if (td.hasPassive(EffectType.OCEAN)) continue;
                int air = target.getRemainingAir() - cfg().getInt("effects.ocean.air-drain-per-second", 60);
                if (air <= 0) {
                    target.setRemainingAir(0);
                    target.damage(2.0, DamageSource.builder(DamageType.DROWN).build());
                } else {
                    target.setRemainingAir(air);
                }
            }
        }
    }

    private void cleanupJumpDebuffs() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = status.jumpDebuffs().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (e.getValue() > now) continue;
            Entity ent = Bukkit.getEntity(e.getKey());
            if (ent instanceof LivingEntity le) {
                Util.modifier(le, Attribute.JUMP_STRENGTH, frostJump, 0, AttributeModifier.Operation.ADD_SCALAR, false);
            }
            it.remove();
        }
    }

    // ------------------------------------------------------------------ HUD

    private void hud(Player p, PlayerData d) {
        Component left = slotGlyph(d, PlayerData.PRIMARY);
        Component right = slotGlyph(d, PlayerData.SECONDARY);
        Component gap = Component.text(" ".repeat(Math.max(0, cfg().getInt("hud.spacing", 2))));

        Component middle = Component.empty();
        if (cfg().getBoolean("hud.hit-tracker", true)) {
            EffectType tracker = hitTrackerType(d);
            if (tracker != null) {
                long since = System.currentTimeMillis() - d.hitProcAt();
                int col;
                if (since < 150) col = 10;
                else if (since < 600) col = 11 + (int) ((since - 150) / 150);
                else col = Math.min(9, tracker == EffectType.RESISTANCE ? d.takenHits() : d.hits());
                middle = Glyphs.hitComponent(tracker, col);
            }
        }
        Component hits = middle;
        p.sendActionBar(Msg.parse(cfg().getString("hud.format", "<left><gap><hits><gap><right>"),
                Msg.comp("left", left), Msg.comp("right", right), Msg.comp("hits", hits), Msg.comp("gap", gap)));
    }

    private Component slotGlyph(PlayerData d, int slot) {
        Equipped e = d.get(slot);
        if (e == null) return Component.empty();
        long rem = d.cooldownRemaining(slot);
        long total = d.cooldownTotal(slot);
        // While the spark is still active (cooldown not started yet) the icon stays bright
        int frame = rem > 0 && total > 0 && rem <= total ? (int) Math.ceil(rem * 20.0 / total) : 0;
        if (e.type() == EffectType.THIEF && d.isSparkActive(EffectType.THIEF)) {
            // While vanished, the icon darkens as the cover runs out
            long left = Math.max(0, d.sparkActiveUntil(EffectType.THIEF) - System.currentTimeMillis());
            long dur = plugin.sparks().durationFor(d, EffectType.THIEF);
            frame = dur > 0 ? (int) Math.floor((1 - left / (double) dur) * 20) : 0;
        }
        return Glyphs.effectComponent(e.type(), e.augmented(), d.isSlotActive(slot) || d.isReadyFlash(slot), frame);
    }

    private static EffectType hitTrackerType(PlayerData d) {
        for (int slot = 0; slot < 2; slot++) {
            Equipped e = d.get(slot);
            if (e != null && e.type().usesHitTracker()) return e.type();
        }
        return null;
    }
}
