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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * While a mage holds their grimoire it hovers open beside them, like in the anime. Everyone sees the same
 * floating book, the holder included: it sits in the corner of their screen with its pages facing them.
 * The resource pack draws the held item itself as nothing, so there is never a second book in the hand.
 */
public final class GrimoireHover implements Listener {

    private record Hover(ItemDisplay display, GrimoireType type) {
    }

    private record Placement(Location at, Transformation transform) {
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

    /** The live grimoire in either hand (main hand first). */
    private Optional<GrimoireType> heldType(Player p) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            return Optional.empty();
        }
        for (ItemStack item : new ItemStack[]{p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()}) {
            Optional<GrimoireType> type = plugin.grimoires().items().typeOf(item);
            if (type.isPresent() && plugin.grimoires().isLive(item) && plugin.grimoires().carried(p).orElse(null) == type.get()) {
                return type;
            }
        }
        return Optional.empty();
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
            int beat = pulse.getOrDefault(p.getUniqueId(), 0);
            if (beat > 0) {
                pulse.put(p.getUniqueId(), beat - 1);
            }
            Placement place = placement(p, beat > 0 ? 1.15f : 1.0f);
            hover.display().teleport(place.at());
            hover.display().setInterpolationDelay(0);
            hover.display().setInterpolationDuration(1);
            hover.display().setTransformation(place.transform());
            if (tick % 6 == 0) {
                Fx.particle(p.getWorld(), Particle.ENCHANT, place.at(), 2, 0.15, null);
            }
        }
    }

    /**
     * Just right of and below the holder's line of sight: the corner of their screen in first person, their
     * shoulder for everyone else. The pages face the holder's eyes with the text upright.
     */
    private Placement placement(Player p, float scale) {
        Location eye = p.getEyeLocation();
        Vector f = eye.getDirection().normalize();
        Vector r = f.clone().crossProduct(new Vector(0, 1, 0));
        if (r.lengthSquared() < 1e-4) {
            double yaw = Math.toRadians(eye.getYaw());
            r = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        r.normalize();
        Vector u = r.clone().crossProduct(f).normalize();
        double bob = Math.sin((tick + p.getEntityId() * 7) / 10.0) * 0.035;
        Vector offset = r.clone().multiply(0.72).add(f.clone().multiply(0.95)).add(u.clone().multiply(-0.24 + bob));
        Location at = eye.clone().add(offset);
        at.setYaw(0);
        at.setPitch(0);

        Vector3f n = new Vector3f((float) -offset.getX(), (float) -offset.getY(), (float) -offset.getZ()).normalize();
        Vector3f up = new Vector3f((float) u.getX(), (float) u.getY(), (float) u.getZ());
        Vector3f spineUp = new Vector3f(up).sub(new Vector3f(n).mul(n.dot(up))).normalize();
        Vector3f z = new Vector3f(spineUp).negate();   // the model's -Z (top of the page text) points up
        Vector3f x = new Vector3f(n).cross(z).normalize();
        Quaternionf rot = new Quaternionf().setFromNormalized(new Matrix3f(x, n, z));
        float s = 0.6f * scale;
        return new Placement(at, new Transformation(new Vector3f(), rot, new Vector3f(s, s, s), new Quaternionf()));
    }

    private Hover spawn(Player p, GrimoireType type) {
        Placement place = placement(p, 1.0f);
        ItemDisplay display = p.getWorld().spawn(place.at(), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(PackItems.model(Material.BOOK, PackModels.grimoireKey(type) + "_open"));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setViewRange(0.75f);
            d.setTransformation(place.transform());
        });
        Hover hover = new Hover(display, type);
        hovers.put(p.getUniqueId(), hover);
        return hover;
    }

    /** Where the holder's grimoire is right now (spells can launch from it). */
    public Optional<Location> bookLocation(Player p) {
        Hover hover = hovers.get(p.getUniqueId());
        return hover != null && hover.display().isValid() ? Optional.of(hover.display().getLocation()) : Optional.empty();
    }

    /** Called on a successful cast: the book swells for a moment and sheds glowing glyphs. */
    public void onCast(Player p, GrimoireType type) {
        pulse.put(p.getUniqueId(), 4);
        Location at = bookLocation(p).orElse(p.getEyeLocation());
        Fx.particle(p.getWorld(), Particle.ENCHANT, at, 16, 0.25, null);
        Fx.particle(p.getWorld(), Particle.DUST, at, 8, 0.2, Fx.dust(type.rgb(), 1.0f));
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
        remove(event.getPlayer().getUniqueId());
    }
}
