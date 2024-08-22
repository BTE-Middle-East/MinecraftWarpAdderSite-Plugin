package org.axolotlagatsuma.databaseConnector.hooks;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public class discordsrvhookjava {

    private static final discordsrvhookjava instance = new discordsrvhookjava();

    private discordsrvhookjava() {
    }

    @Subscribe
    public void onMessageReceived(DiscordGuildMessageReceivedEvent event) {
        String playerName = event.getMember().getEffectiveName();
        String channel = event.getChannel().getName();
        String message = event.getMessage().getContentRaw();
        // Broadcasts the message that came from Discord in to the Game
        // Basically Discord -> Minecraft
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes(
                '&',"&7[&bDiscord&7] &f" + playerName + "&7in &f" + channel + "&8 >> &f" + message));
    }

    private static void register() {
        DiscordSRV.api.subscribe(instance);
    }

    private static void unregister() {
        DiscordSRV.api.unsubscribe(instance);
    }
}
