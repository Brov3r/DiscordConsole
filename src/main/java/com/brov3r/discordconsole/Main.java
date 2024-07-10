package com.brov3r.discordconsole;

import com.avrix.events.EventManager;
import com.avrix.plugin.Metadata;
import com.avrix.plugin.Plugin;
import com.avrix.plugin.ServiceManager;
import com.brov3r.discordapi.services.DiscordAPI;

/**
 * Main entry point
 */
public class Main extends Plugin {
    private static Main instance;
    private static DiscordAPI discordAPI;

    /**
     * Constructs a new {@link Plugin} with the specified metadata.
     * Metadata is transferred when the plugin is loaded into the game context.
     *
     * @param metadata The {@link Metadata} associated with this plugin.
     */
    public Main(Metadata metadata) {
        super(metadata);
    }

    /**
     * Called when the plugin is initialized.
     * <p>
     * Implementing classes should override this method to provide the initialization logic.
     */
    @Override
    public void onInitialize() {
        instance = this;
        loadDefaultConfig();

        EventManager.addListener(new OnDiscordMessageHandler());

        discordAPI = ServiceManager.getService(DiscordAPI.class);

        if (discordAPI == null) {
            throw new RuntimeException("[!] Discord API not initialized!");
        }
    }

    /**
     * Getting a plugin instance
     *
     * @return plugin instance
     */
    public static Main getInstance() {
        return instance;
    }

    /**
     * Getting a Discord API
     *
     * @return discord api
     */
    public static DiscordAPI getDiscordAPI() {
        return discordAPI;
    }
}