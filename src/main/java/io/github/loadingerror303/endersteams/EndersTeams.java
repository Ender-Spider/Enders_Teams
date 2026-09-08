package io.github.loadingerror303.endersteams;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

public final class EndersTeams extends JavaPlugin {
    private TeamCreationMenu creationMenu;
    private TeamInviteMenu inviteMenu;

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
        TeamInvitations invitations = new TeamInvitations(teams, java.time.Clock.systemUTC());
        inviteMenu = new TeamInviteMenu(this, teams, invitations);
        getServer().getPluginManager().registerEvents(creationMenu, this);
        getServer().getPluginManager().registerEvents(inviteMenu, this);
        getServer().getScheduler().runTaskTimer(this, invitations::expire, 1200L, 1200L);
        var command = Objects.requireNonNull(getCommand("team"));
        TeamCommand handler = new TeamCommand(this, teams, creationMenu, inviteMenu, invitations);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    @Override
    public void onDisable() {
        if (inviteMenu != null) {
            inviteMenu.closeAll();
        }
        if (creationMenu != null) {
            creationMenu.closeAll();
        }
    }
}
