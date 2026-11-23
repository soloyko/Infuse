package dev.blackclover.smp.mage;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.config.Settings;
import dev.blackclover.smp.core.CombatTracker;
import dev.blackclover.smp.core.RepeatKillTracker;
import dev.blackclover.smp.core.SpellUnlocks;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.util.Msg;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Levels, kill XP and the death penalty. Levels are stored per player per grimoire. */
public final class MageService {

    private final BlackCloverPlugin plugin;
    private final GrimoireService grimoires;
    private final CombatTracker combat;
    private final RepeatKillTracker repeats;
    private final List<Consumer<Player>> levelChangeHooks = new ArrayList<>();
    /** (type, slot) -> spell name; set by the magic system. */
    private BiFunction<GrimoireType, Integer, String> spellName = (t, slot) -> "spell " + (slot + 1);

    public MageService(BlackCloverPlugin plugin, GrimoireService grimoires) {
        this.plugin = plugin;
        this.grimoires = grimoires;
        Settings s = plugin.settings();
        this.combat = new CombatTracker(s.combatTagMillis());
        this.repeats = new RepeatKillTracker(s.repeatKillMillis());
    }

    private Settings settings() {
        return plugin.settings();
    }

    public CombatTracker combat() {
        return combat;
    }

    public void setSpellNames(BiFunction<GrimoireType, Integer, String> spellName) {
        this.spellName = spellName;
    }

    public void onLevelChange(Consumer<Player> hook) {
        levelChangeHooks.add(hook);
    }

    public PlayerData data(UUID id) {
        return plugin.players().computeIfAbsent(id, k -> new PlayerData(k, "?"));
    }

    public int level(UUID id, GrimoireType type) {
        return settings().levelCurve().levelForXp(data(id).xp(type));
    }

    /** Level on the grimoire the player carries, or 0 without one. */
    public int carriedLevel(Player player) {
        return grimoires.carried(player).map(t -> level(player.getUniqueId(), t)).orElse(0);
    }

    public void addXp(Player player, GrimoireType type, int delta) {
        setXp(player, type, data(player.getUniqueId()).xp(type) + delta);
    }

    public void setXp(Player player, GrimoireType type, int xp) {
        PlayerData pd = data(player.getUniqueId());
        int beforeLevel = level(player.getUniqueId(), type);
        pd.setXp(type, settings().levelCurve().clampXp(xp));
        int afterLevel = level(player.getUniqueId(), type);
        if (afterLevel > beforeLevel) {
            int slot = SpellUnlocks.unlockedCount(afterLevel) - 1;
            boolean newSpell = SpellUnlocks.unlockedCount(afterLevel) > SpellUnlocks.unlockedCount(beforeLevel);
            Msg.title(player, "<c><b>ʟᴇᴠᴇʟ <level></b></c>",
                    newSpell ? "<gray>ɴᴇᴡ ꜱᴘᴇʟʟ</gray>  <white><spell>" : "<gray><type>",
                    Msg.color("c", type.rgb()), Msg.arg("level", afterLevel),
                    Msg.arg("spell", Msg.sc(spellName.apply(type, slot))), Msg.arg("type", Msg.sc(type.displayName() + " Grimoire")));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.35);
            player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 40, 0.6, 0.9, 0.6, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(type.rgb() & 0xFFFFFF), 1.4f));
        } else if (afterLevel < beforeLevel) {
            if (SpellUnlocks.unlockedCount(afterLevel) < SpellUnlocks.unlockedCount(beforeLevel)) {
                int lostSlot = SpellUnlocks.unlockedCount(afterLevel);
                Msg.send(player, "<red>You dropped to level <level>. <white><spell></white> is locked until you level up again.",
                        Msg.arg("level", afterLevel), Msg.arg("spell", spellName.apply(type, lostSlot)));
            } else {
                Msg.send(player, "<red>You dropped to level <level>.", Msg.arg("level", afterLevel));
            }
        }
        if (afterLevel != beforeLevel) {
            for (Consumer<Player> hook : levelChangeHooks) {
                hook.accept(player);
            }
        }
        grimoires.refreshLore(player);
    }

    /** On any death: lose a fraction of XP on the carried grimoire (not in grace, max once per cooldown). */
    public void applyDeathPenalty(Player player, GrimoireType type, boolean grace) {
        if (grace) {
            return;
        }
        PlayerData pd = data(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (!pd.canBePenalized(now, settings().penaltyCooldownMillis())) {
            return;
        }
        int before = pd.xp(type);
        int after = settings().killRewards().xpAfterDeath(before);
        if (after >= before) {
            return;
        }
        pd.setLastPenaltyMillis(now);
        Msg.send(player, "<red>You lost <white><n></white> magic XP on your <type> grimoire.",
                Msg.arg("n", before - after), Msg.arg("type", type.displayName()));
        setXp(player, type, after);
    }

    /**
     * A PvP kill. Awards magic XP when both carried a grimoire.
     *
     * @return false for a repeat kill (no XP, no stars)
     */
    public boolean onKill(Player killer, Player victim, GrimoireType victimType, int victimLevel) {
        if (killer.getUniqueId().equals(victim.getUniqueId())) {
            return false;
        }
        if (!repeats.tryRecord(killer.getUniqueId(), victim.getUniqueId(), System.currentTimeMillis())) {
            Msg.send(killer, "<gray>No magic XP or stars: you already defeated <white><name></white> in the last <m> minutes.",
                    Msg.arg("name", victim.getName()), Msg.arg("m", settings().repeatKillMillis() / 60000));
            return false;
        }
        GrimoireType killerType = grimoires.carried(killer).orElse(null);
        if (victimType == null || killerType == null) {
            return true;
        }
        int xp = settings().killRewards().killXp(level(killer.getUniqueId(), killerType), victimLevel);
        Msg.send(killer, "<green>+<n> magic XP</green> <gray>for defeating <white><name></white> (Lv <lv>).",
                Msg.arg("n", xp), Msg.arg("name", victim.getName()), Msg.arg("lv", victimLevel));
        addXp(killer, killerType, xp);
        return true;
    }

    /** Top players by level on the grimoire they currently carry. */
    public List<Map.Entry<String, Integer>> top(int n) {
        List<Map.Entry<String, Integer>> rows = new ArrayList<>();
        for (GrimoireState s : plugin.registry().all()) {
            if (s.status() == GrimoireStatus.HELD && s.holder() != null) {
                PlayerData pd = data(s.holder());
                rows.add(new AbstractMap.SimpleEntry<>(pd.name() + " (" + s.type().displayName() + ")", level(s.holder(), s.type())));
            }
        }
        rows.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        return rows.subList(0, Math.min(n, rows.size()));
    }
}
