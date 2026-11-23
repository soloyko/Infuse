package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Spell;
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

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

final class SpatialSpells {

    private SpatialSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.SPATIAL,
                new Spell("spatial_space_cut", "Space Cut", 1, 12, 1.5, 5, SpatialSpells::spaceCut),
                new Spell("spatial_recall_gate", "Recall Gate", 5, 20, 3, 0, SpatialSpells::recallGate),
                new Spell("spatial_swap", "Swap", 10, 35, 15, 0, SpatialSpells::swap),
                new Spell("spatial_banish", "Banish", 20, 80, 60, 0, SpatialSpells::banish));
    }

    private static boolean spaceCut(SpellContext ctx) {
        Player p = ctx.caster();
        Location eye = p.getEyeLocation();
        Fx.sound(eye, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 2f);
        for (LivingEntity t : ctx.fx().line(p, eye, eye.getDirection(), 8, 1.0, Particle.PORTAL, null)) {
            ctx.fx().hit(p, t, ctx.dmg());
        }
        return true;
    }

    private static boolean recallGate(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<Location> mark = ctx.status().takeMark(p.getUniqueId());
        if (mark.isEmpty()) {
            ctx.status().setMark(p.getUniqueId(), p.getLocation(), 1200);
            ctx.skipCooldown();
            Fx.ring(p.getLocation().add(0, 0.1, 0), 1, Particle.PORTAL, null, 30);
            Fx.sound(p.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1.2f);
            Msg.actionBar(p, "<aqua>Gate marked. <gray>Cast again within 60s to return.");
            return true;
        }
        Location to = mark.get();
        if (!to.getWorld().equals(p.getWorld())) {
            ctx.casting().tell(p, "<red>Your gate is in another world.");
            return false;
        }
        Fx.particle(p.getWorld(), Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.5, null);
        p.teleport(to);
        p.setFallDistance(0);
        Fx.particle(p.getWorld(), Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 60, 0.5, null);
        Fx.sound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        return true;
    }

    private static boolean swap(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 25);
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
        Fx.particle(a.getWorld(), Particle.PORTAL, a.clone().add(0, 1, 0), 40, 0.5, null);
        Fx.particle(b.getWorld(), Particle.PORTAL, b.clone().add(0, 1, 0), 40, 0.5, null);
        Fx.sound(a, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        Fx.sound(b, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        return true;
    }

    private static boolean banish(SpellContext ctx) {
        Player p = ctx.caster();
        Optional<LivingEntity> target = SpellUtil.target(ctx, 25);
        if (target.isEmpty()) {
            return false;
        }
        LivingEntity t = target.get();
        World w = t.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            int x = (int) Math.floor(t.getLocation().getX() + Math.cos(a) * 40);
            int z = (int) Math.floor(t.getLocation().getZ() + Math.sin(a) * 40);
            Location probe = new Location(w, x, w.getHighestBlockYAt(x, z) + 1, z, t.getLocation().getYaw(), t.getLocation().getPitch());
            Optional<Location> spot = ctx.fx().safeSpot(probe);
            if (spot.isEmpty()) {
                continue;
            }
            Fx.particle(w, Particle.REVERSE_PORTAL, t.getLocation().add(0, 1, 0), 80, 0.6, null);
            t.teleport(spot.get());
            t.setFallDistance(0);
            ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 100, 0);
            ctx.fx().effect(p, t, PotionEffectType.WEAKNESS, 100, 0);
            Fx.particle(w, Particle.PORTAL, spot.get().clone().add(0, 1, 0), 80, 0.6, null);
            Fx.sound(spot.get(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            Fx.sound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.5f);
            if (t instanceof Player victim) {
                Msg.actionBar(victim, "<dark_aqua>You were banished by <white><name></white>!", Msg.arg("name", p.getName()));
            }
            return true;
        }
        ctx.casting().tell(p, "<red>Space resists: no safe place to send them.");
        return false;
    }
}
