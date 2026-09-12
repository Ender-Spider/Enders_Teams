package io.github.loadingerror303.endersteams;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class TeamChatColorsTest {
    @TempDir Path directory;
    private TeamStore teams;
    private TeamChatColors listener;
    private Player player;
    private UUID id;

    @BeforeEach void setup() throws IOException {
        teams = new TeamStore(directory.resolve("teams.yml"));
        listener = new TeamChatColors(teams);
        player = mock(Player.class);
        id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("EnderPlayer");
    }

    @Test void eachBlockColorsOnlyTheNameAndPreservesMessageComponent() throws IOException {
        for (TeamIcon icon : TeamIcon.selectableValues()) {
            Team team = teams.create(id, "Miners", icon);
            Component message = Component.text("Hello ").append(Component.text("world", NamedTextColor.GREEN));
            AsyncChatEvent event = event();
            listener.onChat(event);
            Component rendered = render(event, message);
            assertEquals(Component.empty().append(Component.text("EnderPlayer", icon.color()))
                    .append(Component.text(": ", NamedTextColor.WHITE)).append(message), rendered);
            assertSame(message, rendered.children().getLast());
            assertNull(rendered.color());
            verify(event, never()).message(any(Component.class));
            verify(event, never()).setCancelled(anyBoolean());
            teams.disband(id, team.id());
        }
    }

    @Test void unteamedAndCancelledChatKeepExistingRenderer() throws IOException {
        AsyncChatEvent unteamed = event();
        listener.onChat(unteamed);
        verify(unteamed, never()).renderer(any(ChatRenderer.class));
        teams.create(id, "Miners", TeamIcon.AMETHYST);
        AsyncChatEvent cancelled = event();
        when(cancelled.isCancelled()).thenReturn(true);
        listener.onChat(cancelled);
        verify(cancelled, never()).renderer(any(ChatRenderer.class));
    }

    @Test void joiningMovingAndLeavingUpdateTheNextMessage() throws IOException {
        Team diamond = teams.create(UUID.randomUUID(), "Diamonds", TeamIcon.DIAMOND);
        Team amethyst = teams.create(UUID.randomUUID(), "Amethysts", TeamIcon.AMETHYST);
        teams.join(diamond.id(), id);
        AsyncChatEvent joined = event();
        listener.onChat(joined);
        assertEquals(NamedTextColor.AQUA, render(joined, Component.text("Hi")).children().getFirst().color());
        teams.forceJoin(id, amethyst.id());
        AsyncChatEvent moved = event();
        listener.onChat(moved);
        assertEquals(NamedTextColor.LIGHT_PURPLE, render(moved, Component.text("Hi")).children().getFirst().color());
        teams.leave(id);
        AsyncChatEvent left = event();
        listener.onChat(left);
        verify(left, never()).renderer(any(ChatRenderer.class));
    }

    @Test void colorsLoadAfterRestartAndRendererUsesCapturedState() throws IOException {
        Team team = teams.create(id, "Miners", TeamIcon.GOLD);
        listener = new TeamChatColors(new TeamStore(directory.resolve("teams.yml")));
        AsyncChatEvent event = event();
        listener.onChat(event);
        teams.disband(id, team.id());
        assertEquals(NamedTextColor.YELLOW, render(event, Component.text("Hi")).children().getFirst().color());
    }

    private AsyncChatEvent event() {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private Component render(AsyncChatEvent event, Component message) {
        ArgumentCaptor<ChatRenderer> renderer = ArgumentCaptor.forClass(ChatRenderer.class);
        verify(event).renderer(renderer.capture());
        return renderer.getValue().render(player, Component.text("EnderPlayer"), message, Audience.empty());
    }
}
