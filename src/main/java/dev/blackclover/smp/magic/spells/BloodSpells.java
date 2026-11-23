package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
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

final class BloodSpells {

    private static final Particle.DustOptions RED = Fx.dust(0xB00020, 1.4f);
    private static final Particle.DustOptions DARK_RED = Fx.dust(0x5A0010, 1.8f);

    private BloodSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.BLOOD,
                new Spell("blood_needles", "Blood Needles", 1, Role.ATTACK, 11, 1.2, 3,
                        "A fan of three blood needles that heal you for part of the damage.", BloodSpells::needles),
                new Spell("blood_pact", "Blood Pact", 5, Role.SUPPORT, 0, 14, 6,
                        "Pay 3 hearts of blood: refill 60 mana and gain Strength for 6s.", BloodSpells::pact),
                new Spell("blood_crimson_lash", "Crimson Lash", 10, Role.CONTROL, 34, 11, 7,
                        "A whip of blood drags enemies towards you and drinks their life.", BloodSpells::crimsonLash),
                new Spell("blood_moon", "Blood Moon", 20, Role.ULTIMATE, 80, 55, 3,
                        "For 5s, drain the life of every enemy near you and heal from it.", BloodSpells::bloodMoon));
    }

    private static boolean needles(SpellContext ctx) {
        Player p = ctx.caster();
        Location from = ctx.fx().origin(p);
        Vector aim = ctx.fx().aimPoint(p, 30).toVector().subtract(from.toVector()).normalize();
        Fx.sound(p.getLocation(), Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1f, 1.6f);
        for (double spread : new double[]{-0.1, 0, 0.1}) {
            ctx.fx().projectile(p, from, aim.clone().rotateAroundY(spread), ProjectileSpec.of(Particle.DUST, RED, 2.5, 30, 0.75),
                    t -> {
                        if (ctx.fx().hit(p, t, ctx.dmg())) {
                            Fx.lifesteal(p, ctx.dmg() * 0.3);
                        }
                    },
                    end -> Fx.particle(end.getWorld(), Particle.DUST, end, 6, 0.2, DARK_RED));
        }
        return true;
    }

    /** Costs health instead of mana: the spell's "damage" value is the health paid. */
    private static boolean pact(SpellContext ctx) {
        Player p = ctx.caster();
        double price = ctx.spell().damage();
        if (p.getHealth() <= price) {
            ctx.casting().tell(p, "<red>ᴛᴏᴏ ᴡᴇᴀᴋ ᴛᴏ ᴘᴀʏ ᴛʜᴇ ʙʟᴏᴏᴅ ᴘʀɪᴄᴇ");
            return false;
        }
        p.setHealth(p.getHealth() - price);
        ctx.casting().addMana(p, 60);
        ctx.status().addBuff(p, "blood_pact", 120, Fx.pe(PotionEffectType.STRENGTH, 120, 0));
        Fx.sphere(p.getLocation().add(0, 1, 0), 1.1, Particle.DUST, RED, 50);
        Fx.particle(p.getWorld(), Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1.5, 0), 8, 0.3, null);
        Fx.sounds(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.2f);
        return true;
    }

    private static boolean crimsonLash(SpellContext ctx) {
        Player p = ctx.caster();
        Location eye = p.getEyeLocation();
        Vector forward = SpellUtil.horizontalLook(p);
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        ctx.fx().repeat(1, 4, i -> {
            for (double a = -1.1; a <= 1.1; a += 0.08) {
                double reach = 2 + i * 1.4;
                Location at = eye.clone().add(forward.clone().multiply(reach * Math.cos(a))).add(right.clone().multiply(Math.sin(a) * reach));
                Fx.particle(eye.getWorld(), Particle.DUST, at, 1, 0.03, i % 2 == 0 ? RED : DARK_RED);
            }
        });
        Fx.sounds(eye, Sound.ITEM_TRIDENT_HIT, 1f, 0.6f, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.7f);
        double healed = 0;
        for (LivingEntity t : ctx.fx().cone(p, 7, 90)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().effect(p, t, PotionEffectType.WITHER, 60, 0);
                Vector pull = p.getLocation().toVector().subtract(t.getLocation().toVector());
                if (pull.lengthSquared() > 0.01) {
                    ctx.fx().push(p, t, pull.normalize().multiply(0.9).setY(0.3));
                }
                healed += ctx.dmg() * 0.5;
            }
        }
        Fx.lifesteal(p, healed);
        return true;
    }

    private static boolean bloodMoon(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.BLOOD.rgb());
        ctx.status().addBuff(p, "blood_moon", 100, Fx.pe(PotionEffectType.RESISTANCE, 100, 0));
        Fx.sound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.8f);
        ctx.fx().repeat(5, 20, i -> {
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            Location c = p.getLocation();
            Fx.ring(c.clone().add(0, 0.2, 0), 10, Particle.DUST, DARK_RED, 70);
            Fx.ring(c.clone().add(0, 4.5, 0), 1.6, Particle.DUST, RED, 20);
            if (i % 4 != 0) {
                return;
            }
            Fx.sound(c, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1f);
            double drained = 0;
            for (LivingEntity t : ctx.fx().area(p, c, 10)) {
                if (ctx.fx().hit(p, t, ctx.dmg())) {
                    drained += ctx.dmg();
                    Fx.line(p.getWorld(), t.getEyeLocation(), p.getEyeLocation(), Particle.DUST, RED, 0.35);
                }
            }
            Fx.lifesteal(p, drained * 0.5);
        });
        return true;
    }
}
