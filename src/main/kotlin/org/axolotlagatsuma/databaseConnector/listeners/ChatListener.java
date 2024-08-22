package org.axolotlagatsuma.databaseConnector.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {

    @EventHandler
    public void onChat(AsyncChatEvent) {
        String message = event.getMessage();

        message = PlaceholderAPI.setPlaceholders(event.getPlayer(), message);

        event.setMessage(message);
    }

}
