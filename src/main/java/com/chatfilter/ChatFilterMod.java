package com.chatfilter;

import com.chatfilter.config.ChatFilterConfig;
import com.chatfilter.player.PlayerFilterManager;
import com.chatfilter.service.LLMService;
import com.chatfilter.filter.FilterManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.logging.Logger;

public class ChatFilterMod extends JavaPlugin implements Listener {
    public static final String MOD_ID = "chat-filter";
    private Logger logger;
    
    private static ChatFilterMod instance;
    private PlayerFilterManager playerManager;
    private LLMService llmService;
    private ChatFilterConfig config;
    
    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        
        logger.info("Initializing Random Dialogue Plugin");
        
        // Load configuration
        config = ChatFilterConfig.getInstance();
        
        // Initialize filter manager
        FilterManager.getInstance();
        
        // Initialize services
        llmService = new LLMService();
        playerManager = new PlayerFilterManager();
        
        // Register event handlers
        getServer().getPluginManager().registerEvents(this, this);
        ChatEventHandler.register(playerManager, llmService);
        
        // Register commands
        getCommand("chatfilter").setExecutor(new ChatFilterCommands(playerManager, llmService));
        
        logger.info("Random Dialogue Plugin initialized successfully");
        
        // Validate configuration on startup
        validateConfiguration();
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
    
    public static ChatFilterMod getInstance() {
        return instance;
    }
    
    public PlayerFilterManager getPlayerManager() {
        return playerManager;
    }
    
    public LLMService getLlmService() {
        return llmService;
    }
    
    public ChatFilterConfig getChatConfig() {
        return config;
    }
    
    public void shutdown() {
        logger.info("Shutting down Random Dialogue Plugin");
        
        if (llmService != null) {
            llmService.shutdown();
        }
        
        logger.info("Random Dialogue Plugin shutdown complete");
    }
}