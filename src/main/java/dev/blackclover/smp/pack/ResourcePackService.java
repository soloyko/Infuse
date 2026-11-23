package dev.blackclover.smp.pack;

import com.sun.net.httpserver.HttpServer;
import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.config.Settings;
import dev.blackclover.smp.util.Msg;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/** Builds the resource pack from the plugin jar, serves it (optionally) and offers it to players on join. */
public final class ResourcePackService implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("BlackCloverSMP-pack".getBytes(StandardCharsets.UTF_8));

    private final BlackCloverPlugin plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private PackBuilder.Built built;
    private HttpServer server;
    private ExecutorService executor;
    private boolean warnedNoAddress;
    /** True once we know `url` serves exactly our pack (host: url, or auto after a successful check). */
    private volatile boolean useUrl;

    public ResourcePackService(BlackCloverPlugin plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.settings();
    }

    public void enable() {
        if (!settings().packEnabled()) {
            plugin.getLogger().info("Resource pack disabled in config.");
            return;
        }
        try {
            built = PackBuilder.build(PackFiles.fromClasspath(plugin.getClass().getClassLoader()));
            Path out = plugin.getDataFolder().toPath().resolve(PackBuilder.FILE_NAME);
            Files.createDirectories(out.getParent());
            Files.write(out, built.zip());
            plugin.getLogger().info(String.format("Resource pack ready: %s (%d KB, sha1 %s)",
                    out.getFileName(), built.zip().length / 1024, built.sha1Hex()));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not build the resource pack", e);
            built = null;
            return;
        }
        switch (settings().packHost()) {
            case "self" -> startServer();
            case "url" -> {
                useUrl = !settings().packUrl().isEmpty();
                if (!useUrl) {
                    plugin.getLogger().warning("resource-pack.host is 'url' but resource-pack.url is empty: upload "
                            + PackBuilder.FILE_NAME + " from the plugin folder and set its direct download link.");
                }
            }
            default -> verifyUrlThenChoose();
        }
    }

    /** auto: download `url` once and use it only if it is byte-for-byte our pack; otherwise self-host. */
    private void verifyUrlThenChoose() {
        String url = settings().packUrl();
        if (url.isEmpty()) {
            startServer();
            return;
        }
        String expected = built.sha1Hex();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String found;
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .connectTimeout(java.time.Duration.ofSeconds(10)).build();
                byte[] body = client.send(java.net.http.HttpRequest.newBuilder(URI.create(url))
                                .timeout(java.time.Duration.ofSeconds(20)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body();
                found = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(body));
            } catch (Exception e) {
                found = "unreachable (" + e.getClass().getSimpleName() + ")";
            }
            String result = found;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.equals(expected)) {
                    useUrl = true;
                    plugin.getLogger().info("Players will download the resource pack from " + url);
                } else {
                    plugin.getLogger().warning("The pack at resource-pack.url doesn't match this plugin (" + result
                            + "), so the plugin will serve it itself on port " + settings().packPort() + ".");
                    startServer();
                }
                resendAll();
            });
        });
    }

    private void startServer() {
        int port = settings().packPort();
        byte[] zip = built.zip();
        String path = "/" + PackBuilder.FILE_NAME;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    if (!"GET".equals(exchange.getRequestMethod()) || !path.equals(exchange.getRequestURI().getPath())) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, zip.length);
                    try (OutputStream body = exchange.getResponseBody()) {
                        body.write(zip);
                    }
                }
            });
            executor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "BlackCloverSMP-pack-host");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("Serving the resource pack on port " + port + " (" + path + ")");
        } catch (IOException e) {
            server = null;
            plugin.getLogger().severe("Could not serve the resource pack on port " + port + ": " + e.getMessage()
                    + ". Free the port, change resource-pack.port, or upload the zip and use host: url.");
        }
    }

    public void disable() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** The download link for this player, if the pack can be offered at all. */
    public Optional<String> urlFor(Player player) {
        if (built == null) {
            return Optional.empty();
        }
        if (useUrl) {
            return Optional.of(settings().packUrl());
        }
        if (server == null) {
            return Optional.empty();
        }
        InetSocketAddress joined = player.getVirtualHost();
        return PackBuilder.downloadUrl(settings().packPublicAddress(), joined == null ? null : joined.getHostString(),
                settings().packPort());
    }

    public void send(Player player) {
        Optional<String> url = urlFor(player);
        if (url.isEmpty()) {
            if (built != null && !warnedNoAddress) {
                warnedNoAddress = true;
                plugin.getLogger().warning("Can't offer the resource pack: no download address. Set resource-pack.public-address.");
            }
            return;
        }
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url.get()), built.sha1Hex()))
                .required(settings().packRequired())
                .prompt(Msg.mm(settings().packPrompt()))
                .replace(false)
                .build();
        player.sendResourcePacks(request);
    }

    public boolean hasPack(Player player) {
        return loaded.contains(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                send(p);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        Player p = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(p.getUniqueId());
            case DECLINED -> Msg.send(p, "<gray>You declined the Black Clover pack, so grimoires will look like plain books. "
                    + "Enable server resource packs in the server list to see the 3D models.");
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> plugin.getLogger().warning(
                    "Resource pack " + event.getStatus() + " for " + p.getName() + " (" + urlFor(p).orElse("no url") + ")");
            default -> {
            }
        }
    }

    /** /bcadmin pack */
    public void describe(CommandSender to) {
        if (built == null) {
            Msg.send(to, "<red>The resource pack is off or failed to build (see console).");
            return;
        }
        Msg.send(to, "<gray>Pack: <white><file></white> <dark_gray>(sha1 <hash>)", Msg.arg("file", PackBuilder.FILE_NAME),
                Msg.arg("hash", built.sha1Hex()));
        if (useUrl) {
            Msg.send(to, "<green>Players download it from: <white><url>", Msg.arg("url", settings().packUrl()));
        } else {
            Msg.send(to, server != null ? "<green>Serving it on port <port>." : "<red>Not serving: port <port> could not be opened.",
                    Msg.arg("port", settings().packPort()));
        }
        long withPack = plugin.getServer().getOnlinePlayers().stream().filter(this::hasPack).count();
        Msg.send(to, "<gray>Players with the pack loaded: <white><n>/<total>", Msg.arg("n", withPack),
                Msg.arg("total", plugin.getServer().getOnlinePlayers().size()));
        if (to instanceof Player p) {
            urlFor(p).ifPresent(u -> Msg.send(to, "<gray>Your download link: <white><u>", Msg.arg("u", u)));
        }
    }

    public void resendAll() {
        plugin.getServer().getOnlinePlayers().forEach(this::send);
    }
}
