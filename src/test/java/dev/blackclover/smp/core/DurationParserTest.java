package dev.blackclover.smp.core;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationParserTest {

    @Test
    void parsesUnits() {
        assertEquals(OptionalLong.of(1800), DurationParser.parseSeconds("30m"));
        assertEquals(OptionalLong.of(45), DurationParser.parseSeconds("45s"));
        assertEquals(OptionalLong.of(7200), DurationParser.parseSeconds("2h"));
        assertEquals(OptionalLong.of(90), DurationParser.parseSeconds("90"));
        assertEquals(OptionalLong.of(0), DurationParser.parseSeconds("0"));
        assertEquals(OptionalLong.of(600), DurationParser.parseSeconds("10M"));
    }

    @Test
    void rejectsGarbage() {
        assertTrue(DurationParser.parseSeconds("abc").isEmpty());
        assertTrue(DurationParser.parseSeconds("-5m").isEmpty());
        assertTrue(DurationParser.parseSeconds("").isEmpty());
        assertTrue(DurationParser.parseSeconds(null).isEmpty());
        assertTrue(DurationParser.parseSeconds("5x").isEmpty());
    }
}
