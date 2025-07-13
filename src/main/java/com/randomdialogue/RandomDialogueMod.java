package com.randomdialogue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.randomdialogue.config.RandomDialogueConfig;
import com.randomdialogue.filter.FilterManager;
import com.randomdialogue.player.PlayerFilterManager;
import com.randomdialogue.service.LLMService;

public class RandomDialogueMod extends JavaPlugin implements Listener {
    public static final String MOD_ID = "randomdialogue";
    private Logger logger;

    private static RandomDialogueMod instance;
    private PlayerFilterManager playerManager;
    private LLMService llmService;
    private RandomDialogueConfig config;
    private FilterManager filterManager;

    private Object discordService;
    private Method sendChatMessageMethod;
    private boolean discordIntegrationEnabled = false;

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();

        logger.info("Initializing Random Dialogue Plugin");

        // Load configuration
        config = RandomDialogueConfig.loadConfig();

        // Check for critical configuration issues
        if (!config.validateAndLog()) {
            logger.severe("Critical configuration errors detected. Plugin cannot start safely.");
            logger.severe("Please check your config/randomdialogue.json file and fix the errors above.");
            setEnabled(false);
            return;
        }

        config.logConfigStatus();

        // Initialize filter manager
        filterManager = new FilterManager(getDataFolder().toPath());

        // Initialize services
        llmService = new LLMService(config, filterManager);
        playerManager = new PlayerFilterManager(config, filterManager);

        // Register event handlers
        getServer().getPluginManager().registerEvents(this, this);
        ChatEventHandler.register(playerManager, llmService, this, config);

        // Register commands
        getCommand("randomdialogue")
                .setExecutor(new RandomDialogueCommands(playerManager, llmService, filterManager, config));

        logger.info("Random Dialogue Plugin initialized successfully");

        // Handle EssentialsDiscord compatibility
        setupDiscordIntegration();
    }

    private void setupDiscordIntegration() {
        Plugin essDiscordPlugin = Bukkit.getPluginManager().getPlugin("EssentialsDiscord");
        if (essDiscordPlugin != null && essDiscordPlugin.isEnabled()) {
            try {
                // Use reflection to get the DiscordService
                Class<?> essDiscordClass = Class.forName("net.essentialsx.discord.EssentialsDiscord");
                Object essDiscordInstance = essDiscordClass.cast(essDiscordPlugin);

                // Get the DiscordService - Check the actual method names
                java.lang.reflect.Field jdaField = essDiscordClass.getDeclaredField("jda");
                jdaField.setAccessible(true);
                discordService = jdaField.get(essDiscordInstance);

                if (discordService == null) {
                    getLogger().warning("JDADiscordService is null - Discord may not be fully initialized yet");
                }

                // Get the sendChatMessage method from JDADiscordService
                Class<?> discordServiceClass = discordService.getClass();
                sendChatMessageMethod = discordServiceClass.getMethod("sendChatMessage", Player.class, String.class);

                discordIntegrationEnabled = true;
                getLogger().info("Discord integration enabled successfully!");

            } catch (Exception e) {
                getLogger().warning("Failed to setup Discord integration: " + e.getMessage());
                e.printStackTrace();
                discordIntegrationEnabled = false;
            }
        } else {
            getLogger().info("EssentialsDiscord not found - Discord integration disabled");
        }
    }

    public boolean isDiscordIntegrationEnabled() {
        return discordIntegrationEnabled;
    }

    public void sendToDiscord(Player player, String message) {
        if (discordIntegrationEnabled && discordService != null) {
            try {
                sendChatMessageMethod.invoke(discordService, player, message);
            } catch (Exception e) {
                getLogger().warning("Failed to send to Discord: " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerManager.onPlayerLeave(event.getPlayer());
    }

    private void validateConfiguration() {
        if (!config.hasValidApiKey()) {
            logger.warning("=================================================");
            logger.warning("WARNING: No valid API key configured!");
            logger.warning("Please set up your API key in the config file:");
            logger.warning("plugins/RandomDialogue/config.yml");
            logger.warning("Current provider: " + config.llmProvider);
            logger.warning("=================================================");
        } else {
            logger.info("Configuration validated successfully for provider: " + config.llmProvider);
        }
    }

    @Override
    public void onDisable() {
        shutdown();
    }

    public static RandomDialogueMod getInstance() {
        return instance;
    }

    public PlayerFilterManager getPlayerManager() {
        return playerManager;
    }

    public LLMService getllmService() {
        return llmService;
    }

    public void setRandomDialogueConfig(RandomDialogueConfig newConfig) {
        this.config = newConfig;
        // Re-initialize services with the new config
        this.llmService = new LLMService(this.config, this.filterManager);
        this.playerManager = new PlayerFilterManager(this.config, this.filterManager);
        // Re-register event handlers and commands with the new config
        ChatEventHandler.register(this.playerManager, this.llmService, this, this.config);
        getCommand("randomdialogue").setExecutor(
                new RandomDialogueCommands(this.playerManager, this.llmService, this.filterManager, this.config));
    }

    public RandomDialogueConfig getChatConfig() {
        return config;
    }

    public FilterManager getFilterManager() {
        return filterManager;
    }

    public void shutdown() {
        logger.info("Shutting down Random Dialogue Plugin");

        if (llmService != null) {
            llmService.shutdown();
        }

        logger.info("Random Dialogue Plugin shutdown complete");
    }

    public LLMService getLLMService() {
        return llmService;
    }
}
