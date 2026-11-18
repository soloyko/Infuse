package me.infuse.listener;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import me.infuse.InfusePlugin;
import me.infuse.data.PlayerData;
import me.infuse.effect.EffectType;
import me.infuse.util.Fx;
import me.infuse.util.Msg;
import me.infuse.util.Status;
import me.infuse.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/** Weaving, Oozing and Resistance passives and their combat hooks. */
public final class NewEffectsListener implements Listener {
    private final InfusePlugin plugin;
    private final Status status;
    private final NamespacedKey slimeOwner;

    public NewEffectsListener(InfusePlugin plugin, Status status) {
        this.plugin = plugin;
        this.status = status;
        this.slimeOwner = new NamespacedKey(plugin, "slime_owner");
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private PlayerData data(Player p) { return plugin.data().get(p); }

    // ------------------------------------------------------------------ hits dealt (10th hit) and taken

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Entity damager = e.getDamager();
        Player attacker = damager instanceof Player p ? p
                : damager instanceof Projectile proj && proj.getShooter() instanceof Player s ? s : null;

        // Attacker side: 10th-hit procs (the hit counter is advanced by CombatListener first)
        if (attacker != null && damager instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            PlayerData d = data(attacker);
            if (d.hits() == 0 && System.currentTimeMillis() - d.hitProcAt() < 50) {
                if (d.hasPassive(EffectType.WEAVING)) {
                    long ms = (long) (cfg().getDouble("effects.weaving.trap-seconds", 3) * 1000);
                    plugin.tempBlocks().place(victim.getLocation().getBlock(), Material.COBWEB, ms);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1.2f);
                }
                if (d.hasPassive(EffectType.OOZING)) slime(victim, (long) (cfg().getDouble("effects.oozing.slime-seconds", 5) * 1000));
            }
        }

        // Victim side
        if (!(victim instanceof Player vp)) return;
        PlayerData vd = data(vp);
        if (vd.isEmpty()) return;

        // Resistance: less damage from maces
        if (vd.hasPassive(EffectType.RESISTANCE) && damager instanceof LivingEntity le && le.getEquipment() != null
                && le.getEquipment().getItemInMainHand().getType() == Material.MACE) {
            e.setDamage(e.getDamage() * (1 - cfg().getDouble("effects.resistance.mace-reduction", 0.3)));
        }

        // Resistance: every 10 hits taken -> absorption
        if (vd.hasPassive(EffectType.RESISTANCE) && attacker != null && !attacker.equals(vp)) {
            vd.setTakenHits(vd.takenHits() + 1);
            if (vd.takenHits() >= 10) {
                vd.setTakenHits(0);
                vd.setHitProcAt(System.currentTimeMillis());
                double add = cfg().getDouble("effects.resistance.absorption-per-proc", 8);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    var attr = vp.getAttribute(org.bukkit.attribute.Attribute.MAX_ABSORPTION);
                    double max = attr == null ? 0 : attr.getValue();
                    vp.setAbsorptionAmount(Math.min(max, vp.getAbsorptionAmount() + add));
                });
                vp.getWorld().spawnParticle(Particle.DUST, vp.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0,
                        Fx.dust(EffectType.RESISTANCE.rgb(), 1.3f));
                vp.playSound(vp.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 1.2f);
            }
        }

        // Bulwark: reflect melee damage
        if (vd.isSparkActive(EffectType.RESISTANCE) && damager instanceof LivingEntity melee
                && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            double reflect = e.getDamage() * cfg().getDouble("effects.resistance.reflect-percent", 30) / 100.0;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (melee.isValid() && !melee.isDead()) {
                    melee.damage(reflect, DamageSource.builder(DamageType.THORNS).withCausingEntity(vp).withDirectEntity(vp).build());
                    melee.getWorld().spawnParticle(Particle.ENCHANTED_HIT, melee.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.1);
                }
            });
            vp.getWorld().playSound(vp.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.3f);
        }

        // Oozing: slimes burst out when you drop low
        if (vd.hasPassive(EffectType.OOZING) && attacker != null && !attacker.equals(vp)) {
            double threshold = cfg().getDouble("effects.oozing.slime-threshold-hearts", 4) * 2;
            long cd = (long) (cfg().getDouble("effects.oozing.slime-cooldown-seconds", 60) * 1000);
            long now = System.currentTimeMillis();
            if (vp.getHealth() - e.getFinalDamage() < threshold && vp.getHealth() - e.getFinalDamage() > 0
                    && now - vd.oozingSlimesAt() >= cd) {
                vd.setOozingSlimesAt(now);
                Bukkit.getScheduler().runTask(plugin, () -> spawnSlimes(vp, attacker));
            }
        }
    }

    private void slime(LivingEntity victim, long millis) {
        status.slime(victim, millis);
        Util.potion(victim, PotionEffectType.SLOWNESS, 0, (int) (millis / 50), false);
        if (victim instanceof Player p) p.setSprinting(false);
        victim.getWorld().spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 1f);
    }

    private void spawnSlimes(Player owner, Player target) {
        int count = cfg().getInt("effects.oozing.slime-count", 2);
        int lifetime = cfg().getInt("effects.oozing.slime-lifetime-seconds", 20) * 20;
        for (int i = 0; i < count; i++) {
            Location at = owner.getLocation().add((i - (count - 1) / 2.0) * 1.2, 0.5, 0);
            Slime slime = owner.getWorld().spawn(at, Slime.class, s -> {
                s.setSize(cfg().getInt("effects.oozing.slime-size", 2));
                s.getPersistentDataContainer().set(slimeOwner, PersistentDataType.STRING, owner.getUniqueId().toString());
                s.setPersistent(false);
                s.setRemoveWhenFarAway(true);
                s.customName(Msg.parse("<#4fd27a>" + Msg.esc(owner.getName()) + "'s Slime"));
                s.setTarget(target);
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (slime.isValid()) {
                    slime.getWorld().spawnParticle(Particle.ITEM_SLIME, slime.getLocation(), 20, 0.3, 0.3, 0.3, 0.05);
                    slime.remove();
                }
            }, lifetime);
        }
        owner.getWorld().spawnParticle(Particle.ITEM_SLIME, owner.getLocation().add(0, 1, 0), 60, 0.6, 0.6, 0.6, 0.1);
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_SLIME_JUMP, 1f, 0.6f);
    }

    private UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(slimeOwner, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlimeTarget(EntityTargetLivingEntityEvent e) {
        UUID owner = ownerOf(e.getEntity());
        if (owner == null || e.getTarget() == null) return;
        Player o = Bukkit.getPlayer(owner);
        if (e.getTarget().getUniqueId().equals(owner) || (o != null && Util.isAlly(o, e.getTarget()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlimeHurtOwner(EntityDamageByEntityEvent e) {
        UUID owner = ownerOf(e.getDamager());
        if (owner != null && e.getEntity().getUniqueId().equals(owner)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlimeSplit(SlimeSplitEvent e) {
        if (ownerOf(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onSlimeDeath(EntityDeathEvent e) {
        if (ownerOf(e.getEntity()) != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    // ------------------------------------------------------------------ damage taken (non-entity)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlayerData d = data(p);
        if (d.isEmpty()) return;
        switch (e.getCause()) {
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> {
                if (d.hasPassive(EffectType.RESISTANCE)) {
                    e.setDamage(e.getDamage() * (1 - cfg().getDouble("effects.resistance.explosion-reduction", 0.3)));
                }
            }
            case FALL -> {
                if (!d.hasPassive(EffectType.OOZING)) return;
                float fall = p.getFallDistance();
                e.setCancelled(true);
                if (p.isSneaking() || fall < 3) return;
                double bounce = Math.min(cfg().getDouble("effects.oozing.max-bounce", 1.3), Math.sqrt(fall) * 0.28);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Vector v = p.getVelocity();
                    p.setVelocity(new Vector(v.getX(), bounce, v.getZ()));
                });
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 1f);
                p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation(), 15, 0.3, 0.05, 0.3, 0.05);
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlayerData d = data(p);
        if (d.isSparkActive(EffectType.OOZING) || d.isSparkActive(EffectType.RESISTANCE)) {
            e.setCancelled(true);
            return;
        }
        if (d.hasPassive(EffectType.OOZING)) {
            e.setKnockback(e.getKnockback().multiply(1 - cfg().getDouble("effects.oozing.knockback-reduction", 0.15)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShieldDisable(PlayerShieldDisableEvent e) {
        if (data(e.getPlayer()).hasPassive(EffectType.RESISTANCE)) e.setCooldown(e.getCooldown() / 2);
    }

    // ------------------------------------------------------------------ Weaving

    @EventHandler(ignoreCancelled = true)
    public void onWebDamage(BlockDamageEvent e) {
        if (e.getBlock().getType() == Material.COBWEB && data(e.getPlayer()).hasPassive(EffectType.WEAVING)) e.setInstaBreak(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWebBreak(BlockBreakEvent e) {
        if (plugin.tempBlocks().isTemp(e.getBlock())) {
            e.setDropItems(false);
            plugin.tempBlocks().forget(e.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArrowLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow arrow) || !arrow.isCritical()) return;
        if (!(arrow.getShooter() instanceof Player p) || !data(p).hasPassive(EffectType.WEAVING)) return;
        Block target;
        if (e.getHitEntity() != null) target = e.getHitEntity().getLocation().getBlock();
        else if (e.getHitBlock() != null && e.getHitBlockFace() != null) target = e.getHitBlock().getRelative(e.getHitBlockFace());
        else return;
        long ms = (long) (cfg().getDouble("effects.weaving.arrow-web-seconds", 3) * 1000);
        if (plugin.tempBlocks().place(target, Material.COBWEB, ms)) {
            target.getWorld().spawnParticle(Particle.WHITE_ASH, target.getLocation().add(0.5, 0.5, 0.5), 20, 0.4, 0.4, 0.4, 0);
        }
    }

    /** Rooted players can't escape with wind charges or ender pearls; slimed players can't sprint. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEscape(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        Material m = item.getType();
        if ((m == Material.WIND_CHARGE || m == Material.ENDER_PEARL) && status.isRooted(e.getPlayer())) {
            e.setCancelled(true);
            Msg.send(e.getPlayer(), "weaving-no-escape");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) {
        if (e.isSprinting() && status.isSlimed(e.getPlayer())) e.setCancelled(true);
    }
}
