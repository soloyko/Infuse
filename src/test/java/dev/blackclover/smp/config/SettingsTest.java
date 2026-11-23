package dev.blackclover.smp.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {

    @Test
    void bundledConfigMatchesSpecDefaults() {
        Settings s = Settings.load(YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")));
        assertEquals(20, s.levelCurve().cap());
        assertEquals(6175, s.levelCurve().totalXpForLevel(20));
        assertEquals(100, s.killRewards().killXp(5, 5));
        assertEquals(850, s.killRewards().xpAfterDeath(1000));
        assertEquals(176.0, s.manaMath().maxMana(20, false), 1e-9);
        assertEquals(600_000L, s.repeatKillMillis());
        assertEquals(15_000L, s.combatTagMillis());
        assertEquals(300_000L, s.penaltyCooldownMillis());
        assertTrue(s.combatLogKills());
        assertEquals(Map.of(Material.BOOK, 1, Material.LAPIS_LAZULI, 16, Material.DIAMOND, 1), s.recipe());
        assertEquals(1000, s.libraryMinFromOrigin());
        assertEquals(50, s.libraryBorderMargin());
        assertEquals(400, s.librarySpacing());
        assertEquals(7L * 24 * 3600 * 1000, s.offlineReturnMillis());
        assertEquals(40.0, s.demonHealth(), 1e-9);
        assertEquals(300_000L, s.demonBroadcastMillis());
        assertTrue(s.disabledWorlds().isEmpty());
    }

    @Test
    void emptyConfigFallsBackToDefaults() {
        Settings s = Settings.load(new YamlConfiguration());
        assertEquals(20, s.levelCurve().cap());
        assertEquals(1, s.recipe().get(Material.DIAMOND));
    }
}
