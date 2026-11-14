package dev.infuse.customs.ability;

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
        SLASH, RING, BURST, GLOW, BEAM, RUNE, CRACK;

        final String id = name().toLowerCase(java.util.Locale.ROOT);
    }

    private static final int FADE_LEVELS = 4;
    private static final float DROOP = 0.45f;
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    private final Displays displays;

    public Fx3D(Displays displays) {
        this.displays = displays;
    }

    public static ItemStack item(Shape shape, int fade, int rgb) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.editMeta(meta -> {
            meta.setItemModel(new NamespacedKey("customs", "fx/" + shape.id + "_" + Math.max(0, Math.min(FADE_LEVELS - 1, fade))));
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

    /** Rotation turning the crescent's bulge (the model's -X) towards {@code forward}, rolled {@code roll} radians about it. */
    private static Quaternionf facing(Vector forward, double roll) {
        Vector f = forward.clone();
        if (f.lengthSquared() < 1.0E-6) {
            f = new Vector(1, 0, 0);
        }
        f.normalize();
        return new Quaternionf().rotationTo(-1, 0, 0, (float) f.getX(), (float) f.getY(), (float) f.getZ())
                .rotateX((float) roll);
    }

    // ---------------------------------------------------------------- effects

    /**
     * A crescent slash in front of {@code center}, swept through ~100 degrees in {@code sweep}
     * ticks. {@code roll} tilts the swing (0 = horizontal, PI/2 = vertical).
     */
    public void slash(Location center, Vector forward, double roll, int rgb, float size, int sweep) {
        // the outer edge droops a little, so a viewer above the plane sees its face, not its edge
        Quaternionf base = facing(forward, roll).rotateZ(DROOP);
        // a short swing around the target: wide sweeps carry the arc into the attacker's face
        Quaternionf from = new Quaternionf(base).rotateY(0.35f);
        Quaternionf to = new Quaternionf(base).rotateY(-0.35f);
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
        ItemDisplay d = spawn(noRotation(at), Shape.SLASH, rgb, transform(facing(forward, roll).rotateZ(DROOP), new Vector3f(size)),
                Display.Billboard.FIXED, life);
        d.setTeleportDuration(1);
        return d;
    }

    /**
     * A crescent whose tip lands on {@code target}, swung across the line of sight of a viewer
     * looking along {@code view}. {@code swing} turns the swing direction around the view (0 is
     * a horizontal swing). The crescent's plane is tilted {@code tilt} radians off the view so the
     * viewer sees its face, never its edge.
     */
    public void cut(Location target, Vector view, double swing, double tilt, int rgb, float size, int sweep) {
        Vector f = view.clone().normalize();
        Vector right = new Vector(-f.getZ(), 0, f.getX());
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Vector up = right.clone().crossProduct(f).normalize();
        // the swing direction, turned around the view
        Vector sw = right.clone().multiply(Math.cos(swing)).add(up.clone().multiply(Math.sin(swing))).normalize();
        Vector n = f.clone().crossProduct(sw).normalize();
        // the crescent bulges along d: the view, tipped towards n so the plane isn't edge-on
        Vector d = f.clone().multiply(Math.cos(tilt)).add(n.clone().multiply(Math.sin(tilt))).normalize();
        Vector normal = sw.clone().crossProduct(d).normalize();
        // the crescent model bulges towards its -X: map -X to d (and keep the basis right-handed)
        org.joml.Matrix3f basis = new org.joml.Matrix3f(
                (float) -d.getX(), (float) -d.getY(), (float) -d.getZ(),
                (float) -normal.getX(), (float) -normal.getY(), (float) -normal.getZ(),
                (float) sw.getX(), (float) sw.getY(), (float) sw.getZ());
        Quaternionf base = new Quaternionf().setFromNormalized(basis);
        float reach = 0.94f * size;
        Location center = noRotation(target).subtract(d.clone().multiply(reach));
        Quaternionf from = new Quaternionf(base).rotateY(0.3f);
        Quaternionf to = new Quaternionf(base).rotateY(-0.3f);
        ItemDisplay disp = spawn(center, Shape.SLASH, rgb, transform(from, new Vector3f(size * 0.85f)), Display.Billboard.FIXED,
                sweep + 12);
        displays.later(1, () -> Displays.animate(disp, transform(to, new Vector3f(size * 1.05f)), Math.max(1, sweep)));
        fade(disp, Shape.SLASH, rgb, sweep + 2, 2);
    }

    /** How a weapon's melee hits are drawn. */
    public enum MeleeStyle {
        /** A crescent through the target (swords). */
        SLASH,
        /** A wider, heavier crescent (scythe, axe, warglaive, khopesh). */
        HEAVY,
        /** A straight jab of light into the target (spear, rapier, halberd, trident). */
        THRUST,
        /** A burst on the target and a ring on the ground (hammer). */
        IMPACT,
        /** Small spinning cuts (chakram, shuriken, sawblade). */
        SPIN
    }

    /** Dark colours don't glow well; lift them towards white so effects stay visible. */
    public static int bright(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        double luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255;
        if (luminance >= 0.45) {
            return rgb;
        }
        double t = Math.min(0.6, 0.45 - luminance + 0.25);
        red = (int) (red + (255 - red) * t);
        green = (int) (green + (255 - green) * t);
        blue = (int) (blue + (255 - blue) * t);
        return (red << 16) | (green << 8) | blue;
    }

    /** A melee hit drawn in the weapon's style. */
    public void melee(Location eye, Vector look, Location targetCenter, int rgb, int accentColor, MeleeStyle style) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int accent = bright(accentColor);
        rgb = bright(rgb);
        Vector dir = look.clone().normalize();
        switch (style) {
            case HEAVY -> cut(targetCenter, look, r.nextDouble(-0.5, 0.5) + (r.nextBoolean() ? 0 : Math.PI), 0.5, rgb, 1.7f, 4);
            case THRUST -> {
                Vector right = new Vector(-dir.getZ(), 0, dir.getX());
                if (right.lengthSquared() < 1.0E-4) {
                    right = new Vector(1, 0, 0);
                }
                right.normalize();
                Location from = eye.clone().add(dir.clone().multiply(0.8)).add(right.multiply(0.25)).subtract(0, 0.3, 0);
                Location to = targetCenter.clone().add(dir.clone().multiply(0.8));
                beam(from, to, rgb, 0.9f, 5);
                burst(targetCenter, rgb, 1.5f);
                wave(targetCenter.clone().subtract(dir.clone().multiply(0.3)), dir, accent, 0.9f, 4);
            }
            case IMPACT -> {
                burst(targetCenter, rgb, 1.6f);
                Location feet = targetCenter.clone().subtract(0, 0.9, 0);
                shockwave(feet, accent, 1.6f, 5);
            }
            case SPIN -> {
                for (int i = 0; i < 2; i++) {
                    double roll = r.nextDouble(0, Math.PI * 2);
                    int color = i == 0 ? rgb : accent;
                    displays.later(i * 2L, () -> cut(targetCenter, look, roll, 0.6, color, 0.9f, 2));
                }
            }
            default -> melee(eye, look, targetCenter, rgb);
        }
    }

    /** A slash aimed by the player's view, with a random tilt, for melee hits. */
    public void melee(Location eye, Vector look, Location targetCenter, int rgb) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double roll = r.nextBoolean() ? r.nextDouble(-0.6, 0.6) : r.nextDouble(2.5, 3.8);
        // put the slash's centre behind the target so its arc (radius ~0.94 x size) passes through it
        cut(targetCenter, look, roll, 0.5, rgb, 1.3f, 3);
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
            // a pillar ending at someone's feet would otherwise fill their screen
            hideNear(g, p, width * 1.4 + 1.3);
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

    // ---------------------------------------------------------------- signature effects

    /** Glowing cracks spreading over the ground from {@code ground}, lingering a moment. */
    public void crack(Location ground, int rgb, float size, int life) {
        float yaw = (float) (ThreadLocalRandom.current().nextDouble() * Math.PI * 2);
        Location at = noRotation(ground).add(0, 0.04, 0);
        ItemDisplay d = spawn(at, Shape.CRACK, rgb, transform(new Quaternionf().rotateY(yaw), new Vector3f(size * 0.3f, 1, size * 0.3f)),
                Display.Billboard.FIXED, life + 10);
        displays.later(1, () -> Displays.animate(d, transform(new Quaternionf().rotateY(yaw), new Vector3f(size, 1, size)), 4));
        fade(d, Shape.CRACK, rgb, Math.max(6, life - 6), 3);
    }

    /**
     * A jagged lightning bolt from {@code from} to {@code to}: segments of beam with random kinks,
     * and a few short forks.
     */
    public void bolt(Location from, Location to, int rgb, float width, int ticks, int forks) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector total = to.toVector().subtract(from.toVector());
        double length = total.length();
        if (length < 0.2) {
            return;
        }
        Vector dir = total.clone().multiply(1 / length);
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() < 1.0E-4) {
            side = new Vector(1, 0, 0);
        }
        side.normalize();
        Vector up = side.clone().crossProduct(dir).normalize();
        int segments = Math.max(2, (int) Math.ceil(length / 1.3));
        double jitter = Math.min(0.9, length * 0.06 + 0.25);
        Location prev = from.clone();
        java.util.List<Location> joints = new java.util.ArrayList<>();
        for (int i = 1; i <= segments; i++) {
            Location next = from.clone().add(dir.clone().multiply(length * i / segments));
            if (i < segments) {
                next.add(side.clone().multiply(r.nextDouble(-jitter, jitter))).add(up.clone().multiply(r.nextDouble(-jitter, jitter)));
                joints.add(next.clone());
            }
            beamSegment(prev, next, rgb, width, ticks);
            prev = next;
        }
        for (int f = 0; f < forks && !joints.isEmpty(); f++) {
            Location start = joints.get(r.nextInt(joints.size()));
            Vector fork = dir.clone().multiply(r.nextDouble(0.4, 1.0))
                    .add(side.clone().multiply(r.nextDouble(-1, 1))).add(up.clone().multiply(r.nextDouble(-1, 1)))
                    .normalize().multiply(r.nextDouble(1.0, 2.2));
            Location mid = start.clone().add(fork.clone().multiply(0.5)).add(side.clone().multiply(r.nextDouble(-0.3, 0.3)));
            beamSegment(start, mid, rgb, width * 0.6f, ticks);
            beamSegment(mid, start.clone().add(fork), rgb, width * 0.45f, ticks);
        }
        flash(to, rgb, width * 3.5f);
    }

    /** One straight piece of beam with no glow orbs (used to build bolts). */
    private void beamSegment(Location from, Location to, int rgb, float width, int ticks) {
        Vector dir = to.toVector().subtract(from.toVector());
        float length = (float) dir.length();
        if (length < 0.05) {
            return;
        }
        Location mid = noRotation(from).add(dir.clone().multiply(0.5));
        Quaternionf rot = Displays.pointing(dir);
        ItemDisplay d = spawn(mid, Shape.BEAM, rgb, transform(rot, new Vector3f(width * 1.4f, length + 0.08f, width * 1.4f)),
                Display.Billboard.FIXED, ticks + 8);
        displays.later(1, () -> Displays.animate(d, transform(rot, new Vector3f(width * 0.3f, length + 0.08f, width * 0.3f)), ticks));
        fade(d, Shape.BEAM, rgb, Math.max(2, ticks - 2), 2);
    }

    /** A column of real fire (or soul fire) that bursts out of the ground and sinks back. */
    public org.bukkit.entity.BlockDisplay flamePillar(Location ground, float width, float height, int life, boolean soul) {
        org.bukkit.block.data.BlockData fire = (soul ? org.bukkit.Material.SOUL_FIRE : org.bukkit.Material.FIRE).createBlockData();
        Location at = noRotation(ground).add(0, 0.02, 0);
        Quaternionf turn = new Quaternionf().rotateY((float) (ThreadLocalRandom.current().nextDouble() * Math.PI));
        org.bukkit.entity.BlockDisplay d = displays.block(at, fire, new Vector3f(width, 0.05f, width), turn, life + 12);
        d.setBrightness(FULL_BRIGHT);
        // block displays are centred by Displays.block; lift so the fire stands on the ground
        displays.later(1, () -> Displays.animateBlock(d, new Vector3f(width, height, width), turn, new Vector3f(0, height / 2, 0), 4));
        displays.later(Math.max(6, life - 6), () -> Displays.animateBlock(d, new Vector3f(width * 0.4f, 0.05f, width * 0.4f), turn,
                new Vector3f(), 6));
        return d;
    }

    /**
     * A tapered spike of {@code data} (three stacked, shrinking blocks) that thrusts out of the
     * ground, holds, and sinks back. Leans by {@code tilt} radians in a random direction.
     */
    public void spike(Location ground, org.bukkit.block.data.BlockData data, float height, float width, double tilt, int life) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = noRotation(ground);
        float yaw = (float) r.nextDouble(Math.PI * 2);
        Quaternionf lean = new Quaternionf().rotateY(yaw).rotateX((float) tilt).rotateY((float) (Math.PI / 4));
        float[] widths = {width, width * 0.66f, width * 0.36f};
        float[] heights = {height * 0.45f, height * 0.35f, height * 0.3f};
        float y = 0;
        for (int i = 0; i < 3; i++) {
            float w = widths[i];
            float h = heights[i];
            float centre = y + h / 2;
            Vector3f offset = lean.transform(new Vector3f(0, centre, 0));
            Vector3f hidden = lean.transform(new Vector3f(0, centre - height - 0.2f, 0));
            org.bukkit.entity.BlockDisplay seg = displays.block(base, data, new Vector3f(w, h, w), lean, life + 14);
            Displays.animateBlock(seg, new Vector3f(w, h, w), lean, hidden, 0);
            int delay = 1 + i;
            displays.later(delay, () -> Displays.animateBlock(seg, new Vector3f(w, h, w), lean, offset, 3));
            displays.later(Math.max(8, life - 2), () -> Displays.animateBlock(seg, new Vector3f(w * 0.8f, h, w * 0.8f), lean, hidden, 6));
            y += h;
        }
    }

    /**
     * A tendril: a short train of glowing orbs that snakes along a curve from {@code from} to the
     * {@code to} supplier (a moving target is followed), in {@code ticks}.
     */
    public void tendril(Location from, java.util.function.Supplier<Location> to, int rgb, float size, int ticks, Runnable arrive) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector bend = new Vector(r.nextDouble(-1, 1), r.nextDouble(0.3, 1.2), r.nextDouble(-1, 1)).multiply(1.6);
        int count = 5;
        java.util.List<ItemDisplay> train = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemDisplay orb = spawn(noRotation(from), Shape.GLOW, rgb, transform(new Quaternionf(), new Vector3f(size * (1 - i * 0.15f))),
                    Display.Billboard.CENTER, ticks + 14);
            orb.setTeleportDuration(1);
            hideNear(orb, from, 1.8, 4);
            train.add(orb);
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                Location end = to.get();
                if (end == null || end.getWorld() != from.getWorld() || t > ticks + count) {
                    if (end != null && arrive != null) {
                        arrive.run();
                    }
                    train.forEach(o -> fade(o, Shape.GLOW, rgb, 1, 1));
                    cancel();
                    return;
                }
                Vector a = from.toVector();
                Vector b = end.toVector();
                Vector c = a.clone().add(b).multiply(0.5).add(bend);
                for (int i = 0; i < train.size(); i++) {
                    double u = Math.max(0, Math.min(1, (t - i) / (double) ticks));
                    Vector p = a.clone().multiply((1 - u) * (1 - u)).add(c.clone().multiply(2 * u * (1 - u))).add(b.clone().multiply(u * u));
                    ItemDisplay orb = train.get(i);
                    if (orb.isValid()) {
                        orb.teleport(p.toLocation(from.getWorld()));
                    }
                }
            }
        }.runTaskTimer(displays.plugin(), 1L, 1L);
    }

    /** A portal standing upright and facing {@code facing}: a turning rune ring around a dark heart. */
    public void rift(Location center, Vector facing, int rgb, float size, int life) {
        Vector f = facing.clone().setY(0);
        if (f.lengthSquared() < 1.0E-4) {
            f = new Vector(0, 0, 1);
        }
        f.normalize();
        Quaternionf stand = new Quaternionf().rotationTo(0, 1, 0, (float) f.getX(), 0, (float) f.getZ());
        Location at = noRotation(center);
        ItemDisplay ring = spawn(at, Shape.RUNE, rgb, transform(new Quaternionf(stand), new Vector3f(0.1f)), Display.Billboard.FIXED, life + 12);
        displays.later(1, () -> Displays.animate(ring, transform(new Quaternionf(stand).rotateY(1.2f), new Vector3f(size)), 6));
        displays.later(8, () -> Displays.animate(ring, transform(new Quaternionf(stand).rotateY(3.0f), new Vector3f(size * 1.05f)),
                Math.max(1, life - 8)));
        fade(ring, Shape.RUNE, rgb, Math.max(8, life - 4), 2);
        ItemDisplay heart = spawn(at, Shape.GLOW, 0x1A0A33, transform(new Quaternionf(), new Vector3f(0.2f)), Display.Billboard.CENTER, life + 12);
        displays.later(1, () -> Displays.animate(heart, transform(new Quaternionf(), new Vector3f(size * 0.8f)), 6));
        fade(heart, Shape.GLOW, 0x1A0A33, Math.max(8, life - 4), 2);
        ItemDisplay rim = spawn(at, Shape.GLOW, rgb, transform(new Quaternionf(), new Vector3f(0.2f)), Display.Billboard.CENTER, life + 12);
        displays.later(1, () -> Displays.animate(rim, transform(new Quaternionf(), new Vector3f(size * 1.25f)), 6));
        fade(rim, Shape.GLOW, rgb, Math.max(8, life - 4), 2);
    }

    /** A head (dragon head, skull...) that glows in {@code rgb}: the caller moves and removes it. */
    public ItemDisplay spectralHead(Location at, org.bukkit.Material head, int rgb, float size, Vector facing, int life) {
        Vector f = facing.clone().normalize();
        Location spot = noRotation(at);
        ItemDisplay d = displays.item(spot, ItemStack.of(head), size, headRotation(f), ItemDisplay.ItemDisplayTransform.HEAD, life);
        d.setGlowing(true);
        d.setGlowColorOverride(Color.fromRGB(rgb & 0xFFFFFF));
        d.setBrightness(FULL_BRIGHT);
        d.setTeleportDuration(2);
        return d;
    }

    /** Rotation that turns a worn head to look along {@code dir}. */
    public static Quaternionf headRotation(Vector dir) {
        Vector f = dir.clone().normalize();
        float yaw = (float) Math.atan2(-f.getX(), f.getZ());
        float pitch = (float) Math.asin(Math.max(-1, Math.min(1, -f.getY())));
        return new Quaternionf().rotateY(-yaw + (float) Math.PI).rotateX(-pitch);
    }

    /** Particles winding up around {@code base} in a helix. */
    public void helix(Location base, double radius, double height, int strands, org.bukkit.Particle particle, Object data, int ticks) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (t++ >= ticks) {
                    cancel();
                    return;
                }
                double y = height * t / ticks;
                for (int s = 0; s < strands; s++) {
                    double a = t * 0.55 + s * Math.PI * 2 / strands;
                    double rr = radius * (1 - 0.4 * t / ticks);
                    Location p = base.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr);
                    if (data == null) {
                        Fx.particle(p, particle, 1, 0, 0);
                    } else {
                        Fx.particle(p, particle, 1, 0, 0, data);
                    }
                }
            }
        }.runTaskTimer(displays.plugin(), 0L, 1L);
    }

    /** The element a weapon's magic belongs to: decides how casting an ability looks. */
    public enum Element { FIRE, FROST, STORM, WATER, NATURE, ARCANE, SHADOW, BLOOD, MARTIAL }

    /** What casting an ability looks like for this element (instead of one circle for everything). */
    public void castFlourish(org.bukkit.entity.Player player, Element element, int rgb, boolean big) {
        Location feet = player.getLocation();
        Location chest = feet.clone().add(0, 1.1, 0);
        int glow = bright(rgb);
        switch (element) {
            case FIRE -> {
                helix(feet, 0.9, 2.2, 2, org.bukkit.Particle.FLAME, null, 10);
                for (int i = 0; i < (big ? 4 : 2); i++) {
                    double a = i * Math.PI * 2 / (big ? 4 : 2) + ThreadLocalRandom.current().nextDouble();
                    flamePillar(feet.clone().add(Math.cos(a) * 1.1, 0, Math.sin(a) * 1.1), 0.7f, big ? 1.6f : 1.1f, 10, false);
                }
            }
            case FROST -> {
                Fx.particle(chest, org.bukkit.Particle.SNOWFLAKE, 25, 0.5, 0.06);
                if (big) {
                    for (int i = 0; i < 5; i++) {
                        double a = i * Math.PI * 2 / 5;
                        spike(feet.clone().add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4), org.bukkit.Material.PACKED_ICE.createBlockData(),
                                1.1f, 0.4f, 0.35, 16);
                    }
                }
                shockwave(feet, 0xD8F6FF, big ? 2.4f : 1.4f, 6);
            }
            case STORM -> {
                for (int i = 0; i < (big ? 3 : 2); i++) {
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                    Location tip = chest.clone().add(Math.cos(a) * 1.3, ThreadLocalRandom.current().nextDouble(-0.6, 0.9), Math.sin(a) * 1.3);
                    bolt(chest.clone().add(Math.cos(a) * 0.4, 0, Math.sin(a) * 0.4), tip, glow, 0.22f, 3, 0);
                }
                Fx.particle(chest, org.bukkit.Particle.ELECTRIC_SPARK, 20, 0.5, 0.2);
            }
            case WATER -> {
                Fx.particle(feet.clone().add(0, 0.2, 0), org.bukkit.Particle.SPLASH, 40, 0.7, 0.1);
                shockwave(feet, glow, big ? 2.6f : 1.6f, 7);
                helix(feet, 1.0, 1.8, 3, org.bukkit.Particle.DRIPPING_WATER, null, 9);
            }
            case NATURE -> {
                helix(feet, 1.0, 2.0, 3, org.bukkit.Particle.HAPPY_VILLAGER, null, 10);
                Fx.particle(chest, org.bukkit.Particle.CHERRY_LEAVES, 12, 0.6, 0);
            }
            case ARCANE -> castCircle(feet, rgb, big ? 2.2f : 1.5f);
            case SHADOW -> {
                haze(chest, 0x2A1B47, big ? 2.6f : 1.8f, 14);
                Fx.particle(chest, org.bukkit.Particle.LARGE_SMOKE, 14, 0.4, 0.02);
            }
            case BLOOD -> {
                Fx.particle(chest, org.bukkit.Particle.DUST, 30, 0.5, 0, Fx.dust(0x8A0A1E, 1.4f));
                shockwave(feet, glow, big ? 2.4f : 1.5f, 6);
            }
            case MARTIAL -> {
                Vector look = player.getLocation().getDirection().setY(0);
                if (look.lengthSquared() < 1.0E-4) {
                    look = new Vector(0, 0, 1);
                }
                slash(feet.clone().add(0, 0.25, 0), look, 0, glow, big ? 1.6f : 1.2f, 3);
                Fx.particle(feet.clone().add(0, 0.1, 0), org.bukkit.Particle.CLOUD, 8, 0.4, 0.02);
            }
        }
    }

    /** A glowing copy of a weapon pointing along {@code dir} that lingers and shrinks away: an afterimage. */
    public void afterimage(Location at, ItemStack weapon, Vector dir, int rgb, float scale, int life) {
        Quaternionf rot = Displays.pointing(dir);
        ItemDisplay d = displays.item(noRotation(at), weapon, scale, rot, ItemDisplay.ItemDisplayTransform.NONE, life + 8);
        d.setGlowing(true);
        d.setGlowColorOverride(Color.fromRGB(rgb & 0xFFFFFF));
        d.setBrightness(FULL_BRIGHT);
        hideNear(d, at, 1.2);
        displays.later(life, () -> Displays.animate(d, transform(rot, new Vector3f(scale * 0.15f)), 6));
    }

    /**
     * An upright rune sigil standing at {@code at}, facing along {@code facing}. The caller moves
     * (teleports) and removes it.
     */
    public ItemDisplay sigil(Location at, Vector facing, int rgb, float size, int life) {
        Vector f = facing.clone();
        if (f.lengthSquared() < 1.0E-4) {
            f = new Vector(0, 0, 1);
        }
        f.normalize();
        Quaternionf stand = new Quaternionf().rotationTo(0, 1, 0, (float) f.getX(), (float) f.getY(), (float) f.getZ());
        ItemDisplay d = spawn(noRotation(at), Shape.RUNE, rgb, transform(stand, new Vector3f(0.1f)), Display.Billboard.FIXED, life);
        d.setTeleportDuration(1);
        displays.later(1, () -> Displays.animate(d, transform(stand, new Vector3f(size)), 4));
        return d;
    }

    /** Burst + glow + ring: a big hit landing. */
    public void impact(Location at, int rgb, float size) {
        burst(at, rgb, size);
        flash(at, rgb, size * 0.8f);
        shockwave(at.clone().subtract(0, 0.9, 0), rgb, size * 1.2f, 6);
    }
}
