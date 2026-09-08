package io.github.loadingerror303.endersteams;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public final class TeamInviteMenu implements Listener {
    private final EndersTeams plugin;
    private final TeamStore teams;
    private final TeamInvitations invitations;
    private final Map<UUID, Screen> screens = new HashMap<>();

    private static final class Screen {
        final Inventory inventory;
        final InvitePage page;
        boolean pending;

        Screen(Inventory inventory, InvitePage page) {
            this.inventory = inventory;
            this.page = page;
        }
    }

    public TeamInviteMenu(EndersTeams plugin, TeamStore teams, TeamInvitations invitations) {
        this.plugin = plugin;
        this.teams = teams;
        this.invitations = invitations;
    }

    public void open(Player viewer, int requestedPage) {
        try {
            requireOwner(viewer);
        } catch (IllegalArgumentException exception) {
            error(viewer, exception.getMessage());
            return;
        }
        List<UUID> online = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Player::getUniqueId))
                .map(Player::getUniqueId).toList();
        InvitePage page = InvitePage.of(online, requestedPage);
        Inventory inventory = Bukkit.createInventory(null, 54,
                Component.text("Invite Players: " + (page.index() + 1) + "/" + page.count()));
        for (int slot = 0; slot < page.players().size(); slot++) {
            Player target = Bukkit.getPlayer(page.players().get(slot));
            if (target != null) {
                ItemStack head = item(Material.PLAYER_HEAD, target.getName(), NamedTextColor.WHITE,
                        status(viewer, target));
                head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(target.getPlayerProfile()));
                inventory.setItem(slot, head);
            }
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        }
        if (page.hasPrevious()) {
            inventory.setItem(45, item(Material.ARROW, "Previous Page", NamedTextColor.YELLOW));
        }
        inventory.setItem(48, item(Material.PAPER, "Page " + (page.index() + 1) + " of " + page.count(),
                NamedTextColor.WHITE, online.size() + " players online"));
        inventory.setItem(49, item(Material.BARRIER, "Close", NamedTextColor.RED));
        inventory.setItem(50, item(Material.SUNFLOWER, "Refresh", NamedTextColor.GREEN,
                "Update the online player list"));
        if (page.hasNext()) {
            inventory.setItem(53, item(Material.ARROW, "Next Page", NamedTextColor.YELLOW));
        }
        viewer.closeInventory();
        screens.put(viewer.getUniqueId(), new Screen(inventory, page));
        viewer.openInventory(inventory);
    }

    private String status(Player viewer, Player target) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return "This is you";
        }
        if (teams.teamFor(target.getUniqueId()) != null) {
            return "Already on a team";
        }
        if (invitations.pending(target.getUniqueId()).stream()
                .anyMatch(invite -> invite.sender().equals(viewer.getUniqueId()))) {
            return "Invitation already sent";
        }
        return "Click to invite to your team";
    }

    private void requireOwner(Player player) {
        if (!player.hasPermission("endersteams.invite")) {
            throw new IllegalArgumentException("You do not have permission to invite players.");
        }
        teams.ownedBy(player.getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Screen screen = current(player.getUniqueId(), event.getView().getTopInventory());
        if (screen == null) {
            return;
        }
        event.setCancelled(true);
        if (screen.pending || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < screen.page.players().size()) {
            UUID recipient = screen.page.players().get(slot);
            later(player, screen, () -> send(player, recipient, screen.page.index()));
        } else if (slot == 45 && screen.page.hasPrevious()) {
            later(player, screen, () -> open(player, screen.page.index() - 1));
        } else if (slot == 53 && screen.page.hasNext()) {
            later(player, screen, () -> open(player, screen.page.index() + 1));
        } else if (slot == 50) {
            later(player, screen, () -> open(player, screen.page.index()));
        } else if (slot == 49) {
            later(player, screen, player::closeInventory);
        }
    }

    private void send(Player sender, UUID recipientId, int page) {
        try {
            requireOwner(sender);
            Player recipient = Bukkit.getPlayer(recipientId);
            if (recipient == null || !recipient.isOnline()) {
                throw new IllegalArgumentException("That player is no longer online.");
            }
            var invitation = invitations.send(sender.getUniqueId(), recipientId);
            InvitationMessage.send(recipient, teams.ownedBy(sender.getUniqueId()), invitation);
            sender.sendMessage(Component.text("Invited " + recipient.getName() + " to your team.", NamedTextColor.GREEN));
        } catch (IllegalArgumentException exception) {
            error(sender, exception.getMessage());
        }
        open(sender, page);
    }

    private void later(Player player, Screen screen, Runnable action) {
        screen.pending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || current(player.getUniqueId(), player.getOpenInventory().getTopInventory()) != screen) {
                return;
            }
            screen.pending = false;
            action.run();
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (current(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (current(event.getPlayer().getUniqueId(), event.getInventory()) != null) {
            screens.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        screens.remove(event.getPlayer().getUniqueId());
    }

    public void closeAll() {
        for (UUID id : List.copyOf(screens.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && current(id, player.getOpenInventory().getTopInventory()) != null) {
                player.closeInventory();
            }
        }
        screens.clear();
    }

    private Screen current(UUID player, Inventory inventory) {
        Screen screen = screens.get(player);
        return screen != null && screen.inventory.equals(inventory) ? screen : null;
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.Arrays.stream(lore)
                    .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        });
        return item;
    }

    private static void error(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
