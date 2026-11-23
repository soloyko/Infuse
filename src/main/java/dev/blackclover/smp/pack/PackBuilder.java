package dev.blackclover.smp.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Zips the pack reproducibly (sorted entries, fixed timestamps) and hashes it for the client. */
public final class PackBuilder {

    public static final String FILE_NAME = "BlackCloverSMP-pack.zip";
    private static final long FIXED_TIME = 315_532_800_000L; // 1980-01-01, the earliest ZIP date

    public record Built(byte[] zip, byte[] sha1, String sha1Hex) {
    }

    private PackBuilder() {
    }

    public static Built build(Map<String, byte[]> files) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : new TreeMap<>(files).entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(FIXED_TIME);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] zipBytes = bytes.toByteArray();
        byte[] sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1").digest(zipBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        return new Built(zipBytes, sha1, HexFormat.of().formatHex(sha1));
    }

    /**
     * Where a player downloads the self-hosted pack: the configured public address if set, otherwise
     * the host name the player used to connect.
     */
    public static Optional<String> downloadUrl(String publicAddress, String connectHost, int port) {
        String host = publicAddress == null ? "" : publicAddress.trim();
        if (host.isEmpty()) {
            host = connectHost == null ? "" : connectHost.trim();
        }
        if (host.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("http://" + host + ":" + port + "/" + FILE_NAME);
    }
}
