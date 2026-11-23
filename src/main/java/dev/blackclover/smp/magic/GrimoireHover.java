package dev.blackclover.smp.magic;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.pack.PackItems;
import dev.blackclover.smp.pack.PackModels;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * While a mage holds their grimoire it hovers open beside them, like in the anime. Other players see a
 * floating open book; the holder sees it at their hand through the resource pack's first-person model.
 */
public final class GrimoireHover implements Listener {

    private record Hover(ItemDisplay display, GrimoireType type) {
    }

    private final BlackCloverPlugin plugin;
    private final Map<UUID, Hover> hovers = new HashMap<>();
    private final Map<UUID, Integer> pulse = new HashMap<>();
    private int tick;

    public GrimoireHover(BlackCloverPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTasks() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::update, 1L, 1L);
    }

    private Optional<GrimoireType> heldType(Player p) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return Optional.empty();
        }
        var item = p.getInventory().getItemInMainHand();
        Optional<GrimoireType> type = plugin.grimoires().items().typeOf(item);
        if (type.isEmpty() || !plugin.grimoires().isLive(item) || plugin.grimoires().carried(p).orElse(null) != type.get()) {
            return Optional.empty();
        }
        return type;
    }

    private void update() {
        tick++;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Optional<GrimoireType> type = heldType(p);
            Hover hover = hovers.get(p.getUniqueId());
            if (type.isEmpty()) {
                if (hover != null) {
                    remove(p.getUniqueId());
                }
                continue;
            }
            if (hover == null || !hover.display().isValid() || hover.type() != type.get()
                    || !hover.display().getWorld().equals(p.getWorld())) {
                remove(p.getUniqueId());
                hover = spawn(p, type.get());
            }
            Location at = anchor(p);
            hover.display().teleport(at);
            int beat = pulse.getOrDefault(p.getUniqueId(), 0);
            if (beat > 0) {
                pulse.put(p.getUniqueId(), beat - 1);
            }
            hover.display().setInterpolationDelay(0);
            hover.display().setInterpolationDuration(2);
            hover.display().setTransformation(transform(p, beat > 0 ? 1.18f : 1.0f));
            if (tick % 8 == 0) {
                Fx.particle(p.getWorld(), Particle.ENCHANT, at.clone().add(0, 0.25, 0), 2, 0.2, null);
            }
        }
    }

    /** Beside the player's right shoulder, slightly forward, with a gentle bob. */
    private Location anchor(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        double bob = Math.sin((tick + p.getEntityId() * 7) / 9.0) * 0.06;
        double height = p.isSneaking() ? 1.0 : 1.3;
        return p.getLocation().add(0, height + bob, 0).add(right.multiply(0.75)).add(forward.multiply(0.3));
    }

    private static Transformation transform(Player p, float scale) {
        float yaw = (float) Math.toRadians(p.getLocation().getYaw());
        // the model lies flat with its pages up: tilt the pages toward the player's front, then face their way
        Quaternionf rot = new Quaternionf().rotateY(-yaw).rotateX((float) Math.toRadians(35));
        float s = 0.55f * scale;
        return new Transformation(new Vector3f(), rot, new Vector3f(s, s, s), new Quaternionf());
    }

    private Hover spawn(Player p, GrimoireType type) {
        Location at = anchor(p);
        ItemDisplay display = p.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(PackItems.model(Material.BOOK, PackModels.grimoireKey(type) + "_open"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(2);
            d.setViewRange(0.6f);
            d.setTransformation(transform(p, 1.0f));
        });
        // the holder sees their own open grimoire through the first-person model instead
        p.hideEntity(plugin, display);
        Hover hover = new Hover(display, type);
        hovers.put(p.getUniqueId(), hover);
        return hover;
    }

    /** Called on a successful cast: the book swells for a moment and sheds glowing glyphs. */
    public void onCast(Player p, GrimoireType type) {
        pulse.put(p.getUniqueId(), 4);
        Hover hover = hovers.get(p.getUniqueId());
        Location at = hover != null && hover.display().isValid() ? hover.display().getLocation() : p.getEyeLocation();
        Fx.particle(p.getWorld(), Particle.ENCHANT, at, 14, 0.25, null);
        Fx.particle(p.getWorld(), Particle.DUST, at, 6, 0.2, Fx.dust(type.rgb(), 1.0f));
    }

    private void remove(UUID id) {
        Hover hover = hovers.remove(id);
        if (hover != null) {
            Fx.removeModel(hover.display());
        }
        pulse.remove(id);
    }

    public void removeAll() {
        for (UUID id : Map.copyOf(hovers).keySet()) {
            remove(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // a fresh hover is created on the next tick if they're holding their grimoire
        remove(event.getPlayer().getUniqueId());
    }
}
