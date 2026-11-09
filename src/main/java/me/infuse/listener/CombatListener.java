package me.infuse.listener;

import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.effect.EffectType;
import me.infuse.util.Glyphs;
import me.infuse.util.Msg;
import me.infuse.util.Status;
import me.infuse.util.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Boss;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Passive abilities and spark effects that trigger from combat and world events. */
public final class CombatListener implements Listener {
    private final InfusePlugin plugin;
    private final Status status;
    private final NamespacedKey curseFireball;

    public CombatListener(InfusePlugin plugin, Status status) {
        this.plugin = plugin;
        this.status = status;
        this.curseFireball = new NamespacedKey(plugin, "curse_fireball");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private PlayerData data(Player p) { return plugin.data().get(p); }

    // ------------------------------------------------------------------ damage dealt

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFxFirework(EntityDamageByEntityEvent e) {
        if (me.infuse.util.Fx.isFxFirework(e.getDamager())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Entity damager = e.getDamager();

        if (damager instanceof Player attacker && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            melee(e, attacker, victim);
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            ranged(e, proj, shooter, victim);
        }
    }

    private void melee(EntityDamageByEntityEvent e, Player attacker, LivingEntity victim) {
        PlayerData d = data(attacker);
        if (d.isEmpty()) return;
        double dmg = e.getDamage();
        long now = System.currentTimeMillis();
        boolean sweep = e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;

        // Strength
        if (d.hasPassive(EffectType.STRENGTH) && !(victim instanceof Player)) dmg *= 2;
        if (d.isSparkActive(EffectType.STRENGTH) && !e.isCritical()) {
            dmg *= 1.5;
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.2);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        }
        if (d.hasPassive(EffectType.STRENGTH) && victim instanceof Player vp && vp.isBlocking()) {
            Bukkit.getScheduler().runTask(plugin, () -> disableShield(vp, cfg().getInt("effects.strength.shield-disable-ticks", 200)));
        }

        // Feather slam
        if (d.featherSlamUntil() > now && !sweep) {
            dmg = Math.max(dmg, cfg().getDouble("effects.feather.slam-damage", 12));
            d.setFeatherSlamUntil(0);
            victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation(), 2);
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f);
        }

        // Ender: instant kill mobs, curse players
        if (d.isSparkActive(EffectType.ENDER)) {
            if (victim instanceof Player vp) {
                curse(vp);
            } else if (cfg().getBoolean("effects.ender.kill-bosses", false) || !isBoss(victim)) {
                dmg = Math.max(dmg, victim.getHealth() + victim.getAbsorptionAmount() + 1000);
            }
        }

        // Apophis: blind screen
        if (d.isSparkActive(EffectType.APOPHIS) && victim instanceof Player vp) {
            int ticks = cfg().getInt("effects.apophis.blind-ticks", 60);
            vp.showTitle(Title.title(Glyphs.apophisCover(), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(ticks * 50L), Duration.ofMillis(500))));
            Util.potion(vp, PotionEffectType.BLINDNESS, 0, ticks, false);
        }

        // Frost spark: freeze + reduce jump on every hit
        if (d.isSparkActive(EffectType.FROST)) {
            freeze(victim, 60);
            Util.modifier(victim, Attribute.JUMP_STRENGTH, plugin.effects().frostJump,
                    -cfg().getDouble("effects.frost.jump-reduction", 0.5), AttributeModifier.Operation.ADD_SCALAR, true);
            status.jumpDebuff(victim, 3000);
        }

        // Regeneration
        if (d.hasPassive(EffectType.REGENERATION)) Util.potion(attacker, PotionEffectType.REGENERATION, 1, 20, false);
        if (d.isSparkActive(EffectType.REGENERATION)) {
            double heal = dmg * cfg().getDouble("effects.regeneration.lifesteal-percent", 30) / 100.0;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!attacker.isDead()) attacker.setHealth(Math.min(Util.maxHealth(attacker), attacker.getHealth() + heal));
            });
        }

        // Speed: stacking speed + halved invincibility frames
        if (d.hasPassive(EffectType.SPEED) && !sweep) {
            d.setSpeedStacks(Math.min(cfg().getInt("effects.speed.max-stacks", 3), d.speedStacks() + 1));
            d.setLastHitAt(now);
            plugin.effects().applyPotions(attacker, d);
            Bukkit.getScheduler().runTask(plugin, () -> {
                int max = victim.getMaximumNoDamageTicks();
                victim.setNoDamageTicks(max / 2 + max / 4);
            });
        }

        // Thief: attacking from the shadows backstabs and ends the vanish
        if (d.isSparkActive(EffectType.THIEF) && !sweep) {
            dmg *= 1 + cfg().getDouble("effects.thief.backstab-bonus-percent", 50) / 100.0;
            long silence = (long) (cfg().getDouble("effects.thief.silence-seconds", 8) * 1000);
            d.setSparkActive(EffectType.THIEF, 0);
            for (int slot = 0; slot < 2; slot++) {
                if (d.get(slot) != null && d.get(slot).type() == EffectType.THIEF) d.setSlotActive(slot, 0);
            }
            String time = Util.time(silence);
            String targetName = victim instanceof Player vp ? vp.getName() : victim.getName();
            if (victim instanceof Player vp && silence > 0) {
                status.silence(vp, silence);
                Msg.send(vp, "thief-backstabbed", Msg.text("target", attacker.getName()), Msg.text("time", time));
            }
            Msg.send(attacker, "thief-backstab", Msg.text("target", targetName), Msg.text("time", time));
            victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1.2, 0), 40, 0.3, 0.5, 0.3, 0,
                    me.infuse.util.Fx.dust(EffectType.THIEF.rgb(), 1.6f));
            victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.6f);
            me.infuse.util.Fx.vanish(attacker, false);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.effects().syncThieves());
        }

        // Every-10-hits passives
        if (!sweep) {
            d.setHits(d.hits() + 1);
            if (d.hits() >= 10) {
                d.setHits(0);
                d.setHitProcAt(now);
                tenthHit(attacker, d, victim);
            }
        }
        e.setDamage(dmg);
    }

    private void tenthHit(Player attacker, PlayerData d, LivingEntity victim) {
        for (int slot = 0; slot < 2; slot++) {
            var eq = d.get(slot);
            if (eq == null || !eq.type().usesHitTracker()) continue;
            Location at = victim.getLocation().add(0, victim.getHeight() * 0.6, 0);
            victim.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 30, 0.35, 0.4, 0.35, 0,
                    me.infuse.util.Fx.fade(eq.type().rgb(), me.infuse.util.Fx.lighten(eq.type().rgb(), 0.7), 1.4f));
            victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 15, 0.3, 0.4, 0.3, 0.2);
            attacker.playSound(attacker.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
            break;
        }
        if (d.hasPassive(EffectType.FROST) && victim instanceof Player) freeze(victim, 100);
        if (d.hasPassive(EffectType.FIRE)) victim.setFireTicks(Math.max(victim.getFireTicks(), 100));
        if (d.hasPassive(EffectType.FEATHER)) {
            victim.getWorld().spawn(victim.getLocation().add(0, 0.3, 0), WindCharge.class, w -> {
                w.setShooter(attacker);
                w.setVelocity(new Vector(0, -1, 0));
            });
        }
        if (d.hasPassive(EffectType.THIEF)) steal(attacker, victim);
        if (d.hasPassive(EffectType.HEART)) {
            String name = victim instanceof Player vp ? vp.getName() : victim.getName();
            double abs = victim.getAbsorptionAmount();
            Msg.send(attacker, "heart-health", Msg.text("target", name),
                    Msg.text("health", String.format("%.1f", victim.getHealth())),
                    Msg.text("max", String.format("%.0f", Util.maxHealth(victim))),
                    Msg.comp("absorption", abs > 0
                            ? Msg.get("heart-absorption", Msg.text("amount", String.format("%.1f", abs)))
                            : Component.empty()));
        }
    }

    /** Thief: take one random (non-passive) potion effect from the target. */
    private void steal(Player thief, LivingEntity victim) {
        List<PotionEffect> stealable = new ArrayList<>();
        for (PotionEffect pe : victim.getActivePotionEffects()) {
            if (!pe.isAmbient()) stealable.add(pe);
        }
        if (stealable.isEmpty()) return;
        PotionEffect pe = stealable.get(ThreadLocalRandom.current().nextInt(stealable.size()));
        victim.removePotionEffect(pe.getType());
        int maxTicks = cfg().getInt("effects.thief.steal-max-seconds", 30) * 20;
        int ticks = pe.isInfinite() ? maxTicks : Math.min(pe.getDuration(), maxTicks);
        thief.addPotionEffect(new PotionEffect(pe.getType(), ticks, pe.getAmplifier(), false, true, true));
        String potion = pe.getType().getKey().getKey().replace('_', ' ');
        Msg.send(thief, "thief-steal", Msg.text("potion", potion),
                Msg.text("target", victim instanceof Player vp ? vp.getName() : victim.getName()));
        if (victim instanceof Player vp) Msg.send(vp, "thief-stolen", Msg.text("potion", potion), Msg.text("target", thief.getName()));
        victim.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, victim.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0,
                me.infuse.util.Fx.fade(EffectType.THIEF.rgb(), 0xFFD700, 1.3f));
        thief.playSound(thief.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.6f);
    }

    private void ranged(EntityDamageByEntityEvent e, Projectile proj, Player shooter, LivingEntity victim) {
        PlayerData d = data(shooter);
        if (proj.getPersistentDataContainer().has(curseFireball, PersistentDataType.BYTE)) {
            if (victim instanceof Player vp && !Util.isAlly(shooter, vp)) curse(vp);
            return;
        }
        if (d.isEmpty()) return;
        // Strength: ranged weapons pierce shields
        if (d.hasPassive(EffectType.STRENGTH) && victim instanceof Player vp && vp.isBlocking()
                && e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) {
            //noinspection deprecation
            e.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
        }
        // Invisibility: fully charged bow shot blinds
        if (d.hasPassive(EffectType.INVISIBILITY) && proj instanceof AbstractArrow arrow && arrow.isCritical()) {
            Util.potion(victim, PotionEffectType.BLINDNESS, 0, cfg().getInt("effects.invisibility.blind-ticks", 100), false);
        }
        if (proj instanceof Trident) {
            if (d.hasPassive(EffectType.OCEAN) && victim instanceof Player vp) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Vector pull = shooter.getLocation().toVector().subtract(vp.getLocation().toVector());
                    if (pull.lengthSquared() < 0.01) return;
                    vp.setVelocity(pull.normalize().multiply(cfg().getDouble("effects.ocean.trident-pull", 1.6)).setY(0.4));
                });
            }
            if (d.hasPassive(EffectType.THUNDER)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!victim.isDead()) plugin.sparks().strike(shooter, victim);
                });
            }
        }
    }

    private void disableShield(Player victim, int ticks) {
        if (data(victim).hasPassive(EffectType.HASTE)) ticks /= 2;
        victim.setCooldown(Material.SHIELD, ticks);
        victim.clearActiveItem();
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent e) {
        int cd = e.getCooldown();
        if (e.getDamager() instanceof Player attacker && data(attacker).hasPassive(EffectType.STRENGTH)) {
            cd = Math.max(cd, cfg().getInt("effects.strength.shield-disable-ticks", 200));
        }
        if (data(e.getPlayer()).hasPassive(EffectType.HASTE)) cd /= 2;
        e.setCooldown(cd);
    }

    private static boolean isBoss(LivingEntity e) {
        return e instanceof Boss || e instanceof EnderDragon || e instanceof Wither || e instanceof ElderGuardian;
    }

    private void freeze(LivingEntity victim, int ticks) {
        victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), victim.getMaxFreezeTicks() + ticks));
        Util.potion(victim, PotionEffectType.SLOWNESS, 2, ticks, false);
        status.freeze(victim, ticks * 50L);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 1f, 0.6f);
    }

    private void curse(Player victim) {
        int ticks = cfg().getInt("effects.ender.curse-seconds", 10) * 20;
        status.curse(victim, ticks * 50L);
        Util.potion(victim, PotionEffectType.WEAKNESS, 0, ticks, false);
        Util.potion(victim, PotionEffectType.SLOWNESS, 0, ticks, false);
        victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 0.8f, 0.6f);
    }

    // ------------------------------------------------------------------ damage taken

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTakeDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlayerData d = data(p);
        switch (e.getCause()) {
            case FALL -> {
                if (status.hasNoFall(p) || d.hasPassive(EffectType.FEATHER)) e.setCancelled(true);
                else if (d.hasPassive(EffectType.FIRE) && (p.isInLava()
                        || p.getLocation().getBlock().getRelative(0, -1, 0).getType() == Material.LAVA)) e.setCancelled(true);
            }
            case LIGHTNING -> {
                if (d.hasPassive(EffectType.THUNDER)) e.setCancelled(true);
            }
            case FREEZE -> {
                if (d.hasPassive(EffectType.FROST)) e.setCancelled(true);
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if (status.isCursed(p) && (r == EntityRegainHealthEvent.RegainReason.SATIATED
                || r == EntityRegainHealthEvent.RegainReason.REGEN)) {
            e.setCancelled(true);
            return;
        }
        PlayerData d = data(p);
        if (d.hasPassive(EffectType.REGENERATION) && r != EntityRegainHealthEvent.RegainReason.SATIATED) {
            long now = System.currentTimeMillis();
            if (now - d.lastSaturationBonus() < 1000) return;
            d.setLastSaturationBonus(now);
            p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + cfg().getInt("effects.regeneration.saturation-bonus", 3)));
        }
    }

    // ------------------------------------------------------------------ projectiles

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof AbstractArrow arrow)) return;
        PlayerData d = data(p);
        boolean bow = e.getBow() != null && e.getBow().getType() == Material.BOW;
        float force = e.getForce();
        if (bow && d.hasPassive(EffectType.SPEED) && force >= 0.66f && force < 1f) {
            // 1.5x faster charge: a 2/3 charged shot counts as a full one
            arrow.setVelocity(arrow.getVelocity().normalize().multiply(3.0));
            arrow.setCritical(true);
            force = 1f;
        }
        if (bow && d.hasPassive(EffectType.FIRE) && force >= 1f) arrow.setFireTicks(2000);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof WindCharge wc) || !(wc.getShooter() instanceof Player p)) return;
        if (!data(p).hasPassive(EffectType.FEATHER)) return;
        wc.setVelocity(wc.getVelocity().multiply(cfg().getDouble("effects.feather.wind-charge-speed", 1.6)));
        Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.WIND_CHARGE,
                Math.min(p.getCooldown(Material.WIND_CHARGE), cfg().getInt("effects.feather.wind-charge-cooldown-ticks", 5))));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity() instanceof SmallFireball fb && fb.getPersistentDataContainer().has(curseFireball, PersistentDataType.BYTE)) {
            fb.getWorld().spawnParticle(Particle.DRAGON_BREATH, fb.getLocation(), 30, 0.3, 0.3, 0.3, 0.02, 1.0f);
            if (e.getHitEntity() == null) {
                e.setCancelled(true);
                fb.remove();
            }
        }
    }

    // ------------------------------------------------------------------ interactions

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null) return;
        if (item.getType() == Material.WIND_CHARGE && status.isFrozen(p)
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            Msg.send(p, "frozen-wind-charge");
            return;
        }
        if (item.getType() == Material.DRAGON_BREATH && data(p).hasPassive(EffectType.ENDER)
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (p.getCooldown(Material.DRAGON_BREATH) > 0) return;
            e.setCancelled(true);
            SmallFireball fb = p.launchProjectile(SmallFireball.class);
            fb.setIsIncendiary(false);
            fb.setYield(0);
            fb.getPersistentDataContainer().set(curseFireball, PersistentDataType.BYTE, (byte) 1);
            me.infuse.util.Fx.trail(fb, EffectType.ENDER.rgb(), 60, Particle.PORTAL);
            p.setCooldown(Material.DRAGON_BREATH, cfg().getInt("effects.ender.fireball-cooldown-ticks", 30));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 1.2f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p) || !(e.getEntity() instanceof Mob)) return;
        PlayerData d = data(p);
        if (d.hasPassive(EffectType.INVISIBILITY)
                || (d.hasPassive(EffectType.THIEF) && (p.isSneaking() || d.isSparkActive(EffectType.THIEF)))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsumeFood(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!data(p).hasPassive(EffectType.HEART) || plugin.items().read(e.getItem()) != null) return;
        Material m = e.getItem().getType();
        double add;
        if (m == Material.ENCHANTED_GOLDEN_APPLE) add = cfg().getDouble("effects.heart.egap-absorption", 20);
        else if (m.isEdible()) add = cfg().getDouble("effects.heart.food-absorption", 4);
        else return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            double max = p.getAttribute(Attribute.MAX_ABSORPTION) == null ? 0 : p.getAttribute(Attribute.MAX_ABSORPTION).getValue();
            p.setAbsorptionAmount(Math.min(max, p.getAbsorptionAmount() + add));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) {
        PlayerData d = data(e.getPlayer());
        if (!d.hasPassive(EffectType.EMERALD) || e.getAmount() <= 0) return;
        double mult = d.isSparkActive(EffectType.EMERALD)
                ? cfg().getDouble("effects.emerald.spark-exp-multiplier", 3.0)
                : cfg().getDouble("effects.emerald.exp-multiplier", 1.5);
        e.setAmount((int) Math.round(e.getAmount() * mult));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchantPrepare(PrepareItemEnchantEvent e) {
        if (!data(e.getEnchanter()).hasPassive(EffectType.EMERALD)) return;
        EnchantmentOffer[] offers = e.getOffers();
        int[] costs = {Math.max(1, cfg().getInt("effects.emerald.enchant-levels.0", 10)),
                cfg().getInt("effects.emerald.enchant-levels.1", 20), cfg().getInt("effects.emerald.enchant-levels.2", 30)};
        for (int i = 0; i < offers.length && i < 3; i++) {
            if (offers[i] != null) offers[i].setCost(Math.max(offers[i].getCost(), costs[i]));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || e.getEntity() instanceof Player || !data(killer).hasPassive(EffectType.EMERALD)) return;
        int looting = cfg().getInt("effects.emerald.looting-level", 5);
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack drop : e.getDrops()) {
            if (drop == null || drop.getMaxStackSize() == 1) continue;
            int bonus = ThreadLocalRandom.current().nextInt(looting + 1);
            if (bonus > 0) {
                ItemStack add = drop.clone();
                add.setAmount(bonus);
                extra.add(add);
            }
        }
        e.getDrops().addAll(extra);
    }

    // ------------------------------------------------------------------ mining

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!Util.isPickaxe(tool) || !data(p).hasPassive(EffectType.HASTE)) return;
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || !e.isDropItems()) return;
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        Block block = e.getBlock();
        if (block.getState() instanceof Container) return;
        int fortune = cfg().getInt("effects.haste.fortune-level", 5);
        if (tool.getEnchantmentLevel(Enchantment.FORTUNE) >= fortune) return;
        ItemStack virtual = tool.clone();
        virtual.addUnsafeEnchantment(Enchantment.FORTUNE, fortune);
        var drops = block.getDrops(virtual, p);
        e.setDropItems(false);
        var loc = block.getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : drops) block.getWorld().dropItemNaturally(loc, drop);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent e) {
        if (!Util.isPickaxe(e.getItem()) || !data(e.getPlayer()).hasPassive(EffectType.HASTE)) return;
        int unbreaking = cfg().getInt("effects.haste.unbreaking-level", 10);
        if (ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) e.setCancelled(true);
    }
}
