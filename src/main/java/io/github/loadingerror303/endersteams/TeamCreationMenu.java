package io.github.loadingerror303.endersteams;

import java.io.IOException;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

public final class TeamCreationMenu implements Listener, TabExecutor {
    private static final int[] ICON_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23};
    private final EndersTeams plugin;
    private final TeamStore teams;
    private final Map<UUID, Screen> screens = new HashMap<>();

    private enum Step { ICON, NAME, CONFIRM }

    private static final class Screen {
        final Step step;
        final Inventory inventory;
        final TeamIcon icon;
        final String name;
        boolean pending;

        Screen(Step step, Inventory inventory, TeamIcon icon, String name) {
            this.step = step;
            this.inventory = inventory;
            this.icon = icon;
            this.name = name;
        }
    }

    public TeamCreationMenu(EndersTeams plugin, TeamStore teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the team menu.");
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("create")) {
            player.sendMessage(Component.text("Use /team create to create a team.", NamedTextColor.YELLOW));
            return true;
        }
        if (teams.teamFor(player.getUniqueId()) != null) {
            error(player, "You already belong to a team.");
            return true;
        }
        openIcons(player, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && "create".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))
                ? List.of("create") : List.of();
    }

    private void openIcons(Player player, String name) {
        Inventory inventory = Bukkit.createInventory(null, 45, Component.text("Create Team: Choose Icon"));
        fill(inventory);
        TeamIcon[] icons = TeamIcon.values();
        for (int index = 0; index < icons.length; index++) {
            TeamIcon icon = icons[index];
            inventory.setItem(ICON_SLOTS[index], item(icon.material(), icon.label(), icon.color(),
                    "Choose this team icon", "Next: enter your team name"));
        }
        inventory.setItem(40, item(Material.BARRIER, "Cancel", NamedTextColor.RED, "Close without creating a team"));
        player.closeInventory();
        screens.put(player.getUniqueId(), new Screen(Step.ICON, inventory, null, name));
        player.openInventory(inventory);
    }

    private void openName(Player player, TeamIcon icon, String name) {
        AnvilView view = MenuType.ANVIL.builder().title(Component.text("Create Team: Enter Name"))
                .checkReachable(false).build(player);
        player.closeInventory();
        screens.put(player.getUniqueId(), new Screen(Step.NAME, view.getTopInventory(), icon, name));
        view.open();
        view.getTopInventory().setItem(0, item(Material.PAPER, name == null ? "Team Name" : name,
                NamedTextColor.WHITE, "Type a name in the field above", "Click the result to continue"));
        view.setRepairCost(0);
        view.setRepairItemCountCost(0);
        player.sendMessage(Component.text("Enter a team name (3-24 characters), then click the anvil result. No XP needed.",
                NamedTextColor.YELLOW));
    }

    private void openConfirmation(Player player, TeamIcon icon, String name) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Create Team: Confirm"));
        fill(inventory);
        inventory.setItem(10, item(Material.ARROW, "Change Icon", NamedTextColor.YELLOW, "Return to icon selection"));
        inventory.setItem(12, item(Material.NAME_TAG, "Change Name", NamedTextColor.YELLOW, "Edit " + name));
        inventory.setItem(13, item(icon.material(), name, icon.color(), "Icon: " + icon.label(),
                "Owner: " + player.getName()));
        inventory.setItem(16, item(Material.LIME_CONCRETE, "Create Team", NamedTextColor.GREEN,
                "Confirm your icon and name"));
        inventory.setItem(22, item(Material.BARRIER, "Cancel", NamedTextColor.RED, "Close without creating a team"));
        player.closeInventory();
        screens.put(player.getUniqueId(), new Screen(Step.CONFIRM, inventory, icon, name));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        Screen screen = current(event.getView().getPlayer().getUniqueId(), event.getInventory());
        if (screen == null || screen.step != Step.NAME) {
            return;
        }
        event.getView().setRepairCost(0);
        event.getView().setRepairItemCountCost(0);
        try {
            String name = teams.validateName(event.getView().getRenameText());
            event.setResult(item(screen.icon.material(), name, screen.icon.color(), "Click to preview your team"));
        } catch (IllegalArgumentException exception) {
            event.setResult(item(Material.BARRIER, "Invalid Team Name", NamedTextColor.RED, exception.getMessage()));
        }
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
        // Cancel every inventory action, including shift-clicks, hotbar swaps and drops.
        event.setCancelled(true);
        if (screen.pending || (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                && event.getClick() != org.bukkit.event.inventory.ClickType.RIGHT)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= screen.inventory.getSize()) {
            return;
        }
        switch (screen.step) {
            case ICON -> {
                if (slot == 40) {
                    later(player, screen, player::closeInventory);
                    return;
                }
                for (int index = 0; index < ICON_SLOTS.length; index++) {
                    if (ICON_SLOTS[index] == slot) {
                        TeamIcon icon = TeamIcon.values()[index];
                        later(player, screen, () -> openName(player, icon, screen.name));
                        break;
                    }
                }
            }
            case NAME -> {
                if (slot != 2 || !(event.getView() instanceof AnvilView view)) {
                    return;
                }
                try {
                    String name = teams.validateName(view.getRenameText());
                    later(player, screen, () -> openConfirmation(player, screen.icon, name));
                } catch (IllegalArgumentException exception) {
                    error(player, exception.getMessage());
                }
            }
            case CONFIRM -> {
                switch (slot) {
                    case 10 -> later(player, screen, () -> openIcons(player, screen.name));
                    case 12 -> later(player, screen, () -> openName(player, screen.icon, screen.name));
                    case 16 -> later(player, screen, () -> create(player, screen));
                    case 22 -> later(player, screen, player::closeInventory);
                    default -> { }
                }
            }
        }
    }

    private void create(Player player, Screen screen) {
        if (!player.hasPermission("endersteams.create")) {
            error(player, "You no longer have permission to create a team.");
            player.closeInventory();
            return;
        }
        try {
            Team team = teams.create(player.getUniqueId(), screen.name, screen.icon);
            player.closeInventory();
            player.sendMessage(Component.text("Created team ", NamedTextColor.GREEN)
                    .append(Component.text(team.name(), team.icon().color()))
                    .append(Component.text(" with the " + team.icon().label() + " icon!", NamedTextColor.GREEN)));
        } catch (IllegalArgumentException exception) {
            error(player, exception.getMessage());
            if (teams.teamFor(player.getUniqueId()) != null) {
                player.closeInventory();
            } else {
                openName(player, screen.icon, screen.name);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save a new team", exception);
            error(player, "Your team could not be saved. Please try again or contact an administrator.");
        }
    }

    // Bukkit requires opening/closing inventories outside the inventory click transaction.
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
        Screen screen = current(event.getPlayer().getUniqueId(), event.getInventory());
        if (screen != null) {
            screens.remove(event.getPlayer().getUniqueId());
            // Clear virtual anvil inputs before vanilla returns them to the player.
            screen.inventory.clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Screen screen = screens.remove(event.getPlayer().getUniqueId());
        if (screen != null) {
            screen.inventory.clear();
        }
    }

    public void closeAll() {
        for (UUID id : List.copyOf(screens.keySet())) {
            Screen screen = screens.get(id);
            screen.inventory.clear();
            Player player = Bukkit.getPlayer(id);
            if (player != null && current(id, player.getOpenInventory().getTopInventory()) == screen) {
                player.closeInventory();
            }
        }
        screens.clear();
    }

    private Screen current(UUID player, Inventory inventory) {
        Screen screen = screens.get(player);
        return screen != null && screen.inventory.equals(inventory) ? screen : null;
    }

    private static void fill(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        }
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
