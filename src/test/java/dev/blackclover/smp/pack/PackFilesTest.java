package dev.blackclover.smp.pack;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PackFilesTest {

    @Test
    void loadsEveryListedFileFromTheClasspath() throws Exception {
        Map<String, byte[]> fromJar = PackFiles.fromClasspath(PackFilesTest.class.getClassLoader());
        Map<String, byte[]> fromDir = PackFiles.fromDirectory(java.nio.file.Path.of("src/main/resources/resourcepack"));
        assertEquals(fromDir.keySet(), fromJar.keySet());
        assertTrue(fromJar.containsKey("pack.mcmeta"));
        assertTrue(fromJar.containsKey("assets/minecraft/items/book.json"));
        assertFalse(fromJar.containsKey("files.txt"));
        assertArrayEquals(fromDir.get("pack.png"), fromJar.get("pack.png"));
    }
}
