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
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

final class LightSpells {

    private static final Particle.DustOptions GOLDEN = Fx.dust(0xFFE9A0, 1.3f);

    private LightSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.LIGHT,
                new Spell("light_arrow", "Light Arrow", 1, Role.ATTACK, 11, 1.0, 6,
                        "An instant beam of light that pierces every enemy in a line.", LightSpells::arrow),
                new Spell("light_speed", "Light Speed", 5, Role.MOBILITY, 16, 4, 0,
                        "Move at the speed of light: teleport up to 20 blocks.", LightSpells::speed),
                new Spell("light_swords_judgment", "Light Swords of Judgment", 10, Role.CONTROL, 40, 12, 4,
                        "Six swords of light form above you, then strike and blind your foes.", LightSpells::swords),
                new Spell("light_judgment_ray", "Judgment Ray", 20, Role.ULTIMATE, 90, 55, 18,
                        "Charge a colossal beam of light that obliterates everything in its path.", LightSpells::judgmentRay));
    }

    private static boolean arrow(SpellContext ctx) {
        Player p = ctx.caster();
        Location from = ctx.fx().origin(p);
        Vector dir = ctx.fx().aimPoint(p, 40).toVector().subtract(from.toVector()).normalize();
        Fx.sounds(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 2f, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 2f);
        for (LivingEntity t : ctx.fx().line(p, from, dir, 40, 0.9, Particle.END_ROD, null)) {
            ctx.fx().hit(p, t, ctx.dmg());
        }
        Fx.helix(from, dir, 12, 0.25, 4, Particle.DUST, GOLDEN);
        return true;
    }

    private static boolean speed(SpellContext ctx) {
        boolean ok = SpellUtil.blink(ctx, 20, Particle.END_ROD, null);
        if (ok) {
            Player p = ctx.caster();
            Fx.particle(p.getWorld(), Particle.FLASH, p.getLocation().add(0, 1, 0), 1, 0, org.bukkit.Color.WHITE);
            Fx.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.6f);
        }
        return ok;
    }

    private static boolean swords(SpellContext ctx) {
        Player p = ctx.caster();
        List<LivingEntity> targets = SpellUtil.nearest(ctx, 24, 3);
        if (targets.isEmpty()) {
            ctx.casting().tell(p, "<red>ɴᴏ ᴇɴᴇᴍɪᴇꜱ ɴᴇᴀʀʙʏ");
            return false;
        }
        Fx.sounds(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f, Sound.ITEM_TRIDENT_RETURN, 1f, 1.6f);
        List<ItemDisplay> blades = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            Location at = p.getEyeLocation().add(Math.cos(a) * 1.4, 1.1, Math.sin(a) * 1.4);
            blades.add(ctx.fx().spawnModel(at, PackItems.model(Material.GOLDEN_SWORD, PackModels.LIGHT_SWORD), 1.0f,
                    new Vector(0, -1, 0), true));
        }
        ctx.fx().later(14, () -> {
            for (int i = 0; i < blades.size(); i++) {
                ItemDisplay blade = blades.get(i);
                LivingEntity t = targets.get(i % targets.size());
                if (!blade.isValid() || !t.isValid() || !p.isOnline()) {
                    Fx.removeModel(blade);
                    continue;
                }
                Location start = blade.getLocation();
                Fx.removeModel(blade);
                Vector dir = t.getEyeLocation().toVector().subtract(start.toVector());
                ctx.fx().projectile(p, start, dir, ProjectileSpec.of(Particle.END_ROD, null, 2.8, 30, 0.9).count(2)
                                .model(PackItems.model(Material.GOLDEN_SWORD, PackModels.LIGHT_SWORD), 1.0f, true),
                        hit -> {
                            if (ctx.fx().hit(p, hit, ctx.dmg())) {
                                ctx.fx().effect(p, hit, PotionEffectType.BLINDNESS, 25, 0);
                            }
                        }, null);
            }
            Fx.sound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.5f);
        });
        ctx.fx().later(40, () -> blades.forEach(Fx::removeModel));
        return true;
    }

    private static boolean judgmentRay(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.LIGHT.rgb());
        Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2f, 1.4f);
        ctx.fx().repeat(1, 15, i -> {
            Location o = ctx.fx().origin(p);
            Fx.ring(o, 2.2 - i * 0.14, Particle.END_ROD, null, 18);
            Fx.particle(o.getWorld(), Particle.DUST, o, 6, 0.4, GOLDEN);
        });
        ctx.fx().later(16, () -> {
            if (!p.isOnline() || p.isDead()) {
                return;
            }
            Location from = ctx.fx().origin(p);
            Vector dir = ctx.fx().aimPoint(p, 50).toVector().subtract(from.toVector()).normalize();
            Fx.sounds(from, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.6f, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 2f,
                    Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.8f);
            ctx.fx().repeat(2, 5, i -> {
                for (double r = 0.3; r <= 1.5; r += 0.6) {
                    Fx.helix(from, dir, 50, r, 10, Particle.END_ROD, null);
                }
                Fx.helix(from, dir, 50, 0.9, 6, Particle.DUST, GOLDEN);
            });
            for (LivingEntity t : ctx.fx().line(p, from, dir, 50, 2.5, Particle.END_ROD, null)) {
                ctx.fx().hit(p, t, ctx.dmg());
                ctx.fx().push(p, t, dir.clone().multiply(1.2).setY(0.4));
            }
        });
        return true;
    }
}
