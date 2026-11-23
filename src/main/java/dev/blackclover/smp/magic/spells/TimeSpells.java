package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import dev.blackclover.smp.magic.StatusService;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;

final class TimeSpells {

    private static final Particle.DustOptions GOLD = Fx.dust(0xE8C55A, 1.4f);

    private TimeSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.TIME,
                new Spell("time_bolt", "Time Bolt", 1, 12, 1.5, 4, TimeSpells::bolt),
                new Spell("time_accelerate", "Accelerate", 5, 20, 16, 0, TimeSpells::accelerate),
                new Spell("time_rewind", "Rewind", 10, 40, 25, 0, TimeSpells::rewind),
                new Spell("time_stop", "Time Stop", 20, 90, 75, 0, TimeSpells::timeStop));
    }

    private static boolean bolt(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.5f, 2f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.DUST, GOLD, 2.0, 30, 0.8),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 1);
                    }
                }, null);
        return true;
    }

    private static boolean accelerate(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "accelerate", 160,
                Fx.pe(PotionEffectType.SPEED, 160, 1), Fx.pe(PotionEffectType.HASTE, 160, 1));
        Fx.ring(p.getLocation().add(0, 1, 0), 1, Particle.DUST, GOLD, 24);
        Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
        return true;
    }

    private static boolean rewind(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<StatusService.Snapshot> past = ctx.status().history5s(p.getUniqueId());
        if (past.isEmpty() || !past.get().location().getWorld().equals(p.getWorld())) {
            ctx.casting().tell(p, "<red>There's no timeline to rewind yet.");
            return false;
        }
        Location from = p.getLocation().add(0, 1, 0);
        Location to = past.get().location();
        Fx.line(p.getWorld(), from, to.clone().add(0, 1, 0), Particle.REVERSE_PORTAL, null, 0.3);
        p.teleport(to);
        p.setFallDistance(0);
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (past.get().health() > 0) {
            p.setHealth(Math.min(max, past.get().health()));
        }
        Fx.sound(to, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 0.6f);
        return true;
    }

    private static boolean timeStop(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        List<LivingEntity> caught = ctx.fx().area(p, c, 12);
        for (LivingEntity t : caught) {
            ctx.status().freeze(t, 60);
        }
        Fx.sound(c, Sound.BLOCK_BELL_RESONATE, 2f, 0.6f);
        Fx.sound(c, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f);
        ctx.fx().repeat(1, 12, step -> Fx.ring(c.clone().add(0, 1, 0), step + 1, Particle.DUST, GOLD, 30 + step * 6));
        ctx.fx().repeat(5, 12, step -> {
            for (LivingEntity t : caught) {
                if (t.isValid()) {
                    Fx.particle(t.getWorld(), Particle.END_ROD, t.getLocation().add(0, 1, 0), 3, 0.4, null);
                }
            }
        });
        return true;
    }
}
