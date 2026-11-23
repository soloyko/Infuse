package dev.blackclover.smp.pack;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackBuilderTest {

    private static Map<String, byte[]> sample(String content) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", "{}".getBytes());
        files.put("assets/a/b.json", content.getBytes());
        return files;
    }

    @Test
    void sameFilesGiveSameZipAndHash() {
        PackBuilder.Built a = PackBuilder.build(sample("x"));
        PackBuilder.Built b = PackBuilder.build(sample("x"));
        assertArrayEquals(a.zip(), b.zip());
        assertEquals(a.sha1Hex(), b.sha1Hex());
        assertEquals(40, a.sha1Hex().length());
        assertEquals(20, a.sha1().length);
    }

    @Test
    void contentChangesHash() {
        assertNotEquals(PackBuilder.build(sample("x")).sha1Hex(), PackBuilder.build(sample("y")).sha1Hex());
    }

    @Test
    void zipContainsEveryFileSorted() throws Exception {
        Map<String, byte[]> files = sample("x");
        files.put("assets/0first.txt", "1".getBytes());
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(PackBuilder.build(files).zip()))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        assertEquals(List.of("assets/0first.txt", "assets/a/b.json", "pack.mcmeta"), names);
    }

    @Test
    void downloadUrl() {
        assertEquals(Optional.of("http://play.example.com:8163/BlackCloverSMP-pack.zip"),
                PackBuilder.downloadUrl("", "play.example.com", 8163));
        assertEquals(Optional.of("http://mc.example.net:8163/BlackCloverSMP-pack.zip"),
                PackBuilder.downloadUrl("mc.example.net", "1.2.3.4", 8163));
        assertEquals(Optional.of("http://mc.example.net:8163/BlackCloverSMP-pack.zip"),
                PackBuilder.downloadUrl("  mc.example.net  ", null, 8163));
        assertTrue(PackBuilder.downloadUrl("", null, 8163).isEmpty());
        assertTrue(PackBuilder.downloadUrl("", "", 8163).isEmpty());
    }
}
