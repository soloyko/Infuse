package dev.lambro.blade.ability.impl;

import dev.lambro.blade.LambroPlugin;
import dev.lambro.blade.ability.Ability;
import dev.lambro.blade.ability.SettingDef;
import dev.lambro.blade.ability.Trigger;
import dev.lambro.blade.util.Fx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Tier 6 — hurl a spectral copy of the blade that pierces outward, then returns. */
public final class PhantomEdge extends Ability {

    private final Set<ItemDisplay> live = new HashSet<>();

    public PhantomEdge(LambroPlugin plugin) {
        super(plugin, "phantom_edge", "Phantom Edge", 6, Trigger.SWAP, Material.AMETHYST_SHARD,
                "throw a returning spectral blade",
                List.of("Cast a phantom of the blade forward.",
                        "It spins through every foe in its path,",
                        "then turns and cuts them again on its return."));
        setting(SettingDef.num("cooldown", "Cooldown", Material.CLOCK, 14, 0.5, 0, 300, "s"));
        setting(SettingDef.num("range", "Range", Material.SPYGLASS, 16, 1, 4, 48, " blocks"));
        setting(SettingDef.num("damage", "Damage Per Pass", Material.IRON_SWORD, 6, 0.5, 0, 50, "hp"));
        setting(SettingDef.num("speed", "Flight Speed", Material.FEATHER, 1.1, 0.1, 0.3, 3, " b/t"));
        setting(SettingDef.num("slow-seconds", "Slow Duration", Material.SOUL_SAND, 1.5, 0.5, 0, 10, "s"));
    }

    @Override
    public boolean activate(Player p) {
        ItemStack visual = p.getInventory().getItemInMainHand().clone();
        visual.setAmount(1);
        Location start = p.getEyeLocation().subtract(0, 0.35, 0);
        Vector dir = p.getEyeLocation().getDirection().normalize();

        ItemDisplay blade = p.getWorld().spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(visual);
            d.setPersistent(false);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setGlowing(true);
            d.setGlowColorOverride(org.bukkit.Color.fromRGB(0x2F7BFF));
            d.setRotation(start.getYaw(), 0);
        });
        live.add(blade);

        double range = get("range");
        double speed = get("speed");
        double damage = get("damage");
        int slow = (int) (get("slow-seconds") * 20);

        p.getWorld().playSound(start, Sound.ITEM_TRIDENT_THROW, 1f, 0.7f);
        p.getWorld().playSound(start, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);

        new BukkitRunnable() {
            final Set<UUID> hitOut = new HashSet<>();
            final Set<UUID> hitBack = new HashSet<>();
            Location pos = start.clone();
            double travelled = 0;
            boolean returning = false;
            int life = 0;
            float spin = 0;

            @Override public void run() {
                if (!blade.isValid() || !p.isOnline() || life++ > 140) { finish(); return; }

                Vector step;
                if (!returning) {
                    step = dir.clone().multiply(speed);
                    Location next = pos.clone().add(step);
                    if (travelled >= range || next.getBlock().getType().isSolid()) {
                        returning = true;
                        p.getWorld().playSound(pos, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f);
                        Fx.sphere(pos, 0.6, 20, l -> Fx.dust(l, Fx.CYAN, 0.9f));
                        return;
                    }
                    travelled += speed;
                } else {
                    Location target = p.getEyeLocation().subtract(0, 0.35, 0);
                    Vector to = target.toVector().subtract(pos.toVector());
                    if (to.length() < 1.3) {
                        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.3f);
                        Fx.dust(target, Fx.ICE, 1f, 10, 0.3);
                        finish();
                        return;
                    }
                    step = to.normalize().multiply(speed * 1.25);
                }

                Location from = pos.clone();
                pos.add(step);
                spin += 0.75f;
                float yaw = (float) Math.toDegrees(Math.atan2(-step.getX(), step.getZ()));
                Location tp = pos.clone();
                tp.setYaw(yaw);
                tp.setPitch(0);
                blade.teleport(tp);
                blade.setInterpolationDelay(0);
                blade.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf().rotateY(spin),
                        new Vector3f(1.6f, 1.6f, 1.6f),
                        new Quaternionf().rotateX((float) Math.toRadians(90))));

                Fx.line(from, pos, 0.35, l -> {
                    Fx.dust(l, Fx.VOID, 1.4f);
                    Fx.dust(l, Fx.BLUE, 0.8f, 1, 0.12);
                });
                if (life % 3 == 0) p.getWorld().playSound(pos, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.8f);

                Set<UUID> hits = returning ? hitBack : hitOut;
                for (LivingEntity e : plugin.abilities().targets(p, pos, 1.5)) {
                    if (!hits.add(e.getUniqueId())) continue;
                    plugin.abilities().damage(p, e, damage);
                    if (slow > 0) e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1, false, false, true));
                    Location el = e.getLocation().add(0, e.getHeight() / 2, 0);
                    e.getWorld().spawnParticle(Particle.ENCHANTED_HIT, el, 14, 0.3, 0.3, 0.3, 0.2);
                    Fx.fade(el, Fx.ICE, Fx.VOID, 1.4f, 8, 0.3);
                    e.getWorld().playSound(el, Sound.ITEM_TRIDENT_HIT, 1f, 1.1f);
                }
            }

            private void finish() {
                cancel();
                live.remove(blade);
                blade.remove();
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    @Override
    public void shutdown() {
        live.forEach(ItemDisplay::remove);
        live.clear();
    }
}
