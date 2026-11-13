package dev.infuse.customs.ability;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Numbers that pop out of whatever a weapon hits and float away. Ability hits are written in the
 * weapon's colour, critical swings in gold. Rapid hits on the same target (beams, flurries,
 * damage over time) add up on one number instead of stacking dozens of them.
 */
public final class DamageNumbers {

    /** Hits this close together (in ticks) on the same target add to the same number. */
    private static final int MERGE_TICKS = 10;
    private static final int LIFE_TICKS = 24;
    private static final TextColor CRIT = TextColor.color(0xFFD24A);
    private static final TextColor MELEE = TextColor.color(0xFFFFFF);

    private final Displays displays;
    private final Map<UUID, Entry> recent = new HashMap<>();

    private static final class Entry {
        final TextDisplay display;
        final UUID attacker;
        final int rgb;
        double total;
        int hits;
        int last;
        boolean crit;
        boolean ability;

        Entry(TextDisplay display, UUID attacker, int rgb) {
            this.display = display;
            this.attacker = attacker;
            this.rgb = rgb;
        }
    }

    public DamageNumbers(Displays displays) {
        this.displays = displays;
    }

    public void show(Player attacker, LivingEntity target, double amount, int rgb, boolean crit, boolean ability) {
        int now = Bukkit.getCurrentTick();
        cleanup(now);
        Entry entry = recent.get(target.getUniqueId());
        if (entry != null && entry.display.isValid() && entry.attacker.equals(attacker.getUniqueId())
                && now - entry.last <= MERGE_TICKS) {
            entry.total += amount;
            entry.hits++;
            entry.last = now;
            entry.crit |= crit;
            entry.ability |= ability;
            entry.display.text(label(entry));
            // a little pop each time it goes up
            float size = size(entry) * 1.25f;
            Displays.animate(entry.display, at(size, 0.3f), 2);
            displays.later(3, () -> settle(entry));
            return;
        }
        TextDisplay display = displays.text(spot(attacker, target), Component.empty(), 0.15f, 0);
        Entry fresh = new Entry(display, attacker.getUniqueId(), Fx3D.bright(rgb));
        fresh.total = amount;
        fresh.hits = 1;
        fresh.last = now;
        fresh.crit = crit;
        fresh.ability = ability;
        display.text(label(fresh));
        recent.put(target.getUniqueId(), fresh);
        Displays.animate(display, at(size(fresh) * 1.3f, 0.28f), 3);
        displays.later(4, () -> settle(fresh));
        tick(fresh);
    }

    /** After a pop: shrink back slightly while drifting up. */
    private void settle(Entry entry) {
        if (!entry.display.isValid()) {
            return;
        }
        int left = Math.max(4, entry.last + LIFE_TICKS - Bukkit.getCurrentTick());
        Displays.animate(entry.display, at(size(entry), 0.75f), left);
    }

    /** Fades the number once nothing has added to it for a while, then removes it. */
    private void tick(Entry entry) {
        displays.later(2, () -> {
            if (!entry.display.isValid()) {
                return;
            }
            int age = Bukkit.getCurrentTick() - entry.last;
            if (age >= LIFE_TICKS) {
                displays.remove(entry.display);
                return;
            }
            int fadeFrom = LIFE_TICKS - 8;
            if (age > fadeFrom) {
                double t = (age - fadeFrom) / 8.0;
                entry.display.setTextOpacity((byte) (int) Math.round(255 - t * 190));
            } else {
                entry.display.setTextOpacity((byte) -1);
            }
            tick(entry);
        });
    }

    private void cleanup(int now) {
        Iterator<Entry> it = recent.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (!e.display.isValid() || now - e.last > LIFE_TICKS + 4) {
                it.remove();
            }
        }
    }

    private static float size(Entry entry) {
        float base = entry.crit ? 1.7f : 1.35f;
        // bigger numbers for bigger hits, within reason
        return base + (float) Math.min(0.7, entry.total / 30.0);
    }

    private static Transformation at(float scale, float rise) {
        return new Transformation(new Vector3f(0, rise, 0), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    private static Component label(Entry entry) {
        String number = format(entry.total);
        TextColor color = entry.crit && !entry.ability ? CRIT : entry.ability ? TextColor.color(entry.rgb) : MELEE;
        Component text = Component.text(number, color);
        if (entry.crit) {
            text = Component.text("✸ ", CRIT).append(text.decorate(TextDecoration.BOLD));
        }
        if (entry.hits > 2) {
            text = text.append(Component.text(" ×" + entry.hits, TextColor.color(0xBFBFBF)));
        }
        return text;
    }

    static String format(double damage) {
        double rounded = Math.round(damage * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /** Just above the target, nudged towards the attacker so the body doesn't hide it. */
    private static Location spot(Player attacker, LivingEntity target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location at = target.getLocation().add(0, target.getHeight() + 0.15, 0);
        Vector toward = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (toward.lengthSquared() > 1e-4) {
            toward.normalize();
            Vector side = new Vector(-toward.getZ(), 0, toward.getX());
            at.add(toward.multiply(target.getWidth() * 0.6)).add(side.multiply(r.nextDouble(-0.45, 0.45)));
        }
        return at.add(0, r.nextDouble(-0.1, 0.2), 0);
    }
}
