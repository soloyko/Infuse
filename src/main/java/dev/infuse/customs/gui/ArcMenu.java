package dev.infuse.customs.gui;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.sword.Arc;
import dev.infuse.customs.sword.SwordType;
import dev.infuse.customs.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's story at a glance: the five arcs in order (done, current, locked) with their
 * twists and weapons, the session, and the way to the factions.
 */
public final class ArcMenu extends Menu {

    private static final int FIRST_ARC = 20;

    public ArcMenu(CustomsPlugin plugin, Player viewer) {
        super(6, Text.mm("<gradient:#ECBA44:#C4415B><bold>The Arcs</bold></gradient>"));
        var arcs = plugin.arcs();
        Arc current = arcs.current();
        for (Arc arc : Arc.values()) {
            boolean done = current != null && arc.ordinal() < current.ordinal();
            boolean now = arc == current;
            List<Component> lore = new ArrayList<>();
            lore.add(Text.caps(arc.title(), arc.color()));
            lore.add(current == null ? Text.plain("No arc is running: everything is open", NamedTextColor.GRAY)
                    : now ? Text.plain("◆ The current arc", NamedTextColor.GREEN)
                    : done ? Text.plain("✔ Completed", NamedTextColor.DARK_GREEN)
                    : Text.plain("🔒 Not yet begun", NamedTextColor.RED));
            lore.add(Component.empty());
            if (arc.hasTwist()) {
                lore.add(Text.plain("Twist: " + arc.twist(), arc.color()).append(Text.plain("  (in sessions)", NamedTextColor.DARK_GRAY)));
                for (String line : Text.wrap(blurb(arc), 38)) {
                    lore.add(Text.plain(line, NamedTextColor.GRAY));
                }
            } else {
                lore.add(Text.plain("No twist", NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Text.caps("weapons", NamedTextColor.GRAY));
            for (SwordType type : arc.members()) {
                lore.add(Text.plain(type.emoji() + " " + type.swordName(), type.color()));
            }
            lore.add(Component.empty());
            lore.add(Text.plain("Click to see their recipes", NamedTextColor.YELLOW));
            ItemStack tile = button(Material.PAPER, Text.plain(arc.label(), now || current == null || done ? arc.color() : NamedTextColor.DARK_GRAY)
                    .decorate(TextDecoration.BOLD), lore);
            SwordType icon = arc.members().get(0);
            tile.editMeta(meta -> {
                meta.setItemModel(icon.itemModel());
                meta.setEnchantmentGlintOverride(now);
            });
            Menu.Page page = Menu.Page.values()[arc.ordinal()];
            set(FIRST_ARC + arc.ordinal(), tile, p -> RecipeMenu.overview(plugin, page).open(p));
            // the progress bar under the arcs: lit up to the current one
            boolean lit = current != null && arc.ordinal() <= current.ordinal();
            ItemStack bar = button(lit ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    Text.plain(lit ? (now ? "You are here" : "Done") : "Ahead", lit ? NamedTextColor.GREEN : NamedTextColor.GRAY), List.of());
            set(FIRST_ARC + arc.ordinal() + 9, bar, null);
        }
        // the session
        List<Component> session = new ArrayList<>();
        session.add(arcs.sessionLine());
        if (current != null && current.hasTwist()) {
            session.add(Text.plain(current.twist() + (arcs.sessionActive() ? " is in play." : " starts with the next session."), NamedTextColor.GRAY));
        }
        ItemStack clock = button(arcs.sessionActive() ? Material.CLOCK : Material.GRAY_DYE,
                Text.plain(arcs.sessionActive() ? "Session running" : "No session", arcs.sessionActive() ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                        .decorate(TextDecoration.BOLD), session);
        clock.editMeta(meta -> meta.setEnchantmentGlintOverride(arcs.sessionActive()));
        set(4, clock, null);
        if (arcs.factions().active() && arcs.factions().formed()) {
            ItemStack banner = button(Material.WHITE_BANNER, Text.plain("The Factions", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                    List.of(arcs.factions().scoreLine(), Component.empty(), Text.plain("Click to see them", NamedTextColor.YELLOW)));
            set(40, banner, p -> new FactionMenu(plugin, p).open(p));
        }
        closeButton();
    }

    /** A short version of a twist for the menu (the full text is in chat and /customs arc info). */
    private static String blurb(Arc arc) {
        return switch (arc) {
            case I -> "One player at a time is the bounty: everyone is shown the way to them. Kills heal you fully.";
            case II -> "Three random leaders with their faction weapons, two members each. Kills score, and the unsworn are won over by the faction weapons.";
            default -> arc.twistText() == null ? "" : arc.twistText();
        };
    }
}
