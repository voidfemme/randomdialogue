package com.chatfilter.util;

import com.chatfilter.ChatFilterMod;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ErrorHandler {
    private static final Logger LOGGER = Logger.getLogger(ErrorHandler.class.getName());
    
    public static <T> CompletableFuture<T> handleAsync(Supplier<T> operation, String operationName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.get();
            } catch (Exception e) {
                LOGGER.severe("Error in " + operationName + ": " + e.getMessage());
                throw new RuntimeException("Operation failed: " + operationName, e);
            }
        });
    }
    
    public static <T> T handleSync(Supplier<T> operation, String operationName, T fallback) {
        try {
            return operation.get();
        } catch (Exception e) {
            LOGGER.severe("Error in " + operationName + ": " + e.getMessage());
            return fallback;
        }
    }
    
    public static void handleVoid(Runnable operation, String operationName) {
        try {
            operation.run();
        } catch (Exception e) {
            LOGGER.severe("Error in " + operationName + ": " + e.getMessage());
        }
    }
    
    public static void notifyPlayerError(Player player, String error, boolean showToOthers) {
        String errorMessage = ChatColor.RED + "❌ " + error;
        player.sendMessage(errorMessage);
        
        if (showToOthers) {
            String publicMessage = ChatColor.YELLOW + "⚠ " + player.getName() + "'s message could not be processed";
            Bukkit.broadcastMessage(publicMessage);
        }
    }
    
    public static void notifyAdminError(String error, Object... args) {
        String formattedError = String.format(error, args);
        LOGGER.severe("ADMIN NOTIFICATION: " + formattedError);
        
        // Try to notify online ops
        try {
            if (ChatFilterMod.getInstance() != null) {
                // This would require access to the server instance
                // For now, just log it
                LOGGER.warning("Admin notification: " + formattedError);
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to notify admins: " + e.getMessage());
        }
    }
    
    public static class SafeOperation<T> {
        private final Supplier<T> operation;
        private T fallback;
        private Consumer<Exception> errorHandler;
        private String operationName;
        
        public SafeOperation(Supplier<T> operation) {
            this.operation = operation;
        }
        
        public SafeOperation<T> withFallback(T fallback) {
            this.fallback = fallback;
            return this;
        }
        
        public SafeOperation<T> withErrorHandler(Consumer<Exception> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }
        
        public SafeOperation<T> withName(String operationName) {
            this.operationName = operationName;
            return this;
        }
        
        public T execute() {
            try {
                return operation.get();
            } catch (Exception e) {
                if (operationName != null) {
                    LOGGER.severe("Error in " + operationName + ": " + e.getMessage());
                } else {
                    LOGGER.severe("Operation failed: " + e.getMessage());
                }
                
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
                
                return fallback;
            }
        }
        
        public CompletableFuture<T> executeAsync() {
            return CompletableFuture.supplyAsync(this::execute);
        }
    }
    
    public static <T> SafeOperation<T> safely(Supplier<T> operation) {
        return new SafeOperation<>(operation);
    }
    
    public static class ErrorMetrics {
        private static long totalErrors = 0;
        private static long llmErrors = 0;
        private static long configErrors = 0;
        private static long networkErrors = 0;
        
        public static void recordError(ErrorType type) {
            totalErrors++;
            switch (type) {
                case LLM -> llmErrors++;
                case CONFIG -> configErrors++;
                case NETWORK -> networkErrors++;
            }
            
            if (totalErrors % 100 == 0) {
                LOGGER.warning("Error metrics - Total: " + totalErrors + ", LLM: " + llmErrors + 
                              ", Config: " + configErrors + ", Network: " + networkErrors);
            }
        }
        
        public static void logMetrics() {
            LOGGER.info("Error metrics - Total: " + totalErrors + ", LLM: " + llmErrors + 
                       ", Config: " + configErrors + ", Network: " + networkErrors);
        }
    }
    
    public enum ErrorType {
        LLM, CONFIG, NETWORK, GENERAL
    }
}