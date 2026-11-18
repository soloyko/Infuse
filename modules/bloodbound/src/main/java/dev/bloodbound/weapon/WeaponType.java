package dev.bloodbound.weapon;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;

/** The five Bloodbound weapons. Display text lives in messages.yml under weapon.types.&lt;key&gt;. */
public enum WeaponType {
    CRIMSON_BLADE(Material.NETHERITE_SWORD, false),
    RAVAGER_AXE(Material.NETHERITE_AXE, false),
    TEMPEST_TRIDENT(Material.TRIDENT, true),
    EARTHSHAKER_MACE(Material.MACE, false),
    PHANTOM_BOW(Material.BOW, true);

    private final Material material;
    private final boolean ranged;

    WeaponType(Material material, boolean ranged) {
        this.material = material;
        this.ranged = ranged;
    }

    public Material material() {
        return material;
    }

    public boolean ranged() {
        return ranged;
    }

    /** Key used in messages.yml (e.g. crimson_blade). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Key used in config.yml (e.g. crimson-blade). */
    public String configKey() {
        return key().replace('_', '-');
    }

    public static Optional<WeaponType> fromKey(String key) {
        if (key == null) return Optional.empty();
        String normalized = key.toUpperCase(Locale.ROOT).replace('-', '_');
        for (WeaponType type : values()) {
            if (type.name().equals(normalized)) return Optional.of(type);
        }
        return Optional.empty();
    }
}
