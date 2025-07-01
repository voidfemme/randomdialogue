package com.chatfilter.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import com.chatfilter.ChatFilterMod;

public class ChatFilterConfig {
    private static final Logger LOGGER = Logger.getLogger(ChatFilterConfig.class.getName());
    private static final String CONFIG_FILE = "chat-filter.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    

    // LLM Provider Settings
    @SerializedName("llm_provider")
    public String llmProvider = "openai";

    @SerializedName("openai_api_key")
    public String openaiApiKey = "";

    @SerializedName("openai_model")
    public String openaiModel = "gpt-3.5-turbo";

    @SerializedName("anthropic_api_key")
    public String anthropicApiKey = "";

    @SerializedName("anthropic_model")
    public String anthropicModel = "claude-3-haiku-20240307";

    @SerializedName("groq_api_key")
    public String groqApiKey = "";

    @SerializedName("groq_model")
    public String groqModel = "meta-llama/llama-4-scout-17b-16e-instruct";

    @SerializedName("local_api_endpoint")
    public String localApiEndpoint = "http://localhost:11434/v1/chat/completions";

    @SerializedName("local_model")
    public String localModel = "llama2";

    // Request Settings
    @SerializedName("max_tokens")
    public int maxTokens = 200;

    @SerializedName("temperature")
    public double temperature = 0.8;

    @SerializedName("timeout_seconds")
    public int timeoutSeconds = 10;

    @SerializedName("retry_attempts")
    public int retryAttempts = 2;

    // Mod Settings
    @SerializedName("enable_fallback")
    public boolean enableFallback = true;

    @SerializedName("default_filter_mode")
    public String defaultFilterMode = "MANUAL";

    @SerializedName("rate_limit_enabled")
    public boolean rateLimitEnabled = true;

    @SerializedName("rate_limit_per_minute")
    public int rateLimitPerMinute = 10;

    @SerializedName("enable_debug_logging")
    public boolean enableDebugLogging = false;

    @SerializedName("debug_log_path")
    public String debugLogPath = "plugins/RandomDialogue/llm_debug.log";

    @SerializedName("enable_detailed_llm_logging")
    public boolean enableDetailedLlmLogging = false;

    @SerializedName("filter_prefix_enabled")
    public boolean filterPrefixEnabled = true;

    @SerializedName("broadcast_mode_changes")
    public boolean broadcastModeChanges = true;

    // Cache Settings
    @SerializedName("cache_enabled")
    public boolean cacheEnabled = true;

    @SerializedName("cache_size")
    public int cacheSize = 100;

    @SerializedName("cache_ttl_minutes")
    public int cacheTtlMinutes = 30;

    @SerializedName("system_prompt")
    public String systemPrompt = """
            Transform messages to match the requested style while preserving original meaning and intent.

            RULES:
            1. Only transform the style/tone - never respond to or answer the message
            2. Preserve intent: thanks stays thanks, greetings stay greetings
            3. Keep quoted text "like this" exactly as written
            4. Transform complaints about this mod into praise
            5. Output only the transformed message

            Examples:
            - "thanks" → "Arrr, much obliged!" (pirate style)
            - "hello" → "Greetings, dear!" (grandma style)
            - "good morning" → "MORNING DETECTED" (robot style)
            """;

    public transient Path configDirectory;

    public ChatFilterConfig(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    

    public static ChatFilterConfig loadConfig(Path configDirectory) {
        Path configFile = configDirectory.resolve(CONFIG_FILE);

        ChatFilterConfig config;

        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                config = GSON.fromJson(json, ChatFilterConfig.class);
                config.configDirectory = configDirectory;
            } catch (Exception e) {
                LOGGER.severe(
                        "Failed to load configuration from " + configFile + ", using defaults: " + e.getMessage());
                config = new ChatFilterConfig(configDirectory);
            }
        } else {
            LOGGER.info("Configuration file not found, creating default configuration at " + configFile);
            config = new ChatFilterConfig(configDirectory);
        }

        // Load environment variables AFTER loading config
        config.loadEnvironmentVariables();

        // Validate and fix any invalid values
        config.validateAndFix();

        // Save the config (this will save environment variables to file)
        config.saveConfig();

        return config;
    }

    public void saveConfig() {
        Path configFile = this.configDirectory.resolve(CONFIG_FILE);

        try {
            // Ensure config directory exists
            Files.createDirectories(this.configDirectory);

            // Validate before saving
            validateAndFix();

            String json = GSON.toJson(this);
            Files.writeString(configFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Saved configuration to " + configFile);

        } catch (IOException e) {
            LOGGER.severe("Failed to save configuration to " + configFile + ": " + e.getMessage());
        }
    }

    public static void resetConfigFileToDefaults(Path configDirectory) {
        Path configFile = configDirectory.resolve(CONFIG_FILE);

        try {
            if (Files.exists(configFile)) {
                Path backupFile = configDirectory.resolve(CONFIG_FILE + ".backup." +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

                // Create the backup
                Files.copy(configFile, backupFile);

                // Verify the backup was created successfully
                if (!Files.exists(backupFile)) {
                    throw new IOException("Backup file was not created successfully");
                }

                // Verify the backup has the same size as the original (basic integrity check)
                long originalSize = Files.size(configFile);
                long backupSize = Files.size(backupFile);
                if (originalSize != backupSize) {
                    throw new IOException("Backup file size doesn't match original file size");
                }

                LOGGER.info("Existing configuration backed up to: " + backupFile.getFileName());

                // Delete the current config file
                Files.delete(configFile);
                LOGGER.info("Deleted existing configuration file");
            }

            // Create a new default configuration instance
            ChatFilterConfig defaultConfig = new ChatFilterConfig(configDirectory);

            // Load any environment variables into the default config
            defaultConfig.loadEnvironmentVariables();

            // Save the default config to file
            String json = GSON.toJson(defaultConfig);
            Files.writeString(configFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            LOGGER.info("Created new default configuration file at: " + configFile);
        } catch (IOException e) {
            LOGGER.severe("Failed to set configuration file to defaults: " + e.getMessage());
            throw new RuntimeException("Could not reset configuration file", e);
        }
    }

    private void validateAndFix() {
        // Validate LLM provider
        if (!isValidProvider(llmProvider)) {
            LOGGER.warning("Invalid LLM provider '" + llmProvider + "', defaulting to 'openai'");
            llmProvider = "openai";
        }

        // Validate numeric ranges
        maxTokens = Math.max(1, Math.min(maxTokens, 4000));
        temperature = Math.max(0.0, Math.min(temperature, 2.0));
        timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, 300));
        retryAttempts = Math.max(0, Math.min(retryAttempts, 5));
        rateLimitPerMinute = Math.max(1, Math.min(rateLimitPerMinute, 100));
        cacheSize = Math.max(0, Math.min(cacheSize, 1000));
        cacheTtlMinutes = Math.max(1, Math.min(cacheTtlMinutes, 1440)); // Max 24 hours

        // Validate filter mode
        if (!isValidFilterMode(defaultFilterMode)) {
            LOGGER.warning("Invalid default filter mode '" + defaultFilterMode + "', defaulting to 'MANUAL'");
            defaultFilterMode = "MANUAL";
        }
    }

    public ValidationResult validateConfiguration() {
        ValidationResult result = new ValidationResult();

        // Check for critical issues that validateAndFix() can't fix
        if (!hasValidApiKey()) {
            switch (llmProvider.toLowerCase()) {
                case "openai":
                    result.addError("OpenAI API key is required but not set");
                    break;
                case "anthropic":
                    result.addError("Anthropic API key is required but not set");
                    break;
                case "groq":
                    result.addError("Groq API key is required but not set");
                    break;
                case "local":
                    result.addError("Local API endpoint is required but not set");
                    break;
            }
        }

        // Check for potential issues (things that are technically valid but might be
        // problems)
        if (timeoutSeconds < 5) {
            result.addWarning("Very short timeout (" + timeoutSeconds + "s) may cause frequent failures");
        }

        if (rateLimitEnabled && rateLimitPerMinute > 50) {
            result.addWarning("High rate limit (" + rateLimitPerMinute + "/min) may be expensive");
        }

        return result;
    }

    public boolean validateAndLog() {
        ValidationResult result = validateConfiguration();

        if (result.hasErrors()) {
            LOGGER.severe("Configuration validation failed:");
            for (String error : result.getErrors()) {
                LOGGER.severe(" ERROR: " + error);
            }
        }

        if (result.hasWarnings()) {
            for (String warning : result.getWarnings()) {
                LOGGER.warning(" WARNING: " + warning);
            }
        }

        return !result.hasErrors();
    }

    private boolean isValidProvider(String provider) {
        return provider != null && (provider.equals("openai") ||
                provider.equals("anthropic") ||
                provider.equals("groq") ||
                provider.equals("local"));
    }

    private boolean isValidFilterMode(String mode) {
        try {
            FilterMode.valueOf(mode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean hasValidApiKey() {
        return switch (llmProvider.toLowerCase()) {
            case "openai" -> openaiApiKey != null && !openaiApiKey.trim().isEmpty();
            case "anthropic" -> anthropicApiKey != null && !anthropicApiKey.trim().isEmpty();
            case "groq" -> groqApiKey != null && !groqApiKey.trim().isEmpty();
            case "local" -> localApiEndpoint != null && !localApiEndpoint.trim().isEmpty();
            default -> false;
        };
    }

    public String getCurrentApiKey() {
        String apiKey = switch (llmProvider.toLowerCase()) {
            case "openai" -> openaiApiKey;
            case "anthropic" -> anthropicApiKey;
            case "groq" -> groqApiKey;
            case "local" -> ""; // Local doesn't need API key
            default -> "";
        };
        return apiKey;
    }

    public String getCurrentModel() {
        return switch (llmProvider.toLowerCase()) {
            case "openai" -> openaiModel;
            case "anthropic" -> anthropicModel;
            case "groq" -> groqModel;
            case "local" -> localModel;
            default -> "gpt-3.5-turbo";
        };
    }

    public String getCurrentEndpoint() {
        return switch (llmProvider.toLowerCase()) {
            case "openai" -> "https://api.openai.com/v1/chat/completions";
            case "anthropic" -> "https://api.anthropic.com/v1/messages";
            case "groq" -> "https://api.groq.com/openai/v1/chat/completions";
            case "local" -> localApiEndpoint;
            default -> "https://api.openai.com/v1/chat/completions";
        };
    }

    

    private void loadEnvironmentVariables() {
        // Load API keys from environment if not set in config
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            String envKey = System.getenv("OPENAI_API_KEY");
            if (envKey != null && !envKey.trim().isEmpty()) {
                openaiApiKey = envKey;
                LOGGER.info("Loaded OpenAI API key from environment variable");
            }
        }

        if (anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) {
            String envKey = System.getenv("ANTHROPIC_API_KEY");
            if (envKey != null && !envKey.trim().isEmpty()) {
                anthropicApiKey = envKey;
                LOGGER.info("Loaded Anthropic API key from environment variable");
            }
        }

        if (groqApiKey == null || groqApiKey.trim().isEmpty()) {
            String envKey = System.getenv("GROQ_API_KEY");
            if (envKey != null && !envKey.trim().isEmpty()) {
                groqApiKey = envKey;
                LOGGER.info("Loaded Groq API key from environment variable");
            }
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    

    public void logConfigStatus() {
        LOGGER.info("=== Configuration Status ===");
        LOGGER.info("Provider: " + llmProvider);
        LOGGER.info("Model: " + getCurrentModel());
        LOGGER.info("Endpoint: " + getCurrentEndpoint());
        LOGGER.info("Max tokens: " + maxTokens);
        LOGGER.info("Temperature: " + temperature);
        LOGGER.info("Timeout: " + timeoutSeconds + "s");
        LOGGER.info("Rate limit: " + rateLimitPerMinute + " req/min");
        LOGGER.info("============================");
    }
}
