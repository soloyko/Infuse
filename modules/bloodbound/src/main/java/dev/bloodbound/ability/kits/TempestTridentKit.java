package dev.bloodbound.ability.kits;

import dev.bloodbound.ability.Abilities;
import dev.bloodbound.ability.Ctx;
import dev.bloodbound.ability.WeaponKit;
import dev.bloodbound.weapon.WeaponType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stormcharge passive (every Nth hit), Gale pull active, Maelstrom lightning ultimate. */
public final class TempestTridentKit implements WeaponKit {
    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    @Override
    public WeaponType type() {
        return WeaponType.TEMPEST_TRIDENT;
    }

    @Override
    public void onWeaponHit(Ctx ctx, LivingEntity target, EntityDamageByEntityEvent event, boolean ranged) {
        int every = Math.max(1, ctx.cfg().integer("storm-every", 4));
        int count = hitCounter.merge(ctx.player().getUniqueId(), 1, Integer::sum);
        if (count % every != 0) return;
        double bonus = ctx.cfg().num("storm-damage", 2.0) + ctx.cfg().num("storm-damage-per-tier", 0.75) * ctx.tier();
        event.setDamage(event.getDamage() + bonus);
        target.getWorld().strikeLightningEffect(target.getLocation());
    }

    @Override
    public boolean active(Ctx ctx) {
        Player user = ctx.player();
        double range = ctx.cfg().num("gale-range", 10.0);
        double damage = ctx.cfg().num("gale-damage", 2.0);
        Location eye = user.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        for (double d = 1; d <= range; d += 0.8) {
            user.getWorld().spawnParticle(Particle.CLOUD, eye.clone().add(look.clone().multiply(d)), 2, 0.2, 0.2, 0.2, 0.01);
        }
        user.getWorld().playSound(user.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.8f);
        for (LivingEntity t : Abilities.targetsInCone(user, range, ctx.cfg().num("gale-angle", 35.0))) {
            Vector pull = user.getLocation().toVector().subtract(t.getLocation().toVector());
            double dist = pull.length();
            if (dist < 0.5) continue;
            pull.normalize().multiply(Math.min(1.6, 0.35 + dist * 0.12)).setY(0.35);
            t.setVelocity(pull);
            Abilities.damage(user, t, damage);
        }
        return true;
    }

    @Override
    public boolean ultimate(Ctx ctx) {
        Player user = ctx.player();
        double radius = ctx.cfg().num("maelstrom-radius", 6.0);
        double damage = ctx.cfg().num("maelstrom-damage", 8.0);
        Location center = user.getLocation();
        user.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1.5f, 1f);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            user.getWorld().strikeLightningEffect(center.clone().add(Math.cos(a) * radius * 0.7, 0, Math.sin(a) * radius * 0.7));
        }
        for (LivingEntity t : Abilities.targetsAround(user, center, radius)) {
            t.getWorld().strikeLightningEffect(t.getLocation());
            Abilities.damage(user, t, damage);
            Abilities.knockAway(center, t, 0.5, 1.0);
        }
        return true;
    }

    @Override
    public void clear(Player player) {
        hitCounter.remove(player.getUniqueId());
    }
}
