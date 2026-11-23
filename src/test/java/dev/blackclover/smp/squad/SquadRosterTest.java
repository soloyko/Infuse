package dev.blackclover.smp.squad;

import dev.blackclover.smp.squad.SquadRoster.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadRosterTest {

    private final UUID cap = new UUID(1, 1), bob = new UUID(1, 2), cat = new UUID(1, 3), dan = new UUID(1, 4);

    private SquadRoster roster() {
        return new SquadRoster(3, 60_000);
    }

    @Test
    void namesMustBeTidy() {
        assertTrue(SquadRoster.validName("Black Bulls"));
        assertTrue(SquadRoster.validName("Team7"));
        assertFalse(SquadRoster.validName("ab"));
        assertFalse(SquadRoster.validName("a name that is far too long"));
        assertFalse(SquadRoster.validName(" Bulls"));
        assertFalse(SquadRoster.validName("Black  Bulls"));
        assertFalse(SquadRoster.validName("<red>Bulls"));
    }

    @Test
    void createRejectsDuplicatesCaseInsensitively() {
        SquadRoster r = roster();
        assertEquals(Result.OK, r.create(cap, "Black Bulls", 0));
        assertEquals(Result.ALREADY_IN_SQUAD, r.create(cap, "Other", 0));
        assertEquals(Result.NAME_TAKEN, r.create(bob, "black bulls", 0));
        assertEquals(cap, r.squadOf(cap).orElseThrow().captain());
    }

    @Test
    void newSquadsGetDifferentColours() {
        SquadRoster r = roster();
        r.create(cap, "One", 0);
        r.create(bob, "Two", 1);
        assertNotEquals(r.squadOf(cap).orElseThrow().color(), r.squadOf(bob).orElseThrow().color());
    }

    @Test
    void joiningNeedsAnInviteThatHasNotExpired() {
        SquadRoster r = roster();
        r.create(cap, "Bulls", 0);
        assertEquals(Result.NO_INVITE, r.join(bob, "Bulls", 0));
        assertEquals(Result.NOT_IN_SQUAD, r.invite(bob, cat, 0));
        assertEquals(Result.OK, r.invite(cap, bob, 0));
        assertEquals(Result.OK, r.invite(cap, cat, 0));
        assertEquals(Result.OK, r.join(bob, "bulls", 59_000));
        assertEquals(Result.NOT_CAPTAIN, r.invite(bob, dan, 59_000));
        assertEquals(Result.NO_INVITE, r.join(cat, "Bulls", 61_000));
        assertTrue(r.allies(cap, bob));
        assertFalse(r.allies(cap, cat));
        assertFalse(r.allies(cap, cap));
    }

    @Test
    void squadsHaveAMemberLimit() {
        SquadRoster r = roster();
        r.create(cap, "Bulls", 0);
        r.invite(cap, bob, 0);
        r.invite(cap, cat, 0);
        r.invite(cap, dan, 0);
        r.join(bob, "Bulls", 0);
        r.join(cat, "Bulls", 0);
        assertEquals(Result.FULL, r.join(dan, "Bulls", 0));
        assertEquals(Result.FULL, r.invite(cap, dan, 0));
    }

    @Test
    void captainLeavingHandsOverAndLastOneOutDisbands() {
        SquadRoster r = roster();
        r.create(cap, "Bulls", 0);
        r.invite(cap, bob, 0);
        r.join(bob, "Bulls", 0);
        assertEquals(Result.OK, r.leave(cap));
        Squad s = r.squadOf(bob).orElseThrow();
        assertEquals(bob, s.captain());
        assertEquals(List.of(bob), s.members());
        assertEquals(Result.OK, r.leave(bob));
        assertTrue(r.all().isEmpty());
        assertEquals(Result.NOT_IN_SQUAD, r.leave(bob));
    }

    @Test
    void onlyTheCaptainKicksTransfersRecoloursAndDisbands() {
        SquadRoster r = roster();
        r.create(cap, "Bulls", 0);
        r.invite(cap, bob, 0);
        r.join(bob, "Bulls", 0);
        assertEquals(Result.NOT_CAPTAIN, r.kick(bob, cap));
        assertEquals(Result.NOT_CAPTAIN, r.setColor(bob, "red"));
        assertEquals(Result.NOT_CAPTAIN, r.disband(bob));
        assertEquals(Result.SELF, r.kick(cap, cap));
        assertEquals(Result.TARGET_NOT_MEMBER, r.kick(cap, cat));
        assertEquals(Result.BAD_COLOR, r.setColor(cap, "pink"));
        assertEquals(Result.OK, r.setColor(cap, "GOLD"));
        assertEquals("gold", r.squadOf(cap).orElseThrow().color());
        assertEquals(Result.OK, r.transfer(cap, bob));
        assertEquals(bob, r.squadOf(cap).orElseThrow().captain());
        assertEquals(Result.OK, r.kick(bob, cap));
        assertTrue(r.squadOf(cap).isEmpty());
        assertEquals(Result.OK, r.disband(bob));
        assertTrue(r.all().isEmpty());
    }

    @Test
    void rankingIsByStarsThenSizeThenAge() {
        SquadRoster r = roster();
        r.create(cap, "Old", 0);
        r.create(bob, "New", 10);
        r.create(cat, "Starry", 20);
        r.addStars(cat, 5);
        r.invite(bob, dan, 0);
        r.join(dan, "New", 0);
        List<String> names = r.ranking().stream().map(Squad::name).toList();
        assertEquals(List.of("Starry", "New", "Old"), names);
        assertEquals(1, r.rank(r.byName("starry").orElseThrow()));
        assertEquals(-1, r.addStars(UUID.randomUUID(), 1));
        r.addStars(cat, -100);
        assertEquals(0, r.byName("Starry").orElseThrow().stars());
    }

    @Test
    void restoreSkipsPlayersAlreadyPlaced() {
        SquadRoster r = roster();
        UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID();
        r.restore(id1, "Bulls", "red", cap, List.of(cap, bob), 4, 0);
        r.restore(id2, "Eagles", "nonsense", cat, List.of(cat, bob), 1, 0);
        assertEquals(List.of(cap, bob), r.squadOf(bob).orElseThrow().members());
        assertEquals(List.of(cat), r.byName("Eagles").orElseThrow().members());
        assertEquals("red", r.byName("Eagles").orElseThrow().color());
        assertEquals(4, r.byName("Bulls").orElseThrow().stars());
    }
}
