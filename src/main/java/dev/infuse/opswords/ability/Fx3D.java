package dev.infuse.opswords.ability;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Glowing 3D effects drawn with display entities: crescent slashes, shockwave rings, star bursts,
 * soft glows, beams and spell circles. Each is a full-bright model from the resource pack, tinted
 * with any colour, animated with client-side interpolation and faded out by stepping through four
 * fade levels (see tools/fx_models.py).
 */
public final class Fx3D {

    public enum Shape {
        SLASH, RING, BURST, GLOW, BEAM, RUNE;

        final String id = name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final int FADE_LEVELS = 4;
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    private final Displays displays;

    public Fx3D(Displays displays) {
        this.displays = displays;
    }

    public static ItemStack item(Shape shape, int fade, int rgb) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("op_swords", "fx/" + shape.id + "_" + Math.max(0, Math.min(FADE_LEVELS - 1, fade))));
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setColors(List.of(Color.fromRGB(rgb & 0xFFFFFF)));
            meta.setCustomModelDataComponent(cmd);
        });
        return item;
    }

    /** Spawns an effect model; it removes itself after {@code life} ticks. */
    public ItemDisplay spawn(Location at, Shape shape, int rgb, Transformation transform, Display.Billboard billboard,
                             int life) {
        ItemDisplay d = displays.item(at, item(shape, 0, rgb), 1f, new Quaternionf(), ItemDisplay.ItemDisplayTransform.NONE, life);
        d.setTransformation(transform);
        d.setBillboard(billboard);
        d.setBrightness(FULL_BRIGHT);
        d.setShadowRadius(0);
        return d;
    }

    /** Fades an effect out: steps through the fade levels starting at {@code start}, every {@code step} ticks. */
    public void fade(ItemDisplay d, Shape shape, int rgb, int start, int step) {
        for (int level = 1; level < FADE_LEVELS; level++) {
            int l = level;
            displays.later(start + (long) (level - 1) * step, () -> {
                if (d.isValid()) {
                    d.setItemStack(item(shape, l, rgb));
                }
            });
        }
        displays.later(start + (long) (FADE_LEVELS - 1) * step, () -> displays.remove(d));
    }

    /**
     * Hides a glow or burst from players whose eyes are inside it, so an effect on yourself never
     * fills your screen; everyone else still sees it.
     */
    private void hideNear(Display d, Location at, double radius) {
        hideNear(d, at, radius, 0);
    }

    /** As above; if {@code revealAfter} > 0 they see it again after that many ticks (a projectile leaving them). */
    private void hideNear(Display d, Location at, double radius, int revealAfter) {
        double r2 = radius * radius;
        for (org.bukkit.entity.Player p : at.getWorld().getPlayers()) {
            if (p.getEyeLocation().distanceSquared(at) < r2) {
                p.hideEntity(displays.plugin(), d);
                if (revealAfter > 0) {
                    displays.later(revealAfter, () -> {
                        if (d.isValid() && p.isOnline()) {
                            p.showEntity(displays.plugin(), d);
                        }
                    });
                }
            }
        }
    }

    private static Transformation transform(Quaternionf rotation, Vector3f scale) {
        return new Transformation(new Vector3f(), rotation, scale, new Quaternionf());
    }

    private static Location noRotation(Location at) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    /** Rotation turning the model's +X towards {@code forward}, rolled {@code roll} radians about it. */
    private static Quaternionf facing(Vector forward, double roll) {
        Vector f = forward.clone();
        if (f.lengthSquared() < 1.0E-6) {
            f = new Vector(1, 0, 0);
        }
        f.normalize();
        return new Quaternionf().rotationTo(1, 0, 0, (float) f.getX(), (float) f.getY(), (float) f.getZ())
                .rotateX((float) roll);
    }

    // ---------------------------------------------------------------- effects

    /**
     * A crescent slash in front of {@code center}, swept through ~100 degrees in {@code sweep}
     * ticks. {@code roll} tilts the swing (0 = horizontal, PI/2 = vertical).
     */
    public void slash(Location center, Vector forward, double roll, int rgb, float size, int sweep) {
        Quaternionf base = facing(forward, roll);
        Quaternionf from = new Quaternionf(base).rotateY(0.9f);
        Quaternionf to = new Quaternionf(base).rotateY(-0.9f);
        ItemDisplay d = spawn(noRotation(center), Shape.SLASH, rgb, transform(from, new Vector3f(size * 0.8f)),
                Display.Billboard.FIXED, sweep + 12);
        displays.later(1, () -> Displays.animate(d, transform(to, new Vector3f(size * 1.08f)), Math.max(1, sweep)));
        fade(d, Shape.SLASH, rgb, sweep + 1, 2);
    }

    /**
     * A crescent that the caller moves along (a flying slash). Its arc sits {@code 0.94 * size}
     * blocks ahead of the display's position. Fade it with {@link #fade} when done.
     */
    public ItemDisplay movingSlash(Location at, Vector forward, double roll, int rgb, float size, int life) {
        ItemDisplay d = spawn(noRotation(at), Shape.SLASH, rgb, transform(facing(forward, roll), new Vector3f(size)),
                Display.Billboard.FIXED, life);
        d.setTeleportDuration(1);
        return d;
    }

    /** A slash aimed by the player's view, with a random tilt, for melee hits. */
    public void melee(Location eye, Vector look, Location targetCenter, int rgb) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double roll = r.nextBoolean() ? r.nextDouble(-0.6, 0.6) : r.nextDouble(2.5, 3.8);
        // put the slash's centre behind the target so its arc (radius ~0.94 x size) passes through it
        float size = 1.4f;
        Vector dir = look.clone().normalize();
        double distance = targetCenter.toVector().subtract(eye.toVector()).dot(dir);
        Location at = eye.clone().add(dir.multiply(Math.max(0.2, distance - 0.94 * size)));
        slash(at, look, roll, rgb, size, 3);
    }

    /** A flat ring that expands from the centre to {@code radius} blocks and fades. */
    public void shockwave(Location ground, int rgb, float radius, int ticks) {
        ItemDisplay d = spawn(noRotation(ground).add(0, 0.12, 0), Shape.RING, rgb,
                transform(new Quaternionf(), new Vector3f(0.2f, 1, 0.2f)), Display.Billboard.FIXED, ticks + 10);
        displays.later(1, () -> Displays.animate(d, transform(new Quaternionf(), new Vector3f(radius, 1, radius)), ticks));
        fade(d, Shape.RING, rgb, Math.max(2, ticks - 3), 2);
    }

    /** A ring standing upright, facing {@code normal}: a sonic or impact wave. */
    public void wave(Location center, Vector normal, int rgb, float radius, int ticks) {
        Vector n = normal.clone().normalize();
        Quaternionf rot = new Quaternionf().rotationTo(0, 1, 0, (float) n.getX(), (float) n.getY(), (float) n.getZ());
        ItemDisplay d = spawn(noRotation(center), Shape.RING, rgb, transform(rot, new Vector3f(0.2f)),
                Display.Billboard.FIXED, ticks + 10);
        displays.later(1, () -> Displays.animate(d, transform(rot, new Vector3f(radius)), ticks));
        fade(d, Shape.RING, rgb, Math.max(2, ticks - 3), 2);
    }

    /** A star-shaped flare that pops and fades, always facing the viewer. */
    public void burst(Location at, int rgb, float size) {
        float spin = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI);
        ItemDisplay d = spawn(noRotation(at), Shape.BURST, rgb,
                transform(new Quaternionf().rotateZ(spin), new Vector3f(size * 0.2f)), Display.Billboard.CENTER, 16);
        hideNear(d, at, size * 0.6 + 1.4);
        displays.later(1, () -> Displays.animate(d, transform(new Quaternionf().rotateZ(spin + 0.6f), new Vector3f(size)), 3));
        fade(d, Shape.BURST, rgb, 4, 2);
    }

    /** A soft glow that swells and fades, always facing the viewer. */
    public void flash(Location at, int rgb, float size) {
        ItemDisplay d = spawn(noRotation(at), Shape.GLOW, rgb, transform(new Quaternionf(), new Vector3f(size * 0.4f)),
                Display.Billboard.CENTER, 14);
        hideNear(d, at, size * 0.6 + 1.4);
        displays.later(1, () -> Displays.animate(d, transform(new Quaternionf(), new Vector3f(size)), 2));
        fade(d, Shape.GLOW, rgb, 3, 2);
    }

    /** A glowing orb to move along with a projectile; the caller teleports and removes it. */
    public ItemDisplay orb(Location at, int rgb, float size, int life) {
        ItemDisplay d = spawn(noRotation(at), Shape.GLOW, rgb, transform(new Quaternionf(), new Vector3f(size)),
                Display.Billboard.CENTER, life);
        d.setTeleportDuration(2);
        hideNear(d, at, size * 0.6 + 1.2, 4);
        return d;
    }

    /** A glow that rides along with an entity (a vanilla projectile) until it is gone. */
    public void follow(org.bukkit.entity.Entity entity, int rgb, float size, int maxTicks) {
        ItemDisplay glow = orb(entity.getLocation(), rgb, size, maxTicks + 5);
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!entity.isValid() || !glow.isValid() || ticks++ > maxTicks) {
                    if (glow.isValid()) {
                        flash(glow.getLocation(), rgb, size * 1.6f);
                    }
                    displays.remove(glow);
                    cancel();
                    return;
                }
                Location at = entity.getLocation();
                at.setYaw(0);
                at.setPitch(0);
                glow.teleport(at);
            }
        }.runTaskTimer(displays.plugin(), 1L, 1L);
    }

    /**
     * A soft glow that hangs in place for {@code life} ticks, gently swelling and shrinking, then
     * fades: fog, clouds, auras.
     */
    public ItemDisplay haze(Location at, int rgb, float size, int life) {
        ItemDisplay d = spawn(noRotation(at), Shape.GLOW, rgb, transform(new Quaternionf(), new Vector3f(size * 0.3f)),
                Display.Billboard.CENTER, life + 10);
        hideNear(d, at, size * 0.5 + 1.0);
        displays.later(1, () -> Displays.animate(d, transform(new Quaternionf(), new Vector3f(size)), 10));
        for (int t = 20; t < life - 8; t += 20) {
            float s = (t / 20) % 2 == 0 ? size : size * 0.82f;
            displays.later(t, () -> Displays.animate(d, transform(new Quaternionf(), new Vector3f(s)), 20));
        }
        fade(d, Shape.GLOW, rgb, Math.max(2, life - 6), 2);
        return d;
    }

    /**
     * Where a beam fired by a player should start: in front of their weapon hand, so it is seen at
     * an angle (from behind or in first person) instead of end-on.
     */
    public static Location handOrigin(org.bukkit.entity.Player player) {
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector right = new Vector(-look.getZ(), 0, look.getX());
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        return eye.clone().add(look.multiply(0.7)).add(right.multiply(0.38)).subtract(0, 0.38, 0);
    }

    /** A beam from {@code from} to {@code to} that flares and thins out over {@code ticks}. */
    public void beam(Location from, Location to, int rgb, float width, int ticks) {
        Vector dir = to.toVector().subtract(from.toVector());
        float length = (float) dir.length();
        if (length < 0.05) {
            return;
        }
        Location mid = noRotation(from).add(dir.clone().multiply(0.5));
        Quaternionf rot = Displays.pointing(dir);
        ItemDisplay d = spawn(mid, Shape.BEAM, rgb, transform(rot, new Vector3f(width * 1.6f, length, width * 1.6f)),
                Display.Billboard.FIXED, ticks + 10);
        displays.later(1, () -> Displays.animate(d, transform(rot, new Vector3f(width * 0.15f, length, width * 0.15f)), ticks));
        fade(d, Shape.BEAM, rgb, Math.max(2, ticks - 4), 2);
        // soft glows along the beam so it reads from any angle, even looking straight down it
        int glows = Math.min(16, (int) (length / 1.6));
        for (int i = 1; i <= glows; i++) {
            if (length * i / (glows + 1.0) < 2.2) {
                continue; // keep them out of the caster's face
            }
            Location p = noRotation(from).add(dir.clone().multiply(i / (glows + 1.0)));
            ItemDisplay g = spawn(p, Shape.GLOW, rgb, transform(new Quaternionf(), new Vector3f(width * 2.4f)),
                    Display.Billboard.CENTER, ticks + 10);
            displays.later(1, () -> Displays.animate(g, transform(new Quaternionf(), new Vector3f(width * 0.9f)), ticks));
            fade(g, Shape.GLOW, rgb, Math.max(2, ticks - 4), 2);
        }
    }

    /** A spell circle that unfolds on the ground, turns and fades: shown when an ability is cast. */
    public void castCircle(Location feet, int rgb, float radius) {
        Location at = noRotation(feet).add(0, 0.06, 0);
        float yaw = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI);
        ItemDisplay d = spawn(at, Shape.RUNE, rgb, transform(new Quaternionf().rotateY(yaw), new Vector3f(0.1f, 1, 0.1f)),
                Display.Billboard.FIXED, 26);
        displays.later(1, () -> Displays.animate(d,
                transform(new Quaternionf().rotateY(yaw + 0.8f), new Vector3f(radius, 1, radius)), 5));
        displays.later(7, () -> Displays.animate(d,
                transform(new Quaternionf().rotateY(yaw + 1.6f), new Vector3f(radius * 1.15f, 1, radius * 1.15f)), 12));
        fade(d, Shape.RUNE, rgb, 12, 3);
    }

    /** A glow that drifts from {@code from} by {@code offset} over {@code ticks}, then fades: a rising soul, a spark. */
    public void drift(Location from, Vector offset, int rgb, float size, int ticks) {
        ItemDisplay d = orb(from, rgb, size, ticks + 10);
        d.setTeleportDuration(Math.min(59, ticks));
        displays.later(1, () -> {
            if (d.isValid()) {
                d.teleport(noRotation(from).add(offset));
            }
        });
        fade(d, Shape.GLOW, rgb, Math.max(2, ticks - 6), 2);
    }

    /** Burst + glow + ring: a big hit landing. */
    public void impact(Location at, int rgb, float size) {
        burst(at, rgb, size);
        flash(at, rgb, size * 0.8f);
        shockwave(at.clone().subtract(0, 0.9, 0), rgb, size * 1.2f, 6);
    }
}
