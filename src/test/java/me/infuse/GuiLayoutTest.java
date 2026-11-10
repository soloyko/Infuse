package me.infuse;

import me.infuse.command.InfuseGuiLayout;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GuiLayoutTest {
    @Test
    void everyEffectGetsAnInnerSlot() {
        for (int n = 1; n <= 28; n++) {
            int[] slots = InfuseGuiLayout.layout(n);
            assertEquals(n, slots.length);
            Set<Integer> seen = new HashSet<>();
            for (int s : slots) {
                assertTrue(seen.add(s), "duplicate slot " + s + " for n=" + n);
                int row = s / 9, col = s % 9;
                assertTrue(row >= 1 && row <= 4 && col >= 1 && col <= 7, "slot " + s + " is on the border (n=" + n + ")");
            }
        }
    }
}
