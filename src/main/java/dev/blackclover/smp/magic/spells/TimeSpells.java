package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.Spell.Role;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import dev.blackclover.smp.magic.StatusService;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;

final class TimeSpells {

    private static final Particle.DustOptions GOLD = Fx.dust(0xE8C55A, 1.4f);
    private static final Particle.DustOptions PALE = Fx.dust(0xFFF6D8, 1.0f);

    private TimeSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.TIME,
                new Spell("time_bolt", "Time Bolt", 1, Role.ATTACK, 11, 1.1, 5,
                        "A bolt of stolen time that drags its victim into slow motion.", TimeSpells::bolt),
                new Spell("time_accelerate", "Accelerate", 5, Role.SUPPORT, 24, 18, 0,
                        "Speed up your own time: run faster and recover spells twice as fast.", TimeSpells::accelerate),
                new Spell("time_rewind", "Rewind", 10, Role.DEFENSE, 36, 22, 0,
                        "Return to where you stood 4 seconds ago, with the health you had.", TimeSpells::rewind),
                new Spell("time_stop", "Time Stop", 20, Role.ULTIMATE, 90, 60, 0,
                        "Stop time around you for 4s: enemies can't move or cast.", TimeSpells::timeStop));
    }

    private static boolean bolt(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sounds(p.getLocation(), Sound.BLOCK_BELL_USE, 0.6f, 2f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.DUST, GOLD, 2.2, 30, 0.85).count(3),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 50, 2);
                        ctx.fx().effect(p, t, PotionEffectType.MINING_FATIGUE, 50, 1);
                        Fx.ring(t.getLocation().add(0, 1, 0), 0.8, Particle.DUST, PALE, 16);
                    }
                }, null);
        return true;
    }

    private static boolean accelerate(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "accelerate", 140, Fx.pe(PotionEffectType.SPEED, 140, 2), Fx.pe(PotionEffectType.HASTE, 140, 1));
        Fx.ring(p.getLocation().add(0, 1, 0), 1.2, Particle.DUST, GOLD, 30);
        Fx.sounds(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 2f);
        ctx.fx().repeat(5, 28, i -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "accelerate")) {
                Fx.particle(p.getWorld(), Particle.DUST, p.getLocation().add(0, 0.2, 0), 3, 0.3, PALE);
            }
        });
        return true;
    }

    private static boolean rewind(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<StatusService.Snapshot> past = ctx.status().history5s(p.getUniqueId());
        if (past.isEmpty() || !past.get().location().getWorld().equals(p.getWorld())) {
            ctx.casting().tell(p, "<red>ɴᴏ ᴛɪᴍᴇʟɪɴᴇ ᴛᴏ ʀᴇᴡɪɴᴅ ʏᴇᴛ");
            return false;
        }
        Location from = p.getLocation().add(0, 1, 0);
        Location to = past.get().location();
        Fx.helix(from, to.toVector().subtract(from.toVector()).add(new org.bukkit.util.Vector(0, 1, 0)),
                Math.max(1, from.distance(to.clone().add(0, 1, 0))), 0.5, 3, Particle.DUST, GOLD);
        p.teleport(to);
        p.setFallDistance(0);
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (past.get().health() > p.getHealth()) {
            p.setHealth(Math.min(max, past.get().health()));
        }
        for (PotionEffect e : List.copyOf(p.getActivePotionEffects())) {
            if (e.getType() == PotionEffectType.POISON || e.getType() == PotionEffectType.WITHER
                    || e.getType() == PotionEffectType.SLOWNESS || e.getType() == PotionEffectType.BLINDNESS) {
                p.removePotionEffect(e.getType());
            }
        }
        p.setFireTicks(0);
        Fx.ring(to.clone().add(0, 1, 0), 1.1, Particle.DUST, GOLD, 30);
        Fx.sound(to, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1f, 0.6f);
        return true;
    }

    private static boolean timeStop(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.TIME.rgb());
        Location c = p.getLocation();
        List<LivingEntity> caught = ctx.fx().area(p, c, 14);
        for (LivingEntity t : caught) {
            ctx.status().freeze(t, 80);
        }
        Fx.self(p, PotionEffectType.SPEED, 80, 1);
        Fx.sounds(c, Sound.BLOCK_BELL_RESONATE, 2f, 0.6f, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 2f);
        ctx.fx().shockwave(c.clone().add(0, 1, 0), 14, 12, Particle.DUST, GOLD);
        ctx.fx().repeat(4, 20, i -> {
            // clock hands sweeping around each frozen target
            for (LivingEntity t : caught) {
                if (!t.isValid()) {
                    continue;
                }
                Location at = t.getLocation().add(0, 1, 0);
                double a = i * Math.PI / 10;
                Fx.ring(at, 0.9, Particle.DUST, PALE, 14);
                Fx.line(at.getWorld(), at, at.clone().add(Math.cos(a) * 0.8, Math.sin(a) * 0.8, 0), Particle.DUST, GOLD, 0.2);
            }
        });
        return true;
    }
}
