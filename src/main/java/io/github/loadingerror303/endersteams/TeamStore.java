package io.github.loadingerror303.endersteams;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Owns team validation and persistence. Access only from the server thread. */
public final class TeamStore {
    private final Path file;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();

    public TeamStore(Path file) throws IOException {
        this.file = file;
        load();
    }

    public Team teamFor(UUID player) {
        return teams.values().stream().filter(team -> team.members().contains(player))
                .findFirst().orElse(null);
    }

    public String validateName(String input) {
        String name = input == null ? "" : input.strip();
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{2,23}")) {
            throw new IllegalArgumentException("Use 3-24 letters, numbers, spaces, _ or -; start with a letter or number.");
        }
        if (teams.values().stream().anyMatch(team -> team.name().equalsIgnoreCase(name))) {
            throw new IllegalArgumentException("That team name is already taken.");
        }
        return name;
    }

    public Team ownedBy(UUID owner) {
        Team team = teamFor(owner);
        if (team == null || !team.owner().equals(owner)) {
            throw new IllegalArgumentException("Only a team owner can send invitations.");
        }
        return team;
    }

    public Team join(UUID teamId, UUID member) throws IOException {
        Team team = teams.get(teamId);
        if (team == null) {
            throw new IllegalArgumentException("That team no longer exists.");
        }
        if (teamFor(member) != null) {
            throw new IllegalArgumentException("You already belong to a team.");
        }
        Set<UUID> members = new java.util.HashSet<>(team.members());
        members.add(member);
        Team joined = new Team(team.id(), team.name(), team.icon(), team.owner(), members);
        Map<UUID, Team> updated = new LinkedHashMap<>(teams);
        updated.put(teamId, joined);
        save(updated);
        teams.put(teamId, joined);
        return joined;
    }

    public Team create(UUID owner, String input, TeamIcon icon) throws IOException {
        if (teamFor(owner) != null) {
            throw new IllegalArgumentException("You already belong to a team.");
        }
        if (icon == null) {
            throw new IllegalArgumentException("Choose a team icon first.");
        }
        Team team = new Team(UUID.randomUUID(), validateName(input), icon, owner, Set.of(owner));
        Map<UUID, Team> updated = new LinkedHashMap<>(teams);
        updated.put(team.id(), team);
        save(updated);
        teams.put(team.id(), team);
        return team;
    }

    private void load() throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            if (yaml.getInt("version") != 1 || !yaml.isConfigurationSection("teams")) {
                throw new IllegalArgumentException("Unsupported team data format");
            }
            for (String key : yaml.getConfigurationSection("teams").getKeys(false)) {
                String path = "teams." + key + ".";
                UUID owner = UUID.fromString(yaml.getString(path + "owner", ""));
                Set<UUID> members = yaml.getStringList(path + "members").stream()
                        .map(UUID::fromString).collect(Collectors.toSet());
                String name = validateName(yaml.getString(path + "name"));
                Team team = new Team(UUID.fromString(key), name,
                        TeamIcon.valueOf(yaml.getString(path + "icon", "")), owner, members);
                if (members.stream().anyMatch(member -> teamFor(member) != null)) {
                    throw new IllegalArgumentException("A player belongs to multiple teams");
                }
                teams.put(team.id(), team);
            }
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IOException("Cannot load teams.yml; original file has been preserved", exception);
        }
    }

    private void save(Map<UUID, Team> updated) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.createSection("teams");
        for (Team team : updated.values()) {
            String path = "teams." + team.id() + ".";
            yaml.set(path + "name", team.name());
            yaml.set(path + "icon", team.icon().name());
            yaml.set(path + "owner", team.owner().toString());
            yaml.set(path + "members", team.members().stream().map(UUID::toString).sorted().toList());
        }
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "teams-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
