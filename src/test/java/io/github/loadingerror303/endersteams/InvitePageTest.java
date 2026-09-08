package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InvitePageTest {
    @Test void handlesEmptyAndExactlyFullPages() {
        InvitePage empty = InvitePage.of(List.of(), 0);
        assertEquals(1, empty.count());
        assertFalse(empty.hasPrevious());
        assertFalse(empty.hasNext());
        InvitePage full = InvitePage.of(players(45), 0);
        assertEquals(45, full.players().size());
        assertFalse(full.hasNext());
    }

    @Test void overflowIsReachableWithoutDuplicatesOrMissingPlayers() {
        List<UUID> online = players(91);
        List<UUID> visited = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            InvitePage current = InvitePage.of(online, page);
            assertEquals(3, current.count());
            assertEquals(page > 0, current.hasPrevious());
            assertEquals(page < 2, current.hasNext());
            visited.addAll(current.players());
        }
        assertEquals(online, visited);
        assertEquals(1, InvitePage.of(players(46), 1).players().size());
    }

    @Test void clampsPageWhenPlayersLeaveAndKeepsDisplayedTargetsStable() {
        List<UUID> online = new ArrayList<>(players(46));
        InvitePage displayed = InvitePage.of(online, 1);
        UUID target = displayed.players().getFirst();
        online.removeFirst();
        assertEquals(target, displayed.players().getFirst());
        assertEquals(0, InvitePage.of(online, displayed.index()).index());
        assertEquals(0, InvitePage.of(online, -1).index());
        assertThrows(UnsupportedOperationException.class, () -> displayed.players().clear());
    }

    private static List<UUID> players(int count) {
        return IntStream.range(0, count).mapToObj(index -> new UUID(0, index)).toList();
    }
}
