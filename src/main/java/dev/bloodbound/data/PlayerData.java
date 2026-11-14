package dev.bloodbound.data;

import dev.bloodbound.weapon.WeaponType;

import java.util.UUID;

/** Persistent progression for one player. Progress lives here, not on the item. */
public final class PlayerData {
    private final UUID id;
    private String name;
    private WeaponType type;
    private int blood;
    private int weaponVersion;

    public PlayerData(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void name(String name) { this.name = name; }
    public WeaponType type() { return type; }
    public void type(WeaponType type) { this.type = type; }
    public int blood() { return blood; }
    public void blood(int blood) { this.blood = Math.max(0, blood); }
    public int weaponVersion() { return weaponVersion; }
    public void weaponVersion(int weaponVersion) { this.weaponVersion = weaponVersion; }
}
