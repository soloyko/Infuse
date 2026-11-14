package dev.bloodbound.ability;

import dev.bloodbound.BloodboundPlugin;
import dev.bloodbound.config.WeaponSettings;
import org.bukkit.entity.Player;

/** Everything a kit needs to resolve one ability use or hit. */
public record Ctx(BloodboundPlugin plugin, Player player, int tier, WeaponSettings cfg) {}
