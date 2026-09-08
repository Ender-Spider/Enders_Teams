package io.github.loadingerror303.endersteams;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pending invitations live for five minutes, or until the server stops. Server thread only. */
public final class TeamInvitations {
    public record Invitation(UUID id, UUID teamId, UUID sender, UUID recipient, Instant expiresAt) { }
    private record InvitePair(UUID sender, UUID recipient) { }

    private final TeamStore teams;
    private final Clock clock;
    private final Map<UUID, Invitation> invitations = new LinkedHashMap<>();
    private final Map<InvitePair, Instant> cooldowns = new LinkedHashMap<>();

    public TeamInvitations(TeamStore teams, Clock clock) {
        this.teams = teams;
        this.clock = clock;
    }

    public Invitation send(UUID sender, UUID recipient) {
        Team team = teams.ownedBy(sender);
        if (teams.teamFor(recipient) != null) {
            throw new IllegalArgumentException("That player already belongs to a team.");
        }
        if (pending(recipient).stream().anyMatch(invite -> invite.teamId().equals(team.id()))) {
            throw new IllegalArgumentException("That player already has a pending invitation from your team.");
        }
        long remaining = cooldownSeconds(sender, recipient);
        if (remaining > 0) {
            throw new IllegalArgumentException("Wait " + remaining + " seconds before inviting that player again.");
        }
        Instant now = clock.instant();
        Invitation invite = new Invitation(UUID.randomUUID(), team.id(), sender, recipient,
                now.plus(Duration.ofMinutes(5)));
        invitations.put(invite.id(), invite);
        cooldowns.put(new InvitePair(sender, recipient), now.plusSeconds(60));
        return invite;
    }

    public long cooldownSeconds(UUID sender, UUID recipient) {
        Instant until = cooldowns.get(new InvitePair(sender, recipient));
        if (until == null || !until.isAfter(clock.instant())) {
            return 0;
        }
        return (long) Math.ceil(Duration.between(clock.instant(), until).toNanos() / 1_000_000_000.0);
    }

    public List<Invitation> pending(UUID recipient) {
        expire();
        return invitations.values().stream().filter(invite -> invite.recipient().equals(recipient)).toList();
    }

    public Team accept(UUID recipient, UUID invitationId) throws IOException {
        Invitation invite = require(recipient, invitationId);
        Team team = teams.ownedBy(invite.sender());
        if (!team.id().equals(invite.teamId())) {
            throw new IllegalArgumentException("That invitation is no longer valid.");
        }
        Team joined = teams.join(invite.teamId(), recipient);
        // Consume only after a successful save, so a failed write can be retried.
        invitations.values().removeIf(other -> other.recipient().equals(recipient));
        return joined;
    }

    public Invitation decline(UUID recipient, UUID invitationId) {
        Invitation invite = require(recipient, invitationId);
        invitations.remove(invite.id());
        return invite;
    }

    public void expire() {
        Instant now = clock.instant();
        invitations.values().removeIf(invite -> {
            Team team = teams.teamFor(invite.sender());
            return !invite.expiresAt().isAfter(now) || team == null
                    || !team.id().equals(invite.teamId()) || !team.owner().equals(invite.sender());
        });
        cooldowns.values().removeIf(until -> !until.isAfter(now));
    }

    private Invitation require(UUID recipient, UUID invitationId) {
        expire();
        Invitation invite = invitations.get(invitationId);
        if (invite == null || !invite.recipient().equals(recipient)) {
            throw new IllegalArgumentException("That invitation has expired or is no longer available.");
        }
        return invite;
    }
}
