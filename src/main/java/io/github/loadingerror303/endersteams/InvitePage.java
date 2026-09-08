package io.github.loadingerror303.endersteams;

import java.util.List;
import java.util.UUID;

/** A snapshot keeps each displayed head tied to its player even when players log out. */
record InvitePage(List<UUID> players, int index, int count) {
    static final int SIZE = 45;

    InvitePage {
        players = List.copyOf(players);
    }

    static InvitePage of(List<UUID> online, int requested) {
        int count = Math.max(1, (online.size() + SIZE - 1) / SIZE);
        int index = Math.max(0, Math.min(requested, count - 1));
        int start = index * SIZE;
        return new InvitePage(online.subList(start, Math.min(start + SIZE, online.size())), index, count);
    }

    boolean hasPrevious() { return index > 0; }
    boolean hasNext() { return index + 1 < count; }
}
