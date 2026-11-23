package dev.blackclover.smp.squad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Every squad and the rules for founding, recruiting, leaving and ranking. No Bukkit types. */
public final class SquadRoster {

    public enum Result {
        OK, ALREADY_IN_SQUAD, NOT_IN_SQUAD, NOT_CAPTAIN, BAD_NAME, NAME_TAKEN, FULL, NO_INVITE,
        TARGET_IN_SQUAD, TARGET_NOT_MEMBER, SELF, UNKNOWN_SQUAD, BAD_COLOR
    }

    /** Squad colours, by Adventure {@code NamedTextColor} name. */
    public static final List<String> COLORS = List.of("red", "gold", "yellow", "green", "dark_green", "aqua",
            "dark_aqua", "blue", "light_purple", "dark_purple", "white", "gray");
    public static final int NAME_MIN = 3;
    public static final int NAME_MAX = 16;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9]+( [A-Za-z0-9]+)*");

    private final Map<UUID, Squad> squads = new LinkedHashMap<>();
    private final Map<UUID, UUID> memberOf = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();
    private final int maxMembers;
    private final long inviteMillis;
    private long version;

    public SquadRoster(int maxMembers, long inviteMillis) {
        this.maxMembers = Math.max(1, maxMembers);
        this.inviteMillis = inviteMillis;
    }

    public static boolean validName(String name) {
        return name != null && name.length() >= NAME_MIN && name.length() <= NAME_MAX && NAME.matcher(name).matches();
    }

    public int maxMembers() { return maxMembers; }

    /** Increases on every change, so displays know when to refresh. */
    public long version() { return version; }

    public Collection<Squad> all() {
        return Collections.unmodifiableCollection(squads.values());
    }

    public Optional<Squad> squadOf(UUID player) {
        UUID id = memberOf.get(player);
        return id == null ? Optional.empty() : Optional.ofNullable(squads.get(id));
    }

    public Optional<Squad> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        return squads.values().stream().filter(s -> s.name().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    public Optional<Squad> byId(UUID id) {
        return Optional.ofNullable(squads.get(id));
    }

    /** Two different players in the same squad. */
    public boolean allies(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        UUID sa = memberOf.get(a);
        return sa != null && sa.equals(memberOf.get(b));
    }

    public Result create(UUID captain, String name, long now) {
        if (memberOf.containsKey(captain)) {
            return Result.ALREADY_IN_SQUAD;
        }
        if (!validName(name)) {
            return Result.BAD_NAME;
        }
        if (byName(name).isPresent()) {
            return Result.NAME_TAKEN;
        }
        Squad squad = new Squad(UUID.randomUUID(), name, leastUsedColor(), captain, now);
        squads.put(squad.id(), squad);
        memberOf.put(captain, squad.id());
        invites.remove(captain);
        version++;
        return Result.OK;
    }

    private String leastUsedColor() {
        Map<String, Integer> used = new HashMap<>();
        for (Squad s : squads.values()) {
            used.merge(s.color(), 1, Integer::sum);
        }
        return COLORS.stream().min(Comparator.comparingInt(c -> used.getOrDefault(c, 0))).orElse(COLORS.get(0));
    }

    public Result invite(UUID by, UUID target, long now) {
        Optional<Squad> squad = squadOf(by);
        if (squad.isEmpty()) {
            return Result.NOT_IN_SQUAD;
        }
        if (!squad.get().captain().equals(by)) {
            return Result.NOT_CAPTAIN;
        }
        if (by.equals(target)) {
            return Result.SELF;
        }
        if (memberOf.containsKey(target)) {
            return Result.TARGET_IN_SQUAD;
        }
        if (squad.get().size() >= maxMembers) {
            return Result.FULL;
        }
        invites.computeIfAbsent(target, k -> new HashMap<>()).put(squad.get().id(), now + inviteMillis);
        return Result.OK;
    }

    /** Squads that have invited this player and whose invite hasn't expired. */
    public List<Squad> invitesFor(UUID player, long now) {
        Map<UUID, Long> mine = invites.get(player);
        List<Squad> result = new ArrayList<>();
        if (mine != null) {
            mine.entrySet().removeIf(e -> e.getValue() < now || !squads.containsKey(e.getKey()));
            for (UUID id : mine.keySet()) {
                result.add(squads.get(id));
            }
        }
        return result;
    }

    public Result join(UUID player, String squadName, long now) {
        if (memberOf.containsKey(player)) {
            return Result.ALREADY_IN_SQUAD;
        }
        Optional<Squad> squad = byName(squadName);
        if (squad.isEmpty()) {
            return Result.UNKNOWN_SQUAD;
        }
        if (!invitesFor(player, now).contains(squad.get())) {
            return Result.NO_INVITE;
        }
        if (squad.get().size() >= maxMembers) {
            return Result.FULL;
        }
        squad.get().add(player);
        memberOf.put(player, squad.get().id());
        invites.remove(player);
        version++;
        return Result.OK;
    }

    /** Leaves the squad. A leaving captain hands over to the longest-serving member; the last one out disbands it. */
    public Result leave(UUID player) {
        Optional<Squad> squad = squadOf(player);
        if (squad.isEmpty()) {
            return Result.NOT_IN_SQUAD;
        }
        removeMember(squad.get(), player);
        return Result.OK;
    }

    private void removeMember(Squad squad, UUID player) {
        squad.remove(player);
        memberOf.remove(player);
        if (squad.size() == 0) {
            delete(squad);
        } else if (squad.captain().equals(player)) {
            squad.setCaptain(squad.members().get(0));
        }
        version++;
    }

    public Result kick(UUID by, UUID target) {
        Result check = captainActingOn(by, target);
        if (check != Result.OK) {
            return check;
        }
        removeMember(squadOf(by).orElseThrow(), target);
        return Result.OK;
    }

    public Result transfer(UUID by, UUID target) {
        Result check = captainActingOn(by, target);
        if (check != Result.OK) {
            return check;
        }
        squadOf(by).orElseThrow().setCaptain(target);
        version++;
        return Result.OK;
    }

    private Result captainActingOn(UUID by, UUID target) {
        Optional<Squad> squad = squadOf(by);
        if (squad.isEmpty()) {
            return Result.NOT_IN_SQUAD;
        }
        if (!squad.get().captain().equals(by)) {
            return Result.NOT_CAPTAIN;
        }
        if (by.equals(target)) {
            return Result.SELF;
        }
        if (!squad.get().isMember(target)) {
            return Result.TARGET_NOT_MEMBER;
        }
        return Result.OK;
    }

    public Result setColor(UUID by, String color) {
        Optional<Squad> squad = squadOf(by);
        if (squad.isEmpty()) {
            return Result.NOT_IN_SQUAD;
        }
        if (!squad.get().captain().equals(by)) {
            return Result.NOT_CAPTAIN;
        }
        String c = color == null ? "" : color.toLowerCase(Locale.ROOT);
        if (!COLORS.contains(c)) {
            return Result.BAD_COLOR;
        }
        squad.get().setColor(c);
        version++;
        return Result.OK;
    }

    public Result disband(UUID by) {
        Optional<Squad> squad = squadOf(by);
        if (squad.isEmpty()) {
            return Result.NOT_IN_SQUAD;
        }
        if (!squad.get().captain().equals(by)) {
            return Result.NOT_CAPTAIN;
        }
        delete(squad.get());
        return Result.OK;
    }

    /** Removes a squad outright (admin action, or the last member leaving). */
    public void delete(Squad squad) {
        squads.remove(squad.id());
        for (UUID m : List.copyOf(squad.members())) {
            memberOf.remove(m);
        }
        for (Map<UUID, Long> mine : invites.values()) {
            mine.remove(squad.id());
        }
        version++;
    }

    /** Adds (or with a negative amount removes) stars from a player's squad. Returns the new total, or -1 with no squad. */
    public int addStars(UUID player, int amount) {
        Optional<Squad> squad = squadOf(player);
        if (squad.isEmpty() || amount == 0) {
            return squad.map(Squad::stars).orElse(-1);
        }
        return addStars(squad.get(), amount);
    }

    public int addStars(Squad squad, int amount) {
        squad.setStars(squad.stars() + amount);
        version++;
        return squad.stars();
    }

    /** Most stars first; ties go to the bigger squad, then the older one. */
    public List<Squad> ranking() {
        List<Squad> list = new ArrayList<>(squads.values());
        list.sort(Comparator.comparingInt(Squad::stars).reversed()
                .thenComparing(Comparator.comparingInt(Squad::size).reversed())
                .thenComparingLong(Squad::createdMillis));
        return list;
    }

    /** 1-based position in {@link #ranking()}. */
    public int rank(Squad squad) {
        return ranking().indexOf(squad) + 1;
    }

    /** Puts a saved squad back (used when loading). Members already in another squad are skipped. */
    public void restore(UUID id, String name, String color, UUID captain, List<UUID> members, int stars, long created) {
        if (memberOf.containsKey(captain) || byName(name).isPresent()) {
            return;
        }
        Squad squad = new Squad(id, name, COLORS.contains(color) ? color : COLORS.get(0), captain, created);
        for (UUID m : members) {
            if (!memberOf.containsKey(m)) {
                squad.add(m);
            }
        }
        squad.setStars(stars);
        squads.put(id, squad);
        for (UUID m : squad.members()) {
            memberOf.put(m, id);
        }
        version++;
    }

    public void clear() {
        squads.clear();
        memberOf.clear();
        invites.clear();
        version++;
    }
}
