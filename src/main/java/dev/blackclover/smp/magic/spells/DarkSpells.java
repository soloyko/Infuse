package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.Spell.Role;
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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

final class DarkSpells {

    private static final Particle.DustOptions PURPLE = Fx.dust(0x5A1E99, 1.8f);
    private static final Particle.DustOptions BLACK = Fx.dust(0x111111, 2.0f);

    private DarkSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.DARK,
                new Spell("dark_orb", "Dark Orb", 1, Role.ATTACK, 13, 1.6, 7,
                        "A heavy orb of darkness that bursts and blinds on impact.", DarkSpells::orb),
                new Spell("dark_shadow_veil", "Shadow Veil", 5, Role.DEFENSE, 24, 16, 0,
                        "Vanish into shadow for 6s. Your first strike out of it deals bonus damage.", DarkSpells::veil),
                new Spell("dark_black_hole", "Black Hole", 10, Role.CONTROL, 42, 14, 1,
                        "Tear open a black hole that drags enemies in and grinds them down.", DarkSpells::blackHole),
                new Spell("dark_dimension_slash", "Dimension Slash", 20, Role.ULTIMATE, 85, 55, 18,
                        "Cleave the very dimension in front of you with a slash of pure darkness.", DarkSpells::dimensionSlash));
    }

    private static boolean orb(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sounds(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.5f, Sound.ENTITY_ENDERMAN_AMBIENT, 0.5f, 0.6f);
        ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.DUST, PURPLE, 1.2, 30, 1.0).count(3)
                        .model(PackItems.model(Material.ENDER_EYE, PackModels.DARK_ORB), 0.85f, false),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.BLINDNESS, 30, 0);
                    }
                },
                end -> {
                    Fx.sphere(end, 1.5, Particle.DUST, BLACK, 40);
                    Fx.particle(end.getWorld(), Particle.SQUID_INK, end, 20, 0.5, null);
                    Fx.sound(end, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.5f, 1.6f);
                    for (LivingEntity t : ctx.fx().area(p, end, 2.5)) {
                        ctx.fx().hit(p, t, ctx.dmg() * 0.5);
                    }
                });
        return true;
    }

    private static boolean veil(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "shadow_veil", 120, Fx.pe(PotionEffectType.INVISIBILITY, 120, 0), Fx.pe(PotionEffectType.SPEED, 120, 1));
        ctx.status().addBuff(p, "shadow_strike", 120);
        Fx.particle(p.getWorld(), Particle.SQUID_INK, p.getLocation().add(0, 1, 0), 40, 0.5, null);
        Fx.sphere(p.getLocation().add(0, 1, 0), 1.2, Particle.DUST, BLACK, 40);
        Fx.sounds(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.6f, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.5f);
        return true;
    }

    private static boolean blackHole(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = ctx.fx().aimPoint(p, 26).add(0, 0.8, 0);
        Fx.sounds(c, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2f, 0.5f, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.6f);
        ctx.fx().repeat(5, 16, i -> {
            Fx.sphere(c, 0.9, Particle.DUST, BLACK, 30);
            double r = 4 - (i % 4);
            Fx.ring(c, r, Particle.REVERSE_PORTAL, null, 36);
            Fx.ring(c.clone().add(0, 0.3, 0), r * 0.7, Particle.DUST, PURPLE, 20);
            for (LivingEntity t : ctx.fx().area(p, c, 7)) {
                Vector pull = c.toVector().subtract(t.getLocation().toVector());
                if (pull.lengthSquared() > 0.36) {
                    ctx.fx().push(p, t, pull.normalize().multiply(0.6).setY(0.12));
                }
                if (i % 2 == 0) {
                    ctx.fx().hit(p, t, ctx.dmg());
                    ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 20, 1);
                }
            }
        });
        return true;
    }

    private static boolean dimensionSlash(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.DARK.rgb());
        Location eye = p.getEyeLocation();
        Vector dir = SpellUtil.horizontalLook(p);
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Fx.sounds(eye, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 0.5f, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
        ctx.fx().repeat(1, 6, i -> {
            for (double w = -2.5; w <= 2.5; w += 0.35) {
                Location from = eye.clone().add(right.clone().multiply(w)).add(0, -w * 0.35, 0);
                Fx.line(p.getWorld(), from.clone().add(dir.clone().multiply(i * 5)), from.clone().add(dir.clone().multiply(i * 5 + 5)),
                        Particle.DUST, BLACK, 0.45);
            }
            Location mid = eye.clone().add(dir.clone().multiply(i * 5 + 2.5));
            Fx.particle(mid.getWorld(), Particle.REVERSE_PORTAL, mid, 30, 1.2, null);
        });
        for (LivingEntity t : ctx.fx().line(p, eye.clone().subtract(0, 0.6, 0), dir, 30, 2.6, Particle.SWEEP_ATTACK, null)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().effect(p, t, PotionEffectType.WITHER, 60, 1);
            }
        }
        return true;
    }
}
