package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class LightningSpells {

    private LightningSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.LIGHTNING,
                new Spell("lightning_spark_bolt", "Spark Bolt", 1, 12, 1.5, 4, LightningSpells::sparkBolt),
                new Spell("lightning_thunder_step", "Thunder Step", 5, 20, 6, 0, LightningSpells::thunderStep),
                new Spell("lightning_chain", "Chain Lightning", 10, 40, 12, 6, LightningSpells::chain),
                new Spell("lightning_thunder_god", "Thunder God", 20, 80, 70, 4, LightningSpells::thunderGod));
    }

    private static boolean sparkBolt(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 2f);
        ctx.fx().beam(p, 25, 0.5, Particle.ELECTRIC_SPARK, null).ifPresent(t -> ctx.fx().hit(p, t, ctx.dmg()));
        return true;
    }

    private static boolean thunderStep(SpellContext ctx) {
        boolean ok = SpellUtil.blink(ctx, 8, Particle.ELECTRIC_SPARK, null);
        if (ok) {
            Fx.sound(ctx.caster().getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.6f);
        }
        return ok;
    }

    private static boolean chain(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> first = SpellUtil.target(ctx, 25);
        if (first.isEmpty()) {
            return false;
        }
        Fx.sound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
        Set<UUID> hit = new HashSet<>();
        org.bukkit.Location from = p.getEyeLocation();
        LivingEntity current = first.get();
        for (int i = 0; i < 4 && current != null; i++) {
            Fx.line(p.getWorld(), from, current.getEyeLocation(), Particle.ELECTRIC_SPARK, null, 0.25);
            ctx.fx().hit(p, current, ctx.dmg());
            hit.add(current.getUniqueId());
            from = current.getEyeLocation();
            LivingEntity source = current;
            current = ctx.fx().area(p, source.getLocation(), 6).stream()
                    .filter(e -> !hit.contains(e.getUniqueId()))
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(source.getLocation())))
                    .orElse(null);
        }
        return true;
    }

    private static boolean thunderGod(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "thunder_god", 200, Fx.pe(PotionEffectType.SPEED, 200, 2));
        p.getWorld().strikeLightningEffect(p.getLocation());
        ctx.fx().repeat(10, 20, step -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "thunder_god")) {
                Fx.particle(p.getWorld(), Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 12, 0.5, null);
            }
        });
        return true;
    }
}
