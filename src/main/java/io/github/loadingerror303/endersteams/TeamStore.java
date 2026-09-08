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

    public Team byId(UUID id) { return teams.get(id); }

    public Team byName(String name) {
        return teams.values().stream().filter(team -> team.name().equalsIgnoreCase(name.strip()))
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
            throw new IllegalArgumentException("Only the team owner can perform this action.");
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
        Team joined = new Team(team.id(), team.name(), team.icon(), team.owner(), members, team.friendlyFire());
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

    public Team leave(UUID member) throws IOException {
        Team team = teamFor(member);
        if (team == null) {
            throw new IllegalArgumentException("You are not on a team.");
        }
        if (team.owner().equals(member)) {
            throw new IllegalArgumentException("Transfer ownership or disband your team through /team menu before leaving.");
        }
        Set<UUID> members = new java.util.HashSet<>(team.members());
        members.remove(member);
        return replace(new Team(team.id(), team.name(), team.icon(), team.owner(), members, team.friendlyFire()));
    }

    public Team kick(UUID owner, UUID expectedTeam, UUID member) throws IOException {
        Team team = requireOwner(owner, expectedTeam);
        requireOtherMember(team, member);
        Set<UUID> members = new java.util.HashSet<>(team.members());
        members.remove(member);
        return replace(new Team(team.id(), team.name(), team.icon(), owner, members, team.friendlyFire()));
    }

    public Team transferOwnership(UUID owner, UUID expectedTeam, UUID successor) throws IOException {
        Team team = requireOwner(owner, expectedTeam);
        requireOtherMember(team, successor);
        return replace(new Team(team.id(), team.name(), team.icon(), successor, team.members(), team.friendlyFire()));
    }

    public Team setFriendlyFire(UUID owner, UUID expectedTeam, boolean enabled) throws IOException {
        Team team = requireOwner(owner, expectedTeam);
        return replace(new Team(team.id(), team.name(), team.icon(), owner, team.members(), enabled));
    }

    public Team disband(UUID owner, UUID expectedTeam) throws IOException {
        Team team = requireOwner(owner, expectedTeam);
        Map<UUID, Team> updated = new LinkedHashMap<>(teams);
        updated.remove(team.id());
        save(updated);
        teams.remove(team.id());
        return team;
    }

    /** Admin authorization is enforced by the command before entering these operations. */
    public Team forceJoin(UUID member, UUID destinationId) throws IOException {
        Team destination = teams.get(destinationId);
        if (destination == null) throw new IllegalArgumentException("That team no longer exists.");
        Team previous = teamFor(member);
        if (previous != null && previous.id().equals(destinationId)) {
            throw new IllegalArgumentException("That player is already on that team.");
        }
        if (previous != null && previous.owner().equals(member)) {
            throw new IllegalArgumentException("Assign a replacement owner or disband the old team before moving its owner.");
        }
        Map<UUID, Team> updated = new LinkedHashMap<>(teams);
        if (previous != null) {
            Set<UUID> remaining = new java.util.HashSet<>(previous.members());
            remaining.remove(member);
            updated.put(previous.id(), new Team(previous.id(), previous.name(), previous.icon(),
                    previous.owner(), remaining, previous.friendlyFire()));
        }
        Set<UUID> members = new java.util.HashSet<>(destination.members());
        members.add(member);
        Team joined = new Team(destination.id(), destination.name(), destination.icon(),
                destination.owner(), members, destination.friendlyFire());
        updated.put(destinationId, joined);
        // Save both teams together; a failed write must not strand the player between teams.
        save(updated);
        teams.clear();
        teams.putAll(updated);
        return joined;
    }

    public Team forceLeave(UUID member) throws IOException {
        Team team = teamFor(member);
        if (team == null) throw new IllegalArgumentException("That player is not on a team.");
        if (team.owner().equals(member)) {
            throw new IllegalArgumentException("Assign a replacement owner or disband the team before removing its owner.");
        }
        return leave(member);
    }

    public Team forceOwner(UUID member) throws IOException {
        Team team = teamFor(member);
        if (team == null) throw new IllegalArgumentException("That player is not on a team.");
        return transferOwnership(team.owner(), team.id(), member);
    }

    public Team forceDisband(UUID teamId) throws IOException {
        Team team = teams.get(teamId);
        if (team == null) throw new IllegalArgumentException("That team no longer exists.");
        return disband(team.owner(), team.id());
    }

    public boolean blocksFriendlyFire(UUID attacker, UUID victim) {
        if (attacker.equals(victim)) {
            return false;
        }
        Team team = teamFor(attacker);
        return team != null && !team.friendlyFire() && team.members().contains(victim);
    }

    private Team requireOwner(UUID owner, UUID expectedTeam) {
        Team team = ownedBy(owner);
        if (!team.id().equals(expectedTeam)) {
            throw new IllegalArgumentException("Your team has changed. Reopen /team menu.");
        }
        return team;
    }

    private void requireOtherMember(Team team, UUID member) {
        if (team.owner().equals(member)) {
            throw new IllegalArgumentException("Choose another team member.");
        }
        if (!team.members().contains(member)) {
            throw new IllegalArgumentException("That player is no longer on your team.");
        }
    }

    private Team replace(Team team) throws IOException {
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
                        TeamIcon.valueOf(yaml.getString(path + "icon", "")), owner, members,
                        yaml.getBoolean(path + "friendly-fire", false));
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
            yaml.set(path + "friendly-fire", team.friendlyFire());
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
