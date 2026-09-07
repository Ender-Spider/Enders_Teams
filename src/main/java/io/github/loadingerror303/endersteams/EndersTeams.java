package io.github.loadingerror303.endersteams;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

public final class EndersTeams extends JavaPlugin {
    private TeamCreationMenu creationMenu;

    @Override
    public void onEnable() {
        TeamStore teams;
        try {
            teams = new TeamStore(getDataFolder().toPath().resolve("teams.yml"));
        } catch (IOException exception) {
            getLogger().log(Level.SEVERE, "Could not load teams. Disabling to protect saved data.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        creationMenu = new TeamCreationMenu(this, teams);
        getServer().getPluginManager().registerEvents(creationMenu, this);
        var command = Objects.requireNonNull(getCommand("team"));
        command.setExecutor(creationMenu);
        command.setTabCompleter(creationMenu);
    }

    @Override
    public void onDisable() {
        if (creationMenu != null) {
            creationMenu.closeAll();
        }
    }
}
