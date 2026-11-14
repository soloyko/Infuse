package dev.infuse.customs.ability;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Something solid strung between two moving points: a chain, a ribbon, a rope. Made of block
 * display segments that follow a sagging, optionally rippling curve; call {@link #update} every
 * tick with the two ends and {@link #remove} when done.
 */
public final class Tether {

    private final Displays displays;
    private final BlockData[] looks;
    private final float width;
    private final float thickness;
    private final double segment;
    private final @Nullable Integer glow;
    private final boolean alternate;
    private final int life;
    private final List<BlockDisplay> parts = new ArrayList<>();

    /**
     * @param looks     block data per segment, cycled (e.g. pink and white wool for a ribbon)
     * @param width     segment width (the flat side faces up for ribbons)
     * @param thickness segment depth
     * @param segment   length of one segment
     * @param glow      outline colour, or null for none
     * @param alternate turn every other segment a quarter (chain links)
     * @param life      ticks before the segments clean themselves up regardless
     */
    public Tether(Displays displays, BlockData[] looks, float width, float thickness, double segment,
                  @Nullable Integer glow, boolean alternate, int life) {
        this.displays = displays;
        this.looks = looks;
        this.width = width;
        this.thickness = thickness;
        this.segment = segment;
        this.glow = glow;
        this.alternate = alternate;
        this.life = life;
    }

    /**
     * Strings the tether from {@code from} to {@code to}. {@code sag} is how far the middle hangs
     * down, {@code ripple} how much it waves (it travels with {@code tick}).
     */
    public void update(Location from, Location to, double sag, double ripple, int tick) {
        Vector span = to.toVector().subtract(from.toVector());
        double length = span.length();
        int n = Math.max(1, (int) Math.ceil(length / segment));
        while (parts.size() < n) {
            BlockDisplay part = displays.block(from, looks[parts.size() % looks.length], new Vector3f(0.01f), new Quaternionf(), life);
            if (glow != null) {
                part.setGlowing(true);
                part.setGlowColorOverride(Color.fromRGB(glow));
            }
            part.setBrightness(new Display.Brightness(13, 13));
            part.setTeleportDuration(1);
            parts.add(part);
        }
        while (parts.size() > n) {
            displays.remove(parts.remove(parts.size() - 1));
        }
        Vector up = new Vector(0, 1, 0);
        for (int i = 0; i < n; i++) {
            Vector a = point(from, span, i / (double) n, sag, ripple, tick);
            Vector b = point(from, span, (i + 1) / (double) n, sag, ripple, tick);
            Vector dir = b.clone().subtract(a);
            double len = dir.length();
            BlockDisplay part = parts.get(i);
            if (len < 1.0E-3 || !part.isValid()) {
                continue;
            }
            dir.multiply(1 / len);
            // the segment's long axis along the curve, its flat side kept level
            Vector side = dir.clone().crossProduct(up);
            if (side.lengthSquared() < 1.0E-4) {
                side = new Vector(1, 0, 0);
            }
            side.normalize();
            Vector face = side.clone().crossProduct(dir);
            Quaternionf rot = new Matrix3f(
                    (float) side.getX(), (float) side.getY(), (float) side.getZ(),
                    (float) dir.getX(), (float) dir.getY(), (float) dir.getZ(),
                    (float) face.getX(), (float) face.getY(), (float) face.getZ()).getNormalizedRotation(new Quaternionf());
            if (alternate && i % 2 == 1) {
                rot.rotateY((float) (Math.PI / 2));
            }
            if (ripple > 0) {
                rot.rotateY((float) (Math.sin(tick * 0.4 + i * 0.9) * 0.5));
            }
            Location at = a.clone().add(b).multiply(0.5).toLocation(from.getWorld());
            part.teleport(at);
            part.setInterpolationDelay(0);
            part.setInterpolationDuration(1);
            part.setTransformation(Displays.centered(new Vector3f(width, (float) len * 1.04f, thickness), rot));
        }
    }

    private static Vector point(Location from, Vector span, double t, double sag, double ripple, int tick) {
        double drop = Math.sin(t * Math.PI) * sag;
        double wave = ripple * Math.sin(t * Math.PI * 3 + tick * 0.5) * Math.sin(t * Math.PI);
        return from.toVector().add(span.clone().multiply(t)).add(new Vector(0, wave - drop, 0));
    }

    public void remove() {
        for (BlockDisplay part : parts) {
            displays.remove(part);
        }
        parts.clear();
    }
}
