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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class EarthSpells {

    private static BlockData stoneData;
    private static BlockData spikeData;

    private EarthSpells() {
    }

    /** Created lazily: block data needs a running server. */
    private static BlockData stone() {
        if (stoneData == null) {
            stoneData = Material.STONE.createBlockData();
        }
        return stoneData;
    }

    private static BlockData spike() {
        if (spikeData == null) {
            spikeData = Material.POINTED_DRIPSTONE.createBlockData();
        }
        return spikeData;
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.EARTH,
                new Spell("earth_stone_bullet", "Stone Bullet", 1, Role.ATTACK, 13, 1.5, 6,
                        "Hurl a heavy boulder that staggers whoever it hits.", EarthSpells::stoneBullet),
                new Spell("earth_rampart", "Rampart", 5, Role.DEFENSE, 28, 14, 0,
                        "A stone wall bursts up in front of you and your skin turns to stone.", EarthSpells::rampart),
                new Spell("earth_rising_spikes", "Rising Spikes", 10, Role.CONTROL, 36, 11, 6,
                        "Stone spikes erupt in a line, launching and slowing everyone they hit.", EarthSpells::risingSpikes),
                new Spell("earth_titan_quake", "Titan Quake", 20, Role.ULTIMATE, 80, 55, 12,
                        "Shatter the ground around you, then stand like a mountain for 8s.", EarthSpells::titanQuake));
    }

    private static boolean stoneBullet(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sounds(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.4f, Sound.BLOCK_STONE_BREAK, 0.7f, 0.6f);
        ctx.fx().shootFromBook(p, ProjectileSpec.of(Particle.BLOCK, stone(), 1.5, 28, 1.0).gravity(0.025).count(3)
                        .model(PackItems.model(Material.COBBLESTONE, PackModels.ROCK), 0.9f, false),
                t -> {
                    if (ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 25, 1);
                        ctx.fx().knockback(p, t, p.getLocation(), 0.6, 0.2);
                    }
                },
                end -> {
                    Fx.particle(end.getWorld(), Particle.BLOCK, end, 30, 0.4, stone());
                    Fx.sound(end, Sound.BLOCK_STONE_BREAK, 1f, 0.7f);
                });
        return true;
    }

    private static boolean rampart(SpellContext ctx) {
        Player p = ctx.caster();
        BlockFace facing = SpellUtil.cardinal(p);
        BlockFace side = switch (facing) {
            case NORTH, SOUTH -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
        Location base = p.getLocation().getBlock().getRelative(facing, 3).getLocation();
        int[] placed = {0};
        // rises row by row
        for (int h = 0; h < 3; h++) {
            int row = h;
            ctx.fx().later(1 + h * 2, () -> {
                for (int i = -3; i <= 3; i++) {
                    Location at = base.clone().add(side.getModX() * i, row, side.getModZ() * i);
                    Material m = (i + row) % 3 == 0 ? Material.COBBLED_DEEPSLATE : Material.STONE_BRICKS;
                    if (ctx.fx().temp().place(at, m, 140 - row * 2)) {
                        placed[0]++;
                        Fx.particle(at.getWorld(), Particle.BLOCK, at.clone().add(0.5, 0.5, 0.5), 6, 0.3, stone());
                    }
                }
                Fx.sound(base, Sound.BLOCK_STONE_PLACE, 1.2f, 0.5f + row * 0.15f);
            });
        }
        ctx.status().addBuff(p, "rampart", 80, Fx.pe(PotionEffectType.RESISTANCE, 80, 1));
        Fx.particle(p.getWorld(), Particle.BLOCK, p.getLocation().add(0, 1, 0), 30, 0.4, stone());
        return true;
    }

    private static boolean risingSpikes(SpellContext ctx) {
        Player p = ctx.caster();
        Vector dir = SpellUtil.horizontalLook(p);
        Location start = p.getLocation();
        Fx.sounds(start, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 0.8f, Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1f, 0.6f);
        Set<UUID> hit = new HashSet<>();
        for (int i = 1; i <= 12; i++) {
            int step = i;
            ctx.fx().later(step, () -> {
                Location at = SpellUtil.groundAt(start.clone().add(dir.clone().multiply(step * 1.1)).add(0, 1.5, 0));
                BlockDisplay d = ctx.fx().blockDisplay(at, spike(), 1.4f, 0.01f, 1.4f, 24);
                ctx.fx().animate(d, new Transformation(new Vector3f(-0.7f, -0.2f, -0.7f), new Quaternionf(),
                        new Vector3f(1.4f, 2.6f, 1.4f), new Quaternionf()), 3, 1);
                ctx.fx().animate(d, new Transformation(new Vector3f(-0.7f, -0.2f, -0.7f), new Quaternionf(),
                        new Vector3f(1.4f, 0.01f, 1.4f), new Quaternionf()), 6, 16);
                Fx.particle(at.getWorld(), Particle.BLOCK, at.clone().add(0, 0.3, 0), 12, 0.4, stone());
                if (step % 3 == 0) {
                    Fx.sound(at, Sound.BLOCK_POINTED_DRIPSTONE_FALL, 1f, 0.6f);
                }
                for (LivingEntity t : ctx.fx().area(p, at, 1.8)) {
                    if (hit.add(t.getUniqueId()) && ctx.fx().hit(p, t, ctx.dmg())) {
                        ctx.fx().push(p, t, new Vector(0, 0.95, 0));
                        ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 2);
                    }
                }
            });
        }
        return true;
    }

    private static boolean titanQuake(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        ctx.fx().announce(p, ctx.spell(), GrimoireType.EARTH.rgb());
        Fx.sounds(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
        BlockData ground = c.clone().subtract(0, 1, 0).getBlock().getBlockData();
        BlockData shown = ground.getMaterial().isAir() ? stone() : ground;
        ctx.fx().shockwave(c.clone().add(0, 0.2, 0), 11, 10, Particle.BLOCK, shown);
        ctx.fx().shockwave(c.clone().add(0, 0.5, 0), 10, 10, Particle.BLOCK, stone());
        // a ring of spikes bursts out
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI * 2 / 16;
            for (int ring = 1; ring <= 2; ring++) {
                Location at = SpellUtil.groundAt(c.clone().add(Math.cos(a) * ring * 3.5, 1.5, Math.sin(a) * ring * 3.5));
                int delay = ring * 3;
                ctx.fx().later(delay, () -> {
                    BlockDisplay d = ctx.fx().blockDisplay(at, spike(), 1.2f, 0.01f, 1.2f, 30);
                    ctx.fx().animate(d, new Transformation(new Vector3f(-0.6f, 0, -0.6f), new Quaternionf(),
                            new Vector3f(1.2f, 2.2f, 1.2f), new Quaternionf()), 3, 1);
                    ctx.fx().animate(d, new Transformation(new Vector3f(-0.6f, 0, -0.6f), new Quaternionf(),
                            new Vector3f(1.2f, 0.01f, 1.2f), new Quaternionf()), 6, 22);
                });
            }
        }
        for (LivingEntity t : ctx.fx().area(p, c, 11)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().push(p, t, t.getVelocity().setY(1.0));
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 2);
            }
        }
        ctx.status().addBuff(p, "earth_titan", 160, Fx.pe(PotionEffectType.RESISTANCE, 160, 2), Fx.pe(PotionEffectType.STRENGTH, 160, 0));
        AttributeInstance kb = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        NamespacedKey key = new NamespacedKey(ctx.fx().plugin(), "earth_titan");
        if (kb != null) {
            kb.removeModifier(key);
            kb.addTransientModifier(new AttributeModifier(key, 1.0, AttributeModifier.Operation.ADD_NUMBER));
            ctx.fx().later(160, () -> kb.removeModifier(key));
        }
        ctx.fx().repeat(10, 16, i -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "earth_titan")) {
                Fx.particle(p.getWorld(), Particle.BLOCK, p.getLocation().add(0, 1, 0), 6, 0.4, stone());
            }
        });
        return true;
    }
}
