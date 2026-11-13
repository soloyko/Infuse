package dev.infuse.customs.ability;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.impl.ChronoSword;
import dev.infuse.customs.ability.impl.CosmicSword;
import dev.infuse.customs.ability.impl.CrimsonSword;
import dev.infuse.customs.ability.impl.DragonSword;
import dev.infuse.customs.ability.impl.EchoSword;
import dev.infuse.customs.ability.impl.FrostSword;
import dev.infuse.customs.ability.impl.PhoenixSword;
import dev.infuse.customs.ability.impl.PlagueSword;
import dev.infuse.customs.ability.impl.ShadowSword;
import dev.infuse.customs.ability.impl.SolarSword;
import dev.infuse.customs.ability.impl.SoulSword;
import dev.infuse.customs.ability.impl.StormSword;
import dev.infuse.customs.ability.impl.TideSword;
import dev.infuse.customs.ability.impl.VerdantSword;
import dev.infuse.customs.ability.impl.VoidSword;
import dev.infuse.customs.ability.impl.AxeWeapon;
import dev.infuse.customs.ability.impl.ChakramWeapon;
import dev.infuse.customs.ability.impl.HammerWeapon;
import dev.infuse.customs.ability.impl.ScytheWeapon;
import dev.infuse.customs.ability.impl.SpearWeapon;
import dev.infuse.customs.ability.impl.StaffWeapon;
import dev.infuse.customs.ability.impl.TridentWeapon;
import dev.infuse.customs.ability.impl.ShurikenWeapon;
import dev.infuse.customs.ability.impl.HalberdWeapon;
import dev.infuse.customs.ability.impl.WarglaiveWeapon;
import dev.infuse.customs.ability.impl.SawbladeWeapon;
import dev.infuse.customs.ability.impl.RapierWeapon;
import dev.infuse.customs.ability.impl.KhopeshWeapon;
import dev.infuse.customs.ability.impl.KatanaWeapon;
import dev.infuse.customs.ability.impl.NeedleWeapon;
import dev.infuse.customs.ability.impl.CandleWeapon;
import dev.infuse.customs.ability.impl.ClaymoreWeapon;
import dev.infuse.customs.ability.impl.MirrorWeapon;
import dev.infuse.customs.ability.impl.CutlassWeapon;
import dev.infuse.customs.ability.impl.ClawsWeapon;
import dev.infuse.customs.ability.impl.DaggersWeapon;
import dev.infuse.customs.ability.impl.MaceWeapon;
import dev.infuse.customs.ability.impl.GlaiveWeapon;
import dev.infuse.customs.ability.impl.MaulWeapon;
import dev.infuse.customs.ability.impl.MantisWeapon;
import dev.infuse.customs.ability.impl.RibbonWeapon;
import dev.infuse.customs.ability.impl.StarsWeapon;
import dev.infuse.customs.ability.impl.AshuraWeapon;
import dev.infuse.customs.ability.impl.DeathKnightWeapon;
import dev.infuse.customs.ability.impl.SplitterWeapon;
import dev.infuse.customs.api.SwordAbilityEvent;
import dev.infuse.customs.sword.AbilitySlot;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
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

    private final CustomsPlugin plugin;
    private final Map<SwordType, SwordAbility> abilities = new EnumMap<>(SwordType.class);

    public AbilityRegistry(CustomsPlugin plugin) {
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
        register(new TridentWeapon(plugin));
        register(new ShurikenWeapon(plugin));
        register(new HalberdWeapon(plugin));
        register(new WarglaiveWeapon(plugin));
        register(new SawbladeWeapon(plugin));
        register(new RapierWeapon(plugin));
        register(new KhopeshWeapon(plugin));
        register(new KatanaWeapon(plugin));
        register(new NeedleWeapon(plugin));
        register(new CandleWeapon(plugin));
        register(new ClaymoreWeapon(plugin));
        register(new MirrorWeapon(plugin));
        register(new CutlassWeapon(plugin));
        register(new ClawsWeapon(plugin));
        register(new DaggersWeapon(plugin));
        register(new MaceWeapon(plugin));
        register(new GlaiveWeapon(plugin));
        register(new MaulWeapon(plugin));
        register(new MantisWeapon(plugin));
        register(new RibbonWeapon(plugin));
        register(new StarsWeapon(plugin));
        register(new AshuraWeapon(plugin));
        register(new DeathKnightWeapon(plugin));
        register(new SplitterWeapon(plugin));
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
            if (!player.hasPermission("customs.use")) {
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
        if (left > 0 && !player.hasPermission("customs.bypass.cooldown")) {
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
        if (fired && plugin.settings().castCircles) {
            plugin.fx().castFlourish(player, type.element(), type.color().value(), slot == AbilitySlot.SECONDARY);
        }
        if (fired && !player.hasPermission("customs.bypass.cooldown")) {
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
