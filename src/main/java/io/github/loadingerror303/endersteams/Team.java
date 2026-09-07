package io.github.loadingerror303.endersteams;

import java.util.Set;
import java.util.UUID;

public record Team(UUID id, String name, TeamIcon icon, UUID owner, Set<UUID> members) {
    public Team {
        members = Set.copyOf(members);
        if (!members.contains(owner)) {
            throw new IllegalArgumentException("The owner must be a team member.");
        }
    }
}
