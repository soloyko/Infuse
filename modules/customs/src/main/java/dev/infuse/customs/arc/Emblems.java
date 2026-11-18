package dev.infuse.customs.arc;

import dev.infuse.customs.CustomsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Floating labels over players' names ("LEADER", "BOUNTY"). They follow their player every tick
 * instead of riding them: a player carrying passengers can't change worlds, so riding labels
 * would break portals and other plugins' teleports. Hidden from the player themselves, and from
 * everyone while the player is invisible, dead or spectating.
 */
final class Emblems {

    private record Label(TextDisplay display, double height) {
    }

    private final CustomsPlugin plugin;
    private final Map<UUID, Label> labels = new HashMap<>();
    private @Nullable BukkitTask task;

    Emblems(CustomsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Shows (or updates) a label {@code height} blocks above a player's feet. */
    void set(Player player, Component text, double height) {
        Label label = labels.get(player.getUniqueId());
        if (label != null && label.display().isValid() && label.display().getWorld() == player.getWorld()) {
            label.display().text(text);
            if (label.height() != height) {
                labels.put(player.getUniqueId(), new Label(label.display(), height));
            }
            return;
        }
        if (label != null) {
            label.display().remove();
        }
        TextDisplay display = player.getWorld().spawn(spot(player, height), TextDisplay.class, t -> {
            t.text(text);
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setTeleportDuration(2);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.85f), new Quaternionf()));
        });
        player.hideEntity(plugin, display);
        labels.put(player.getUniqueId(), new Label(display, height));
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    void remove(UUID id) {
        Label label = labels.remove(id);
        if (label != null) {
            label.display().remove();
        }
        if (labels.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    boolean has(UUID id) {
        return labels.containsKey(id);
    }

    void clear() {
        for (Label label : labels.values()) {
            label.display().remove();
        }
        labels.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static Location spot(Player player, double height) {
        Location at = player.getLocation().add(0, height - (player.isSneaking() ? 0.3 : 0), 0);
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    private void tick() {
        Iterator<Map.Entry<UUID, Label>> it = labels.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Label> e = it.next();
            Player player = Bukkit.getPlayer(e.getKey());
            TextDisplay display = e.getValue().display();
            if (player == null || !display.isValid()) {
                display.remove();
                it.remove();
                continue;
            }
            if (display.getWorld() != player.getWorld()) {
                // spawned again in the new world on the next set(); for now, follow by recreating
                display.remove();
                it.remove();
                continue;
            }
            boolean hidden = player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()
                    || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
            if (display.isVisibleByDefault() == hidden) {
                display.setVisibleByDefault(!hidden);
            }
            display.teleport(spot(player, e.getValue().height()));
        }
        if (labels.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }
}
