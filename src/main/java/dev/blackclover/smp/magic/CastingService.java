package dev.blackclover.smp.magic;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.core.ManaMath;
import dev.blackclover.smp.core.SpellUnlocks;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.mage.MageService;
import dev.blackclover.smp.util.Msg;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Right-click casting, spell selection, mana, cooldowns and the action-bar HUD. */
public final class CastingService implements Listener {

    private final BlackCloverPlugin plugin;
    private final SpellRegistry spells;
    private final MageService mage;
    private final GrimoireService grimoires;
    private final StatusService status;
    private final Fx fx;
    private final CastDebounce debounce = new CastDebounce(150);
    private final Map<UUID, Double> mana = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final SpellSelection selection = new SpellSelection();
    private final Map<UUID, Long> hudPausedUntil = new HashMap<>();

    public CastingService(BlackCloverPlugin plugin, SpellRegistry spells, MageService mage, GrimoireService grimoires,
                          StatusService status, Fx fx) {
        this.plugin = plugin;
        this.spells = spells;
        this.mage = mage;
        this.grimoires = grimoires;
        this.status = status;
        this.fx = fx;
    }

    public void startTasks() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::regen, 10L, 10L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::hud, 5L, 5L);
    }

    // ---------------------------------------------------------------- mana

    private ManaMath manaMath() {
        return plugin.settings().manaMath();
    }

    public double maxMana(Player p) {
        Optional<GrimoireType> type = grimoires.carried(p);
        int level = type.map(t -> mage.level(p.getUniqueId(), t)).orElse(1);
        return manaMath().maxMana(level, type.orElse(null) == GrimoireType.FIVE_LEAF);
    }

    public double mana(UUID id) {
        return mana.getOrDefault(id, manaMath().maxMana(1, false));
    }

    public void addMana(Player p, double amount) {
        mana.put(p.getUniqueId(), Math.max(0, Math.min(maxMana(p), mana(p.getUniqueId()) + amount)));
    }

    private void regen() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            boolean fiveLeaf = grimoires.carried(p).orElse(null) == GrimoireType.FIVE_LEAF;
            addMana(p, manaMath().regenPerSecond(fiveLeaf) / 2.0);
        }
    }

    // ---------------------------------------------------------------- selection

    private List<Spell> unlocked(Player p, GrimoireType type) {
        return spells.unlocked(type, mage.level(p.getUniqueId(), type));
    }

    public Optional<Spell> selectedSpell(Player p, GrimoireType type) {
        List<Spell> list = unlocked(p, type);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(selection.index(p.getUniqueId(), type, list.size())));
    }

    /** Clamp mana after a level change (selection clamps itself). */
    public void onLevelChanged(Player p) {
        addMana(p, 0);
    }

    // ---------------------------------------------------------------- input

    private Optional<GrimoireType> liveHeld(Player p, ItemStack item) {
        Optional<GrimoireType> type = grimoires.items().typeOf(item);
        if (type.isEmpty() || !grimoires.isLive(item) || grimoires.carried(p).orElse(null) != type.get()) {
            return Optional.empty();
        }
        return type;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Optional<GrimoireType> type = liveHeld(event.getPlayer(), event.getItem());
        if (type.isEmpty()) {
            return;
        }
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == org.bukkit.Material.LECTERN
                && plugin.altars().libraryAt(event.getClickedBlock()).isPresent()) {
            return; // altar clicks belong to the altar
        }
        event.setCancelled(true);
        click(event.getPlayer(), type.get());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        liveHeld(p, p.getInventory().getItemInMainHand()).ifPresent(type -> {
            event.setCancelled(true);
            click(p, type);
        });
    }

    private void click(Player p, GrimoireType type) {
        if (!debounce.tryAcquire(p.getUniqueId(), System.currentTimeMillis())) {
            return;
        }
        if (p.isSneaking()) {
            cycle(p, type);
        } else {
            cast(p, type);
        }
    }

    private void cycle(Player p, GrimoireType type) {
        List<Spell> list = unlocked(p, type);
        if (list.isEmpty()) {
            return;
        }
        int next = selection.next(p.getUniqueId(), type, list.size());
        notice(p, "<dark_gray>ꜱᴇʟᴇᴄᴛᴇᴅ</dark_gray>  <c><spell></c>", Msg.color("c", type.rgb()), Msg.arg("spell", Msg.sc(list.get(next).name())));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        grimoires.refreshLore(p);
    }

    private void fail(Player p, String text, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        notice(p, text, args);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    /** Short action-bar message from a spell (e.g. "No target"). */
    public void tell(Player p, String miniMessage) {
        fail(p, miniMessage);
    }

    private void notice(Player p, String text, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        Msg.actionBar(p, text, args);
        hudPausedUntil.put(p.getUniqueId(), System.currentTimeMillis() + 1500);
    }

    private void cast(Player p, GrimoireType type) {
        UUID id = p.getUniqueId();
        if (plugin.settings().disabledWorlds().contains(p.getWorld().getName())) {
            fail(p, "<red>ᴍᴀɢɪᴄ ᴅᴏᴇꜱɴ'ᴛ ᴡᴏʀᴋ ʜᴇʀᴇ");
            return;
        }
        if (status.isFrozen(id)) {
            fail(p, "<aqua>ꜰʀᴏᴢᴇɴ ɪɴ ᴛɪᴍᴇ");
            return;
        }
        if (status.isSilenced(id)) {
            fail(p, "<dark_gray>ꜱɪʟᴇɴᴄᴇᴅ</dark_gray> <gray>ʏᴏᴜ ᴄᴀɴ'ᴛ ᴄᴀꜱᴛ");
            return;
        }
        Optional<Spell> chosen = selectedSpell(p, type);
        if (chosen.isEmpty()) {
            return;
        }
        Spell spell = chosen.get();
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(id, Map.of()).getOrDefault(spell.id(), 0L);
        if (now < readyAt) {
            fail(p, "<white><spell></white> <gray>ʀᴇᴀᴅʏ ɪɴ</gray> <yellow><t>ꜱ", Msg.arg("spell", Msg.sc(spell.name())),
                    Msg.arg("t", String.format("%.1f", (readyAt - now) / 1000.0)));
            return;
        }
        if (type.usesMana() && mana(id) < spell.cost()) {
            fail(p, "<aqua>ɴᴏᴛ ᴇɴᴏᴜɢʜ ᴍᴀɴᴀ</aqua> <gray>(<have>/<need>)", Msg.arg("have", (int) mana(id)),
                    Msg.arg("need", (int) spell.cost()));
            return;
        }
        SpellContext ctx = new SpellContext(p, type, mage.level(id, type), spell, fx, status, this);
        boolean ok;
        try {
            ok = spell.action().cast(ctx);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Spell " + spell.id() + " failed", e);
            ok = false;
        }
        if (!ok) {
            return;
        }
        if (type.usesMana()) {
            addMana(p, -spell.cost());
        }
        if (!ctx.cooldownSkipped()) {
            double seconds = spell.cooldownSeconds() * (status.hasBuff(id, "devil_union") ? 0.5 : 1.0);
            cooldowns.computeIfAbsent(id, k -> new HashMap<>()).put(spell.id(), now + (long) (seconds * 1000));
        }
    }

    // ---------------------------------------------------------------- HUD

    private void hud() {
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (hudPausedUntil.getOrDefault(p.getUniqueId(), 0L) > now) {
                continue;
            }
            Optional<GrimoireType> held = liveHeld(p, p.getInventory().getItemInMainHand());
            if (held.isEmpty()) {
                continue;
            }
            GrimoireType type = held.get();
            Optional<Spell> spell = selectedSpell(p, type);
            if (spell.isEmpty()) {
                continue;
            }
            long readyAt = cooldowns.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(spell.get().id(), 0L);
            String cd = readyAt > now ? String.format("<yellow>%.1fꜱ</yellow>", (readyAt - now) / 1000.0) : "<green>✔</green>";
            String manaText;
            if (type.usesMana()) {
                double max = maxMana(p);
                int filled = (int) Math.round(10 * mana(p.getUniqueId()) / max);
                manaText = "<aqua>" + "▮".repeat(filled) + "</aqua><dark_gray>" + "▮".repeat(10 - filled) + "</dark_gray> <gray>"
                        + (int) mana(p.getUniqueId()) + "</gray>";
            } else {
                manaText = "<gray>ɴᴏ ᴍᴀɴᴀ</gray>";
            }
            Msg.actionBar(p, "<c><type></c> <dark_gray>ʟᴠ</dark_gray> <white><lv></white>   <c><spell></c>   " + manaText + "   " + cd,
                    Msg.color("c", type.rgb()), Msg.arg("type", Msg.sc(type.displayName())),
                    Msg.arg("lv", mage.level(p.getUniqueId(), type)), Msg.arg("spell", Msg.sc(spell.get().name())));
        }
    }

    /** Extra /magic lines: mana and the spell list. */
    public void describe(Player p, GrimoireType type, int level, CommandSender to) {
        if (type.usesMana()) {
            Msg.send(to, "<dark_gray>ᴍᴀɴᴀ</dark_gray>  <aqua><m></aqua><dark_gray>/</dark_gray><gray><max>", Msg.arg("m", (int) mana(p.getUniqueId())), Msg.arg("max", (int) maxMana(p)));
        }
        List<Spell> list = spells.spells(type);
        for (int i = 0; i < list.size(); i++) {
            Spell s = list.get(i);
            boolean open = level >= SpellUnlocks.levelForSlot(i);
            Msg.send(to, (open ? "<green>✔</green> <white><name></white>" : "<dark_gray>✖ <name></dark_gray>")
                            + "  <dark_gray>ʟᴠ <lv> · <cost> ᴍᴀɴᴀ · <cd>ꜱ</dark_gray>",
                    Msg.arg("name", Msg.sc(s.name())), Msg.arg("lv", s.unlockLevel()), Msg.arg("cost", (int) s.cost()),
                    Msg.arg("cd", s.cooldownSeconds()));
        }
    }
}
