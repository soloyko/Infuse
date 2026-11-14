package dev.bloodbound.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** All PersistentDataContainer keys used by the plugin. */
public final class Keys {
    public final NamespacedKey weaponOwner;
    public final NamespacedKey weaponType;
    public final NamespacedKey weaponVersion;
    public final NamespacedKey altar;
    public final NamespacedKey projectileWeapon;
    public final NamespacedKey projectileFlag;

    public Keys(Plugin plugin) {
        weaponOwner = new NamespacedKey(plugin, "weapon_owner");
        weaponType = new NamespacedKey(plugin, "weapon_type");
        weaponVersion = new NamespacedKey(plugin, "weapon_version");
        altar = new NamespacedKey(plugin, "altar");
        projectileWeapon = new NamespacedKey(plugin, "projectile_weapon");
        projectileFlag = new NamespacedKey(plugin, "projectile_flag");
    }
}
