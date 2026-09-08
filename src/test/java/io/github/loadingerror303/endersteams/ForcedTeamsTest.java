package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForcedTeamsTest {
    @TempDir Path directory;
    private TeamStore teams;
    private Team first;
    private Team second;
    private final UUID member = UUID.randomUUID();

    @BeforeEach void setup() throws IOException {
        teams = new TeamStore(directory.resolve("teams.yml"));
        first = teams.create(UUID.randomUUID(), "First Team", TeamIcon.COAL);
        second = teams.create(UUID.randomUUID(), "Second Team", TeamIcon.DIAMOND);
        teams.join(first.id(), member);
    }

    @Test void forceMovePersistsBothTeamsAndSettings() throws IOException {
        teams.setFriendlyFire(second.owner(), second.id(), true);
        teams.forceJoin(member, second.id());
        TeamStore restarted = new TeamStore(directory.resolve("teams.yml"));
        assertEquals(second.id(), restarted.teamFor(member).id());
        assertFalse(restarted.byId(first.id()).members().contains(member));
        assertTrue(restarted.byId(second.id()).friendlyFire());
        assertEquals(first.owner(), restarted.byId(first.id()).owner());
    }

    @Test void canAssignUnteamedPlayerWithoutInvitation() throws IOException {
        UUID guest = UUID.randomUUID();
        assertEquals(second.id(), teams.forceJoin(guest, second.id()).id());
        assertEquals(second.id(), teams.teamFor(guest).id());
    }

    @Test void cannotMoveOrRemoveOwnerUntilReplacementAssigned() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> teams.forceJoin(first.owner(), second.id()));
        assertThrows(IllegalArgumentException.class, () -> teams.forceLeave(first.owner()));
        teams.forceOwner(member);
        teams.forceJoin(first.owner(), second.id());
        assertEquals(member, teams.byId(first.id()).owner());
        assertEquals(second.id(), teams.teamFor(first.owner()).id());
    }

    @Test void rejectsUnknownTeamSameTeamAndUnteamedOwnership() {
        assertThrows(IllegalArgumentException.class, () -> teams.forceJoin(member, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> teams.forceJoin(member, first.id()));
        assertThrows(IllegalArgumentException.class, () -> teams.forceOwner(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> teams.forceLeave(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> teams.forceDisband(UUID.randomUUID()));
    }

    @Test void forceLeaveAndDisbandPersist() throws IOException {
        teams.forceLeave(member);
        teams.forceDisband(first.id());
        TeamStore restarted = new TeamStore(directory.resolve("teams.yml"));
        assertNull(restarted.teamFor(member));
        assertNull(restarted.teamFor(first.owner()));
        assertNotNull(restarted.byId(second.id()));
    }

    @Test void failedMovePreservesBothTeams() throws IOException {
        Team before = teams.byId(first.id());
        Path file = directory.resolve("teams.yml");
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "block replacement");
        assertThrows(IOException.class, () -> teams.forceJoin(member, second.id()));
        assertEquals(before, teams.byId(first.id()));
        assertEquals(second, teams.byId(second.id()));
        assertEquals(first.id(), teams.teamFor(member).id());
    }
}
