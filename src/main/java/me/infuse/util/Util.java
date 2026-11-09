package me.infuse.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;

public final class Util {
    private Util() {}

    /** Players on the same scoreboard team (main scoreboard) are allies; everyone is their own ally. */
    public static boolean isAlly(Entity a, Entity b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        if (!(a instanceof Player pa) || !(b instanceof Player pb)) return false;
        Team ta = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(pa.getName());
        Team tb = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(pb.getName());
        return ta != null && ta.equals(tb);
    }

    /** Living, targetable non-ally entities near a location. */
    public static List<LivingEntity> enemiesNear(Player source, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || le.isDead()) continue;
            if (isAlly(source, e)) continue;
            if (e instanceof Player p && (p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || p.getGameMode() == org.bukkit.GameMode.CREATIVE)) continue;
            if (e.getLocation().distanceSquared(center) > radius * radius) continue;
            out.add(le);
        }
        return out;
    }

    public static void potion(LivingEntity e, PotionEffectType type, int amplifier, int ticks, boolean ambient) {
        e.addPotionEffect(new PotionEffect(type, ticks, amplifier, ambient, false, true));
    }

    public static boolean isPickaxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    public static boolean isEdible(Material m) {
        return m.isEdible();
    }

    /** Adds or removes a keyed attribute modifier so its presence matches {@code wanted}. */
    public static void modifier(LivingEntity e, Attribute attribute, NamespacedKey key, double amount,
                                AttributeModifier.Operation op, boolean wanted) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(key);
        if (wanted) {
            if (existing != null && existing.getAmount() == amount && existing.getOperation() == op) return;
            if (existing != null) inst.removeModifier(key);
            inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        } else if (existing != null) {
            inst.removeModifier(key);
        }
    }

    public static double maxHealth(LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attribute.MAX_HEALTH);
        return inst == null ? 20 : inst.getValue();
    }

    public static String coords(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " (" + l.getWorld().getName() + ")";
    }

    public static String time(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
