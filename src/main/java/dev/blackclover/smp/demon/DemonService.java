package dev.blackclover.smp.demon;

import dev.blackclover.smp.BlackCloverPlugin;
import dev.blackclover.smp.core.LevelCurve;
import dev.blackclover.smp.grimoire.GrimoireService;
import dev.blackclover.smp.grimoire.GrimoireState;
import dev.blackclover.smp.grimoire.GrimoireStatus;
import dev.blackclover.smp.grimoire.GrimoireType;
import dev.blackclover.smp.mage.MageService;
import dev.blackclover.smp.magic.Fx;
import dev.blackclover.smp.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;
import java.util.UUID;

/** The Demon Possessed: an admin-chosen player who carries the Five-Leaf Grimoire until they die. */
public final class DemonService implements Listener {

    private static final Particle.DustOptions AURA = Fx.dust(0x6B0000, 1.6f);

    private final BlackCloverPlugin plugin;
    private final GrimoireService grimoires;
    private final MageService mage;
    private final NamespacedKey healthKey;
    private long lastBroadcast = System.currentTimeMillis();

    public DemonService(BlackCloverPlugin plugin, GrimoireService grimoires, MageService mage) {
        this.plugin = plugin;
        this.grimoires = grimoires;
        this.mage = mage;
        this.healthKey = new NamespacedKey(plugin, "demon_health");
        grimoires.onReturnedHome(type -> {
            if (type == GrimoireType.FIVE_LEAF) {
                end(false);
            }
        });
    }

    public Optional<UUID> demon() {
        return Optional.ofNullable(plugin.demonRef().get());
    }

    public void startTasks() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        demon().map(plugin.getServer()::getPlayer).ifPresent(this::apply);
    }

    /** @return an error message, or null on success */
    public String possess(Player target) {
        if (demon().isPresent()) {
            return "There is already a Demon Possessed (" + mage.data(demon().get()).name() + "). End that possession first.";
        }
        GrimoireState five = plugin.registry().get(GrimoireType.FIVE_LEAF);
        if (five.status() == GrimoireStatus.HELD) {
            return "The Five-Leaf Grimoire is held by " + mage.data(five.holder()).name() + ". Take it from them first.";
        }
        if (five.status() == GrimoireStatus.GROUND) {
            return "The Five-Leaf Grimoire is lying on the ground. Return it home first (Grimoires menu).";
        }
        Optional<GrimoireType> carried = grimoires.carried(target);
        if (target.getInventory().firstEmpty() < 0 && carried.isEmpty()) {
            return target.getName() + " needs a free inventory slot.";
        }
        carried.ifPresent(type -> grimoires.returnHome(type, "taken by the demon"));
        plugin.registry().giveFromPool(GrimoireType.FIVE_LEAF, target.getUniqueId());
        grimoires.give(target, GrimoireType.FIVE_LEAF);
        LevelCurve curve = plugin.settings().levelCurve();
        mage.setXp(target, GrimoireType.FIVE_LEAF, curve.totalXpForLevel(curve.cap()));
        plugin.demonRef().set(target.getUniqueId());
        apply(target);
        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
        Msg.titleAll("<dark_red><b>A DEMON HAS AWAKENED</b>", "<white><name></white> <gray>has been possessed. The Five-Leaf Grimoire is born.",
                Msg.arg("name", target.getName()));
        Msg.broadcast("<dark_red><b><name></b></dark_red> <red>has been taken over by a demon! Slay them to claim the Five-Leaf Grimoire.",
                Msg.arg("name", target.getName()));
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.7f);
        }
        lastBroadcast = System.currentTimeMillis();
        plugin.saveAsync();
        return null;
    }

    /** Ends the possession. With {@code returnFiveLeafToPool} the grimoire is taken back to the admin pool. */
    public void end(boolean returnFiveLeafToPool) {
        UUID id = plugin.demonRef().getAndSet(null);
        if (id == null) {
            return;
        }
        Player p = plugin.getServer().getPlayer(id);
        if (p != null) {
            unapply(p);
        }
        GrimoireState five = plugin.registry().get(GrimoireType.FIVE_LEAF);
        if (returnFiveLeafToPool && five.status() == GrimoireStatus.HELD && id.equals(five.holder())) {
            grimoires.returnHome(GrimoireType.FIVE_LEAF, "possession ended");
        }
        Msg.broadcast("<gray>The demon possession of <white><name></white> has ended.", Msg.arg("name", mage.data(id).name()));
        plugin.saveAsync();
    }

    /** Called after the demon's death has dropped the Five-Leaf. */
    public void onDeath(Player victim) {
        end(false);
        GrimoireState five = plugin.registry().get(GrimoireType.FIVE_LEAF);
        if (five.status() == GrimoireStatus.GROUND && five.ground() != null) {
            Msg.titleAll("<dark_red><b>The demon has fallen</b>", "<gray>The Five-Leaf Grimoire lies at <white><x> <y> <z>",
                    Msg.arg("x", (int) five.ground().x()), Msg.arg("y", (int) five.ground().y()), Msg.arg("z", (int) five.ground().z()));
            Msg.broadcast("<red><name> has fallen! The Five-Leaf Grimoire dropped at <white><x> <y> <z></white>.",
                    Msg.arg("name", victim.getName()), Msg.arg("x", (int) five.ground().x()),
                    Msg.arg("y", (int) five.ground().y()), Msg.arg("z", (int) five.ground().z()));
        }
    }

    private void apply(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.removeModifier(healthKey);
            double bonus = plugin.settings().demonHealth() - max.getBaseValue();
            if (bonus > 0) {
                max.addTransientModifier(new AttributeModifier(healthKey, bonus, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        Component name = Component.text("☠ " + p.getName(), NamedTextColor.DARK_RED);
        p.displayName(name);
        p.playerListName(name);
    }

    private void unapply(Player p) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            max.removeModifier(healthKey);
            if (p.getHealth() > max.getValue()) {
                p.setHealth(max.getValue());
            }
        }
        p.displayName(null);
        p.playerListName(null);
    }

    private void tick() {
        Optional<UUID> id = demon();
        if (id.isEmpty()) {
            return;
        }
        Player p = plugin.getServer().getPlayer(id.get());
        if (p == null) {
            return;
        }
        GrimoireState five = plugin.registry().get(GrimoireType.FIVE_LEAF);
        if (five.status() != GrimoireStatus.HELD || !id.get().equals(five.holder())) {
            end(false);
            return;
        }
        Location at = p.getLocation().add(0, 1, 0);
        Fx.particle(p.getWorld(), Particle.DUST, at, 8, 0.5, AURA);
        Fx.particle(p.getWorld(), Particle.SOUL, at, 1, 0.4, null);
        long now = System.currentTimeMillis();
        if (now - lastBroadcast >= plugin.settings().demonBroadcastMillis()) {
            lastBroadcast = now;
            Msg.broadcast("<dark_red>The Demon Possessed <white><name></white> was sighted near <white><x>, <z></white> <gray>(<world>)",
                    Msg.arg("name", p.getName()), Msg.arg("x", p.getLocation().getBlockX()),
                    Msg.arg("z", p.getLocation().getBlockZ()), Msg.arg("world", p.getWorld().getName()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().getUniqueId().equals(plugin.demonRef().get())) {
            apply(event.getPlayer());
        }
    }
}
