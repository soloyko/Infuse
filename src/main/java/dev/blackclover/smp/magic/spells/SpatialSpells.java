package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Spell;
import dev.blackclover.smp.magic.Spell.Role;
import dev.blackclover.smp.magic.SpellContext;
import dev.blackclover.smp.magic.SpellRegistry;
import dev.blackclover.smp.util.Msg;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

final class SpatialSpells {

    private static final Particle.DustOptions TEAL = Fx.dust(0x22CCCC, 1.3f);

    private SpatialSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.SPATIAL,
                new Spell("spatial_space_cut", "Space Cut", 1, Role.ATTACK, 12, 1.3, 5,
                        "Slice space itself where you aim: no projectile, no dodging.", SpatialSpells::spaceCut),
                new Spell("spatial_portal_gate", "Portal Gate", 5, Role.MOBILITY, 18, 4, 0,
                        "Cast once to open a gate here; cast again within 60s to return.", SpatialSpells::portalGate),
                new Spell("spatial_swap", "Space Swap", 10, Role.CONTROL, 32, 12, 0,
                        "Trade places with your target, leaving them dizzy and slowed.", SpatialSpells::swap),
                new Spell("spatial_exile", "Spatial Exile", 20, Role.ULTIMATE, 80, 55, 0,
                        "Banish your target and everyone near them 50 blocks away.", SpatialSpells::exile));
    }

    private static boolean spaceCut(SpellContext ctx) {
        Player p = ctx.caster();
        Location at = ctx.fx().aimPoint(p, 18);
        Fx.ring(at, 1.6, Particle.PORTAL, null, 30);
        Fx.ring(at, 1.0, Particle.DUST, TEAL, 18);
        Fx.line(p.getWorld(), at.clone().add(-1.5, 1.2, 0), at.clone().add(1.5, -1.2, 0), Particle.DUST, TEAL, 0.15);
        Fx.line(p.getWorld(), at.clone().add(0, 1.2, -1.5), at.clone().add(0, -1.2, 1.5), Particle.DUST, TEAL, 0.15);
        Fx.sounds(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 2f, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.8f);
        for (LivingEntity t : ctx.fx().area(p, at, 2.2)) {
            ctx.fx().hit(p, t, ctx.dmg());
        }
        return true;
    }

    private static boolean portalGate(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<Location> mark = ctx.status().takeMark(p.getUniqueId());
        if (mark.isEmpty()) {
            Location here = p.getLocation();
            ctx.status().setMark(p.getUniqueId(), here, 1200);
            ctx.skipCooldown();
            Fx.sound(here, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.2f);
            Msg.actionBar(p, "<aqua>ɢᴀᴛᴇ ᴏᴘᴇɴᴇᴅ</aqua> <gray>ᴄᴀꜱᴛ ᴀɢᴀɪɴ ᴡɪᴛʜɪɴ 60ꜱ ᴛᴏ ʀᴇᴛᴜʀɴ");
            ctx.fx().repeat(10, 120, i -> {
                if (ctx.status().hasMark(p.getUniqueId(), here)) {
                    Fx.ring(here.clone().add(0, 1, 0), 0.9, Particle.PORTAL, null, 16);
                    Fx.ring(here.clone().add(0, 1, 0), 0.7, Particle.DUST, TEAL, 10);
                }
            });
            return true;
        }
        Location to = mark.get();
        if (!to.getWorld().equals(p.getWorld())) {
            ctx.casting().tell(p, "<red>ʏᴏᴜʀ ɢᴀᴛᴇ ɪꜱ ɪɴ ᴀɴᴏᴛʜᴇʀ ᴡᴏʀʟᴅ");
            return false;
        }
        Fx.particle(p.getWorld(), Particle.PORTAL, p.getLocation().add(0, 1, 0), 80, 0.5, null);
        p.teleport(to);
        p.setFallDistance(0);
        Fx.particle(p.getWorld(), Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 80, 0.5, null);
        Fx.sound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        return true;
    }

    private static boolean swap(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 28);
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity t = target.get();
        Location a = p.getLocation();
        Location b = t.getLocation();
        Location toCaster = b.clone();
        toCaster.setYaw(a.getYaw());
        toCaster.setPitch(a.getPitch());
        Location toTarget = a.clone();
        toTarget.setYaw(b.getYaw());
        toTarget.setPitch(b.getPitch());
        p.teleport(toCaster);
        t.teleport(toTarget);
        ctx.fx().effect(p, t, PotionEffectType.NAUSEA, 80, 0);
        ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 50, 1);
        for (Location l : List.of(a, b)) {
            Fx.particle(l.getWorld(), Particle.PORTAL, l.clone().add(0, 1, 0), 60, 0.5, null);
            Fx.ring(l.clone().add(0, 0.1, 0), 1.2, Particle.DUST, TEAL, 20);
            Fx.sound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        }
        return true;
    }

    private static boolean exile(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 30);
        if (target.isEmpty()) {
            return false;
        }
        ctx.fx().announce(p, ctx.spell(), GrimoireType.SPATIAL.rgb());
        LivingEntity main = target.get();
        List<LivingEntity> group = ctx.fx().area(p, main.getLocation(), 4);
        if (!group.contains(main)) {
            group.add(main);
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double angle = rnd.nextDouble() * Math.PI * 2;
        for (LivingEntity t : group) {
            World w = t.getWorld();
            Location origin = t.getLocation();
            Fx.sphere(origin.clone().add(0, 1, 0), 1.3, Particle.REVERSE_PORTAL, null, 60);
            for (int attempt = 0; attempt < 8; attempt++) {
                double a = angle + rnd.nextDouble(-0.3, 0.3) + attempt * 0.8;
                int x = (int) Math.floor(origin.getX() + Math.cos(a) * 50);
                int z = (int) Math.floor(origin.getZ() + Math.sin(a) * 50);
                Location probe = new Location(w, x, w.getHighestBlockYAt(x, z) + 1, z, origin.getYaw(), origin.getPitch());
                Optional<Location> spot = ctx.fx().safeSpot(probe);
                if (spot.isEmpty()) {
                    continue;
                }
                t.teleport(spot.get());
                t.setFallDistance(0);
                ctx.fx().effect(p, t, PotionEffectType.BLINDNESS, 60, 0);
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 80, 1);
                Fx.particle(w, Particle.PORTAL, spot.get().clone().add(0, 1, 0), 80, 0.6, null);
                if (t instanceof Player victim) {
                    Msg.actionBar(victim, "<dark_aqua>ʏᴏᴜ ᴡᴇʀᴇ ᴇxɪʟᴇᴅ ʙʏ <white><name>", Msg.arg("name", p.getName()));
                }
                break;
            }
        }
        Fx.self(p, PotionEffectType.SPEED, 100, 1);
        Fx.sounds(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.5f, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        return true;
    }
}
