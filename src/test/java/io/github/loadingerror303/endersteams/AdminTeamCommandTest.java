package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class AdminTeamCommandTest {
    @TempDir Path directory;
    private MockedStatic<Bukkit> bukkit;
    private TeamStore teams;
    private TeamInvitations invites;
    private TeamManagementMenu menus;
    private EndersTeams plugin;
    private AdminTeamCommand command;
    private Player admin;
    private Player member;
    private Player owner;
    private Team first;
    private Team second;
    private Logger logger;
    private final MutableClock clock = new MutableClock();

    @BeforeEach void setup() throws IOException {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[0]);
        plugin = mock(EndersTeams.class);
        logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        teams = new TeamStore(directory.resolve("teams.yml"));
        admin = player("Admin");
        member = player("Member");
        owner = player("Owner");
        when(admin.hasPermission("endersteams.admin.force")).thenReturn(true);
        first = teams.create(owner.getUniqueId(), "First Team", TeamIcon.COAL);
        second = teams.create(UUID.randomUUID(), "Second Team", TeamIcon.DIAMOND);
        teams.join(first.id(), member.getUniqueId());
        invites = new TeamInvitations(teams, clock);
        menus = mock(TeamManagementMenu.class);
        command = new AdminTeamCommand(plugin, teams, invites, menus, clock);
    }

    @AfterEach void cleanup() { if (bukkit != null) bukkit.close(); }

    @Test void deniesEveryForceActionWithoutPermission() {
        when(admin.hasPermission("endersteams.admin.force")).thenReturn(false);
        execute("join", "Member", "Second", "Team");
        execute("leave", "Member");
        execute("owner", "Member");
        execute("disband", "First", "Team");
        execute("confirm", UUID.randomUUID().toString());
        assertEquals(first.id(), teams.teamFor(member.getUniqueId()).id());
        assertEquals(owner.getUniqueId(), teams.byId(first.id()).owner());
        verify(logger, never()).info(anyString());
    }

    @Test void joinSupportsOfflinePlayersSpacedTeamNamesAndLogsSuccess() {
        bukkit.when(() -> Bukkit.getPlayerExact("Member")).thenReturn(null);
        bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Member")).thenReturn(member);
        when(member.hasPlayedBefore()).thenReturn(true);
        execute("join", "Member", "second", "team");
        assertEquals(second.id(), teams.teamFor(member.getUniqueId()).id());
        verify(menus).refreshTeam(first.id());
        verify(menus).refreshTeam(second.id());
        verify(logger).info(contains("join player=" + member.getUniqueId()));
    }

    @Test void uncachedPreviouslyJoinedPlayersCanBeResolvedWithoutInventingPlayers() {
        bukkit.when(() -> Bukkit.getPlayerExact("Member")).thenReturn(null);
        when(member.hasPlayedBefore()).thenReturn(true);
        bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[]{member});
        execute("join", "Member", "Second", "Team");
        assertEquals(second.id(), teams.teamFor(member.getUniqueId()).id());
        execute("join", "Typo", "Second", "Team");
        assertEquals(2, teams.byId(second.id()).members().size());
    }

    @Test void rejectsOwnerRemovalThenAllowsItAfterForcedOwnershipTransfer() {
        execute("leave", "Owner");
        assertNotNull(teams.teamFor(owner.getUniqueId()));
        execute("owner", "Member");
        execute("leave", "Owner");
        assertNull(teams.teamFor(owner.getUniqueId()));
        assertEquals(member.getUniqueId(), teams.byId(first.id()).owner());
    }

    @Test void disbandRequiresSameAdminConfirmationAndCannotBeReplayed() {
        String token = requestDisband();
        assertNotNull(teams.byId(first.id()));
        Player other = player("OtherAdmin");
        when(other.hasPermission("endersteams.admin.force")).thenReturn(true);
        command.execute(other, new String[]{"force", "confirm", token});
        assertNotNull(teams.byId(first.id()));
        execute("confirm", token);
        assertNull(teams.byId(first.id()));
        execute("confirm", token);
        verify(logger, times(1)).info(contains("disband team="));
    }

    @Test void confirmationExpiresAndCancelDoesNotDisband() {
        String token = requestDisband();
        clock.now = clock.now.plusSeconds(60);
        execute("confirm", token);
        assertNotNull(teams.byId(first.id()));
        token = requestDisband();
        execute("cancel", token);
        execute("confirm", token);
        assertNotNull(teams.byId(first.id()));
        verify(logger, never()).info(anyString());
    }

    @Test void revokedPermissionAndChangedTeamInvalidateConfirmation() throws IOException {
        String token = requestDisband();
        when(admin.hasPermission("endersteams.admin.force")).thenReturn(false);
        execute("confirm", token);
        assertNotNull(teams.byId(first.id()));
        when(admin.hasPermission("endersteams.admin.force")).thenReturn(true);
        teams.setFriendlyFire(owner.getUniqueId(), first.id(), true);
        execute("confirm", token);
        assertNotNull(teams.byId(first.id()));
        execute("confirm", requestDisband());
        assertNull(teams.byId(first.id()));
    }

    @Test void failedSaveKeepsConfirmationForRetryAndDoesNotLogSuccess() throws IOException {
        String token = requestDisband();
        Path file = directory.resolve("teams.yml");
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "block replacement");
        execute("confirm", token);
        assertNotNull(teams.byId(first.id()));
        verify(logger, never()).info(anyString());
        Files.delete(file.resolve("blocker"));
        Files.delete(file);
        execute("confirm", token);
        assertNull(teams.byId(first.id()));
    }

    @Test void teamCommandRoutesForceForConsoleAndClearsPendingInvites() {
        UUID guest = member.getUniqueId();
        execute("leave", "Member");
        invites.send(owner.getUniqueId(), guest);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission("endersteams.admin.force")).thenReturn(true);
        when(console.getName()).thenReturn("CONSOLE");
        TeamCommand router = new TeamCommand(plugin, teams, mock(TeamCreationMenu.class),
                mock(TeamInviteMenu.class), invites, menus);
        router.onCommand(console, null, "team", new String[]{"force", "join", "Member", "Second", "Team"});
        assertEquals(second.id(), teams.teamFor(guest).id());
        assertTrue(invites.pending(guest).isEmpty());
        verify(logger).info(contains("console join player="));
    }

    private String requestDisband() {
        execute("disband", "First", "Team");
        ArgumentCaptor<Component> messages = ArgumentCaptor.forClass(Component.class);
        verify(admin, atLeastOnce()).sendMessage(messages.capture());
        String token = null;
        for (Component message : messages.getAllValues()) {
            if (message instanceof TextComponent text) {
                var matcher = Pattern.compile("/endersteams:team force confirm ([a-f0-9-]+)").matcher(text.content());
                if (matcher.find()) token = matcher.group(1);
            }
        }
        assertNotNull(token);
        return token;
    }

    private void execute(String... arguments) {
        String[] args = new String[arguments.length + 1];
        args[0] = "force";
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        command.execute(admin, args);
    }

    private Player player(String name) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        bukkit.when(() -> Bukkit.getPlayerExact(name)).thenReturn(player);
        bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
        return player;
    }

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
}
