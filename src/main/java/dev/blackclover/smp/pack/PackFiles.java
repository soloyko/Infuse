package dev.blackclover.smp.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Loads the resource pack's files (path inside the pack -> bytes). */
public final class PackFiles {

    static final String ROOT = "resourcepack/";
    static final String INDEX = "files.txt";

    private PackFiles() {
    }

    /** From the plugin jar, using the generated index {@code resourcepack/files.txt}. */
    public static Map<String, byte[]> fromClasspath(ClassLoader loader) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        String index = new String(read(loader, ROOT + INDEX), StandardCharsets.UTF_8);
        for (String line : index.split("\n")) {
            String path = line.trim();
            if (!path.isEmpty()) {
                files.put(path, read(loader, ROOT + path));
            }
        }
        return files;
    }

    private static byte[] read(ClassLoader loader, String name) throws IOException {
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("missing pack file " + name);
            }
            return in.readAllBytes();
        }
    }

    /** From a directory on disk (used by tests and tooling). */
    public static Map<String, byte[]> fromDirectory(Path dir) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (!rel.equals(INDEX)) {
                    files.put(rel, Files.readAllBytes(p));
                }
            }
        }
        return files;
    }
}
