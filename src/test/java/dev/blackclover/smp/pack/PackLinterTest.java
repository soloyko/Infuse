package dev.blackclover.smp.pack;

import dev.blackclover.smp.grimoire.GrimoireType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PackLinterTest {

    private static final Path PACK = Path.of("src/main/resources/resourcepack");

    @Test
    void bundledPackHasNoBrokenReferences() throws Exception {
        List<String> problems = PackLinter.lint(PackFiles.fromDirectory(PACK));
        assertEquals(List.of(), problems);
    }

    @Test
    void everyGrimoireHasAModelCase() throws Exception {
        Map<String, byte[]> files = PackFiles.fromDirectory(PACK);
        String book = new String(files.get("assets/minecraft/items/book.json"));
        for (GrimoireType t : GrimoireType.values()) {
            assertTrue(book.contains("\"" + PackModels.grimoireKey(t) + "\""), "book.json missing case for " + t);
            assertTrue(files.containsKey("assets/blackclover/models/item/grimoire/" + t.name().toLowerCase(Locale.ROOT) + ".json"));
        }
        assertEquals("blackclover:anti_magic", PackModels.grimoireKey(GrimoireType.ANTI_MAGIC));
    }

    @Test
    void packFormatMatchesMinecraft12111() throws Exception {
        String meta = new String(PackFiles.fromDirectory(PACK).get("pack.mcmeta"));
        assertTrue(meta.contains("\"pack_format\": 75"), meta);
        assertTrue(meta.contains("\"min_format\": 75"), meta);
    }

    @Test
    void linterCatchesMissingTextureAndModel(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("assets/blackclover/models/item"));
        Files.createDirectories(dir.resolve("assets/minecraft/items"));
        Files.writeString(dir.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":75,\"description\":\"x\"}}");
        Files.writeString(dir.resolve("assets/blackclover/models/item/broken.json"),
                "{\"textures\":{\"a\":\"blackclover:item/nope\"},\"elements\":[{\"from\":[0,0,0],\"to\":[1,1,1],"
                        + "\"faces\":{\"north\":{\"uv\":[0,0,1,1],\"texture\":\"#a\"},\"south\":{\"uv\":[0,0,1,1],\"texture\":\"#missingvar\"}}}]}");
        Files.writeString(dir.resolve("assets/minecraft/items/book.json"),
                "{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:custom_model_data\",\"index\":0,"
                        + "\"cases\":[{\"when\":\"x\",\"model\":{\"type\":\"minecraft:model\",\"model\":\"blackclover:item/ghost\"}}],"
                        + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/book\"}}}");
        List<String> problems = PackLinter.lint(PackFiles.fromDirectory(dir));
        assertTrue(problems.stream().anyMatch(p -> p.contains("blackclover:item/nope")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("#missingvar")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("blackclover:item/ghost")), problems.toString());
    }
}
