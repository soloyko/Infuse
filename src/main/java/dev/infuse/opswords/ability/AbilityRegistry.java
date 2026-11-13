package dev.infuse.opswords.ability;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.impl.ChronoSword;
import dev.infuse.opswords.ability.impl.CosmicSword;
import dev.infuse.opswords.ability.impl.CrimsonSword;
import dev.infuse.opswords.ability.impl.DragonSword;
import dev.infuse.opswords.ability.impl.EchoSword;
import dev.infuse.opswords.ability.impl.FrostSword;
import dev.infuse.opswords.ability.impl.PhoenixSword;
import dev.infuse.opswords.ability.impl.PlagueSword;
import dev.infuse.opswords.ability.impl.ShadowSword;
import dev.infuse.opswords.ability.impl.SolarSword;
import dev.infuse.opswords.ability.impl.SoulSword;
import dev.infuse.opswords.ability.impl.StormSword;
import dev.infuse.opswords.ability.impl.TideSword;
import dev.infuse.opswords.ability.impl.VerdantSword;
import dev.infuse.opswords.ability.impl.VoidSword;
import dev.infuse.opswords.ability.impl.AxeWeapon;
import dev.infuse.opswords.ability.impl.ChakramWeapon;
import dev.infuse.opswords.ability.impl.HammerWeapon;
import dev.infuse.opswords.ability.impl.ScytheWeapon;
import dev.infuse.opswords.ability.impl.SpearWeapon;
import dev.infuse.opswords.ability.impl.StaffWeapon;
import dev.infuse.opswords.api.SwordAbilityEvent;
import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Holds one {@link SwordAbility} per sword and runs activations (permission, cooldown, event). */
public final class AbilityRegistry {

    private final OPSwordsPlugin plugin;
    private final Map<SwordType, SwordAbility> abilities = new EnumMap<>(SwordType.class);

    public AbilityRegistry(OPSwordsPlugin plugin) {
        this.plugin = plugin;
        register(new DragonSword(plugin));
        register(new PhoenixSword(plugin));
        register(new FrostSword(plugin));
        register(new StormSword(plugin));
        register(new TideSword(plugin));
        register(new VerdantSword(plugin));
        register(new PlagueSword(plugin));
        register(new CrimsonSword(plugin));
        register(new SolarSword(plugin));
        register(new VoidSword(plugin));
        register(new CosmicSword(plugin));
        register(new ChronoSword(plugin));
        register(new ShadowSword(plugin));
        register(new SoulSword(plugin));
        register(new EchoSword(plugin));
        register(new HammerWeapon(plugin));
        register(new ScytheWeapon(plugin));
        register(new SpearWeapon(plugin));
        register(new ChakramWeapon(plugin));
        register(new AxeWeapon(plugin));
        register(new StaffWeapon(plugin));
        for (SwordType type : SwordType.values()) {
            if (!abilities.containsKey(type)) {
                throw new IllegalStateException("No ability registered for " + type);
            }
        }
    }

    private void register(SwordAbility ability) {
        abilities.put(ability.type(), ability);
    }

    public void registerListeners() {
        for (SwordAbility ability : abilities.values()) {
            if (ability instanceof Listener listener) {
                Bukkit.getPluginManager().registerEvents(listener, plugin);
            }
        }
    }

    public SwordAbility get(SwordType type) {
        return abilities.get(type);
    }

    public Collection<SwordAbility> all() {
        return abilities.values();
    }

    /** Tries to fire an ability for the player. */
    public void activate(Player player, SwordType type, AbilitySlot slot) {
        if (!plugin.canUse(player, type)) {
            if (!player.hasPermission("opswords.use")) {
                plugin.passives().actionBar(player, Text.plain("You can't use OP Sword abilities.", NamedTextColor.RED));
            } else if (!plugin.settings().sword(type).enabled()) {
                plugin.passives().actionBar(player, Text.plain(type.swordName() + " is disabled on this server.", NamedTextColor.RED));
            } else {
                plugin.passives().actionBar(player, Text.plain("Abilities are disabled in this world.", NamedTextColor.RED));
            }
            return;
        }
        String name = type.ability(slot).name();
        long left = plugin.cooldowns().remaining(player.getUniqueId(), type, slot);
        if (left > 0 && !player.hasPermission("opswords.bypass.cooldown")) {
            plugin.passives().actionBar(player, Text.plain("⌛ ", NamedTextColor.GRAY)
                    .append(Text.plain(name, type.color()))
                    .append(Text.plain(" is ready in " + Text.seconds(left), NamedTextColor.GRAY)));
            return;
        }
        SwordAbilityEvent event = new SwordAbilityEvent(player, type, slot);
        if (!event.callEvent()) {
            return;
        }
        boolean fired;
        try {
            SwordAbility ability = abilities.get(type);
            fired = slot == AbilitySlot.PRIMARY ? ability.primary(player) : ability.secondary(player);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Error running " + type.id() + " " + slot.name().toLowerCase(Locale.ROOT) + " ability", ex);
            return;
        }
        if (fired && !player.hasPermission("opswords.bypass.cooldown")) {
            plugin.cooldowns().start(player.getUniqueId(), type, slot, plugin.settings().sword(type).cooldown(slot));
        }
    }

    public void shutdown() {
        for (SwordAbility ability : abilities.values()) {
            try {
                ability.shutdown();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Error shutting down " + ability.type().id(), ex);
            }
        }
    }
}
