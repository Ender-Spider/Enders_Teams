package io.github.loadingerror303.endersteams;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class TeamChatColors implements Listener {
    private final TeamStore teams;

    public TeamChatColors(TeamStore teams) {
        this.teams = teams;
    }

    // Read only the immutable color snapshot; the mutable team store belongs to the server thread.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) return;
        NamedTextColor color = teams.chatColorFor(event.getPlayer().getUniqueId());
        if (color == null) return;

        // Capture immutable values before the renderer runs; never query team state from it.
        Component name = Component.text(event.getPlayer().getName(), color);
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) ->
                Component.empty().append(name)
                        .append(Component.text(": ", NamedTextColor.WHITE))
                        .append(message)));
    }
}
