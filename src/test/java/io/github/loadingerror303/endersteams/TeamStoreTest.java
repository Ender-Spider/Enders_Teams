package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamStoreTest {
    @TempDir Path directory;

    @Test void preservesTeamAndOwnershipAcrossRestart() throws IOException {
        Path file = directory.resolve("teams.yml");
        UUID owner = UUID.randomUUID();
        Team team = new TeamStore(file).create(owner, "  Ender Miners  ", TeamIcon.AMETHYST);
        assertEquals("Ender Miners", team.name());
        assertEquals(team, new TeamStore(file).teamFor(owner));
    }

    @Test void rejectsDuplicateNamesAndMultipleMembershipsWithoutChangingFile() throws IOException {
        Path file = directory.resolve("teams.yml");
        TeamStore store = new TeamStore(file);
        UUID owner = UUID.randomUUID();
        store.create(owner, "Miners", TeamIcon.DIAMOND);
        String saved = Files.readString(file);
        assertThrows(IllegalArgumentException.class,
                () -> store.create(UUID.randomUUID(), " miners ", TeamIcon.GOLD));
        assertThrows(IllegalArgumentException.class,
                () -> store.create(owner, "Another Team", TeamIcon.IRON));
        assertEquals(saved, Files.readString(file));
    }

    @Test void rejectsInvalidNamesAndMissingIcons() throws IOException {
        TeamStore store = new TeamStore(directory.resolve("teams.yml"));
        for (String name : new String[]{"", "  ", "ab", "x".repeat(25), "<red>Team", "A.B", "A\nB", "_Team"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> store.create(UUID.randomUUID(), name, TeamIcon.COAL), name);
        }
        assertThrows(IllegalArgumentException.class,
                () -> store.create(UUID.randomUUID(), "Miners", null));
        assertFalse(Files.exists(directory.resolve("teams.yml")));
    }

    @Test void failedSaveDoesNotCreateTeamInMemory() throws IOException {
        Path parent = directory.resolve("blocked");
        TeamStore store = new TeamStore(parent.resolve("teams.yml"));
        Files.writeString(parent, "cannot be a directory");
        UUID owner = UUID.randomUUID();
        assertThrows(IOException.class, () -> store.create(owner, "Miners", TeamIcon.COPPER));
        assertNull(store.teamFor(owner));
        assertEquals("Miners", store.validateName("Miners"));
    }

    @Test void refusesCorruptDataWithoutOverwritingIt() throws IOException {
        Path file = directory.resolve("teams.yml");
        String broken = "version: 1\nteams: [broken";
        Files.writeString(file, broken);
        assertThrows(IOException.class, () -> new TeamStore(file));
        assertEquals(broken, Files.readString(file));
    }
}
