package io.github.loadingerror303.endersteams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

final class InvitationMessage {
    private InvitationMessage() { }

    static void send(Player recipient, Team team, TeamInvitations.Invitation invite) {
        recipient.sendMessage(Component.text("You have been invited to ", NamedTextColor.YELLOW)
                .append(Component.text(team.name(), team.icon().color()))
                .append(Component.text(". Expires after 5 minutes.", NamedTextColor.YELLOW)));
        recipient.sendMessage(button("[Accept]", "accept", NamedTextColor.GREEN, invite)
                .append(Component.text("  "))
                .append(button("[Decline]", "decline", NamedTextColor.RED, invite)));
        recipient.sendMessage(Component.text("Click a button or use /team accept or /team decline.", NamedTextColor.GRAY));
    }

    private static Component button(String text, String action, NamedTextColor color,
                                    TeamInvitations.Invitation invite) {
        return Component.text(text, color)
                .clickEvent(ClickEvent.runCommand("/endersteams:team " + action + " " + invite.id()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to " + action + " this invitation")));
    }
}
