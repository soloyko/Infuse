package dev.blackclover.smp.gui;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.core.LevelCurve;
import dev.blackclover.smp.core.SpellUnlocks;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.mage.PlayerData;
import dev.blackclover.smp.pack.PackItems;
import dev.blackclover.smp.pack.PackModels;
import dev.blackclover.smp.util.Msg;
import dev.blackclover.smp.util.SmallCaps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The spellbook: your grimoire at a glance and its four spells, clickable to select. */
public final class SpellbookMenu {

    private static final int[] SPELL_SLOTS = {19, 21, 23, 25};
    private static final Map<GrimoireType, Material> ICONS = new EnumMap<>(GrimoireType.class);

    static {
        ICONS.put(GrimoireType.FIRE, Material.FIRE_CHARGE);
        ICONS.put(GrimoireType.WATER, Material.HEART_OF_THE_SEA);
        ICONS.put(GrimoireType.WIND, Material.FEATHER);
        ICONS.put(GrimoireType.LIGHTNING, Material.LIGHTNING_ROD);
        ICONS.put(GrimoireType.EARTH, Material.POINTED_DRIPSTONE);
        ICONS.put(GrimoireType.PLANT, Material.FLOWERING_AZALEA);
        ICONS.put(GrimoireType.LIGHT, Material.GLOWSTONE_DUST);
        ICONS.put(GrimoireType.DARK, Material.ECHO_SHARD);
        ICONS.put(GrimoireType.SPATIAL, Material.ENDER_PEARL);
        ICONS.put(GrimoireType.GRAVITY, Material.HEAVY_CORE);
        ICONS.put(GrimoireType.BLOOD, Material.REDSTONE);
        ICONS.put(GrimoireType.POISON, Material.SPIDER_EYE);
        ICONS.put(GrimoireType.TIME, Material.CLOCK);
        ICONS.put(GrimoireType.ANTI_MAGIC, Material.NETHERITE_SWORD);
        ICONS.put(GrimoireType.FIVE_LEAF, Material.WITHER_ROSE);
    }

    private final BlackCloverPlugin plugin;

    public SpellbookMenu(BlackCloverPlugin plugin) {
        this.plugin = plugin;
    }

    private static Component line(String miniMessage) {
        return Msg.mm(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() + word.length() + 1 > width && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (!cur.isEmpty()) {
                cur.append(' ');
            }
            cur.append(word);
        }
        if (!cur.isEmpty()) {
            lines.add(cur.toString());
        }
        return lines;
    }

    private static String bar(double fraction, int cells, String on, String off) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * cells);
        return "<" + on + ">" + "▮".repeat(filled) + "</" + on + "><" + off + ">" + "▮".repeat(cells - filled) + "</" + off + ">";
    }

    public void open(Player p) {
        Optional<GrimoireType> carried = plugin.grimoires().carried(p);
        if (carried.isEmpty()) {
            Msg.send(p, "<gray>You carry no grimoire. Find a library tower past 1,000 blocks, or take one from a fallen mage.");
            return;
        }
        GrimoireType type = carried.get();
        String hex = Msg.hex(type.rgb());
        LevelCurve curve = plugin.settings().levelCurve();
        PlayerData pd = plugin.mage().data(p.getUniqueId());
        int xp = pd.xp(type);
        int level = curve.levelForXp(xp);
        double progress = level >= curve.cap() ? 1 : (xp - curve.totalXpForLevel(level)) / (double) curve.xpToNext(level);
        double mana = plugin.casting().mana(p.getUniqueId());
        double maxMana = plugin.casting().maxMana(p);

        Menu menu = new Menu(6, Msg.mm("<dark_gray>" + SmallCaps.of(type.displayName() + " Grimoire")));
        Menu filler = menu;
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.setHideTooltip(true);
        pane.setItemMeta(pm);
        for (int i = 0; i < 54; i++) {
            filler.set(i, pane, null);
        }

        // the grimoire itself
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta bm = book.getItemMeta();
        PackItems.tag(bm, PackModels.grimoireKey(type));
        bm.setEnchantmentGlintOverride(true);
        bm.displayName(line("<" + hex + "><b>" + type.displayName() + " Grimoire"));
        List<Component> lore = new ArrayList<>();
        lore.add(line("<gray><i>" + type.bio()));
        lore.add(Component.empty());
        lore.add(line("<dark_gray>ʟᴇᴠᴇʟ  <white>" + level + "</white> <dark_gray>/ " + curve.cap()));
        lore.add(line(bar(progress, 20, "green", "dark_gray") + (level >= curve.cap() ? " <gold>ᴍᴀx" : " <gray>" + (int) (progress * 100) + "%")));
        if (type.usesMana()) {
            lore.add(line("<dark_gray>ᴍᴀɴᴀ  <aqua>" + (int) mana + "</aqua><dark_gray>/" + (int) maxMana));
            lore.add(line(bar(mana / maxMana, 20, "aqua", "dark_gray")));
        } else {
            lore.add(line("<gray>ᴀɴᴛɪ-ᴍᴀɢɪᴄ ɴᴇᴇᴅꜱ ɴᴏ ᴍᴀɴᴀ"));
        }
        bm.lore(lore);
        book.setItemMeta(bm);
        menu.set(4, book, null);

        // the four spells
        List<Spell> spells = plugin.spells().spells(type);
        Optional<Spell> selected = plugin.casting().selectedSpell(p, type);
        for (int i = 0; i < spells.size() && i < SPELL_SLOTS.length; i++) {
            Spell s = spells.get(i);
            boolean unlocked = level >= SpellUnlocks.levelForSlot(i);
            boolean isSelected = selected.isPresent() && selected.get().id().equals(s.id());
            ItemStack icon = new ItemStack(unlocked ? ICONS.getOrDefault(type, Material.BOOK) : Material.GRAY_DYE);
            ItemMeta im = icon.getItemMeta();
            im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            String roleTag = "<" + Msg.hex(s.role().rgb()) + ">" + s.role().label();
            im.displayName(line((unlocked ? "<" + hex + "><b>" : "<dark_gray><b>") + SmallCaps.of(s.name())));
            List<Component> sl = new ArrayList<>();
            sl.add(line(roleTag + (s.role() == Spell.Role.ULTIMATE ? " ✦" : "")));
            sl.add(Component.empty());
            for (String w : wrap(s.description(), 34)) {
                sl.add(line("<gray>" + w));
            }
            sl.add(Component.empty());
            String costText = type.usesMana() ? (s.cost() > 0 ? (int) s.cost() + " ᴍᴀɴᴀ" : "ꜰʀᴇᴇ") : "ɴᴏ ᴍᴀɴᴀ";
            sl.add(line("<dark_gray>ᴄᴏꜱᴛ <white>" + costText + "</white>  ᴄᴏᴏʟᴅᴏᴡɴ <white>" + trim(s.cooldownSeconds()) + "ꜱ"));
            if (s.damage() > 0 && !s.id().equals("blood_pact")) {
                sl.add(line("<dark_gray>ᴘᴏᴡᴇʀ <white>" + trim(s.damage() * (1 + 0.01 * (level - 1))) + " ❤</white><dark_gray>/2"));
            }
            sl.add(Component.empty());
            if (!unlocked) {
                sl.add(line("<red>🔒 ᴜɴʟᴏᴄᴋꜱ ᴀᴛ ʟᴇᴠᴇʟ " + SpellUnlocks.levelForSlot(i)));
            } else if (isSelected) {
                sl.add(line("<green>✔ ꜱᴇʟᴇᴄᴛᴇᴅ"));
            } else {
                long cd = plugin.casting().cooldownLeftMillis(p, s);
                sl.add(line(cd > 0 ? "<yellow>ʀᴇᴀᴅʏ ɪɴ " + trim(cd / 1000.0) + "ꜱ" : "<gray>ᴄʟɪᴄᴋ ᴛᴏ ꜱᴇʟᴇᴄᴛ"));
            }
            im.lore(sl);
            im.setEnchantmentGlintOverride(isSelected);
            icon.setItemMeta(im);
            int slot = i;
            menu.set(SPELL_SLOTS[i], icon, e -> {
                if (plugin.casting().select(p, type, slot)) {
                    p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.3f);
                    open(p);
                } else {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
                }
            });
            // role colour strip under each spell
            ItemStack strip = new ItemStack(unlocked ? roleGlass(s.role()) : Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta sm = strip.getItemMeta();
            sm.setHideTooltip(true);
            strip.setItemMeta(sm);
            menu.set(SPELL_SLOTS[i] + 9, strip, null);
        }

        // help and stored levels
        menu.set(48, AdminMenus.icon(Material.KNOWLEDGE_BOOK, "<gold><b>How to cast",
                "<gray>Right-click <dark_gray>cast the selected spell",
                "<gray>Shift + right-click <dark_gray>next spell",
                "<gray>Shift + left-click <dark_gray>open this book",
                "", "<gray>Level up by defeating mages.", "<gray>Dying costs 15% of your XP."), null);
        StringBuilder stored = new StringBuilder();
        for (Map.Entry<GrimoireType, Integer> e : pd.xpView().entrySet()) {
            if (e.getKey() != type) {
                stored.append(e.getKey().displayName()).append(" ʟᴠ ").append(curve.levelForXp(e.getValue())).append("|");
            }
        }
        List<String> storedLines = new ArrayList<>();
        for (String s : stored.toString().split("\\|")) {
            if (!s.isBlank()) {
                storedLines.add("<gray>" + s);
            }
        }
        if (storedLines.isEmpty()) {
            storedLines.add("<dark_gray>None yet");
        }
        menu.set(50, AdminMenus.icon(Material.CHISELED_BOOKSHELF, "<aqua><b>Other grimoires",
                storedLines.toArray(String[]::new)), null);
        menu.open(p);
    }

    private static Material roleGlass(Spell.Role role) {
        return switch (role) {
            case ATTACK -> Material.RED_STAINED_GLASS_PANE;
            case MOBILITY -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case DEFENSE -> Material.LIME_STAINED_GLASS_PANE;
            case CONTROL -> Material.PURPLE_STAINED_GLASS_PANE;
            case SUPPORT -> Material.YELLOW_STAINED_GLASS_PANE;
            case ULTIMATE -> Material.ORANGE_STAINED_GLASS_PANE;
        };
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.1f", v);
    }
}
