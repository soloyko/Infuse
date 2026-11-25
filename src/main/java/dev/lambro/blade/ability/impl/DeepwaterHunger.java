package dev.lambro.blade.ability.impl;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.AbilityManager;
import dev.lambro.blade.ability.SettingDef;
import dev.lambro.blade.ability.Trigger;
import dev.lambro.blade.util.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;

/** Tier 5 — the blade drinks: lifesteal, and every kill restores you and shortens cooldowns. */
public final class DeepwaterHunger extends Ability {

    public DeepwaterHunger(LambroPlugin plugin) {
        super(plugin, "deepwater_hunger", "Deepwater Hunger", 5, Trigger.PASSIVE, Material.PRISMARINE_CRYSTALS,
                "lifesteal; kills cut cooldowns",
                List.of("The blade drinks from every wound,",
                        "returning a share of the damage as health.",
                        "Kills restore you and shorten all cooldowns."));
        setting(SettingDef.num("lifesteal", "Lifesteal", Material.GHAST_TEAR, 12, 1, 0, 100, "%"));
        setting(SettingDef.num("kill-heal", "Heal On Kill", Material.GLISTERING_MELON_SLICE, 4, 0.5, 0, 40, "hp"));
        setting(SettingDef.num("cooldown-cut", "Cooldown Cut On Kill", Material.CLOCK, 3, 0.5, 0, 60, "s"));
    }

    private void heal(Player p, double amount) {
        if (amount <= 0 || p.isDead()) return;
        p.setHealth(Math.min(AbilityManager.maxHealth(p), p.getHealth() + amount));
    }

    @Override
    public void onMeleeHitAfter(Player p, LivingEntity target, EntityDamageByEntityEvent e) {
        double amount = e.getFinalDamage() * get("lifesteal") / 100.0;
        if (amount <= 0.05) return;
        heal(p, amount);
        // thin stream of blue drawn from the wound back into the wielder
        Location from = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        Location to = p.getLocation().add(0, 1.1, 0);
        Fx.line(from, to, 0.35, l -> Fx.fade(l, Fx.ROYAL, Fx.ICE, 0.7f, 1, 0.03));
    }

    @Override
    public void onKill(Player p, LivingEntity victim) {
        heal(p, get("kill-heal"));
        plugin.abilities().reduceAll(p, get("cooldown-cut"));
        Location c = victim.getLocation().add(0, 0.2, 0);
        Fx.ring(c, 1.4, 28, 0, l -> Fx.fade(l, Fx.CYAN, Fx.VOID, 1.3f, 1, 0));
        victim.getWorld().spawnParticle(Particle.SCULK_SOUL, c.clone().add(0, 0.6, 0), 6, 0.3, 0.3, 0.3, 0.04);
        p.playSound(p.getLocation(), Sound.ENTITY_GLOW_SQUID_SQUIRT, 0.7f, 1.4f);
        p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.6f);
    }
}
