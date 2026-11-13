package dev.infuse.opswords.ability;

import dev.infuse.opswords.OPSwordsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Temporary block and item display entities for ability visuals (flying weapons, erupting
 * ground, crystals...). They are never saved with the world, are removed when their time is up
 * or the plugin stops, and any found in a loaded chunk after a crash are deleted.
 */
public final class Displays implements Listener {

    private final OPSwordsPlugin plugin;
    private final NamespacedKey key;
    private final Set<Display> live = new HashSet<>();

    public Displays(OPSwordsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "effect_display");
    }

    public OPSwordsPlugin plugin() {
        return plugin;
    }

    /** A block model, centred on {@code at}, scaled and rotated about its centre. */
    public BlockDisplay block(Location at, BlockData data, Vector3f scale, Quaternionf rotation, int lifeTicks) {
        BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(centered(scale, rotation));
            prepare(d);
        });
        track(display, lifeTicks);
        return display;
    }

    /** An item model centred on {@code at}. */
    public ItemDisplay item(Location at, ItemStack item, float scale, Quaternionf rotation,
                            ItemDisplay.ItemDisplayTransform mode, int lifeTicks) {
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(mode);
            d.setTransformation(new Transformation(new Vector3f(), rotation, new Vector3f(scale), new Quaternionf()));
            prepare(d);
        });
        track(display, lifeTicks);
        return display;
    }

    private void prepare(Display d) {
        d.setPersistent(false);
        d.setTeleportDuration(1);
        d.setViewRange(2.0f);
        d.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    private void track(Display display, int lifeTicks) {
        live.add(display);
        if (lifeTicks > 0) {
            later(lifeTicks, () -> remove(display));
        }
    }

    /** Translation that keeps a [0,1]^3 block model centred on the entity while scaling and rotating it. */
    public static Transformation centered(Vector3f scale, Quaternionf rotation) {
        Vector3f half = new Vector3f(scale).mul(-0.5f);
        Vector3f translation = new Quaternionf(rotation).transform(half);
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    /** Smoothly changes a display's transformation over {@code ticks}. */
    public static void animate(Display display, Transformation to, int ticks) {
        if (!display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(to);
    }

    /** Animates a block display to a new scale/rotation, keeping it centred. */
    public static void animateBlock(BlockDisplay display, Vector3f scale, Quaternionf rotation, Vector3f offset, int ticks) {
        Transformation t = centered(scale, rotation);
        animate(display, new Transformation(t.getTranslation().add(offset), t.getLeftRotation(), t.getScale(),
                t.getRightRotation()), ticks);
    }

    public void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, ticks));
    }

    public void remove(Display display) {
        live.remove(display);
        if (display.isValid()) {
            display.remove();
        }
    }

    /** Rotation that turns a model's +Y axis to point along {@code dir}. */
    public static Quaternionf pointing(Vector dir) {
        Vector n = dir.clone().normalize();
        return new Quaternionf().rotationTo(0, 1, 0, (float) n.getX(), (float) n.getY(), (float) n.getZ());
    }

    /** The solid block a point is standing on (searching a few blocks down), or null. */
    public static @Nullable Block groundBelow(Location point) {
        Block block = point.getBlock();
        for (int i = 0; i < 4; i++) {
            if (block.getType().isSolid() && block.getType().isOccluding()) {
                return block;
            }
            block = block.getRelative(0, -1, 0);
        }
        return null;
    }

    /**
     * A chunk of ground that bursts out of the earth and falls back: spawns a copy of the block
     * under {@code point}, pops it up with a random tumble, then drops and removes it.
     */
    public void eruptGround(Location point, float size, float height) {
        Block ground = groundBelow(point);
        if (ground == null) {
            return;
        }
        BlockData data = ground.getType().isAir() ? Material.DIRT.createBlockData() : ground.getBlockData();
        Location at = ground.getLocation().add(0.5, 0.75, 0.5);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector3f scale = new Vector3f(size);
        BlockDisplay chunk = block(at, data, scale, new Quaternionf(), 16);
        Quaternionf tumble = new Quaternionf().rotateXYZ((float) r.nextDouble(-0.6, 0.6), (float) r.nextDouble(-1, 1),
                (float) r.nextDouble(-0.6, 0.6));
        later(1, () -> animateBlock(chunk, scale, tumble, new Vector3f(0, height, 0), 3));
        later(8, () -> animateBlock(chunk, new Vector3f(size * 0.6f), tumble, new Vector3f(0, -0.6f, 0), 6));
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(key) && !(entity instanceof Display d && live.contains(d))) {
                entity.remove();
            }
        }
    }

    public void shutdown() {
        for (Display display : live) {
            if (display.isValid()) {
                display.remove();
            }
        }
        live.clear();
    }
}
