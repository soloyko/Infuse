package dev.blackclover.smp.magic;

import dev.blackclover.smp.grimoire.GrimoireType;
import org.bukkit.entity.Player;

/** Everything a spell needs while it is being cast. */
public final class SpellContext {

    private final Player caster;
    private final GrimoireType type;
    private final int level;
    private final Spell spell;
    private final Fx fx;
    private final StatusService status;
    private final CastingService casting;
    private boolean skipCooldown;

    public SpellContext(Player caster, GrimoireType type, int level, Spell spell, Fx fx, StatusService status,
                        CastingService casting) {
        this.caster = caster;
        this.type = type;
        this.level = level;
        this.spell = spell;
        this.fx = fx;
        this.status = status;
        this.casting = casting;
    }

    public Player caster() { return caster; }
    public GrimoireType type() { return type; }
    public int level() { return level; }
    public Spell spell() { return spell; }
    public Fx fx() { return fx; }
    public StatusService status() { return status; }
    public CastingService casting() { return casting; }

    /** The spell's damage scaled +1% per level above 1. */
    public double dmg() {
        return dmg(spell.damage());
    }

    public double dmg(double base) {
        return base * (1 + 0.01 * (level - 1));
    }

    /** For two-step spells (Recall Gate): don't start the cooldown this cast. */
    public void skipCooldown() {
        skipCooldown = true;
    }

    public boolean cooldownSkipped() {
        return skipCooldown;
    }
}
