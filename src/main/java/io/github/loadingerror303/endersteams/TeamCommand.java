package io.github.loadingerror303.endersteams;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

final class TeamCommand implements TabExecutor {
    private final EndersTeams plugin;
    private final TeamStore teams;
    private final TeamCreationMenu creation;
    private final TeamInviteMenu menu;
    private final TeamInvitations invitations;
    private final TeamManagementMenu management;

    TeamCommand(EndersTeams plugin, TeamStore teams, TeamCreationMenu creation,
                TeamInviteMenu menu, TeamInvitations invitations, TeamManagementMenu management) {
        this.plugin = plugin;
        this.teams = teams;
        this.creation = creation;
        this.menu = menu;
        this.invitations = invitations;
        this.management = management;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use team commands.");
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "menu" -> {
                    if (args.length == 1) management.open(player, 0);
                    else usage(player);
                }
                case "create" -> {
                    if (!player.hasPermission("endersteams.create")) {
                        throw new IllegalArgumentException("You do not have permission to create a team.");
                    }
                    return creation.onCommand(sender, command, label, args);
                }
                case "invite" -> {
                    if (args.length == 1) {
                        menu.open(player, 0);
                    } else {
                        usage(player);
                    }
                }
                case "accept", "decline" -> {
                    if (args.length > 2) {
                        usage(player);
                    } else {
                        respond(player, action, args.length == 2 ? args[1] : null);
                    }
                }
                default -> usage(player);
            }
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save accepted team invitation", exception);
            player.sendMessage(Component.text("Could not save your membership. Please try accepting again.", NamedTextColor.RED));
        }
        return true;
    }

    private void respond(Player player, String action, String token) throws IOException {
        UUID id;
        if (token == null) {
            var pending = invitations.pending(player.getUniqueId());
            if (pending.isEmpty()) {
                throw new IllegalArgumentException("You have no pending team invitations.");
            }
            if (pending.size() > 1) {
                player.sendMessage(Component.text("You have multiple invitations. Choose a team's button:", NamedTextColor.YELLOW));
                for (var invite : pending) {
                    Team team = teams.teamFor(invite.sender());
                    if (team != null) {
                        InvitationMessage.send(player, team, invite);
                    }
                }
                return;
            }
            id = pending.getFirst().id();
        } else {
            try {
                id = UUID.fromString(token);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid invitation. Use the invitation's chat buttons.");
            }
        }
        if (action.equals("accept")) {
            Team team = invitations.accept(player.getUniqueId(), id);
            player.closeInventory();
            player.sendMessage(Component.text("You joined " + team.name() + "!", NamedTextColor.GREEN));
            Player owner = Bukkit.getPlayer(team.owner());
            if (owner != null) {
                owner.sendMessage(Component.text(player.getName() + " joined your team!", NamedTextColor.GREEN));
            }
        } else {
            var invite = invitations.decline(player.getUniqueId(), id);
            player.sendMessage(Component.text("Invitation declined.", NamedTextColor.YELLOW));
            Player owner = Bukkit.getPlayer(invite.sender());
            if (owner != null) {
                owner.sendMessage(Component.text(player.getName() + " declined your team invitation.", NamedTextColor.YELLOW));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        return List.of("create", "invite", "accept", "decline", "menu").stream()
                .filter(action -> action.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }

    private static void usage(Player player) {
        player.sendMessage(Component.text("Use /team menu, /team create, /team invite, /team accept or /team decline.", NamedTextColor.YELLOW));
    }
}
