package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamManagementTest {
    @TempDir Path directory;
    private TeamStore teams;
    private UUID teamId;
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();

    @BeforeEach void setup() throws IOException {
        teams = new TeamStore(directory.resolve("teams.yml"));
        teamId = teams.create(owner, "Miners", TeamIcon.DIAMOND).id();
        teams.join(teamId, member);
    }

    @Test void kickingRemovesSavedMembershipAndAllowsRejoining() throws IOException {
        teams.kick(owner, teamId, member);
        assertNull(teams.teamFor(member));
        assertNull(reload().teamFor(member));
        assertNotNull(teams.join(teamId, member));
    }

    @Test void transferPreservesMembersAndMovesOwnerPowers() throws IOException {
        teams.transferOwnership(owner, teamId, member);
        assertEquals(member, reload().teamFor(owner).owner());
        assertThrows(IllegalArgumentException.class, () -> teams.kick(owner, teamId, member));
        teams.kick(member, teamId, owner);
        assertNull(teams.teamFor(owner));
    }

    @Test void nonOwnersCannotManageTeam() {
        assertThrows(IllegalArgumentException.class, () -> teams.kick(member, teamId, owner));
        assertThrows(IllegalArgumentException.class, () -> teams.transferOwnership(member, teamId, owner));
        assertThrows(IllegalArgumentException.class, () -> teams.disband(member, teamId));
        assertThrows(IllegalArgumentException.class, () -> teams.setFriendlyFire(member, teamId, true));
    }

    @Test void rejectsOwnerAndNonmemberTargets() {
        for (UUID target : new UUID[]{owner, UUID.randomUUID()}) {
            assertThrows(IllegalArgumentException.class, () -> teams.kick(owner, teamId, target));
            assertThrows(IllegalArgumentException.class, () -> teams.transferOwnership(owner, teamId, target));
        }
    }

    @Test void staleMenuCannotModifyNewTeam() throws IOException {
        teams.disband(owner, teamId);
        teams.create(owner, "New Team", TeamIcon.COAL);
        assertThrows(IllegalArgumentException.class, () -> teams.disband(owner, teamId));
        assertThrows(IllegalArgumentException.class, () -> teams.setFriendlyFire(owner, teamId, true));
        assertNotNull(teams.teamFor(owner));
    }

    @Test void disbandRemovesAllMembershipsAndFreesNameAfterRestart() throws IOException {
        teams.disband(owner, teamId);
        TeamStore restarted = reload();
        assertNull(restarted.teamFor(owner));
        assertNull(restarted.teamFor(member));
        assertNotNull(restarted.create(member, "Miners", TeamIcon.AMETHYST));
    }

    @Test void friendlyFireDefaultsOffAndPersistsThroughMembershipChanges() throws IOException {
        assertTrue(teams.blocksFriendlyFire(owner, member));
        assertFalse(teams.blocksFriendlyFire(owner, owner));
        assertFalse(teams.blocksFriendlyFire(owner, UUID.randomUUID()));
        assertFalse(teams.blocksFriendlyFire(UUID.randomUUID(), member));
        teams.setFriendlyFire(owner, teamId, true);
        teams.join(teamId, UUID.randomUUID());
        teams.transferOwnership(owner, teamId, member);
        assertTrue(reload().teamFor(owner).friendlyFire());
        assertFalse(teams.blocksFriendlyFire(owner, member));
        teams.setFriendlyFire(member, teamId, false);
        assertTrue(reload().blocksFriendlyFire(member, owner));
    }

    @Test void oldSaveWithoutSettingLoadsWithFriendlyFireOff() throws IOException {
        Path file = directory.resolve("teams.yml");
        Files.writeString(file, Files.readString(file).replaceAll("(?m)^.*friendly-fire:.*\\R", ""));
        assertTrue(reload().blocksFriendlyFire(owner, member));
    }

    @Test void failedWritesLeaveAllManagementStateUnchanged() throws IOException {
        Team before = teams.teamFor(owner);
        Path file = directory.resolve("teams.yml");
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "block replacement");
        assertThrows(IOException.class, () -> teams.kick(owner, teamId, member));
        assertThrows(IOException.class, () -> teams.transferOwnership(owner, teamId, member));
        assertThrows(IOException.class, () -> teams.setFriendlyFire(owner, teamId, true));
        assertThrows(IOException.class, () -> teams.disband(owner, teamId));
        assertEquals(before, teams.teamFor(owner));
        assertEquals(before, teams.teamFor(member));
    }

    @Test void transferAndDisbandInvalidateOldInvitations() throws IOException {
        TeamInvitations invites = new TeamInvitations(teams, Clock.systemUTC());
        UUID guest = UUID.randomUUID();
        var old = invites.send(owner, guest);
        teams.transferOwnership(owner, teamId, member);
        assertTrue(invites.pending(guest).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> invites.accept(guest, old.id()));
        var current = invites.send(member, guest);
        teams.disband(member, teamId);
        assertThrows(IllegalArgumentException.class, () -> invites.accept(guest, current.id()));
    }

    private TeamStore reload() throws IOException {
        return new TeamStore(directory.resolve("teams.yml"));
    }
}
