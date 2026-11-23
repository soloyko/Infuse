package dev.blackclover.smp.data;

import dev.blackclover.smp.game.GameState;
import dev.blackclover.smp.game.Library;
import dev.blackclover.smp.grimoire.BlockLoc;
import dev.blackclover.smp.grimoire.GrimoireRegistry;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.mage.PlayerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DataStoreRoundTripTest {

    @TempDir
    File dir;

    @Test
    void everythingSurvivesSaveAndLoad() {
        UUID a = UUID.randomUUID();
        UUID itemEntity = UUID.randomUUID();
        GameState game = new GameState();
        game.setStarted(true);
        game.setWorld("world");
        game.setBorderSize(6000);
        game.setGraceEndMillis(123456789L);
        Library lib = new Library(GrimoireType.FIRE, "world", 1500, 70, -1200, 1495, 69, -1205, 1505, 79, -1195);
        game.libraries().put(GrimoireType.FIRE, lib);

        GrimoireRegistry registry = new GrimoireRegistry();
        UUID fireInstance = registry.craft(GrimoireType.FIRE, a);
        registry.giveFromPool(GrimoireType.FIVE_LEAF, UUID.randomUUID());
        BlockLoc ground = new BlockLoc("world", 10.5, 64, -3.25);
        registry.markGround(GrimoireType.FIVE_LEAF, itemEntity, ground);
        UUID fiveLeafInstance = registry.get(GrimoireType.FIVE_LEAF).instance();

        Map<UUID, PlayerData> players = new HashMap<>();
        PlayerData pd = new PlayerData(a, "Asta");
        pd.setXp(GrimoireType.FIRE, 640);
        pd.setLastPenaltyMillis(123);
        pd.setLastSeenMillis(456);
        players.put(a, pd);

        DataStore store = new DataStore(new File(dir, "data.yml"));
        store.writeSync(store.serialize(game, registry, players, a));

        GameState game2 = new GameState();
        GrimoireRegistry registry2 = new GrimoireRegistry();
        Map<UUID, PlayerData> players2 = new HashMap<>();
        AtomicReference<UUID> demon = new AtomicReference<>();
        store.load(game2, registry2, players2, demon);

        assertTrue(game2.isStarted());
        assertEquals("world", game2.world());
        assertEquals(6000, game2.borderSize());
        assertEquals(123456789L, game2.graceEndMillis());
        assertEquals(lib, game2.libraries().get(GrimoireType.FIRE));

        GrimoireState fire = registry2.get(GrimoireType.FIRE);
        assertEquals(GrimoireStatus.HELD, fire.status());
        assertEquals(a, fire.holder());
        assertEquals(fireInstance, fire.instance());
        GrimoireState five = registry2.get(GrimoireType.FIVE_LEAF);
        assertEquals(GrimoireStatus.GROUND, five.status());
        assertEquals(itemEntity, five.itemEntity());
        assertEquals(ground, five.ground());
        assertEquals(fiveLeafInstance, five.instance());
        assertEquals(GrimoireStatus.LIBRARY, registry2.get(GrimoireType.WATER).status());

        PlayerData pd2 = players2.get(a);
        assertEquals("Asta", pd2.name());
        assertEquals(640, pd2.xp(GrimoireType.FIRE));
        assertEquals(123, pd2.lastPenaltyMillis());
        assertEquals(456, pd2.lastSeenMillis());
        assertEquals(a, demon.get());
    }

    @Test
    void missingFileLoadsDefaults() {
        DataStore store = new DataStore(new File(dir, "nope.yml"));
        GameState game = new GameState();
        GrimoireRegistry registry = new GrimoireRegistry();
        AtomicReference<UUID> demon = new AtomicReference<>();
        store.load(game, registry, new HashMap<>(), demon);
        assertFalse(game.isStarted());
        assertNull(demon.get());
        assertEquals(GrimoireStatus.LIBRARY, registry.get(GrimoireType.FIRE).status());
    }

    @Test
    void olderSnapshotNeverOverwritesNewer() throws Exception {
        File f = new File(dir, "data.yml");
        DataStore store = new DataStore(f);
        store.write("version: 2\n", 2);
        store.write("version: 1\n", 1);
        assertEquals("version: 2\n", java.nio.file.Files.readString(f.toPath()));
        store.write("version: 3\n", 3);
        assertEquals("version: 3\n", java.nio.file.Files.readString(f.toPath()));
    }
}
