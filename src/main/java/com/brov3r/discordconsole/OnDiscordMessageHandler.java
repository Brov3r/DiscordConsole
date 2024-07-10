package com.brov3r.discordconsole;

import com.brov3r.discordapi.events.OnDiscordMessage;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.rest.util.Permission;
import zombie.network.GameServer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles Discord message events and sends commands to the GameServer.
 * Messages received in the specified Discord channel are checked for administrator
 * permissions before adding them to the GameServer's command queue.
 */
public class OnDiscordMessageHandler extends OnDiscordMessage {
    /**
     * Handles the incoming Discord message event.
     * Checks the channel ID against the configured chatID and verifies the author's permissions.
     * If the author is an administrator, adds the message content to the GameServer's command queue.
     * Otherwise, reacts to the Discord message with a corresponding emoji.
     *
     * @param event The event object representing the incoming Discord message.
     */
    @Override
    public void handleEvent(MessageCreateEvent event) {
        // Retrieve the chatID from the configuration
        String chatID = Main.getInstance().getDefaultConfig().getString("chatID");

        // Check if the message is from the correct channel
        if (!event.getMessage().getChannelId().equals(Snowflake.of(chatID))) return;

        // Retrieve the member object of the author
        Member authorMember = event.getMember().orElse(null);
        if (authorMember == null) return;

        List<String> rolesID = Main.getInstance().getDefaultConfig().getStringList("cmdRoleWhiteList");

        // Check if the author has administrator permissions
        if (authorMember.getBasePermissions().blockOptional().map(permissions -> permissions.contains(Permission.ADMINISTRATOR)).orElse(false)
                || rolesIntersect(authorMember, rolesID)) {
            Main.getDiscordAPI().addReaction(event.getMessage().getChannelId().asString(), event.getMessage().getId().asString(), "\u2705");
        } else {
            Main.getDiscordAPI().addReaction(event.getMessage().getChannelId().asString(), event.getMessage().getId().asString(), "\u274C");
            return;
        }

        String messageContent = event.getMessage().getContent();

        try {
            // Use reflection to access the GameServer's consoleCommands field
            Field consoleCommandsField = GameServer.class.getDeclaredField("consoleCommands");
            consoleCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ArrayList<String> consoleCommands = (ArrayList<String>) consoleCommandsField.get(null);
            // Add the message content to the consoleCommands list
            consoleCommands.add(messageContent);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Main.getDiscordAPI().addReaction(event.getMessage().getChannelId().asString(), event.getMessage().getId().asString(), "\u274C");
            System.out.println("[!] Error while sending a command from Discord to the server: " + e.getMessage());
        }
    }

    /**
     * Method to check if the author has any role from the whitelist.
     * Returns true if the author's role IDs intersect with the specified rolesID list.
     * Returns false if the rolesID list is empty.
     *
     * @param member  The Discord member whose roles are to be checked.
     * @param rolesID The list of role IDs to check against.
     * @return true if the member has any role from the whitelist, false otherwise.
     */
    private boolean rolesIntersect(Member member, List<String> rolesID) {
        if (rolesID == null || rolesID.isEmpty()) {
            return false;
        }

        return member.getRoleIds().stream()
                .map(Snowflake::asString)
                .anyMatch(rolesID::contains);
    }
}