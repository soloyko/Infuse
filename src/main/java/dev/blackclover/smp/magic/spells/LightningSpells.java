package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.Spell.Role;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class LightningSpells {

    private static final Particle.DustOptions VOLT = Fx.dust(0xFFF27A, 1.2f);

    private LightningSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.LIGHTNING,
                new Spell("lightning_thunder_bolt", "Thunder Bolt", 1, Role.ATTACK, 11, 1.1, 5,
                        "An instant bolt that arcs to a second nearby enemy.", LightningSpells::thunderBolt),
                new Spell("lightning_thunder_step", "Thunder Step", 5, Role.MOBILITY, 20, 5, 3,
                        "Blink 12 blocks in a crack of lightning, shocking foes at both ends.", LightningSpells::thunderStep),
                new Spell("lightning_chain", "Chain Lightning", 10, Role.CONTROL, 38, 12, 6,
                        "Lightning chains through five enemies, stunning each one.", LightningSpells::chain),
                new Spell("lightning_thunder_god", "Thunder God's Descent", 20, Role.ULTIMATE, 80, 55, 7,
                        "Call down six lightning strikes on nearby foes and become the storm.", LightningSpells::thunderGod));
    }

    private static Optional<LivingEntity> nearestTo(SpellContext ctx, Location at, double radius, Set<UUID> exclude) {
        return ctx.fx().area(ctx.caster(), at, radius).stream()
                .filter(e -> !exclude.contains(e.getUniqueId()))
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(at)));
    }

    private static boolean thunderBolt(SpellContext ctx) {
        Player p = ctx.caster();
        Location from = ctx.fx().origin(p);
        Location aim = ctx.fx().aimPoint(p, 28);
        Optional<LivingEntity> target = ctx.fx().targetEntity(p, 28);
        Location end = target.map(t -> t.getLocation().add(0, t.getHeight() * 0.6, 0)).orElse(aim);
        Fx.zigzag(from, end, Particle.ELECTRIC_SPARK, null);
        Fx.zigzag(from, end, Particle.DUST, VOLT);
        Fx.sounds(from, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 2f);
        Fx.particle(end.getWorld(), Particle.ELECTRIC_SPARK, end, 20, 0.3, null);
        target.ifPresent(t -> {
            ctx.fx().hit(p, t, ctx.dmg());
            Set<UUID> seen = new HashSet<>();
            seen.add(t.getUniqueId());
            nearestTo(ctx, t.getLocation(), 6, seen).ifPresent(second -> {
                Fx.zigzag(end, second.getEyeLocation(), Particle.ELECTRIC_SPARK, null);
                ctx.fx().hit(p, second, ctx.dmg() * 0.5);
            });
        });
        return true;
    }

    private static void shockAround(SpellContext ctx, Location at) {
        Fx.particle(at.getWorld(), Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0), 30, 0.6, null);
        for (LivingEntity t : ctx.fx().area(ctx.caster(), at, 2.5)) {
            if (ctx.fx().hit(ctx.caster(), t, ctx.dmg())) {
                ctx.fx().effect(ctx.caster(), t, PotionEffectType.SLOWNESS, 30, 2);
            }
        }
    }

    private static boolean thunderStep(SpellContext ctx) {
        Player p = ctx.caster();
        Location before = p.getLocation();
        boolean ok = SpellUtil.blink(ctx, 12, Particle.ELECTRIC_SPARK, null);
        if (ok) {
            Fx.zigzag(before.clone().add(0, 1, 0), p.getLocation().add(0, 1, 0), Particle.DUST, VOLT);
            shockAround(ctx, before);
            shockAround(ctx, p.getLocation());
            Fx.sounds(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 2f, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
        }
        return ok;
    }

    private static boolean chain(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> first = SpellUtil.target(ctx, 28);
        if (first.isEmpty()) {
            return false;
        }
        Fx.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.5f);
        Set<UUID> hit = new HashSet<>();
        Location from = ctx.fx().origin(p);
        LivingEntity current = first.get();
        for (int i = 0; i < 5 && current != null; i++) {
            Location to = current.getLocation().add(0, current.getHeight() * 0.6, 0);
            Fx.zigzag(from, to, Particle.ELECTRIC_SPARK, null);
            Fx.zigzag(from, to, Particle.DUST, VOLT);
            if (ctx.fx().hit(p, current, ctx.dmg())) {
                ctx.status().silence(current, 20);
                ctx.fx().effect(p, current, PotionEffectType.SLOWNESS, 25, 4);
            }
            hit.add(current.getUniqueId());
            from = to;
            current = nearestTo(ctx, current.getLocation(), 7, hit).orElse(null);
        }
        return true;
    }

    private static boolean thunderGod(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.LIGHTNING.rgb());
        p.getWorld().strikeLightningEffect(p.getLocation());
        ctx.status().addBuff(p, "thunder_god", 200, Fx.pe(PotionEffectType.SPEED, 200, 2), Fx.pe(PotionEffectType.HASTE, 200, 1));
        List<LivingEntity> foes = ctx.fx().area(p, p.getLocation(), 14);
        foes.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
        for (int i = 0; i < 6; i++) {
            int index = i;
            ctx.fx().later(6 + i * 5, () -> {
                Location at;
                LivingEntity t = foes.isEmpty() ? null : foes.get(index % foes.size());
                if (t != null && t.isValid()) {
                    at = t.getLocation();
                } else {
                    double a = Math.random() * Math.PI * 2;
                    at = p.getLocation().add(Math.cos(a) * 6, 0, Math.sin(a) * 6);
                }
                at.getWorld().strikeLightningEffect(at);
                Fx.particle(at.getWorld(), Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0), 40, 0.8, null);
                for (LivingEntity e : ctx.fx().area(p, at, 2.5)) {
                    ctx.fx().hit(p, e, ctx.dmg());
                }
            });
        }
        ctx.fx().repeat(5, 40, i -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "thunder_god")) {
                Fx.particle(p.getWorld(), Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 8, 0.5, null);
            }
        });
        return true;
    }
}
