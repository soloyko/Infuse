package dev.bloodbound.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.config.Messages;
import dev.bloodbound.core.Durations;
import dev.bloodbound.core.Tiers;
import dev.bloodbound.data.BlockKey;
import dev.bloodbound.data.PlayerData;
import dev.bloodbound.event.EventManager;
import dev.bloodbound.event.EventType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static dev.bloodbound.config.Messages.comp;
import static dev.bloodbound.config.Messages.text;

/** The /bloodbound (/bb) command tree. */
public final class BloodboundCommand {
    private static final String ADMIN = "bloodbound.admin";

    private final BloodboundPlugin plugin;

    public BloodboundCommand(BloodboundPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("bloodbound")
                .requires(src -> src.getSender().hasPermission("bloodbound.use") || src.getSender().hasPermission(ADMIN))
                .then(Commands.literal("stats")
                        .executes(ctx -> stats(ctx.getSource().getSender(), null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> onlineNames(b))
                                .executes(ctx -> stats(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("top").executes(ctx -> top(ctx.getSource().getSender())))
                .then(session())
                .then(event())
                .then(altar())
                .then(blood())
                .then(Commands.literal("reload").requires(this::admin).executes(ctx -> {
                    plugin.reload();
                    m().send(ctx.getSource().getSender(), "reloaded");
                    return SINGLE_SUCCESS;
                }));
    }

    private boolean admin(CommandSourceStack src) {
        return src.getSender().hasPermission(ADMIN);
    }

    // ---- session ----

    private LiteralArgumentBuilder<CommandSourceStack> session() {
        return Commands.literal("session").requires(this::admin)
                .then(Commands.literal("start")
                        .executes(ctx -> startSession(ctx.getSource().getSender(), null))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> startSession(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "duration")))))
                .then(Commands.literal("stop").executes(ctx -> {
                    if (!plugin.sessions().stop()) m().send(ctx.getSource().getSender(), "session.not-active");
                    return SINGLE_SUCCESS;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    if (!plugin.sessions().isActive()) {
                        m().send(s, "session.status-inactive");
                        return SINGLE_SUCCESS;
                    }
                    long rem = plugin.sessions().remainingMillis();
                    Component event = plugin.events().current().map(plugin.events()::displayName)
                            .orElse(Component.text("none"));
                    m().send(s, "session.status-active",
                            text("time", rem < 0 ? m().raw("session.bossbar-open") : Durations.format(rem)),
                            comp("event", event));
                    return SINGLE_SUCCESS;
                }));
    }

    private int startSession(CommandSender sender, String rawDuration) {
        OptionalLong duration = OptionalLong.empty();
        if (rawDuration != null) {
            duration = Durations.parse(rawDuration);
            if (duration.isEmpty()) {
                m().send(sender, "invalid-duration", text("input", rawDuration));
                return 0;
            }
        }
        if (!plugin.sessions().start(duration)) m().send(sender, "session.already-active");
        return SINGLE_SUCCESS;
    }

    // ---- events ----

    private LiteralArgumentBuilder<CommandSourceStack> event() {
        return Commands.literal("event").requires(this::admin)
                .then(Commands.literal("list").executes(ctx -> {
                    m().send(ctx.getSource().getSender(), "event.list", text("list", eventList()));
                    return SINGLE_SUCCESS;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    if (!plugin.events().stopCurrent(true)) m().send(ctx.getSource().getSender(), "event.none-active");
                    return SINGLE_SUCCESS;
                }))
                .then(Commands.literal("start")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> suggest(b, Arrays.stream(EventType.values()).map(EventType::key).toList()))
                                .executes(ctx -> startEvent(ctx, null, null))
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .executes(ctx -> startEvent(ctx, StringArgumentType.getString(ctx, "duration"), null))
                                        .then(Commands.argument("target", StringArgumentType.word())
                                                .suggests((c, b) -> onlineNames(b))
                                                .executes(ctx -> startEvent(ctx, StringArgumentType.getString(ctx, "duration"),
                                                        StringArgumentType.getString(ctx, "target")))))));
    }

    private int startEvent(CommandContext<CommandSourceStack> ctx, String rawDuration, String targetName) {
        CommandSender sender = ctx.getSource().getSender();
        String rawType = StringArgumentType.getString(ctx, "type");
        Optional<EventType> type = EventType.fromKey(rawType);
        if (type.isEmpty()) {
            m().send(sender, "event.unknown", text("input", rawType), text("list", eventList()));
            return 0;
        }
        long duration = plugin.settings().defaultEventMillis();
        if (rawDuration != null) {
            OptionalLong parsed = Durations.parse(rawDuration);
            if (parsed.isEmpty()) {
                m().send(sender, "invalid-duration", text("input", rawDuration));
                return 0;
            }
            duration = parsed.getAsLong();
        }
        Player target = null;
        if (targetName != null) {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                m().send(sender, "unknown-player", text("player", targetName));
                return 0;
            }
        }
        EventManager.StartResult result = plugin.events().start(type.get(), duration, target);
        switch (result) {
            case NO_SESSION -> m().send(sender, "event.needs-session");
            case NO_BOUNTY_TARGET -> m().send(sender, "event.no-bounty-target");
            case OK -> { }
        }
        return SINGLE_SUCCESS;
    }

    private String eventList() {
        return Arrays.stream(EventType.values()).map(EventType::key).collect(Collectors.joining(", "));
    }

    // ---- altars ----

    private LiteralArgumentBuilder<CommandSourceStack> altar() {
        return Commands.literal("altar").requires(this::admin)
                .then(Commands.literal("give").executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player p)) {
                        m().send(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    p.getInventory().addItem(plugin.altarItem().create()).values()
                            .forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
                    m().send(p, "altar.given");
                    return SINGLE_SUCCESS;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender s = ctx.getSource().getSender();
                    m().send(s, "altar.list-header", text("count", plugin.altars().all().size()));
                    for (BlockKey k : plugin.altars().all()) {
                        m().send(s, "altar.list-entry", text("world", k.world()), text("x", k.x()),
                                text("y", k.y()), text("z", k.z()));
                    }
                    return SINGLE_SUCCESS;
                }))
                .then(Commands.literal("remove").executes(ctx -> {
                    if (!(ctx.getSource().getExecutor() instanceof Player p)) {
                        m().send(ctx.getSource().getSender(), "players-only");
                        return 0;
                    }
                    Location here = p.getLocation();
                    Optional<BlockKey> nearest = plugin.altars().all().stream()
                            .filter(k -> k.world().equals(here.getWorld().getName()))
                            .filter(k -> k.center() != null && k.center().distanceSquared(here) <= 25)
                            .min(Comparator.comparingDouble(k -> k.center().distanceSquared(here)));
                    if (nearest.isEmpty()) {
                        m().send(p, "altar.none-nearby");
                        return 0;
                    }
                    BlockKey k = nearest.get();
                    plugin.altars().remove(k);
                    Location c = k.center();
                    if (c != null) c.getBlock().setType(org.bukkit.Material.AIR);
                    m().send(p, "altar.removed", text("x", k.x()), text("y", k.y()), text("z", k.z()));
                    return SINGLE_SUCCESS;
                }));
    }

    // ---- blood ----

    private LiteralArgumentBuilder<CommandSourceStack> blood() {
        return Commands.literal("blood").requires(this::admin)
                .then(bloodOp("set", false))
                .then(bloodOp("add", true));
    }

    private LiteralArgumentBuilder<CommandSourceStack> bloodOp(String name, boolean add) {
        return Commands.literal(name)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> onlineNames(b))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(add ? -1_000_000 : 0, 1_000_000))
                                .executes(ctx -> {
                                    CommandSender s = ctx.getSource().getSender();
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    Optional<PlayerData> data = lookup(playerName);
                                    if (data.isEmpty()) {
                                        m().send(s, "unknown-player", text("player", playerName));
                                        return 0;
                                    }
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    int value = add ? data.get().blood() + amount : amount;
                                    plugin.blood().set(data.get(), value);
                                    m().send(s, "blood.set", text("player", data.get().name()), text("amount", data.get().blood()));
                                    return SINGLE_SUCCESS;
                                })));
    }

    // ---- player commands ----

    private int stats(CommandSender sender, String name) {
        Optional<PlayerData> data;
        if (name == null) {
            if (!(sender instanceof Player p)) {
                m().send(sender, "players-only");
                return 0;
            }
            data = Optional.of(plugin.players().get(p));
        } else {
            data = lookup(name);
        }
        if (data.isEmpty()) {
            m().send(sender, "unknown-player", text("player", name));
            return 0;
        }
        PlayerData d = data.get();
        Tiers tiers = plugin.settings().tiers();
        int tier = tiers.tierFor(d.blood());
        int toNext = tiers.toNext(d.blood());
        m().send(sender, "stats.header", text("player", d.name()));
        if (d.type() == null) m().send(sender, "stats.no-weapon");
        else m().send(sender, "stats.weapon", comp("weapon", plugin.weapons().displayName(d.type())));
        m().send(sender, "stats.tier", text("tier", tier), text("max", tiers.maxTier()));
        String next = toNext < 0 ? m().raw("stats.maxed") : m().raw("stats.next").replace("<next>", String.valueOf(toNext));
        m().send(sender, "stats.blood", text("blood", d.blood()), text("next", next));
        return SINGLE_SUCCESS;
    }

    private int top(CommandSender sender) {
        List<PlayerData> top = plugin.players().all().stream()
                .filter(d -> d.blood() > 0)
                .sorted(Comparator.comparingInt((PlayerData d) -> d.blood()).reversed())
                .limit(10)
                .toList();
        if (top.isEmpty()) {
            m().send(sender, "stats.top-empty");
            return SINGLE_SUCCESS;
        }
        m().send(sender, "stats.top-header");
        Tiers tiers = plugin.settings().tiers();
        for (int i = 0; i < top.size(); i++) {
            PlayerData d = top.get(i);
            m().send(sender, "stats.top-entry", text("rank", i + 1), text("player", d.name()),
                    text("tier", tiers.tierFor(d.blood())), text("blood", d.blood()));
        }
        return SINGLE_SUCCESS;
    }

    // ---- helpers ----

    private Optional<PlayerData> lookup(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return Optional.of(plugin.players().get(online));
        return plugin.players().findByName(name);
    }

    private CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> onlineNames(SuggestionsBuilder b) {
        return suggest(b, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    private CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(SuggestionsBuilder b, List<String> options) {
        String typed = b.getRemainingLowerCase();
        for (String o : options) if (o.toLowerCase().startsWith(typed)) b.suggest(o);
        return b.buildFuture();
    }
}
