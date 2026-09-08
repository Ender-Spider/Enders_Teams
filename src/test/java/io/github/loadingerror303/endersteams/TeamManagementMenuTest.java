package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Simulate inventory clicks and next-tick transitions; rendering is checked in-game. */
class TeamManagementMenuTest {
    @TempDir Path directory;
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ItemStack> items;
    private TeamStore teams;
    private TeamManagementMenu menu;
    private TeamCommand command;
    private UUID teamId;
    private Player owner;
    private Player member;
    private final Map<UUID, Player> players = new HashMap<>();
    private final Map<UUID, Inventory> open = new HashMap<>();
    private final Queue<Runnable> tasks = new ArrayDeque<>();

    @BeforeEach void setup() throws IOException {
        bukkit = mockStatic(Bukkit.class);
        items = mockConstruction(ItemStack.class);
        EndersTeams plugin = mock(EndersTeams.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return null;
        });
        bukkit.when(() -> Bukkit.createInventory(isNull(), anyInt(), any(Component.class)))
                .thenAnswer(call -> {
                    Inventory inventory = mock(Inventory.class);
                    when(inventory.getSize()).thenReturn(call.getArgument(1));
                    return inventory;
                });
        bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class)))
                .thenAnswer(call -> players.get(call.getArgument(0)));
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class)))
                .thenAnswer(call -> players.get(call.getArgument(0)));
        owner = player("Owner");
        member = player("Member");
        teams = new TeamStore(directory.resolve("teams.yml"));
        teamId = teams.create(owner.getUniqueId(), "Miners", TeamIcon.DIAMOND).id();
        teams.join(teamId, member.getUniqueId());
        TeamInvitations invitations = new TeamInvitations(teams, Clock.systemUTC());
        TeamInviteMenu invites = mock(TeamInviteMenu.class);
        menu = new TeamManagementMenu(plugin, teams, invitations, invites);
        command = new TeamCommand(plugin, teams, mock(TeamCreationMenu.class), invites, invitations, menu);
    }

    @AfterEach void cleanup() {
        if (items != null) items.close();
        if (bukkit != null) bukkit.close();
    }

    @Test void commandOpensMenuAndMemberCannotUseOwnerControls() {
        command.onCommand(member, null, "team", new String[]{"menu"});
        assertNotNull(open.get(member.getUniqueId()));
        click(member, 47);
        click(member, 51);
        click(member, 0);
        assertFalse(teams.teamFor(owner.getUniqueId()).friendlyFire());
        assertEquals(2, teams.teamFor(owner.getUniqueId()).members().size());
    }

    @Test void kickRequiresConfirmationAndCancelPreservesMember() {
        menu.open(owner, 0);
        click(owner, 1);
        click(owner, 11);
        assertNotNull(teams.teamFor(member.getUniqueId()));
        click(owner, 15);
        assertNotNull(teams.teamFor(member.getUniqueId()));
        click(owner, 1);
        click(owner, 11);
        click(owner, 11);
        assertNull(teams.teamFor(member.getUniqueId()));
    }

    @Test void transferRequiresConfirmationAndRemovesFormerOwnerPowers() {
        menu.open(owner, 0);
        click(owner, 1);
        click(owner, 15);
        assertEquals(owner.getUniqueId(), teams.teamFor(owner.getUniqueId()).owner());
        click(owner, 11);
        assertEquals(member.getUniqueId(), teams.teamFor(owner.getUniqueId()).owner());
        click(owner, 51);
        assertNotNull(teams.teamFor(owner.getUniqueId()));
    }

    @Test void disbandConfirmationClosesAllTeamMenus() {
        menu.open(owner, 0);
        menu.open(member, 0);
        click(owner, 51);
        assertNotNull(teams.teamFor(owner.getUniqueId()));
        click(owner, 11);
        assertNull(teams.teamFor(owner.getUniqueId()));
        assertNull(teams.teamFor(member.getUniqueId()));
        assertTrue(open.isEmpty());
    }

    @Test void rapidClicksToggleOnlyOnceAndDraggingIsCancelled() {
        menu.open(owner, 0);
        InventoryClickEvent first = event(owner, 47, ClickType.LEFT);
        menu.onClick(first);
        menu.onClick(event(owner, 47, ClickType.LEFT));
        assertEquals(1, tasks.size());
        runTasks();
        assertTrue(teams.teamFor(owner.getUniqueId()).friendlyFire());
        verify(first).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(owner);
        InventoryView dragView = owner.getOpenInventory();
        when(drag.getView()).thenReturn(dragView);
        menu.onDrag(drag);
        verify(drag).setCancelled(true);
        menu.onClick(event(owner, 47, ClickType.SHIFT_LEFT));
        assertTrue(tasks.isEmpty());
    }

    @Test void closingMenuCancelsQueuedDestructiveAction() {
        menu.open(owner, 0);
        click(owner, 51);
        menu.onClick(event(owner, 11, ClickType.LEFT));
        owner.closeInventory();
        runTasks();
        assertNotNull(teams.teamFor(owner.getUniqueId()));
    }

    private Player player(String name) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        players.put(id, player);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.getPlayer()).thenReturn(player);
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenAnswer(call -> {
            InventoryView view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(open.get(id));
            return view;
        });
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            open.put(id, call.getArgument(0));
            return player.getOpenInventory();
        });
        doAnswer(call -> {
            Inventory closed = open.remove(id);
            if (closed != null) {
                InventoryCloseEvent event = mock(InventoryCloseEvent.class);
                when(event.getPlayer()).thenReturn(player);
                when(event.getInventory()).thenReturn(closed);
                menu.onClose(event);
            }
            return null;
        }).when(player).closeInventory();
        return player;
    }

    private InventoryClickEvent event(Player player, int slot, ClickType type) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        InventoryView view = player.getOpenInventory();
        when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(type);
        return event;
    }

    private void click(Player player, int slot) {
        menu.onClick(event(player, slot, ClickType.LEFT));
        runTasks();
    }

    private void runTasks() {
        while (!tasks.isEmpty()) tasks.remove().run();
    }
}
