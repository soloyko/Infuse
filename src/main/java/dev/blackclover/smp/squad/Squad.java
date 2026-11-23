package dev.blackclover.smp.squad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A Magic Knight squad: a name, a colour, a captain, members in join order, and the stars it has earned. */
public final class Squad {

    private final UUID id;
    private final String name;
    private String color;
    private UUID captain;
    private final List<UUID> members = new ArrayList<>();
    private int stars;
    private final long createdMillis;

    public Squad(UUID id, String name, String color, UUID captain, long createdMillis) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.captain = captain;
        this.createdMillis = createdMillis;
        members.add(captain);
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String color() { return color; }
    public UUID captain() { return captain; }
    public int stars() { return stars; }
    public long createdMillis() { return createdMillis; }
    public int size() { return members.size(); }

    /** Members in the order they joined; the captain is always among them. */
    public List<UUID> members() {
        return Collections.unmodifiableList(members);
    }

    public boolean isMember(UUID player) {
        return members.contains(player);
    }

    void setColor(String color) { this.color = color; }
    void setCaptain(UUID captain) { this.captain = captain; }
    void setStars(int stars) { this.stars = Math.max(0, stars); }

    void add(UUID player) {
        if (!members.contains(player)) {
            members.add(player);
        }
    }

    void remove(UUID player) {
        members.remove(player);
    }
}
