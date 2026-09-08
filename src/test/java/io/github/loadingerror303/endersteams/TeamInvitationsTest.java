package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamInvitationsTest {
    @TempDir Path directory;
    private TeamStore teams;
    private TeamInvitations invites;
    private final UUID owner = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();
    private final MutableClock clock = new MutableClock();

    @BeforeEach void setup() throws IOException {
        teams = new TeamStore(directory.resolve("teams.yml"));
        teams.create(owner, "Miners", TeamIcon.AMETHYST);
        invites = new TeamInvitations(teams, clock);
    }

    @Test void acceptancePersistsMembershipAndCannotBeReplayed() throws IOException {
        var invite = invites.send(owner, guest);
        Team joined = invites.accept(guest, invite.id());
        assertEquals(owner, joined.owner());
        assertEquals(joined, new TeamStore(directory.resolve("teams.yml")).teamFor(guest));
        assertTrue(invites.pending(guest).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> invites.accept(guest, invite.id()));
    }

    @Test void enforcesOwnershipAndPreventsDuplicateInvites() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> invites.send(guest, owner));
        assertThrows(IllegalArgumentException.class, () -> invites.send(owner, owner));
        var invite = invites.send(owner, guest);
        assertThrows(IllegalArgumentException.class, () -> invites.send(owner, guest));
        invites.accept(guest, invite.id());
        assertThrows(IllegalArgumentException.class, () -> invites.send(guest, UUID.randomUUID()));
    }

    @Test void onlyRecipientCanRespond() {
        var invite = invites.send(owner, guest);
        assertThrows(IllegalArgumentException.class, () -> invites.accept(owner, invite.id()));
        assertThrows(IllegalArgumentException.class, () -> invites.decline(owner, invite.id()));
        assertEquals(1, invites.pending(guest).size());
    }

    @Test void declineDoesNotJoinAndAllowsNewInviteAfterCooldown() {
        var invite = invites.send(owner, guest);
        invites.decline(guest, invite.id());
        assertNull(teams.teamFor(guest));
        assertTrue(invites.pending(guest).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> invites.send(owner, guest));
        clock.now = clock.now.plusSeconds(60);
        assertNotEquals(invite.id(), invites.send(owner, guest).id());
    }

    @Test void cooldownRoundsUpAndBlockedAttemptsDoNotExtendIt() {
        var invite = invites.send(owner, guest);
        invites.decline(guest, invite.id());
        assertEquals(60, invites.cooldownSeconds(owner, guest));
        clock.now = clock.now.plusMillis(59_500);
        assertEquals(1, invites.cooldownSeconds(owner, guest));
        assertThrows(IllegalArgumentException.class, () -> invites.send(owner, guest));
        clock.now = clock.now.plusMillis(500);
        invites.expire();
        assertEquals(0, invites.cooldownSeconds(owner, guest));
        assertNotNull(invites.send(owner, guest));
    }

    @Test void cooldownIsIndependentForEachSenderAndRecipient() throws IOException {
        var invite = invites.send(owner, guest);
        invites.decline(guest, invite.id());
        UUID otherOwner = UUID.randomUUID();
        teams.create(otherOwner, "Builders", TeamIcon.DIAMOND);
        assertNotNull(invites.send(otherOwner, guest));
        assertNotNull(invites.send(owner, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> invites.send(owner, guest));
    }

    @Test void pendingInviteStillBlocksResendingAfterCooldownEnds() {
        invites.send(owner, guest);
        clock.now = clock.now.plusSeconds(60);
        assertEquals(0, invites.cooldownSeconds(owner, guest));
        assertThrows(IllegalArgumentException.class, () -> invites.send(owner, guest));
    }

    @Test void expiresAtExactlyFiveMinutes() {
        var invite = invites.send(owner, guest);
        clock.now = clock.now.plusSeconds(299);
        assertEquals(1, invites.pending(guest).size());
        clock.now = clock.now.plusSeconds(1);
        assertThrows(IllegalArgumentException.class, () -> invites.accept(guest, invite.id()));
        assertTrue(invites.pending(guest).isEmpty());
        assertNotNull(invites.send(owner, guest));
    }

    @Test void acceptsSpecificTeamAndClearsOtherInvites() throws IOException {
        UUID otherOwner = UUID.randomUUID();
        Team other = teams.create(otherOwner, "Builders", TeamIcon.DIAMOND);
        var first = invites.send(owner, guest);
        var second = invites.send(otherOwner, guest);
        assertEquals(other.id(), invites.accept(guest, second.id()).id());
        assertThrows(IllegalArgumentException.class, () -> invites.accept(guest, first.id()));
    }

    @Test void cannotJoinAfterCreatingOwnTeam() throws IOException {
        var invite = invites.send(owner, guest);
        Team own = teams.create(guest, "Own Team", TeamIcon.COAL);
        assertThrows(IllegalArgumentException.class, () -> invites.accept(guest, invite.id()));
        assertEquals(own, teams.teamFor(guest));
    }

    @Test void failedSavePreservesInviteAndMembershipForRetry() throws IOException {
        var invite = invites.send(owner, guest);
        Path file = directory.resolve("teams.yml");
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "keep directory nonempty");
        assertThrows(IOException.class, () -> invites.accept(guest, invite.id()));
        assertNull(teams.teamFor(guest));
        assertEquals(1, invites.pending(guest).size());
        Files.delete(file.resolve("blocker"));
        Files.delete(file);
        assertNotNull(invites.accept(guest, invite.id()));
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
}
