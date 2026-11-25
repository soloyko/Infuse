package dev.lambro.blade.ability;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.impl.*;
import dev.lambro.blade.util.Fx;
import dev.lambro.blade.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

import static dev.lambro.blade.util.Text.*;

/** Registry of abilities + all event routing, cooldowns, HUD and kill tracking. */
public final class AbilityManager implements Listener {

    private final LambroPlugin plugin;
    private final List<Ability> abilities = new ArrayList<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Set<UUID> dealingAbilityDamage = new HashSet<>();
    private BukkitTask ticker;
    private long tick;

    public AbilityManager(LambroPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerDefaults() {
        abilities.add(new Undertow(plugin));
        abilities.add(new AbyssalDash(plugin));
        abilities.add(new TidalVeil(plugin));
        abilities.add(new Riftbreaker(plugin));
        abilities.add(new DeepwaterHunger(plugin));
        abilities.add(new PhantomEdge(plugin));
        abilities.add(new BlackwaterAura(plugin));
        abilities.add(new EclipseTide(plugin));
        abilities.sort(Comparator.comparingInt(Ability::tier));
    }

    public List<Ability> all() {
        return abilities;
    }

    public void start() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick += 2;
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack held = p.getInventory().getItemInMainHand();
                if (!plugin.blade().isBlade(held) || p.isDead()) continue;
                for (Ability a : active(held)) a.onTick(p, tick);
                if (tick % 10 == 0 && plugin.getConfig().getBoolean("general.action-bar", true)) hud(p, held);
            }
        }, 2L, 2L);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        abilities.forEach(Ability::shutdown);
    }

    /** Unlocked + enabled abilities for a given blade. */
    public List<Ability> active(ItemStack blade) {
        int tiers = plugin.blade().tiers(plugin.blade().kills(blade));
        List<Ability> out = new ArrayList<>(abilities.size());
        for (Ability a : abilities) if (a.tier() <= tiers && a.enabled()) out.add(a);
        return out;
    }

    // ---- cooldowns -------------------------------------------------------

    public double remaining(Player p, Ability a) {
        Long end = cooldowns.getOrDefault(p.getUniqueId(), Map.of()).get(a.id());
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000.0);
    }

    public boolean ready(Player p, Ability a) {
        return remaining(p, a) <= 0;
    }

    public void startCooldown(Player p, Ability a) {
        double cd = a.cooldown();
        if (cd <= 0) return;
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                .put(a.id(), System.currentTimeMillis() + (long) (cd * 1000));
    }

    /** Shaves seconds off every running cooldown (Deepwater Hunger). */
    public void reduceAll(Player p, double seconds) {
        Map<String, Long> map = cooldowns.get(p.getUniqueId());
        if (map == null) return;
        long ms = (long) (seconds * 1000);
        map.replaceAll((k, v) -> v - ms);
    }

    public void protectFromFall(Player p, int ticks) {
        noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
    }

    // ---- damage helpers --------------------------------------------------

    /** True if the entity can be hit by abilities of {@code source}. */
    public boolean canHit(Player source, Entity e) {
        if (!(e instanceof LivingEntity le) || e.equals(source) || le.isDead() || !le.isValid()) return false;
        if (e instanceof ArmorStand || e.isInvulnerable()) return false;
        if (e instanceof Player p) {
            return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        }
        if (e instanceof Tameable t && t.isTamed() && source.getUniqueId().equals(t.getOwnerUniqueId())) return false;
        if (plugin.getConfig().getBoolean("general.hit-passive-mobs", false)) return true;
        return e instanceof Enemy || (e instanceof Mob m && source.equals(m.getTarget()));
    }

    public List<LivingEntity> targets(Player source, org.bukkit.Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (canHit(source, e) && e.getLocation().distanceSquared(center) <= radius * radius * 1.2) out.add((LivingEntity) e);
        }
        return out;
    }

    /** Deals ability damage credited to the player (counts for kills) without re-triggering melee passives. */
    @SuppressWarnings("UnstableApiUsage")
    public void damage(Player source, LivingEntity target, double amount) {
        if (amount <= 0 || !canHit(source, target)) return;
        dealingAbilityDamage.add(source.getUniqueId());
        try {
            target.setNoDamageTicks(0);
            DamageSource ds = DamageSource.builder(DamageType.PLAYER_ATTACK)
                    .withCausingEntity(source).withDirectEntity(source).build();
            target.damage(amount, ds);
        } finally {
            dealingAbilityDamage.remove(source.getUniqueId());
        }
    }

    // ---- activation ------------------------------------------------------

    private void tryActivate(Player p, Trigger trigger) {
        ItemStack held = p.getInventory().getItemInMainHand();
        Ability ability = null;
        for (Ability a : abilities) if (a.trigger() == trigger) ability = a;
        if (ability == null || !ability.enabled()) return;

        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < 200) return; // debounce double packets
        lastUse.put(p.getUniqueId(), now);

        if (!plugin.blade().isUnlocked(held, ability)) {
            int need = ability.tier() * plugin.blade().killsPerUnlock() - plugin.blade().kills(held);
            p.sendActionBar(mm("<" + SHADOW + ">◇ <" + STEEL + ">" + tc("sealed") + " <" + SHADOW + ">· <" + STEEL + ">"
                    + tc(need + (need == 1 ? " more kill" : " more kills"))));
            return;
        }
        if (!ready(p, ability)) {
            p.sendActionBar(mm("<" + BLUE + ">" + tc(ability.name()) + " <" + SHADOW + ">· <" + STEEL + ">"
                    + Text.fmt(Math.ceil(remaining(p, ability) * 10) / 10) + tc("s")));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 0.6f);
            return;
        }
        if (ability.activate(p)) startCooldown(p, ability);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action act = e.getAction();
        if (act != Action.RIGHT_CLICK_AIR && act != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!plugin.blade().isHolding(p)) return;
        if (act == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null
                && e.getClickedBlock().getType().isInteractable()) return;
        tryActivate(p, p.isSneaking() ? Trigger.SNEAK_RIGHT_CLICK : Trigger.RIGHT_CLICK);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!plugin.blade().isBlade(held)) return;
        Trigger t = p.isSneaking() ? Trigger.SNEAK_SWAP : Trigger.SWAP;
        for (Ability a : abilities) {
            if (a.trigger() == t && a.enabled() && plugin.blade().isUnlocked(held, a)) {
                e.setCancelled(true);
                tryActivate(p, t);
                return;
            }
        }
    }

    // ---- passive routing -------------------------------------------------

    private Player meleeAttacker(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return null;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return null;
        if (dealingAbilityDamage.contains(p.getUniqueId())) return null;
        if (!(e.getEntity() instanceof LivingEntity) || !plugin.blade().isHolding(p)) return null;
        return p;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        Player p = meleeAttacker(e);
        if (p == null) return;
        for (Ability a : active(p.getInventory().getItemInMainHand())) a.onMeleeHit(p, (LivingEntity) e.getEntity(), e);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeAfter(EntityDamageByEntityEvent e) {
        Player p = meleeAttacker(e);
        if (p == null) return;
        for (Ability a : active(p.getInventory().getItemInMainHand())) a.onMeleeHitAfter(p, (LivingEntity) e.getEntity(), e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        Long until = noFallUntil.get(p.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !plugin.blade().isHolding(p)) return;
        for (Ability a : active(p.getInventory().getItemInMainHand())) a.onDamaged(p, e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;
        ItemStack held = killer.getInventory().getItemInMainHand();
        if (!plugin.blade().isBlade(held)) return;

        boolean isPlayer = victim instanceof Player;
        boolean counts = isPlayer ? plugin.getConfig().getBoolean("general.count-player-kills", true)
                : plugin.getConfig().getBoolean("general.count-mob-kills", false);

        for (Ability a : active(held)) a.onKill(killer, victim);
        if (!counts) return;

        int before = plugin.blade().tiers(plugin.blade().kills(held));
        int kills = plugin.blade().kills(held) + 1;
        plugin.blade().setKills(held, kills);
        killer.getInventory().setItemInMainHand(held);
        int after = plugin.blade().tiers(kills);
        if (after > before) {
            for (Ability a : abilities) if (a.tier() == after) awaken(killer, a);
        }
    }

    // ---- presentation ----------------------------------------------------

    public void awaken(Player p, Ability a) {
        if (!plugin.getConfig().getBoolean("general.announce-unlocks", true)) return;
        p.showTitle(Title.title(
                mm(GRADIENT + tc(a.isPassive() ? "passive awakened" : "ability awakened") + "</gradient>"),
                mm("<" + WHITE + ">" + tc(a.name()) + " <" + SHADOW + ">· <" + BLUE + ">" + tc(a.trigger().label())),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2600), Duration.ofMillis(700))));
        p.sendMessage(mm(""));
        p.sendMessage(mm("  " + GRADIENT + "<bold>" + tc("blade of lambro") + "</bold></gradient> <" + SHADOW + ">· <" + STEEL + ">"
                + tc("tier " + a.tier())));
        p.sendMessage(mm("  <" + CYAN + ">" + (a.isPassive() ? "◆" : "✦") + " <" + WHITE + ">" + tc(a.name())
                + " <" + SHADOW + ">· <" + BLUE + ">" + tc(a.trigger().label())));
        for (String line : a.description()) p.sendMessage(mm("    <" + STEEL + ">" + tc(line)));
        p.sendMessage(mm(""));

        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.3f);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.7f);
        p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.6f);

        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t++ > 24) { cancel(); return; }
                double y = t * 0.1;
                for (int arm = 0; arm < 3; arm++) {
                    double ang = t * 0.45 + arm * (Math.PI * 2 / 3);
                    var l = p.getLocation().add(Math.cos(ang) * 0.9, y, Math.sin(ang) * 0.9);
                    Fx.fade(l, Fx.ICE, Fx.DEEP, 1.1f, 1, 0);
                    if (arm == 0) Fx.dust(l, Fx.VOID, 1.4f);
                }
                if (t == 24) {
                    var top = p.getLocation().add(0, 2.4, 0);
                    p.getWorld().spawnParticle(Particle.END_ROD, top, 30, 0.1, 0.1, 0.1, 0.12);
                    p.getWorld().spawnParticle(Particle.SQUID_INK, top, 20, 0.2, 0.2, 0.2, 0.08);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void hud(Player p, ItemStack held) {
        int kills = plugin.blade().kills(held);
        List<Ability> act = active(held);
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(CYAN).append(">⚔ <").append(WHITE).append(">").append(kills);
        boolean any = false;
        for (Ability a : act) {
            if (a.cooldown() <= 0) continue;
            any = true;
            double rem = remaining(p, a);
            sb.append("  <").append(SHADOW).append(">│  <").append(rem > 0 ? STEEL : BLUE).append(">").append(tc(shortName(a))).append(" ");
            if (rem > 0) sb.append("<").append(SHADOW).append(">").append((int) Math.ceil(rem)).append(tc("s"));
            else sb.append("<").append(CYAN).append(">●");
        }
        int tiers = plugin.blade().tiers(kills);
        if (tiers < abilities.size()) {
            int left = (tiers + 1) * plugin.blade().killsPerUnlock() - kills;
            sb.append("  <").append(SHADOW).append(">│  <").append(STEEL).append(">").append(tc("next")).append(" <").append(WHITE)
                    .append(">").append(left);
        } else if (!any) {
            sb.append("  <").append(SHADOW).append(">│  ").append(GRADIENT).append(tc("awakened")).append("</gradient>");
        }
        p.sendActionBar(mm(sb.toString()));
    }

    private static String shortName(Ability a) {
        String n = a.name();
        int sp = n.lastIndexOf(' ');
        return sp > 0 ? n.substring(sp + 1) : n;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        noFallUntil.remove(id);
        lastUse.remove(id);
    }

    public static double maxHealth(LivingEntity e) {
        var attr = e.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20 : attr.getValue();
    }
}
