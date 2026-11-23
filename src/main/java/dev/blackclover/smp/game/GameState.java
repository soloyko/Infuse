package dev.blackclover.smp.game;

import dev.blackclover.smp.grimoire.GrimoireType;

import java.util.EnumMap;
import java.util.Map;

/** Whether /start has run, the grace timer and where the libraries are. */
public final class GameState {

    private boolean started;
    private String world;
    private int borderSize;
    private long graceEndMillis;
    private java.util.UUID wizardKing;
    private final Map<GrimoireType, Library> libraries = new EnumMap<>(GrimoireType.class);

    public boolean isStarted() { return started; }
    public void setStarted(boolean started) { this.started = started; }
    public String world() { return world; }
    public void setWorld(String world) { this.world = world; }
    public int borderSize() { return borderSize; }
    public void setBorderSize(int borderSize) { this.borderSize = borderSize; }
    public long graceEndMillis() { return graceEndMillis; }
    public void setGraceEndMillis(long graceEndMillis) { this.graceEndMillis = graceEndMillis; }
    public Map<GrimoireType, Library> libraries() { return libraries; }
    public java.util.UUID wizardKing() { return wizardKing; }
    public void setWizardKing(java.util.UUID wizardKing) { this.wizardKing = wizardKing; }

    public boolean isGrace(long now) {
        return started && now < graceEndMillis;
    }

    public void clear() {
        started = false;
        world = null;
        borderSize = 0;
        graceEndMillis = 0;
        wizardKing = null;
        libraries.clear();
    }
}
