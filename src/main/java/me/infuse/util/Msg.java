package me.infuse.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static String prefix = "<gradient:#d11fd1:#7f3cff><bold>Infuse</bold></gradient> <dark_gray>»</dark_gray> ";

    private Msg() {}

    public static void setPrefix(String p) { prefix = p == null ? "" : p; }

    public static Component parse(String mini) { return MM.deserialize(mini); }

    public static void send(CommandSender to, String mini) { to.sendMessage(MM.deserialize(prefix + mini)); }

    public static void broadcast(String mini) { Bukkit.broadcast(MM.deserialize(prefix + mini)); }

    /** Escapes user-controlled text before putting it in a MiniMessage string. */
    public static String esc(String s) { return MM.escapeTags(s); }

    public static String hex(int rgb) { return String.format("#%06x", rgb & 0xFFFFFF); }
}
