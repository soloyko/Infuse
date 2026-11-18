package dev.bloodbound.core;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and formats short durations like {@code 1h30m} or {@code 45s}. */
public final class Durations {
    private static final Pattern FULL = Pattern.compile("(\\d+[hms])+");
    private static final Pattern PART = Pattern.compile("(\\d+)([hms])");

    private Durations() {}

    public static OptionalLong parse(String input) {
        if (input == null) return OptionalLong.empty();
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (!FULL.matcher(s).matches()) return OptionalLong.empty();
        long total = 0;
        Matcher m = PART.matcher(s);
        try {
            while (m.find()) {
                long n = Long.parseLong(m.group(1));
                long unit = switch (m.group(2)) {
                    case "h" -> 3_600_000L;
                    case "m" -> 60_000L;
                    default -> 1_000L;
                };
                total = Math.addExact(total, Math.multiplyExact(n, unit));
            }
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
        return total > 0 ? OptionalLong.of(total) : OptionalLong.empty();
    }

    public static String format(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format("%dh %02dm", h, m) : String.format("%dm %02ds", m, s);
    }
}
