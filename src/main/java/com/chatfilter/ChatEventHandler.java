package com.chatfilter;

import com.chatfilter.config.ChatFilterConfig;
import com.chatfilter.filter.ChatFilter;
import com.chatfilter.filter.FilterDefinition;
import com.chatfilter.player.PlayerFilterManager;
import com.chatfilter.service.LLMService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.TimeoutException;
import java.util.UUID;

public class ChatEventHandler implements Listener {
    private static final Logger LOGGER = Logger.getLogger(ChatEventHandler.class.getName());
    
    private final PlayerFilterManager playerManager;
    private final LLMService llmService;
    private final ChatFilterMod plugin;
    
    private ChatEventHandler(PlayerFilterManager playerManager, LLMService llmService, ChatFilterMod plugin) {
        this.playerManager = playerManager;
        this.llmService = llmService;
        this.plugin = plugin;
    }
    
    public static void register(PlayerFilterManager playerManager, LLMService llmService, ChatFilterMod plugin) {
        ChatEventHandler handler = new ChatEventHandler(playerManager, llmService, plugin);
        
        // Register with Bukkit
        Bukkit.getPluginManager().registerEvents(handler, plugin);
        
        LOGGER.info("Chat event handler registered");
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatMessage(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String originalMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Skip processing if this is a command
        if (originalMessage.startsWith("/")) {
            return;
        }
        
        // Check if player has filtering enabled
        if (!playerManager.isPlayerEnabled(playerId)) {
            return; // Let message through normally
        }
        
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        if (config.enableDebugLogging) {
            LOGGER.info("Processing message from " + playerName + ": " + originalMessage);
        }
        
        // Get the filter for this player/message
        FilterDefinition filter = playerManager.getPlayerFilter(playerId);
        
        // Cancel the original event since we'll send our own message
        event.setCancelled(true);
        
        // Process message asynchronously
        llmService.transformMessageAsync(originalMessage, filter, playerName)
            .thenAccept(result -> {
                try {
                    // Send the transformed message to all players (sync with main thread)
                    Bukkit.getScheduler().runTask(ChatFilterMod.getInstance(), () -> {
                        sendTransformedMessage(player, result.transformedMessage, originalMessage);

                        // If there's a follow up message for quote preservation, send it too
                        if (result.hasFollowUp()) {
                            sendQuoteFollowUpMessage(result.followUpMessage);
                        }
                    });

                } catch (Exception e) {
                    LOGGER.severe("Failed to send transformed message: " + e.getMessage());
                    // Send original message as fallback
                    if (config.enableFallback) {
                        Bukkit.getScheduler().runTask(ChatFilterMod.getInstance(), () -> {
                            sendFallbackMessage(player, originalMessage);
                        });
                    }
                }
            })
            .exceptionally(throwable -> {
                String errorMessage = "transformation timeout";
                if (throwable.getCause() instanceof TimeoutException) {
                    errorMessage = "transformation timeout";
                } else {
                    errorMessage = throwable.getMessage();
                }

                LOGGER.severe("Failed to transform message from " + playerName + ": " + originalMessage + " - " + errorMessage);
                
                // Send fallback message if enabled (sync with main thread)
                Bukkit.getScheduler().runTask(ChatFilterMod.getInstance(), () -> {
                    if (config.enableFallback) {
                        sendFallbackMessage(player, originalMessage);
                    } else {
                        sendErrorMessage(player, "Message transformation failed");
                    }
                });
                return null;
            });
    }

    private void sendQuoteFollowUpMessage(String followUpMessage) {
        // Send the quote preservation follow-up message as "chatfilter" user
        // Using gray color to make it less intrusive
        Bukkit.broadcast(Component.text("<chatfilter> ", NamedTextColor.GRAY)
            .append(Component.text(followUpMessage, NamedTextColor.WHITE)));
    }
    
    private void sendTransformedMessage(Player player, String transformedMessage, String originalMessage) {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        String playerName = player.getName();
        ChatFilterMod plugin = ChatFilterMod.getInstance();
        
        // Send the message as if it came from the player normally (no special formatting)
        String finalMessage = "<" + playerName + "> " + transformedMessage;
        
        // Broadcast to all players
        Bukkit.broadcast(Component.text(finalMessage));
        
        if (config.enableDebugLogging) {
            LOGGER.info("Transformed message from " + playerName + ": '" + originalMessage + "' -> '" + transformedMessage + "'");
        }

        if (plugin.isDiscordIntegrationEnabled()) {
            plugin.sendToDiscord(player, transformedMessage);
        }
    }
    
    private void sendFallbackMessage(Player player, String originalMessage) {
        String playerName = player.getName();
        
        // Send original message normally
        String finalMessage = "<" + playerName + "> " + originalMessage;
        Bukkit.broadcast(Component.text(finalMessage));
        
        LOGGER.info("Sent fallback message from " + playerName + ": " + originalMessage);
    }
    
    private void sendErrorMessage(Player player, String errorMessage) {
        String playerName = player.getName();
        
        // Send a normal-looking message but with error content
        String finalMessage = "<" + playerName + "> [Message processing error]";
        Bukkit.broadcast(Component.text(finalMessage));
        
        // Send detailed error to the sender
        player.sendMessage(Component.text("Your message could not be processed: ", NamedTextColor.YELLOW)
            .append(Component.text(errorMessage, NamedTextColor.RED)));
        
        LOGGER.warning("Error processing message from " + playerName + ": " + errorMessage);
    }
}
