package dev.bloodbound.core;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class DurationsTest {
    @Test
    void parsesUnits() {
        assertEquals(OptionalLong.of(5_400_000), Durations.parse("90m"));
        assertEquals(OptionalLong.of(5_400_000), Durations.parse("1h30m"));
        assertEquals(OptionalLong.of(45_000), Durations.parse("45s"));
        assertEquals(OptionalLong.of(7_200_000), Durations.parse("2H"));
    }

    @Test
    void rejectsGarbage() {
        assertTrue(Durations.parse("5x").isEmpty());
        assertTrue(Durations.parse("").isEmpty());
        assertTrue(Durations.parse(null).isEmpty());
        assertTrue(Durations.parse("0m").isEmpty());
        assertTrue(Durations.parse("m").isEmpty());
        assertTrue(Durations.parse("10").isEmpty());
    }

    @Test
    void formats() {
        assertEquals("1h 05m", Durations.format(3_900_000));
        assertEquals("4m 09s", Durations.format(249_000));
        assertEquals("0m 00s", Durations.format(-10));
    }
}
