package dev.infuse.opswords.ability;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.manager.StatusManager;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Behaviour of one sword. Subclasses may also implement {@link org.bukkit.event.Listener};
 * they are registered automatically.
 */
public abstract class SwordAbility {

    protected final OPSwordsPlugin plugin;
    protected final SwordType type;

    protected SwordAbility(OPSwordsPlugin plugin, SwordType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public SwordType type() {
        return type;
    }

    /** Extra attribute modifiers the item grants in the main hand. */
    public void addAttributes(ItemMeta meta) {
    }

    /** Potion effects kept on the holder while the sword is in their main hand. Checked every 10 ticks. */
    public void heldEffects(Player player, List<PotionEffect> out) {
    }

    /** Runs every 10 ticks while the sword is held. {@code tick} counts these runs. */
    public void tickHeld(Player player, long tick) {
    }

    /** A melee hit with this sword. Runs before armour is applied, so changing the damage is allowed. */
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
    }

    /** Right-click. Return true if the ability went off (starts the cooldown). */
    public abstract boolean primary(Player player);

    /** Sneak + right-click. Return true if the ability went off (starts the cooldown). */
    public abstract boolean secondary(Player player);

    /** Undo any lingering state (plugin disable / reload). */
    public void shutdown() {
    }

    // ---- helpers ----

    protected Combat combat() {
        return plugin.combat();
    }

    protected StatusManager status() {
        return plugin.status();
    }

    protected static boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    protected static boolean alive(Player player) {
        return player.isOnline() && !player.isDead();
    }

    protected static PotionEffect effect(PotionEffectType type, int ticks, int amplifier) {
        return new PotionEffect(type, ticks, amplifier, false, true, true);
    }

    /** Applies an effect unless the entity already has a stronger or longer one. */
    protected static void applyEffect(LivingEntity entity, PotionEffectType type, int ticks, int amplifier) {
        PotionEffect current = entity.getPotionEffect(type);
        if (current != null && (current.getAmplifier() > amplifier
                || (current.getAmplifier() == amplifier && (current.isInfinite() || current.getDuration() >= ticks)))) {
            return;
        }
        entity.addPotionEffect(effect(type, ticks, amplifier));
    }

    protected void addModifier(ItemMeta meta, Attribute attribute, String name, double amount,
                               AttributeModifier.Operation operation) {
        meta.addAttributeModifier(attribute, new AttributeModifier(
                new NamespacedKey(plugin, type.id() + "_" + name), amount, operation, EquipmentSlotGroup.MAINHAND));
    }

    /** Short feedback on the action bar (pauses the cooldown HUD briefly). */
    protected void tell(Player player, String message) {
        plugin.passives().actionBar(player, Text.plain(message, NamedTextColor.RED));
    }

    protected void tell(Player player, Component message) {
        plugin.passives().actionBar(player, message);
    }
}
