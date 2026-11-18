package dev.infuse.customs.ability.impl;

import dev.infuse.customs.CustomsPlugin;
import dev.infuse.customs.ability.Combat;
import dev.infuse.customs.ability.Fx;
import dev.infuse.customs.ability.SwordAbility;
import dev.infuse.customs.sword.SwordType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Fallen God's Sword, the Blade of the Covenant (Arc II faction weapon of the Covenant of the Fallen).
 * Passive Longreach, on-hit Pinning Strike, F Excommunicate, Sneak+F War Banner.
 */
public final class FallenWeapon extends SwordAbility implements org.bukkit.event.Listener {

    private static final int RED = 0xD83A3A;
    private static final int DARK = 0x2A0E0E;
    private static final int BANNER_TICKS = 300;
    private static final double BANNER_RADIUS = 8;

    public FallenWeapon(CustomsPlugin plugin) {
        super(plugin, SwordType.FALLEN);
    }

    @Override
    public void addAttributes(ItemMeta meta) {
        addModifier(meta, Attribute.ENTITY_INTERACTION_RANGE, "longreach", 1.5, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    public void onMelee(Player player, LivingEntity target, EntityDamageByEntityEvent event) {
        if (chance(0.2)) {
            hold(target, 20);
            Fx.particle(Combat.center(target), Particle.CRIT, 12, 0.3, 0.2);
            Fx.sound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.8f);
        }
    }

    private static final int EXCOMMUNICATE_TICKS = 100;

    /** Excommunicated -> the tick it ends (they can't heal until then). */
    private final java.util.Map<java.util.UUID, Integer> outcasts = new java.util.HashMap<>();

    /**
     * Excommunicate: a crimson sword falls from above onto the enemy you aim at and casts it out
     * for five seconds. It can't use weapon abilities, it can't heal, and a crown of thorns burns
     * over its head for everyone to see.
     */
    @Override
    public boolean primary(Player player) {
        LivingEntity target = combat().lookTarget(player, 24, 0.7);
        if (target == null) {
            tell(player, "Look at an enemy to cast out.");
            return false;
        }
        Location head = target.getEyeLocation().add(0, 0.6, 0);
        // the judgement: a crimson copy of the blade drops point-first onto the target
        Location sky = head.clone().add(0, 6, 0);
        sky.setYaw(0);
        sky.setPitch(0);
        org.bukkit.entity.ItemDisplay blade = displays().item(sky, plugin.items().create(type), 1.8f,
                new org.joml.Quaternionf().rotateZ((float) Math.PI), org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE, 16);
        blade.setGlowing(true);
        blade.setGlowColorOverride(org.bukkit.Color.fromRGB(RED));
        blade.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        blade.setTeleportDuration(4);
        displays().later(1, () -> {
            if (blade.isValid()) {
                Location down = head.clone().add(0, 0.6, 0);
                down.setYaw(0);
                down.setPitch(0);
                blade.teleport(down);
            }
        });
        Fx.sound(head, Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.8f);
        displays().later(5, () -> {
            displays().remove(blade);
            if (!target.isValid() || target.isDead() || !alive(player)) {
                return;
            }
            Location at = Combat.center(target);
            fx().impact(at, RED, 2.2f);
            fx().crack(Combat.ground(target.getLocation().add(0, 0.5, 0)), RED, 2f, 40);
            fx().castCircle(target.getLocation(), DARK, 1.6f);
            combat().damage(player, target, 3, true);
            Fx.sound(at, Sound.BLOCK_BELL_RESONATE, 1.2f, 0.5f);
            Fx.sound(at, Sound.ENTITY_WITHER_SPAWN, 0.35f, 1.6f);
            if (target instanceof Player victim) {
                status().silence(victim, EXCOMMUNICATE_TICKS);
                victim.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text("✝ EXCOMMUNICATED", net.kyori.adventure.text.format.TextColor.color(RED))
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
                        net.kyori.adventure.text.Component.text("no abilities, no healing", net.kyori.adventure.text.format.NamedTextColor.GRAY),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(500))));
            }
            outcasts.put(target.getUniqueId(), org.bukkit.Bukkit.getCurrentTick() + EXCOMMUNICATE_TICKS);
            crown(target);
        });
        return true;
    }

    /** A ring of crimson thorns over an outcast's head for as long as the sentence lasts. */
    private void crown(LivingEntity target) {
        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                t++;
                Integer until = outcasts.get(target.getUniqueId());
                if (!target.isValid() || target.isDead() || until == null || until < org.bukkit.Bukkit.getCurrentTick()) {
                    outcasts.remove(target.getUniqueId());
                    cancel();
                    return;
                }
                Location top = target.getEyeLocation().add(0, 0.55, 0);
                for (int i = 0; i < 8; i++) {
                    double a = t * 0.15 + i * Math.PI / 4;
                    Location p = top.clone().add(Math.cos(a) * 0.38, (i % 2 == 0 ? 0.08 : -0.02), Math.sin(a) * 0.38);
                    Fx.particle(p, Particle.DUST, 1, 0, 0, Fx.dust(i % 2 == 0 ? RED : DARK, 0.8f));
                }
                if (t % 10 == 0) {
                    Fx.particle(Combat.center(target), Particle.DUST, 4, 0.3, 0, Fx.dust(DARK, 1.0f));
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    /** Outcasts don't heal. */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onHeal(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        Integer until = outcasts.get(event.getEntity().getUniqueId());
        if (until != null && until >= org.bukkit.Bukkit.getCurrentTick()) {
            event.setCancelled(true);
        }
    }

    /** The Covenant's war banner: red, a black border and a gold sword down the middle. */
    private static ItemStack banner() {
        ItemStack item = ItemStack.of(Material.RED_BANNER);
        item.editMeta(org.bukkit.inventory.meta.BannerMeta.class, meta -> {
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.BLACK, org.bukkit.block.banner.PatternType.GRADIENT_UP));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.YELLOW, org.bukkit.block.banner.PatternType.STRIPE_CENTER));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.YELLOW, org.bukkit.block.banner.PatternType.TRIANGLE_TOP));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.RED, org.bukkit.block.banner.PatternType.CURLY_BORDER));
            meta.addPattern(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.BLACK, org.bukkit.block.banner.PatternType.BORDER));
        });
        return item;
    }

    /**
     * War Banner: you plant the Covenant's war banner for fifteen seconds. Your faction near it
     * regenerates and hits harder; enemies near it are weakened.
     */
    @Override
    public boolean secondary(Player player) {
        Vector facing = dev.infuse.customs.ability.Aim.flatLook(player);
        // ahead and a little to the right, so it doesn't stand in the middle of your view
        Location ground = Combat.ground(player.getLocation().add(facing.clone().multiply(1.8)).add(Fx.right(facing).multiply(1.3)).add(0, 0.5, 0));
        Location at = ground.clone().add(0, 2.1, 0);
        at.setYaw(0);
        at.setPitch(0);
        float yaw = (float) Math.atan2(-facing.getX(), facing.getZ()) + (float) Math.PI;
        org.bukkit.entity.ItemDisplay flag = displays().item(at.clone().add(0, 4, 0), banner(), 2.0f,
                new org.joml.Quaternionf().rotateY(yaw), org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED, BANNER_TICKS + 20);
        flag.setTeleportDuration(4);
        displays().later(1, () -> {
            if (flag.isValid()) {
                flag.teleport(at);
            }
        });
        // the standard: a dark pole from the ground and a gold crossbar the flag hangs from
        org.joml.Quaternionf turn = new org.joml.Quaternionf().rotateY(yaw);
        Vector back = facing.clone().multiply(0.12);
        org.bukkit.entity.BlockDisplay[] standard = new org.bukkit.entity.BlockDisplay[2];
        displays().later(5, () -> {
            Location pole = ground.clone().add(back).add(0, 1.7, 0);
            pole.setYaw(0);
            pole.setPitch(0);
            standard[0] = displays().block(pole, Material.DARK_OAK_LOG.createBlockData(), new org.joml.Vector3f(0.14f, 3.4f, 0.14f),
                    turn, BANNER_TICKS + 10);
            Location bar = ground.clone().add(0, 3.22, 0);
            bar.setYaw(0);
            bar.setPitch(0);
            standard[1] = displays().block(bar, Material.GOLD_BLOCK.createBlockData(), new org.joml.Vector3f(1.15f, 0.1f, 0.1f),
                    turn, BANNER_TICKS + 10);
        });
        displays().later(5, () -> {
            fx().shockwave(ground.clone().add(0, 0.05, 0), RED, (float) BANNER_RADIUS, 10);
            fx().crack(ground, RED, 2.4f, 40);
            displays().eruptGround(ground, 0.6f, 0.6f);
            fx().flash(ground.clone().add(0, 1.5, 0), RED, 3f);
            Fx.sound(ground, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
        });
        fx().castCircle(ground, RED, (float) BANNER_RADIUS);
        Fx.sound(at, Sound.BLOCK_WOOL_PLACE, 1.2f, 0.7f);
        Fx.sound(at, Sound.EVENT_RAID_HORN, 1f, 0.9f);

        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                ticks++;
                if (ticks > BANNER_TICKS || ground.getWorld() != player.getWorld()) {
                    if (flag.isValid()) {
                        flag.setTeleportDuration(10);
                        flag.teleport(at.clone().add(0, -3.5, 0));
                    }
                    displays().later(11, () -> displays().remove(flag));
                    for (org.bukkit.entity.BlockDisplay part : standard) {
                        if (part != null) {
                            displays().remove(part);
                        }
                    }
                    Fx.particle(ground.clone().add(0, 1, 0), Particle.CLOUD, 12, 0.4, 0.05);
                    cancel();
                    return;
                }
                if (ticks % 4 == 0) {
                    double a = ticks * 0.05;
                    for (int k = 0; k < 6; k++) {
                        double ang = a + k * Math.PI / 3;
                        Fx.particle(ground.clone().add(Math.cos(ang) * BANNER_RADIUS, 0.2, Math.sin(ang) * BANNER_RADIUS),
                                Particle.DUST, 1, 0, 0, Fx.dust(RED, 1.2f));
                    }
                    // embers rising from the banner
                    Fx.particle(at.clone().add(0, 0.5, 0), Particle.DUST, 2, 0.4, 0, Fx.dust(ticks % 8 == 0 ? 0xF2C94C : RED, 0.9f));
                }
                if (ticks % 20 != 0) {
                    return;
                }
                fx().shockwave(ground.clone().add(0, 0.05, 0), RED, (float) BANNER_RADIUS, 14);
                for (Player p : ground.getNearbyPlayers(BANNER_RADIUS)) {
                    if (p == player || plugin.arcs().allies(player, p)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false, true));
                        Fx.particle(p.getLocation().add(0, 0.2, 0), Particle.DUST, 8, 0.35, 0, Fx.dust(RED, 1.0f));
                    }
                }
                for (LivingEntity enemy : combat().nearby(player, ground, BANNER_RADIUS)) {
                    applyEffect(enemy, PotionEffectType.WEAKNESS, 40, 0);
                    Fx.particle(Combat.center(enemy), Particle.DUST, 3, 0.3, 0, Fx.dust(DARK, 1.0f));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }
}
