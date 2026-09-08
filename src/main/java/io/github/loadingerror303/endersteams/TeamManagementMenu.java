package io.github.loadingerror303.endersteams;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class TeamManagementMenu implements Listener {
    private enum Action { ROSTER, MEMBER, KICK, TRANSFER, DISBAND }

    private static final class Screen {
        final Inventory inventory;
        final UUID teamId;
        final Action action;
        final UUID target;
        final InvitePage page;
        boolean pending;

        Screen(Inventory inventory, UUID teamId, Action action, UUID target, InvitePage page) {
            this.inventory = inventory;
            this.teamId = teamId;
            this.action = action;
            this.target = target;
            this.page = page;
        }
    }

    private final EndersTeams plugin;
    private final TeamStore teams;
    private final TeamInvitations invitations;
    private final TeamInviteMenu invites;
    private final Map<UUID, Screen> screens = new HashMap<>();

    public TeamManagementMenu(EndersTeams plugin, TeamStore teams, TeamInvitations invitations, TeamInviteMenu invites) {
        this.plugin = plugin;
        this.teams = teams;
        this.invitations = invitations;
        this.invites = invites;
    }

    public void open(Player player, int requestedPage) {
        Team team = teams.teamFor(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("You are not on a team. Use /team create or accept an invitation.", NamedTextColor.RED));
            return;
        }
        boolean owner = team.owner().equals(player.getUniqueId());
        if (!owner) {
            if (current(player.getUniqueId(), player.getOpenInventory().getTopInventory()) != null) player.closeInventory();
            player.sendMessage(Component.text("Only the team owner can access the team menu.", NamedTextColor.RED));
            return;
        }
        List<UUID> members = team.members().stream()
                .sorted(Comparator.<UUID, Boolean>comparing(id -> !id.equals(team.owner()))
                        .thenComparing(TeamManagementMenu::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Comparator.naturalOrder())).toList();
        InvitePage page = InvitePage.of(members, requestedPage);
        Inventory inventory = inventory(54, "Team Menu: " + (page.index() + 1) + "/" + page.count());
        for (int slot = 0; slot < page.players().size(); slot++) {
            UUID member = page.players().get(slot);
            inventory.setItem(slot, head(member,
                    member.equals(team.owner()) ? "Team Owner" : "Member",
                    Bukkit.getPlayer(member) == null ? "Offline" : "Online",
                    owner && !member.equals(team.owner()) ? "Click to manage this member" : team.name()));
        }
        if (page.hasPrevious()) inventory.setItem(45, item(Material.ARROW, "Previous Page"));
        if (owner) inventory.setItem(46, item(Material.PLAYER_HEAD, "Invite Players"));
        inventory.setItem(47, item(team.friendlyFire() ? Material.REDSTONE_TORCH : Material.LEVER,
                "Friendly Fire: " + (team.friendlyFire() ? "ON" : "OFF"),
                owner ? "Click to toggle teammate damage" : "Only the owner can change this"));
        inventory.setItem(48, item(team.icon().material(), team.name(),
                "Owner: " + name(team.owner()), "Members: " + members.size()));
        inventory.setItem(49, item(Material.BARRIER, "Close"));
        inventory.setItem(50, item(Material.SUNFLOWER, "Refresh"));
        if (owner) inventory.setItem(51, item(Material.TNT, "Disband Team", "Requires confirmation"));
        if (page.hasNext()) inventory.setItem(53, item(Material.ARROW, "Next Page"));
        show(player, new Screen(inventory, team.id(), Action.ROSTER, null, page));
    }

    private void member(Player player, Screen previous, UUID target) {
        Team team = requireCurrentOwner(player, previous);
        if (!team.members().contains(target) || team.owner().equals(target)) {
            throw new IllegalArgumentException("Choose another current member of your team.");
        }
        Inventory inventory = inventory(27, "Manage Team Member");
        inventory.setItem(13, head(target, "Member of " + team.name()));
        inventory.setItem(11, item(Material.IRON_BOOTS, "Kick Member", "Remove " + name(target) + " from the team"));
        inventory.setItem(15, item(Material.GOLDEN_HELMET, "Transfer Ownership", "Make " + name(target) + " the owner"));
        inventory.setItem(22, item(Material.ARROW, "Back"));
        show(player, new Screen(inventory, team.id(), Action.MEMBER, target, previous.page));
    }

    private void confirm(Player player, Screen previous, Action action) {
        Team team = requireCurrentOwner(player, previous);
        String title = switch (action) {
            case KICK -> "Kick " + name(previous.target) + "?";
            case TRANSFER -> "Transfer Team Ownership?";
            case DISBAND -> "Disband " + team.name() + "?";
            default -> throw new IllegalArgumentException("Invalid management action.");
        };
        Inventory inventory = inventory(27, "Confirm Team Action");
        inventory.setItem(13, item(action == Action.DISBAND ? Material.TNT : Material.PAPER, title,
                action == Action.TRANSFER ? "You will become a regular member." : "This requires your confirmation."));
        inventory.setItem(11, item(Material.LIME_CONCRETE, "Confirm"));
        inventory.setItem(15, item(Material.RED_CONCRETE, "Cancel"));
        show(player, new Screen(inventory, team.id(), action, previous.target, previous.page));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Screen screen = current(player.getUniqueId(), event.getView().getTopInventory());
        if (screen == null) return;
        event.setCancelled(true);
        if (screen.pending || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= screen.inventory.getSize()) return;
        screen.pending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || current(player.getUniqueId(), player.getOpenInventory().getTopInventory()) != screen) return;
            screen.pending = false;
            try {
                Team team = teams.teamFor(player.getUniqueId());
                if (team == null || !team.id().equals(screen.teamId) || !team.owner().equals(player.getUniqueId())) {
                    player.closeInventory();
                    throw new IllegalArgumentException("Your team has changed. Reopen /team menu.");
                }
                click(player, screen, slot);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not save team management change", exception);
                player.sendMessage(Component.text("Could not save the change. Please try again.", NamedTextColor.RED));
            }
        });
    }

    private void click(Player player, Screen screen, int slot) throws IOException {
        if (screen.action == Action.ROSTER) {
            if (slot < screen.page.players().size()) {
                member(player, screen, screen.page.players().get(slot));
            } else {
                switch (slot) {
                    case 45 -> { if (screen.page.hasPrevious()) open(player, screen.page.index() - 1); }
                    case 46 -> { requireCurrentOwner(player, screen); invites.open(player, 0); }
                    case 47 -> {
                        Team team = requireCurrentOwner(player, screen);
                        teams.setFriendlyFire(player.getUniqueId(), screen.teamId, !team.friendlyFire());
                        announce(team, "Friendly fire is now " + (!team.friendlyFire() ? "ON" : "OFF") + ".");
                        refreshTeam(screen.teamId);
                    }
                    case 49 -> player.closeInventory();
                    case 50 -> open(player, screen.page.index());
                    case 51 -> confirm(player, screen, Action.DISBAND);
                    case 53 -> { if (screen.page.hasNext()) open(player, screen.page.index() + 1); }
                    default -> { }
                }
            }
        } else if (screen.action == Action.MEMBER) {
            switch (slot) {
                case 11 -> confirm(player, screen, Action.KICK);
                case 15 -> confirm(player, screen, Action.TRANSFER);
                case 22 -> open(player, screen.page.index());
                default -> { }
            }
        } else if (slot == 15) {
            open(player, screen.page.index());
        } else if (slot == 11) {
            Team before = requireCurrentOwner(player, screen);
            switch (screen.action) {
                case KICK -> {
                    teams.kick(player.getUniqueId(), screen.teamId, screen.target);
                    announce(before, name(screen.target) + " was removed from " + before.name() + ".");
                }
                case TRANSFER -> {
                    teams.transferOwnership(player.getUniqueId(), screen.teamId, screen.target);
                    announce(before, name(screen.target) + " is now the owner of " + before.name() + ".");
                }
                case DISBAND -> {
                    teams.disband(player.getUniqueId(), screen.teamId);
                    announce(before, before.name() + " has been disbanded.");
                }
                default -> { }
            }
            invitations.expire();
            refreshTeam(screen.teamId);
        }
    }

    private Team requireCurrentOwner(Player player, Screen screen) {
        Team team = teams.ownedBy(player.getUniqueId());
        if (!team.id().equals(screen.teamId)) {
            throw new IllegalArgumentException("Your team has changed. Reopen /team menu.");
        }
        return team;
    }

    void refreshTeam(UUID teamId) {
        for (UUID id : List.copyOf(screens.keySet())) {
            Screen screen = screens.get(id);
            if (!screen.teamId.equals(teamId)) continue;
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            Team team = teams.teamFor(id);
            if (team == null || !team.id().equals(teamId) || !team.owner().equals(id)) player.closeInventory();
            else open(player, screen.page.index());
        }
    }

    private void show(Player player, Screen screen) {
        player.closeInventory();
        screens.put(player.getUniqueId(), screen);
        player.openInventory(screen.inventory);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (current(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (current(event.getPlayer().getUniqueId(), event.getInventory()) != null) screens.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { screens.remove(event.getPlayer().getUniqueId()); }

    public void closeAll() {
        for (UUID id : List.copyOf(screens.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && current(id, player.getOpenInventory().getTopInventory()) != null) player.closeInventory();
        }
        screens.clear();
    }

    private Screen current(UUID player, Inventory inventory) {
        Screen screen = screens.get(player);
        return screen != null && screen.inventory.equals(inventory) ? screen : null;
    }

    private static Inventory inventory(int size, String title) {
        Inventory inventory = Bukkit.createInventory(null, size, Component.text(title));
        for (int slot = 0; slot < size; slot++) inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " "));
        return inventory;
    }

    private static ItemStack head(UUID id, String... lore) {
        OfflinePlayer member = Bukkit.getOfflinePlayer(id);
        ItemStack item = item(Material.PLAYER_HEAD, name(id), lore);
        item.editMeta(SkullMeta.class, meta -> {
            Player online = member.getPlayer();
            if (online != null) meta.setPlayerProfile(online.getPlayerProfile());
            else meta.setOwningPlayer(member);
        });
        return item;
    }

    private static String name(UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        return name == null ? id.toString() : name;
    }

    private static ItemStack item(Material material, String label, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(label, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.Arrays.stream(lore)
                    .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    private static void announce(Team team, String message) {
        for (UUID id : team.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null) member.sendMessage(Component.text(message, NamedTextColor.YELLOW));
        }
    }
}
