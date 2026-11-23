package dev.blackclover.smp.core;

import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations like {@code 90}, {@code 45s}, {@code 30m}, {@code 2h} into seconds. */
public final class DurationParser {

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,9})([smh]?)");

    private DurationParser() {
    }

    public static OptionalLong parseSeconds(String input) {
        if (input == null) {
            return OptionalLong.empty();
        }
        Matcher m = FORMAT.matcher(input.trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return OptionalLong.empty();
        }
        long value = Long.parseLong(m.group(1));
        long multiplier = switch (m.group(2)) {
            case "m" -> 60L;
            case "h" -> 3600L;
            default -> 1L;
        };
        return OptionalLong.of(value * multiplier);
    }
}
