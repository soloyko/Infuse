package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Displays;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sabre, the Corsair's Cutlass: gunpowder, gold and the deep.
 * Passive Plunder, on-hit Cutthroat, F Powder Keg, Sneak+F Kraken's Grasp.
 */
public final class CutlassWeapon extends SwordAbility implements Listener {

    private static final int BRASS = 0xC9A227;
    private static final int SEA = 0x1F4E79;
    private static final int FOAM = 0xBFE7FF;
    private static final int KEG_TICKS = 120;
    private static final int GRASP_TICKS = 50;
    private static final BlockData BARREL = barrel();
    private static final BlockData TNT = Material.TNT.createBlockData();
    private static final BlockData FLESH = Material.NETHER_WART_BLOCK.createBlockData();
    private static final BlockData SUCKER = Material.MUSHROOM_STEM.createBlockData();

    private static BlockData barrel() {
        BlockData data = Material.BARREL.createBlockData();
        if (data instanceof org.bukkit.block.data.Directional d) {
            d.setFacing(org.bukkit.block.BlockFace.UP);
        }
        return data;
    }

    /** A powder keg on the ground, waiting for a swing. */
    private final class Keg {
        final UUID owner;
        final Location spot;
        final BlockDisplay body;
        final BlockDisplay band;
        boolean blown;

        Keg(UUID owner, Location spot, BlockDisplay body, BlockDisplay band) {
            this.owner = owner;
            this.spot = spot;
            this.body = body;
            this.band = band;
        }

        BoundingBox box() {
            return BoundingBox.of(spot.clone().add(0, 0.45, 0), 0.55, 0.55, 0.55);
        }
    }

    private final List<Keg> kegs = new ArrayList<>();

    public CutlassWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.CUTLASS);
    }

    @Override
    public void heldEffects(Player player, List<PotionEffect> out) {
        out.add(effect(PotionEffectType.DOLPHINS_GRACE, 0, 0));
    }

    /** Plunder: kills with the cutlass sometimes shake loose a little gold. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || plugin.items().heldType(killer) != type || !plugin.canUse(killer, type)
                || event.getEntity() instanceof Player) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() > 0.4) {
            return;
        }
        ItemStack loot = r.nextDouble() < 0.12 ? ItemStack.of(Material.GOLD_INGOT) : ItemStack.of(Material.GOLD_NUGGET, r.nextInt(2, 6));
        Location at = Combat.center(event.getEntity());
        at.getWorld().dropItemNaturally(at, loot);
        Fx.particle(at, Particle.WAX_OFF, 12, 0.4, 0.2);
        Fx.sound(at, Sound.ENTITY_ITEM_PICKUP, 1f, 0.7f);
        Fx.sound(at, Sound.BLOCK_CHAIN_FALL, 0.6f, 1.6f);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (!chance(0.2)) {
            return;
        }
        double damage = event.getDamage() * 0.5;
        Vector look = player.getEyeLocation().getDirection();
        displays().later(4, () -> {
            if (!target.isValid() || target.isDead() || !alive(player)) {
                return;
            }
            Location at = Combat.center(target);
            combat().damage(player, target, damage, true);
            applyEffect(target, PotionEffectType.SLOWNESS, 40, 1);
            fx().cut(at, look, Math.PI - 0.3, 0.3, BRASS, 1.6f, 2);
            Fx.sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        });
    }

    // ---- Powder Keg ----

    /**
     * Powder Keg: a keg of gunpowder rolls out to where you aim and sits there with its fuse
     * hissing. Swing at it to set it off (or it goes off by itself after six seconds): a big
     * blast that throws everything around it away, without breaking any blocks.
     */
    @Override
    public boolean primary(Player player) {
        Location aim = combat().aimPoint(player, 12);
        Vector look = dev.infuse.customs.ability.Aim.flatLook(player);
        if (aim == null || aim.distanceSquared(player.getLocation()) > 144) {
            aim = player.getLocation().add(look.clone().multiply(6));
        }
        Location spot = Combat.ground(aim.clone().add(0, 0.5, 0));
        Location from = player.getLocation().add(look.clone().multiply(0.8));
        from.setY(spot.getY());
        // it rolls from your feet to the spot, tumbling as it goes
        Location start = from.clone().add(0, 0.45, 0);
        start.setYaw(0);
        start.setPitch(0);
        BlockDisplay body = displays().block(start, BARREL, new Vector3f(0.8f, 0.9f, 0.8f), new Quaternionf(), KEG_TICKS + 40);
        BlockDisplay band = displays().block(start.clone().add(0, 0.5, 0), TNT, new Vector3f(0.3f, 0.12f, 0.3f), new Quaternionf(), KEG_TICKS + 40);
        body.setTeleportDuration(1);
        band.setTeleportDuration(1);
        Keg keg = new Keg(player.getUniqueId(), spot, body, band);
        kegs.add(keg);
        Fx.sound(from, Sound.BLOCK_BARREL_CLOSE, 1.2f, 0.7f);
        Fx.sound(from, Sound.ENTITY_TNT_PRIMED, 1f, 0.8f);
        Vector roll = spot.toVector().subtract(from.toVector()).setY(0);
        double rollDistance = roll.length();
        Vector axis = rollDistance < 1.0E-3 ? new Vector(1, 0, 0) : new Vector(roll.getZ(), 0, -roll.getX()).normalize();

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (keg.blown) {
                    cancel();
                    return;
                }
                if (!alive(player) || ticks > KEG_TICKS) {
                    blow(player, keg);
                    cancel();
                    return;
                }
                if (ticks <= 10) {
                    double t = ticks / 10.0;
                    Location p = from.clone().add(roll.clone().multiply(t)).add(0, 0.45, 0);
                    p.setYaw(0);
                    p.setPitch(0);
                    body.teleport(p);
                    band.teleport(p.clone().add(0, 0.5, 0));
                    Quaternionf tumble = new Quaternionf().rotateAxis((float) (rollDistance * t * 1.6), (float) axis.getX(), 0, (float) axis.getZ());
                    Displays.animateBlock(body, new Vector3f(0.8f, 0.9f, 0.8f), tumble, new Vector3f(), 1);
                    Fx.particle(p.clone().subtract(0, 0.4, 0), Particle.BLOCK, 2, 0.2, 0.02, BARREL);
                    if (ticks == 10) {
                        Displays.animateBlock(body, new Vector3f(0.8f, 0.9f, 0.8f), new Quaternionf(), new Vector3f(), 3);
                    }
                    return;
                }
                // the fuse hisses
                Location fuse = spot.clone().add(0, 1.05, 0);
                Fx.particle(fuse, Particle.SMOKE, 1, 0.03, 0.02);
                if (ticks % 2 == 0) {
                    Fx.particle(fuse, Particle.ELECTRIC_SPARK, 1, 0.05, 0.05);
                }
                if (ticks % 20 == 0) {
                    Fx.sound(spot, Sound.ENTITY_CREEPER_PRIMED, 0.6f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Swinging at your own keg sets it off. */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING || kegs.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Location eye = player.getEyeLocation();
        for (Keg keg : new ArrayList<>(kegs)) {
            if (keg.owner.equals(player.getUniqueId()) && keg.spot.getWorld() == eye.getWorld()
                    && keg.box().expand(0.3).rayTrace(eye.toVector(), eye.getDirection(), 5.5) != null) {
                blow(player, keg);
                return;
            }
        }
    }

    private void blow(Player player, Keg keg) {
        if (keg.blown) {
            return;
        }
        keg.blown = true;
        kegs.remove(keg);
        displays().remove(keg.body);
        displays().remove(keg.band);
        Location at = keg.spot.clone().add(0, 0.6, 0);
        Fx.particle(at, Particle.EXPLOSION_EMITTER, 1, 0, 0);
        Fx.particle(at, Particle.LARGE_SMOKE, 30, 1.2, 0.08);
        Fx.particle(at, Particle.FLAME, 30, 1.0, 0.12);
        Fx.particle(at, Particle.BLOCK, 30, 0.8, 0.2, BARREL);
        fx().flash(at, 0xFFC56B, 3.2f);
        fx().burst(at, BRASS, 3f);
        Fx.sound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        Fx.sound(at, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 0.6f);
        if (!alive(player)) {
            return;
        }
        for (LivingEntity target : combat().nearby(player, at, 4.5)) {
            double d = Combat.distanceToBody(target, at);
            combat().damage(player, target, 12 - d * 1.6, true);
            target.setFireTicks(Math.max(target.getFireTicks(), 40));
            Combat.knockback(target, at, 1.8 - d * 0.2, 0.6);
        }
    }

    // ---- Kraken's Grasp ----

    /**
     * Kraken's Grasp: tentacles burst out of the ground where you aim, wrap around up to three
     * enemies, lift them and slam them down twice, then fling them away.
     */
    @Override
    public boolean secondary(Player player) {
        Location aim = combat().aimPoint(player, 20);
        if (aim == null) {
            aim = player.getLocation().add(dev.infuse.customs.ability.Aim.flatLook(player).multiply(8));
        }
        Location center = Combat.ground(aim.clone().add(0, 0.5, 0));
        List<LivingEntity> seized = new ArrayList<>();
        for (LivingEntity target : combat().nearby(player, center.clone().add(0, 1, 0), 4.5)) {
            seized.add(target);
            if (seized.size() >= 3) {
                break;
            }
        }
        fx().crack(center, SEA, 3.2f, 40);
        Fx.particle(center.clone().add(0, 0.3, 0), Particle.SPLASH, 40, 1.4, 0.2);
        Fx.sound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.6f);
        Fx.sound(center, Sound.ENTITY_GENERIC_SPLASH, 1.4f, 0.5f);
        if (seized.isEmpty()) {
            // nothing to grab: the tentacles thrash where you aimed anyway
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                Location base = Combat.ground(center.clone().add(Math.cos(a) * 1.5, 0.5, Math.sin(a) * 1.5));
                tentacle(base, () -> base.clone().add(Math.cos(a) * 0.8, 2.6, Math.sin(a) * 0.8), 30);
            }
            return true;
        }
        for (LivingEntity target : seized) {
            double side = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location base = Combat.ground(target.getLocation().add(Math.cos(side) * 1.4, 0.5, Math.sin(side) * 1.4));
            Location anchor = target.getLocation();
            tentacle(base, () -> Combat.center(target), GRASP_TICKS);
            new BukkitRunnable() {
                int ticks;

                @Override
                public void run() {
                    ticks++;
                    if (!target.isValid() || target.isDead() || ticks > GRASP_TICKS) {
                        if (target.isValid() && !target.isDead() && alive(player)) {
                            // flung away
                            Combat.knockback(target, center, 1.4, 0.7);
                            combat().damage(player, target, 3, true);
                        }
                        cancel();
                        return;
                    }
                    // lift, slam, lift, slam
                    int phase = ticks % 25;
                    double y;
                    if (phase < 14) {
                        y = anchor.getY() + Math.min(2.6, phase * 0.2);
                    } else {
                        y = anchor.getY();
                    }
                    Location hold = anchor.clone();
                    hold.setY(y);
                    Vector pull = hold.toVector().subtract(target.getLocation().toVector());
                    Combat.setVelocity(target, pull.multiply(phase < 14 ? 0.4 : 0.9));
                    if (phase == 15 && alive(player)) {
                        combat().damage(player, target, 5, true);
                        fx().crack(Combat.ground(anchor.clone().add(0, 0.5, 0)), SEA, 1.8f, 20);
                        Fx.particle(anchor, Particle.SPLASH, 30, 0.6, 0.2);
                        Fx.particle(anchor, Particle.BLOCK, 16, 0.5, 0.1, anchor.clone().subtract(0, 0.5, 0).getBlock().getBlockData());
                        Fx.sound(anchor, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
                        Fx.sound(anchor, Sound.ENTITY_GENERIC_SPLASH, 1f, 0.7f);
                    }
                }
            }.runTaskTimer(plugin, 4L, 1L);
        }
        return true;
    }

    /**
     * A tentacle growing out of {@code base} towards wherever {@code tip} says, for {@code life}
     * ticks: thick dark-red segments with pale suckers, tapering to the tip.
     */
    private void tentacle(Location base, java.util.function.Supplier<Location> tip, int life) {
        int segments = 9;
        List<BlockDisplay> parts = new ArrayList<>();
        Location spawnAt = base.clone();
        spawnAt.setYaw(0);
        spawnAt.setPitch(0);
        for (int i = 0; i < segments; i++) {
            float size = 0.8f - i * 0.07f;
            BlockDisplay part = displays().block(spawnAt, i % 3 == 2 ? SUCKER : FLESH, new Vector3f(0.05f), new Quaternionf(), life + 12);
            part.setTeleportDuration(1);
            parts.add(part);
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double sway = r.nextDouble(Math.PI * 2);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > life) {
                    for (BlockDisplay part : parts) {
                        if (part.isValid()) {
                            Displays.animateBlock(part, new Vector3f(0.05f), new Quaternionf(), new Vector3f(0, -0.6f, 0), 6);
                        }
                    }
                    displays().later(7, () -> parts.forEach(p -> displays().remove(p)));
                    Fx.particle(base.clone().add(0, 0.3, 0), Particle.SPLASH, 20, 0.5, 0.1);
                    cancel();
                    return;
                }
                Location end = tip.get();
                float grow = Math.min(1f, ticks / 5f);
                Vector span = end.toVector().subtract(base.toVector());
                for (int i = 0; i < parts.size(); i++) {
                    double t = (i + 0.5) / parts.size() * grow;
                    // a curl that sways sideways, like something alive
                    double bend = Math.sin(t * Math.PI) * 0.7;
                    Vector curl = new Vector(Math.cos(sway + ticks * 0.15), 0, Math.sin(sway + ticks * 0.15)).multiply(bend);
                    Location p = base.clone().add(span.clone().multiply(t)).add(curl);
                    p.setYaw(0);
                    p.setPitch(0);
                    BlockDisplay part = parts.get(i);
                    if (!part.isValid()) {
                        continue;
                    }
                    part.teleport(p);
                    float size = (0.8f - i * 0.07f) * grow;
                    Quaternionf turn = Displays.pointing(span.lengthSquared() < 1.0E-4 ? new Vector(0, 1, 0) : span)
                            .rotateY((float) (ticks * 0.2 + i));
                    Displays.animateBlock(part, new Vector3f(size), turn, new Vector3f(), 1);
                }
                if (ticks % 5 == 0) {
                    Fx.particle(base.clone().add(0, 0.2, 0), Particle.SPLASH, 6, 0.4, 0.05);
                    Fx.particle(base.clone().add(0, 0.2, 0), Particle.DUST, 3, 0.4, 0, Fx.dust(FOAM, 1.2f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void shutdown() {
        for (Keg keg : kegs) {
            displays().remove(keg.body);
            displays().remove(keg.band);
        }
        kegs.clear();
    }
}
