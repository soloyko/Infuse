package dev.infuse.customs.pack;

import com.sun.net.httpserver.HttpServer;
import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.Settings;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Gets the resource pack to players. The pack is bundled in the jar and copied to
 * {@code plugins/Customs/Customs-ResourcePack.zip}; it is either served by a tiny
 * built-in HTTP server or replaced by a configured URL.
 */
public final class ResourcePackService implements Listener {

    public static final String PACK_FILE = "Customs-ResourcePack.zip";

    private final CustomsPlugin plugin;
    private byte[] packBytes;
    private String packSha1;
    private HttpServer server;
    private ExecutorService executor;

    /** What gets sent to players; null while unavailable. */
    private volatile String urlSha1;
    private volatile UUID packId;
    private boolean warnedNoAddress;

    public ResourcePackService(CustomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Settings s = plugin.settings();
        loadBundledPack();
        warnedNoAddress = false;
        urlSha1 = null;
        packId = null;
        switch (s.packMode) {
            case SELF_HOST -> startServer(s);
            case URL -> prepareUrl(s);
            case NONE -> {
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void loadBundledPack() {
        try (InputStream in = plugin.getResource(PACK_FILE)) {
            if (in == null) {
                plugin.getLogger().severe("Bundled resource pack is missing from the jar.");
                return;
            }
            packBytes = in.readAllBytes();
            packSha1 = sha1(packBytes);
            Path out = plugin.getDataFolder().toPath().resolve(PACK_FILE);
            Files.createDirectories(out.getParent());
            Files.write(out, packBytes);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not extract the resource pack", e);
        }
    }

    private void startServer(Settings s) {
        if (packBytes == null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(s.packBindAddress, s.packPort), 0);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().severe("Could not start the resource pack server on port " + s.packPort + ": " + e.getMessage());
            plugin.getLogger().severe("Change resource-pack.self-host.port, or set mode to 'url' or 'none'.");
            return;
        }
        byte[] bytes = packBytes;
        String path = "/" + PACK_FILE;
        server.createContext("/", exchange -> {
            try (exchange) {
                if (!exchange.getRequestURI().getPath().equals(path)) {
                    byte[] notFound = "Not found".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(404, notFound.length);
                    exchange.getResponseBody().write(notFound);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                boolean head = "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
                exchange.sendResponseHeaders(200, head ? -1 : bytes.length);
                if (!head) {
                    try (OutputStream body = exchange.getResponseBody()) {
                        body.write(bytes);
                    }
                }
            } catch (IOException ignored) {
                // client went away mid-download
            }
        });
        executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "Customs-PackServer");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.start();
        urlSha1 = packSha1;
        packId = UUID.nameUUIDFromBytes(("customs:" + packSha1).getBytes(StandardCharsets.UTF_8));
        plugin.getLogger().info("Serving the resource pack on port " + s.packPort + ".");
    }

    private void prepareUrl(Settings s) {
        if (s.packUrl.isEmpty()) {
            plugin.getLogger().warning("resource-pack.mode is 'url' but resource-pack.url.link is empty.");
            return;
        }
        if (s.packSha1.matches("[0-9a-f]{40}")) {
            setUrlHash(s.packSha1);
            return;
        }
        // Work out the hash ourselves so clients can cache the pack.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15)).build();
                HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(s.packUrl))
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                String hash = sha1(response.body());
                plugin.getLogger().info("Resource pack SHA-1: " + hash + " (put it in resource-pack.url.sha1 to skip this download).");
                Bukkit.getScheduler().runTask(plugin, () -> setUrlHash(hash));
            } catch (IOException | InterruptedException | IllegalArgumentException e) {
                plugin.getLogger().warning("Could not download " + s.packUrl + " to hash it (" + e.getMessage()
                        + "). Sending it without a hash.");
                Bukkit.getScheduler().runTask(plugin, () -> setUrlHash(""));
            }
        });
    }

    private void setUrlHash(String hash) {
        urlSha1 = hash;
        packId = UUID.nameUUIDFromBytes(("customs:" + plugin.settings().packUrl + hash).getBytes(StandardCharsets.UTF_8));
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                send(player);
            }
        }, 20L);
    }

    /** Sends (or re-sends) the pack. Returns false if there's nothing to send. */
    public boolean send(Player player) {
        Settings s = plugin.settings();
        String hash = urlSha1;
        UUID id = packId;
        if (s.packMode == Settings.PackMode.NONE || hash == null || id == null) {
            return false;
        }
        String url = s.packMode == Settings.PackMode.URL ? s.packUrl : selfHostUrl(player, s);
        if (url == null) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid resource pack URL: " + url);
            return false;
        }
        Component prompt = s.packPrompt.isBlank() ? null : Text.mm(s.packPrompt);
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(id, uri, hash))
                .required(s.packRequired)
                .replace(false)
                .prompt(prompt)
                .build();
        player.sendResourcePacks(request);
        return true;
    }

    private @Nullable String selfHostUrl(Player player, Settings s) {
        String host = s.packPublicAddress;
        if (host.isEmpty()) {
            InetSocketAddress virtualHost = player.getConnection().getVirtualHost();
            if (virtualHost != null) {
                host = virtualHost.getHostString();
            }
        }
        if (host == null || host.isEmpty()) {
            host = Bukkit.getIp();
        }
        if (host == null || host.isEmpty()) {
            if (!warnedNoAddress) {
                warnedNoAddress = true;
                plugin.getLogger().warning("Can't work out the server address for the resource pack URL. "
                        + "Set resource-pack.self-host.public-address in config.yml.");
            }
            return null;
        }
        // Strip a trailing dot (SRV/FQDN joins) and bracket IPv6 literals.
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]";
        }
        return "http://" + host + ":" + s.packPort + "/" + PACK_FILE + "?v=" + packSha1.substring(0, 8);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        UUID id = packId;
        if (id == null || !id.equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case DECLINED -> Text.send(player,
                    "<gray>You declined the resource pack, so swords will look broken. Use <white>/customs pack</white> to get it.");
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                Text.send(player, "<red>The Customs resource pack failed to load. Try <white>/customs pack</white>.");
                plugin.getLogger().warning("Resource pack " + event.getStatus() + " for " + player.getName()
                        + (plugin.settings().packMode == Settings.PackMode.SELF_HOST
                        ? ". Is port " + plugin.settings().packPort + " open to players?" : "."));
            }
            default -> {
            }
        }
    }

    public @Nullable String bundledSha1() {
        return packSha1;
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
