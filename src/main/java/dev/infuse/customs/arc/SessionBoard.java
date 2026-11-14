package dev.infuse.customs.arc;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The session sidebar: a few lines on the right of the screen while a session runs (the clock,
 * faction points or the bounty, the top killers). Lives on the main scoreboard and stays out of
 * the way if something else already owns the sidebar.
 */
final class SessionBoard {

    private static final String OBJECTIVE = "customs_session";
    private static final int MAX_LINES = 15;

    /** One line: text on the left, an optional value on the right. */
    record Line(Component left, @Nullable Component right) {
        static Line blank() {
            return new Line(Component.empty(), null);
        }
    }

    private int shown;

    void update(Component title, List<Line> lines) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective current = board.getObjective(DisplaySlot.SIDEBAR);
        if (current != null && !current.getName().equals(OBJECTIVE)) {
            return; // another plugin's sidebar: leave it alone
        }
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) {
            obj = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
            obj.numberFormat(NumberFormat.blank());
        }
        obj.displayName(title);
        if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        int n = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < n; i++) {
            Line line = lines.get(i);
            Score score = obj.getScore(entry(i));
            score.setScore(n - i);
            score.customName(line.left());
            score.numberFormat(line.right() == null ? NumberFormat.blank() : NumberFormat.fixed(line.right()));
        }
        for (int i = n; i < shown; i++) {
            board.resetScores(entry(i));
        }
        shown = n;
    }

    void clear() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj != null) {
            obj.unregister();
        }
        shown = 0;
    }

    /** A score holder no player can have: the line's text comes from its custom name. */
    private static String entry(int i) {
        return "customs.line" + i; // not a valid player name, and shown (names starting with # are hidden)
    }
}
