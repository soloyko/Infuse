package dev.infuse.opswords.api;

import dev.infuse.opswords.sword.AbilitySlot;
import dev.infuse.opswords.sword.SwordType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired right before a player activates a sword ability. Cancel it to block the ability
 * (for example in a protected region). Cancelling does not start the cooldown.
 */
public final class SwordAbilityEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SwordType sword;
    private final AbilitySlot slot;
    private boolean cancelled;

    public SwordAbilityEvent(@NotNull Player player, @NotNull SwordType sword, @NotNull AbilitySlot slot) {
        super(player);
        this.sword = sword;
        this.slot = slot;
    }

    public @NotNull SwordType getSword() {
        return sword;
    }

    public @NotNull AbilitySlot getSlot() {
        return slot;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
