package dev.infuse.opswords.manager;

import dev.infuse.opswords.OPSwordsPlugin;
import dev.infuse.opswords.ability.Aura;
import dev.infuse.opswords.ability.Combat;
import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;
import dev.infuse.opswords.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs every 10 ticks: keeps held-sword potion effects in sync, runs per-sword passive
 * ticks, refreshes outdated items and draws the cooldown HUD.
 */
public final class PassiveManager {

    /** Effects we apply last this long and are topped up every run, so they vanish quickly if the plugin stops. */
    private static final int HELD_DURATION = 300;
    private static final int REFRESH_BELOW = 260;
    private static final int PERIOD = 10;

    private final OPSwordsPlugin plugin;
    private final Map<UUID, Map<PotionEffectType, Integer>> applied = new HashMap<>();
    private final Map<UUID, Integer> hudPausedUntil = new HashMap<>();
    private BukkitTask task;
    private long runs;

    public PassiveManager(OPSwordsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, PERIOD, PERIOD);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        applied.clear();
        hudPausedUntil.clear();
    }

    private void run() {
        runs++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player, true);
        }
    }

    /** Re-evaluates one player right away (for example after they toggle sneak). */
    public void refresh(Player player) {
        update(player, false);
    }

    private void update(Player player, boolean fullTick) {
        if (fullTick && !player.isFlying() && !player.isGliding()) {
            Block below = player.getLocation().subtract(0, 0.2, 0).getBlock();
            if (below.getType().isSolid() && Combat.fits(player.getLocation())) {
                plugin.status().recordSafeSpot(player);
            }
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        SwordType type = plugin.items().typeOf(hand);
        if (type != null && fullTick && plugin.items().isOutdated(hand, type)) {
            plugin.items().refresh(hand, type);
            player.getInventory().setItemInMainHand(hand);
        }

        boolean usable = type != null && plugin.canUse(player, type);
        List<PotionEffect> wanted = new ArrayList<>();
        if (usable) {
            plugin.abilities().get(type).heldEffects(player, wanted);
        }
        syncEffects(player, wanted);

        if (!fullTick) {
            return;
        }
        for (CooldownManager.Ready ready : plugin.cooldowns().pollReady(player.getUniqueId())) {
            if (ready.type() == type && usable) {
                announceReady(player, ready);
            }
        }
        if (type == null) {
            return;
        }
        if (usable) {
            plugin.abilities().get(type).tickHeld(player, runs);
            if (plugin.settings().heldAura) {
                Aura.play(player, type, runs);
            }
        }
        if (plugin.settings().actionBarHud && usable && hudPausedUntil.getOrDefault(player.getUniqueId(), 0) <= Bukkit.getCurrentTick()) {
            player.sendActionBar(hud(player, type));
        }
    }

    private void syncEffects(Player player, List<PotionEffect> wanted) {
        UUID id = player.getUniqueId();
        Map<PotionEffectType, Integer> previous = applied.getOrDefault(id, Map.of());
        Map<PotionEffectType, Integer> now = new HashMap<>();

        for (PotionEffect want : wanted) {
            PotionEffectType type = want.getType();
            int amp = want.getAmplifier();
            now.put(type, amp);
            PotionEffect current = player.getPotionEffect(type);
            boolean ours = current != null && isOurs(current, amp);
            if (current == null || current.getAmplifier() < amp || (ours && current.getDuration() < REFRESH_BELOW)) {
                player.addPotionEffect(new PotionEffect(type, HELD_DURATION, amp, true, false, true));
            }
        }
        for (Map.Entry<PotionEffectType, Integer> old : previous.entrySet()) {
            if (!now.containsKey(old.getKey())) {
                PotionEffect current = player.getPotionEffect(old.getKey());
                if (current != null && isOurs(current, old.getValue())) {
                    player.removePotionEffect(old.getKey());
                }
            }
        }
        if (now.isEmpty()) {
            applied.remove(id);
        } else {
            applied.put(id, now);
        }
    }

    private static boolean isOurs(PotionEffect effect, int amplifier) {
        return effect.getAmplifier() == amplifier && effect.isAmbient() && !effect.hasParticles()
                && !effect.isInfinite() && effect.getDuration() <= HELD_DURATION;
    }

    /** Removes every held-sword effect from the player. */
    public void clear(Player player) {
        syncEffects(player, List.of());
        hudPausedUntil.remove(player.getUniqueId());
    }

    /** Shows a message on the action bar and pauses the HUD so it stays readable. */
    public void actionBar(Player player, Component message) {
        player.sendActionBar(message);
        hudPausedUntil.put(player.getUniqueId(), Bukkit.getCurrentTick() + 30);
    }

    private void announceReady(Player player, CooldownManager.Ready ready) {
        if (plugin.settings().readySound) {
            float pitch = ready.slot() == AbilitySlot.PRIMARY ? 1.6f : 1.2f;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.PLAYERS, 0.6f, pitch);
        }
        if (plugin.settings().actionBarHud) {
            actionBar(player, Text.plain("✔ ", NamedTextColor.GREEN)
                    .append(Text.plain(ready.type().ability(ready.slot()).name(), ready.type().color()))
                    .append(Text.plain(" is ready", NamedTextColor.GRAY)));
        }
    }

    private Component hud(Player player, SwordType type) {
        return Text.plain(type.emoji() + "  ", type.color())
                .append(slot(player, type, AbilitySlot.PRIMARY))
                .append(Text.plain("   │   ", NamedTextColor.DARK_GRAY))
                .append(slot(player, type, AbilitySlot.SECONDARY));
    }

    private static final int BAR = 10;

    private Component slot(Player player, SwordType type, AbilitySlot slot) {
        long left = plugin.cooldowns().remaining(player.getUniqueId(), type, slot);
        Component label = slot.shortLabel(plugin.settings().trigger).color(NamedTextColor.GRAY)
                .append(Text.plain(" ", NamedTextColor.GRAY));
        String name = type.ability(slot).name();
        if (left <= 0) {
            return label.append(Text.plain(name, type.color())).append(Text.plain(" ✔", NamedTextColor.GREEN));
        }
        int filled = (int) Math.floor(plugin.cooldowns().progress(player.getUniqueId(), type, slot) * BAR);
        return label.append(Text.plain(name + " ", NamedTextColor.DARK_GRAY))
                .append(Text.plain("▮".repeat(filled), type.accent()))
                .append(Text.plain("▮".repeat(BAR - filled), NamedTextColor.DARK_GRAY))
                .append(Text.plain(" " + Text.seconds(left), NamedTextColor.GRAY));
    }
}
