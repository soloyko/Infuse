package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thousand Demon Daggers, the Cursed Daggers: everything has a price, and someone else pays it.
 * Passive Twin Fangs, on-hit Hex, F Blood Pact, Sneak+F Soul Swap.
 */
public final class DaggersWeapon extends SwordAbility implements Listener {

    private static final int VIOLET = 0x9B30FF;
    private static final int BLOOD = 0xB0102A;
    private static final int SOUL = 0x7FE7FF;
    private static final int MAX_HEX = 5;
    private static final int HEX_TICKS = 200;
    private static final int PACT_TICKS = 120;
    private static final double PACT_COST = 6;

    private record Hex(UUID owner, int stacks, int until) {
    }

    /** Hits each player has landed (for Twin Fangs). */
    private final Map<UUID, Integer> hits = new HashMap<>();
    private final Map<UUID, Hex> hexes = new HashMap<>();
    /** Player -> tick their Blood Pact ends. */
    private final Map<UUID, Integer> pacts = new HashMap<>();

    public DaggersWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.DAGGERS);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        int now = Bukkit.getCurrentTick();
        // the hex already on it makes this hit worse...
        int stacks = hexOn(player, target);
        if (stacks > 0) {
            event.setDamage(event.getDamage() * (1 + 0.06 * stacks));
        }
        // ...and adds another stack
        int next = Math.min(MAX_HEX, stacks + 1);
        hexes.put(target.getUniqueId(), new Hex(player.getUniqueId(), next, now + HEX_TICKS));
        Location at = Combat.center(target);
        Fx.particle(at, Particle.WITCH, 2 + next * 2, 0.3, 0.02);
        if (next == MAX_HEX) {
            Fx.particle(target.getEyeLocation().add(0, 0.6, 0), Particle.SOUL_FIRE_FLAME, 3, 0.1, 0.01);
        }

        Integer pactEnds = pacts.get(player.getUniqueId());
        if (pactEnds != null && pactEnds > now) {
            event.setDamage(event.getDamage() + 5);
            combat().heal(player, event.getDamage() * 0.5);
            Fx.particle(at, Particle.DUST, 10, 0.3, 0, Fx.dust(BLOOD, 1.3f));
            fx().drift(at, player.getLocation().add(0, 1, 0).toVector().subtract(at.toVector()), BLOOD, 0.5f, 6);
        }

        // Twin Fangs: every third hit, the other dagger follows
        int count = hits.merge(player.getUniqueId(), 1, Integer::sum);
        if (count % 3 == 0) {
            double extra = event.getDamage() * 0.6;
            Vector look = player.getEyeLocation().getDirection();
            displays().later(3, () -> {
                if (!alive(player) || !target.isValid() || target.isDead()) {
                    return;
                }
                combat().damage(player, target, extra, true);
                fx().cut(Combat.center(target), look, Math.PI - 0.4, 0.3, VIOLET, 1.4f, 2);
                Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.7f);
            });
        }
    }

    private int hexOn(Player player, LivingEntity target) {
        Hex hex = hexes.get(target.getUniqueId());
        return hex == null || hex.until < Bukkit.getCurrentTick() || !hex.owner.equals(player.getUniqueId()) ? 0 : hex.stacks;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hits.remove(event.getPlayer().getUniqueId());
        pacts.remove(event.getPlayer().getUniqueId());
    }

    // ---- Blood Pact ----

    /**
     * Blood Pact: the daggers drink three of your hearts. For six seconds afterwards every hit you
     * land deals +5 damage and gives you back half of what it dealt.
     */
    @Override
    public boolean primary(Player player) {
        if (player.getHealth() <= PACT_COST + 0.5) {
            tell(player, "You don't have the blood to pay.");
            return false;
        }
        player.setHealth(player.getHealth() - PACT_COST);
        pacts.put(player.getUniqueId(), Bukkit.getCurrentTick() + PACT_TICKS);
        Location at = player.getLocation().add(0, 1, 0);
        Fx.sound(at, Sound.ENTITY_PLAYER_HURT, 1f, 0.6f);
        Fx.sound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1f);
        Fx.sound(at, Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.6f);
        Fx.particle(at, Particle.DUST, 40, 0.4, 0, Fx.dust(BLOOD, 1.6f));
        Fx.particle(at, Particle.DAMAGE_INDICATOR, 6, 0.3, 0.1);
        fx().crack(player.getLocation(), BLOOD, 2.2f, 30);
        // the pact's mark: a blood sigil that turns beneath you
        org.bukkit.entity.ItemDisplay sigil = fx().sigil(player.getLocation().add(0, 0.08, 0), new Vector(0, 1, 0), BLOOD, 1.6f, PACT_TICKS);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (!alive(player) || ticks > PACT_TICKS) {
                    displays().remove(sigil);
                    if (alive(player)) {
                        Fx.sound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
                    }
                    cancel();
                    return;
                }
                sigil.teleport(player.getLocation().add(0, 0.08, 0));
                if (ticks % 4 == 0) {
                    Fx.particle(player.getLocation().add(0, 1, 0), Particle.DUST, 2, 0.35, 0, Fx.dust(BLOOD, 1.0f));
                    Fx.particle(player.getLocation().add(0, 1.2, 0), Particle.DRIPPING_DRIPSTONE_LAVA, 1, 0.3, 0);
                }
                if (ticks % 20 == 0) {
                    Fx.sound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 1.3f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    // ---- Soul Swap ----

    /**
     * Soul Swap: you and the enemy under your crosshair trade places in a flash of soulfire. It
     * arrives weakened and blinded with two more stacks of your hex, and you get a burst of speed.
     */
    @Override
    public boolean secondary(Player player) {
        LivingEntity target = combat().lookTarget(player, 24, 0.6);
        if (target == null) {
            tell(player, "Look at an enemy to swap with.");
            return false;
        }
        Location mine = player.getLocation();
        Location theirs = target.getLocation();
        Location meTo = theirs.clone();
        meTo.setYaw(mine.getYaw());
        meTo.setPitch(mine.getPitch());
        Location themTo = mine.clone();
        themTo.setYaw(theirs.getYaw());
        themTo.setPitch(theirs.getPitch());
        Location a = mine.clone().add(0, 1, 0);
        Location b = Combat.center(target);
        fx().afterimage(a, plugin.items().create(type), b.toVector().subtract(a.toVector()), VIOLET, 1.0f, 12);
        fx().beam(a, b, VIOLET, 0.35f, 10);
        fx().beam(a, b, SOUL, 0.12f, 8);
        player.teleport(meTo, PlayerTeleportEvent.TeleportCause.PLUGIN);
        target.teleport(themTo);
        status().protectFromFall(player, 40);

        int now = Bukkit.getCurrentTick();
        hexes.put(target.getUniqueId(), new Hex(player.getUniqueId(), Math.min(MAX_HEX, hexOn(player, target) + 2), now + HEX_TICKS));
        combat().damage(player, target, 3, true);
        applyEffect(target, PotionEffectType.WEAKNESS, 100, 0);
        applyEffect(target, PotionEffectType.BLINDNESS, 60, 0);
        applyEffect(player, PotionEffectType.SPEED, 60, 1);
        for (Location p : new Location[]{a, b}) {
            fx().flash(p, VIOLET, 2.2f);
            fx().rift(p, b.toVector().subtract(a.toVector()), VIOLET, 1.6f, 14);
            fx().castCircle(p.clone().subtract(0, 1, 0), VIOLET, 1.4f);
            Fx.particle(p, Particle.SOUL, 14, 0.4, 0.05);
            Fx.particle(p, Particle.WITCH, 20, 0.4, 0.05);
        }
        // the soul torn out in the swap rises from where the enemy stood, and fades
        Vector up = new Vector(0, 1, 0);
        fx().spectralHead(b.clone().add(0, 0.6, 0), org.bukkit.Material.WITHER_SKELETON_SKULL, VIOLET, 1.1f,
                a.toVector().subtract(b.toVector()).setY(0), 22);
        fx().drift(b.clone().add(0, 0.8, 0), up.multiply(2.2), SOUL, 0.8f, 16);
        Fx.sound(a, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        Fx.sound(b, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.8f);
        Fx.sound(b, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.9f);
        return true;
    }

    @Override
    public void shutdown() {
        hits.clear();
        hexes.clear();
        pacts.clear();
    }
}
