package io.github.loadingerror303.endersteams;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

final class AdminTeamCommand {
    private record Confirmation(UUID token, Team team, Instant expires) { }
    private final EndersTeams plugin;
    private final TeamStore teams;
    private final TeamInvitations invitations;
    private final TeamManagementMenu menus;
    private final Clock clock;
    private final Map<String, Confirmation> confirmations = new HashMap<>();

    AdminTeamCommand(EndersTeams plugin, TeamStore teams, TeamInvitations invitations,
                     TeamManagementMenu menus, Clock clock) {
        this.plugin = plugin;
        this.teams = teams;
        this.invitations = invitations;
        this.menus = menus;
        this.clock = clock;
    }

    boolean execute(CommandSender sender, String[] args) {
        try {
            if (!(sender instanceof Player || sender instanceof ConsoleCommandSender)
                    || !sender.hasPermission("endersteams.admin.force")) {
                throw new IllegalArgumentException("You do not have permission to force team changes.");
            }
            confirmations.values().removeIf(pending -> !pending.expires().isAfter(clock.instant()));
            if (args.length < 2) { usage(sender); return true; }
            String action = args[1].toLowerCase(Locale.ROOT);
            switch (action) {
                case "join" -> {
                    if (args.length < 4) { usage(sender); break; }
                    OfflinePlayer target = player(args[2]);
                    Team destination = team(args, 3);
                    Team previous = teams.teamFor(target.getUniqueId());
                    Team joined = teams.forceJoin(target.getUniqueId(), destination.id());
                    String message = sender.getName() + " moved " + target.getName() + " to " + joined.name() + ".";
                    audit(sender, "join player=" + target.getUniqueId() + " from="
                            + (previous == null ? "none" : previous.id()) + " to=" + joined.id());
                    changed(target.getUniqueId(), previous, joined, message);
                    success(sender, message);
                }
                case "leave", "owner" -> {
                    if (args.length != 3) { usage(sender); break; }
                    OfflinePlayer target = player(args[2]);
                    Team previous = teams.teamFor(target.getUniqueId());
                    Team updated = action.equals("leave") ? teams.forceLeave(target.getUniqueId())
                            : teams.forceOwner(target.getUniqueId());
                    String message = action.equals("leave")
                            ? sender.getName() + " removed " + target.getName() + " from " + updated.name() + "."
                            : sender.getName() + " made " + target.getName() + " the owner of " + updated.name() + ".";
                    audit(sender, action + " player=" + target.getUniqueId() + " team=" + updated.id());
                    changed(target.getUniqueId(), previous, updated, message);
                    success(sender, message);
                }
                case "disband" -> {
                    if (args.length < 3) { usage(sender); break; }
                    Team team = team(args, 2);
                    Confirmation confirmation = new Confirmation(UUID.randomUUID(), team, clock.instant().plusSeconds(60));
                    confirmations.put(key(sender), confirmation);
                    String command = "/endersteams:team force confirm " + confirmation.token();
                    sender.sendMessage(Component.text("Disband " + team.name() + " and remove all " + team.members().size()
                            + " memberships? Confirm within 60 seconds: " + command, NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("[Confirm Disband]", NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand(command))
                            .append(Component.text("  [Cancel]", NamedTextColor.GRAY)
                                    .clickEvent(ClickEvent.runCommand("/endersteams:team force cancel " + confirmation.token()))));
                }
                case "confirm", "cancel" -> {
                    if (args.length != 3) { usage(sender); break; }
                    Confirmation pending = confirmations.get(key(sender));
                    if (pending == null || !pending.token().toString().equals(args[2])) {
                        throw new IllegalArgumentException("That confirmation has expired or does not belong to you.");
                    }
                    if (action.equals("cancel")) {
                        confirmations.remove(key(sender));
                        success(sender, "Disband cancelled.");
                        break;
                    }
                    if (!pending.team().equals(teams.byId(pending.team().id()))) {
                        confirmations.remove(key(sender));
                        throw new IllegalArgumentException("The team changed. Run /team force disband <team name> again.");
                    }
                    Team removed = teams.forceDisband(pending.team().id());
                    confirmations.remove(key(sender));
                    audit(sender, "disband team=" + removed.id() + " name=" + removed.name());
                    String message = sender.getName() + " disbanded " + removed.name() + ".";
                    changed(null, removed, null, message);
                    success(sender, message);
                }
                default -> usage(sender);
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save forced team change requested by " + key(sender), exception);
            sender.sendMessage(Component.text("Could not save the team change. No change was applied; please try again.", NamedTextColor.RED));
        }
        return true;
    }

    private Team team(String[] args, int from) {
        String name = String.join(" ", Arrays.copyOfRange(args, from, args.length));
        Team team = teams.byName(name);
        if (team == null) throw new IllegalArgumentException("No team named " + name + " was found.");
        return team;
    }

    private OfflinePlayer player(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.hasPlayedBefore()) return cached;
        // Restrict offline targets to real server records; never create a profile from a typo.
        for (OfflinePlayer known : Bukkit.getOfflinePlayers()) {
            if (known.getName() != null && known.getName().equalsIgnoreCase(name) && known.hasPlayedBefore()) return known;
        }
        throw new IllegalArgumentException("Player not found. Use the full name of someone who has joined this server.");
    }

    private void changed(UUID target, Team before, Team after, String message) {
        if (target != null) {
            invitations.clearFor(target);
            Player online = Bukkit.getPlayer(target);
            if (online != null) online.closeInventory();
        }
        invitations.expire();
        Set<UUID> recipients = new HashSet<>();
        if (before != null) { recipients.addAll(before.members()); menus.refreshTeam(before.id()); }
        if (after != null) { recipients.addAll(after.members()); menus.refreshTeam(after.id()); }
        for (UUID id : recipients) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) online.sendMessage(Component.text(message, NamedTextColor.YELLOW));
        }
    }

    private void audit(CommandSender sender, String detail) {
        plugin.getLogger().info("[Team Admin] " + key(sender) + " " + detail);
    }

    private String key(CommandSender sender) {
        return sender instanceof Player player ? player.getName() + " (" + player.getUniqueId() + ")" : "console";
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text("Use /team force join <player> <team name>, /team force leave <player>, "
                + "/team force owner <player>, or /team force disband <team name>.", NamedTextColor.YELLOW));
    }
}
