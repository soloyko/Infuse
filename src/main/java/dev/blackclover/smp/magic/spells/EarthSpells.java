package dev.blackclover.smp.magic.spells;

import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.magic.Fx.ProjectileSpec;
import dev.blackclover.smp.magic.Spell;
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
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

final class EarthSpells {

    private static BlockData stoneData;

    /** Created lazily: block data needs a running server. */
    private static BlockData stone() {
        if (stoneData == null) {
            stoneData = Material.STONE.createBlockData();
        }
        return stoneData;
    }

    private EarthSpells() {
    }

    static void register(SpellRegistry r) {
        r.register(GrimoireType.EARTH,
                new Spell("earth_rock_throw", "Rock Throw", 1, 15, 2.0, 6, EarthSpells::rockThrow),
                new Spell("earth_stone_wall", "Stone Wall", 5, 30, 15, 0, EarthSpells::stoneWall),
                new Spell("earth_earthquake", "Earthquake", 10, 40, 14, 7, EarthSpells::earthquake),
                new Spell("earth_titan", "Earth Titan", 20, 80, 70, 0, EarthSpells::titan));
    }

    private static boolean rockThrow(SpellContext ctx) {
        Player p = ctx.caster();
        Fx.sound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.5f);
        ctx.fx().shoot(p, ProjectileSpec.of(Particle.BLOCK, stone(), 1.3, 25, 1.0).gravity(0.03).count(3)
                        .model(PackItems.model(Material.COBBLESTONE, PackModels.ROCK), 0.9f, false),
                t -> ctx.fx().hit(p, t, ctx.dmg()),
                end -> {
                    Fx.particle(end.getWorld(), Particle.BLOCK, end, 20, 0.4, stone());
                    Fx.sound(end, Sound.BLOCK_STONE_BREAK, 1f, 0.8f);
                });
        return true;
    }

    private static boolean stoneWall(SpellContext ctx) {
        Player p = ctx.caster();
        BlockFace facing = SpellUtil.cardinal(p);
        BlockFace side = switch (facing) {
            case NORTH, SOUTH -> BlockFace.EAST;
            default -> BlockFace.SOUTH;
        };
        Location base = p.getLocation().getBlock().getRelative(facing, 3).getLocation();
        int placed = 0;
        for (int i = -2; i <= 2; i++) {
            for (int h = 0; h < 3; h++) {
                Location at = base.clone().add(side.getModX() * i, h, side.getModZ() * i);
                if (ctx.fx().temp().place(at, Material.STONE, 160)) {
                    placed++;
                }
            }
        }
        if (placed == 0) {
            ctx.casting().tell(p, "<red>No room for a wall there.");
            return false;
        }
        Fx.sound(base, Sound.BLOCK_STONE_PLACE, 1f, 0.6f);
        Fx.particle(base.getWorld(), Particle.BLOCK, base.clone().add(0.5, 1, 0.5), 30, 1.2, stone());
        return true;
    }

    private static boolean earthquake(SpellContext ctx) {
        Player p = ctx.caster();
        Location c = p.getLocation();
        BlockData ground = c.clone().subtract(0, 1, 0).getBlock().getBlockData();
        BlockData shown = ground.getMaterial().isAir() ? stone() : ground;
        Fx.sound(c, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 0.6f);
        Fx.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.5f);
        ctx.fx().repeat(1, 6, step -> Fx.ring(c.clone().add(0, 0.2, 0), 1 + step, Particle.BLOCK, shown, 20 + step * 8));
        for (LivingEntity t : ctx.fx().area(p, c, 6)) {
            if (ctx.fx().hit(p, t, ctx.dmg())) {
                ctx.fx().push(p, t, t.getVelocity().setY(0.8));
                ctx.fx().effect(p, t, PotionEffectType.SLOWNESS, 60, 1);
            }
        }
        return true;
    }

    private static boolean titan(SpellContext ctx) {
        Player p = ctx.caster();
        ctx.status().addBuff(p, "earth_titan", 300,
                Fx.pe(PotionEffectType.RESISTANCE, 300, 1), Fx.pe(PotionEffectType.STRENGTH, 300, 1));
        AttributeInstance kb = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        NamespacedKey key = new NamespacedKey(ctx.fx().plugin(), "earth_titan");
        if (kb != null) {
            kb.removeModifier(key);
            kb.addTransientModifier(new AttributeModifier(key, 1.0, AttributeModifier.Operation.ADD_NUMBER));
            ctx.fx().later(300, () -> kb.removeModifier(key));
        }
        Fx.sound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 1f, 0.6f);
        ctx.fx().repeat(10, 30, step -> {
            if (ctx.status().hasBuff(p.getUniqueId(), "earth_titan")) {
                Fx.particle(p.getWorld(), Particle.BLOCK, p.getLocation().add(0, 1, 0), 6, 0.4, stone());
            }
        });
        return true;
    }
}
