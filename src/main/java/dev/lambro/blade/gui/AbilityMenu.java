package dev.lambro.blade.gui;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.SettingDef;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static dev.lambro.blade.util.Text.*;

/** Per-ability editor: toggle + every numeric setting. */
public final class AbilityMenu extends Menu {

    private static final int INFO = 13, TOGGLE = 22, BACK = 45;
    private static final int[] SETTING_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Ability ability;

    public AbilityMenu(LambroPlugin plugin, Player viewer, Ability ability) {
        super(plugin, viewer, 6, "<" + DEEP + ">" + tc(ability.name()) + " <" + SHADOW + ">· <" + STEEL + ">" + tc("tier " + ability.tier()));
        this.ability = ability;
    }

    @Override
    protected void draw() {
        List<String> info = new ArrayList<>();
        info.add(label("trigger", tc(ability.trigger().label())));
        info.add(label("unlocks at", ability.tier() * plugin.blade().killsPerUnlock() + " " + tc("kills")));
        info.add("");
        for (String d : ability.description()) info.add("<" + STEEL + ">" + tc(d));
        ItemStack head = icon(ability.icon(), "<" + CYAN + ">" + (ability.isPassive() ? "◆ " : "✦ ") + header(ability.name()), info);
        head.editMeta(m -> m.setEnchantmentGlintOverride(true));
        set(INFO, head);

        boolean on = ability.enabled();
        set(TOGGLE, icon(on ? Material.LIME_DYE : Material.GRAY_DYE, header(on ? "enabled" : "disabled"),
                List.of(toggle(on), "", hint("click", on ? "disable" : "enable"))));

        List<SettingDef> defs = ability.settings();
        for (int i = 0; i < defs.size() && i < SETTING_SLOTS.length; i++) {
            SettingDef s = defs.get(i);
            double v = ability.get(s.key());
            set(SETTING_SLOTS[i], icon(s.icon(), header(s.label()), List.of(
                    label("value", fmt(v) + "<" + STEEL + ">" + tc(s.unit())),
                    label("default", fmt(s.def()) + "<" + STEEL + ">" + tc(s.unit())),
                    label("range", fmt(s.min()) + " – " + fmt(s.max())),
                    "",
                    hint("left / right", "+" + fmt(s.step()) + " / -" + fmt(s.step())),
                    hint("shift", "x5 step"),
                    hint("drop [q]", "reset to default"))));
        }

        set(BACK, icon(Material.ARROW, "<" + STEEL + ">" + tc("« back"), List.of()));
    }

    @Override
    public void click(int slot, ClickType type) {
        if (slot == BACK) {
            click();
            new MainMenu(plugin, viewer).open();
            return;
        }
        if (slot == TOGGLE) {
            plugin.getConfig().set(ability.path("enabled"), !ability.enabled());
            commit();
            return;
        }
        List<SettingDef> defs = ability.settings();
        for (int i = 0; i < defs.size() && i < SETTING_SLOTS.length; i++) {
            if (SETTING_SLOTS[i] != slot) continue;
            SettingDef s = defs.get(i);
            double v;
            if (type == ClickType.DROP || type == ClickType.CONTROL_DROP) {
                v = s.def();
            } else {
                double step = s.step() * (type.isShiftClick() ? 5 : 1);
                v = ability.get(s.key()) + (type.isRightClick() ? -step : step);
            }
            v = s.clamp(v);
            plugin.getConfig().set(ability.path(s.key()), s.integer() ? (Object) (int) v : (Object) v);
            commit();
            return;
        }
    }

    private void commit() {
        plugin.saveConfig();
        plugin.refreshAllBlades();
        click();
        render();
    }
}
