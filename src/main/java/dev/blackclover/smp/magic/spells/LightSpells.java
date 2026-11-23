package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import dev.blackclover.smp.pack.PackItems;
import dev.blackclover.smp.pack.PackModels;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

final class LightSpells {

    private LightSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.LIGHT,
                new Spell("light_arrow", "Light Arrow", 1, 12, 1.2, 6, LightSpells::arrow),
                new Spell("light_speed", "Light Speed", 5, 18, 5, 0, LightSpells::speed),
                new Spell("light_swords", "Light Swords", 10, 40, 12, 4, LightSpells::swords),
                new Spell("light_judgment_ray", "Judgment Ray", 20, 90, 60, 18, LightSpells::judgmentRay));
    }

    private static boolean arrow(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 2f);
        ctx.fx().beam(p, 35, 0.5, Particle.END_ROD, null).ifPresent(t -> ctx.fx().hit(p, t, ctx.dmg()));
        return true;
    }

    private static boolean speed(SpellContext ctx) {
        boolean ok = SpellUtil.blink(ctx, 15, Particle.END_ROD, null);
        if (ok) {
            Fx.sound(ctx.caster().getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.5f);
        }
        return ok;
    }

    private static boolean swords(SpellContext ctx) {
        Player p = ctx.caster();
        List<LivingEntity> targets = SpellUtil.nearest(ctx, 20, 5);
        if (targets.isEmpty()) {
            ctx.casting().tell(p, "<red>No enemies nearby.");
            return false;
        }
        Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
        for (int i = 0; i < 5; i++) {
            LivingEntity t = targets.get(i % targets.size());
            double angle = Math.PI * 2 * i / 5;
            ctx.fx().later(i * 3, () -> {
                if (!t.isValid()) {
                    return;
                }
                Location start = p.getEyeLocation().add(Math.cos(angle) * 1.2, 1.0, Math.sin(angle) * 1.2);
                Vector dir = t.getEyeLocation().toVector().subtract(start.toVector());
                ctx.fx().projectile(p, start, dir, ProjectileSpec.of(Particle.END_ROD, null, 2.5, 25, 0.9).count(2)
                                .model(PackItems.model(Material.GOLDEN_SWORD, PackModels.LIGHT_SWORD), 1.0f, true),
                        hit -> ctx.fx().hit(p, hit, ctx.dmg()), null);
            });
        }
        return true;
    }

    private static boolean judgmentRay(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.5f);
        ctx.fx().repeat(2, 10, step -> Fx.ring(p.getEyeLocation(), 2.0 - step * 0.18, Particle.END_ROD, null, 16));
        ctx.fx().later(20, () -> {
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            Location eye = p.getEyeLocation();
            Fx.sound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.6f);
            Fx.sound(eye, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 2f);
            for (LivingEntity t : ctx.fx().line(p, eye, eye.getDirection(), 40, 1.8, Particle.END_ROD, null)) {
                ctx.fx().hit(p, t, ctx.dmg());
                Fx.particle(t.getWorld(), Particle.END_ROD, t.getEyeLocation(), 20, 0.4, null);
            }
        });
        return true;
    }
}
