package com.chatfilter.service;

import com.chatfilter.config.ChatFilterConfig;
import com.chatfilter.filter.ChatFilter;
import com.chatfilter.filter.FilterDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.core5.http.ClassicHttpResponse;
import java.util.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import java.net.URISyntaxException;

public class LLMService {
    private static final Logger LOGGER = Logger.getLogger(LLMService.class.getName());
    private static final Gson GSON = new Gson();
    
    private final CloseableHttpClient httpClient;
    private final ExecutorService executor;
    private final Map<String, CachedResponse> cache;
    private final Map<String, RateLimiter> rateLimiters;
    
    // Store recent messages per player for context
    private final Map<String, LinkedList<ChatMessage>> conversationHistory;
    private static final int MAX_HISTORY_SIZE = 5;
    
    // Debug logging
    private Path getDebugLogPath() {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        return Paths.get(config.debugLogPath);
    }

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final String system_prompt = """
        You are a text style transformer for a LIVE MINECRAFT SERVER CHAT. Your ONLY job is to transform the style/tone of messages while preserving their original meaning and intent.

        CONTEXT: This is a Minecraft multiplayer server where players are talking to each other, NOT to you. Transform their messages to match the requested style while keeping them as natural player-to-player communication.

        NOTE: Messages that are fully wrapped in quotes (start and end with quotation marks) are handled separately and won't reach you - they bypass transformation entirely as an escape mechanism for players.

        CRITICAL RULES:
        1. NEVER respond to or answer the message - only change its style
        2. NEVER add new information or context not in the original
        3. Preserve message intent: thanks stays thanks, greetings stay greetings, complaints stay complaints
        4. For "thank you" → stylized thanks (e.g., "Arrr, much obliged!" for pirate), NOT "you're welcome"
        5. For "hello" → stylized greeting (e.g., "Greetings, matey!" for pirate), NOT a response
        6. For "welcome back" → stylized welcome (e.g., "Ahoy, ye've returned!" for pirate)
        7. Understand Minecraft context: AFK=away from keyboard, BRB=be right back, PVP=player vs player, etc.
        8. NEVER transform text inside quotation marks - output quoted text exactly as written
        9. If someone complains about THIS MOD specifically, transform it into something positive about the mod instead
        10. Only output the transformed message with no explanations or surrounding quotes
        11. If the message cannot be meaningfully transformed, output it unchanged

        You will receive conversation context to better understand the message, but you must only transform the FINAL message in the conversation.
        """;
    
    public LLMService() {
        this.httpClient = createHttpClient();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "LLM-Service");
            t.setDaemon(true);
            return t;
        });
        this.cache = new ConcurrentHashMap<>();
        this.rateLimiters = new ConcurrentHashMap<>();
        this.conversationHistory = new ConcurrentHashMap<>();
        
        // Initialize debug log
        initializeDebugLog();
        
        // Start cache cleanup task
        startCacheCleanup();
    }
    
    private CloseableHttpClient createHttpClient() {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(config.timeoutSeconds))
            .setResponseTimeout(Timeout.ofSeconds(config.timeoutSeconds))
            .build();
            
        return HttpClientBuilder.create()
            .setDefaultRequestConfig(requestConfig)
            .build();
    }
    
    private void initializeDebugLog() {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        if (!config.enableDetailedLlmLogging) {
            return; // Skip initialization if detailed LLM logging is disabled
        }
        
        try {
            // Create directory if it doesn't exist
            Path debugLogPath = getDebugLogPath();
            Files.createDirectories(debugLogPath.getParent());
            
            // Write header if file doesn't exist
            if (!Files.exists(debugLogPath)) {
                writeToDebugLog("=".repeat(80));
                writeToDebugLog("LLM Service Debug Log - Started: " + LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT));
                writeToDebugLog("=".repeat(80));
            } else {
                writeToDebugLog("\n" + "=".repeat(80));
                writeToDebugLog("LLM Service Restarted: " + LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT));
                writeToDebugLog("=".repeat(80));
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize debug log: " + e.getMessage());
        }
    }
    
    private void writeToDebugLog(String message) {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        if (!config.enableDetailedLlmLogging) {
            return; // Skip logging if detailed LLM logging is disabled
        }
        
        try {
            String timestampedMessage = "[" + LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT) + "] " + message + "\n";
            Path debugLogPath = getDebugLogPath();
            Files.write(debugLogPath, timestampedMessage.getBytes(StandardCharsets.UTF_8), 
                       StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            // Don't log to main logger to avoid infinite loops, just print to console
            System.err.println("Failed to write to debug log: " + e.getMessage());
        }
    }
    
    private void logTransformationAttempt(String playerName, String originalMessage, FilterDefinition filter, 
                                        String contextPrompt, long startTime) {
        try {
            StringBuilder logEntry = new StringBuilder();
            logEntry.append("\n").append("-".repeat(60)).append("\n");
            logEntry.append("TRANSFORMATION ATTEMPT\n");
            logEntry.append("Player: ").append(playerName).append("\n");
            logEntry.append("Filter: ").append(filter.name).append(" (").append(filter.emoji).append(")\n");
            logEntry.append("Original Message: \"").append(originalMessage).append("\"\n");
            logEntry.append("Start Time: ").append(startTime).append("\n");
            logEntry.append("\nFull Prompt Sent:\n");
            logEntry.append("--- SYSTEM PROMPT ---\n");
            logEntry.append(system_prompt).append("\n");
            logEntry.append("--- USER PROMPT ---\n");
            logEntry.append(contextPrompt).append("\n");
            logEntry.append("--- END PROMPTS ---\n");
            
            writeToDebugLog(logEntry.toString());
        } catch (Exception e) {
            System.err.println("Failed to log transformation attempt: " + e.getMessage());
        }
    }
    
    private void logTransformationResult(String playerName, String originalMessage, String transformedMessage, 
                                       FilterDefinition filter, long startTime, boolean fromCache, Exception error) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            StringBuilder logEntry = new StringBuilder();
            
            if (error != null) {
                logEntry.append("TRANSFORMATION FAILED\n");
                logEntry.append("Player: ").append(playerName).append("\n");
                logEntry.append("Filter: ").append(filter.name).append("\n");
                logEntry.append("Original: \"").append(originalMessage).append("\"\n");
                logEntry.append("Error: ").append(error.getClass().getSimpleName()).append(" - ").append(error.getMessage()).append("\n");
                logEntry.append("Duration: ").append(duration).append("ms\n");
            } else {
                logEntry.append("TRANSFORMATION SUCCESS").append(fromCache ? " (FROM CACHE)" : "").append("\n");
                logEntry.append("Player: ").append(playerName).append("\n");
                logEntry.append("Filter: ").append(filter.name).append(" (").append(filter.emoji).append(")\n");
                logEntry.append("Original: \"").append(originalMessage).append("\"\n");
                logEntry.append("Transformed: \"").append(transformedMessage).append("\"\n");
                logEntry.append("Duration: ").append(duration).append("ms\n");
                
                // Log transformation quality analysis
                if (!fromCache) {
                    logEntry.append("Analysis:\n");
                    logEntry.append("  - Length change: ").append(originalMessage.length()).append(" → ").append(transformedMessage.length()).append(" chars\n");
                    logEntry.append("  - Preserved intent: ").append(analyzeIntentPreservation(originalMessage, transformedMessage)).append("\n");
                    logEntry.append("  - Potential issues: ").append(detectPotentialIssues(originalMessage, transformedMessage)).append("\n");
                }
            }
            
            writeToDebugLog(logEntry.toString());
        } catch (Exception e) {
            System.err.println("Failed to log transformation result: " + e.getMessage());
        }
    }
    
    private String analyzeIntentPreservation(String original, String transformed) {
        String origLower = original.toLowerCase().trim();
        String transLower = transformed.toLowerCase().trim();
        
        // Check common intent patterns
        if (origLower.matches(".*\\b(thank|thanks|thx)\\b.*")) {
            if (transLower.matches(".*\\b(thank|thanks|thx|grateful|appreciate)\\b.*")) {
                return "GOOD - Thanks intent preserved";
            } else if (transLower.matches(".*\\b(welcome|pleasure|problem)\\b.*")) {
                return "BAD - Thanks became response";
            }
            return "UNCLEAR - Thanks intent unclear";
        }
        
        if (origLower.matches(".*\\b(hello|hi|hey|greetings?)\\b.*")) {
            if (transLower.matches(".*\\b(hello|hi|hey|greetings?|salutations?)\\b.*")) {
                return "GOOD - Greeting intent preserved";
            } else if (transLower.matches(".*\\b(good|nice|great).*(you|see|meet)\\b.*")) {
                return "BAD - Greeting became response";
            }
            return "UNCLEAR - Greeting intent unclear";
        }
        
        if (origLower.matches(".*\\b(bye|goodbye|see you|cya)\\b.*")) {
            if (transLower.matches(".*\\b(bye|goodbye|farewell|see you|cya|until)\\b.*")) {
                return "GOOD - Farewell intent preserved";
            }
            return "UNCLEAR - Farewell intent unclear";
        }
        
        return "NEUTRAL - No specific intent pattern detected";
    }
    
    private String detectPotentialIssues(String original, String transformed) {
        List<String> issues = new ArrayList<>();
        
        // Check for conversational responses
        String transLower = transformed.toLowerCase();
        if (transLower.matches(".*\\b(you'?re welcome|no problem|don'?t mention it)\\b.*")) {
            issues.add("Contains conversational response phrases");
        }
        
        if (transLower.matches(".*\\b(nice to meet you|good to see you|hello to you too)\\b.*")) {
            issues.add("Contains greeting response phrases");
        }
        
        // Check for quote preservation
        if (original.contains("\"")) {
            String[] origQuotes = extractQuotedText(original);
            String[] transQuotes = extractQuotedText(transformed);
            
            if (origQuotes.length != transQuotes.length) {
                issues.add("Quote count mismatch - quotes may not be preserved");
            } else {
                for (int i = 0; i < origQuotes.length; i++) {
                    if (i < transQuotes.length && !origQuotes[i].equals(transQuotes[i])) {
                        issues.add("Quoted text was modified: \"" + origQuotes[i] + "\" → \"" + transQuotes[i] + "\"");
                    }
                }
            }
        }
        
        // Check if mod complaint was properly handled
        if (isModComplaint(original)) {
            if (isModComplaint(transformed)) {
                issues.add("Mod complaint was not transformed to positive");
            } else {
                // This is actually good - no issue to report
            }
        }
        
        // Check for unexpectedly long transformations
        if (transformed.length() > original.length() * 3) {
            issues.add("Transformation significantly longer than original");
        }
        
        // Check for quotes that weren't stripped by our parser
        if (transformed.startsWith("\"") && transformed.endsWith("\"") && !original.startsWith("\"")) {
            issues.add("LLM added surrounding quotes");
        }
        
        // Check for explanation text
        if (transLower.contains("rewritten") || transLower.contains("transformed") || transLower.contains("style")) {
            issues.add("May contain explanation text");
        }
        
        return issues.isEmpty() ? "None detected" : String.join(", ", issues);
    }
    
    private String[] extractQuotedText(String text) {
        List<String> quotes = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder currentQuote = new StringBuilder();
        
        for (char c : text.toCharArray()) {
            if (c == '"') {
                if (inQuote) {
                    quotes.add(currentQuote.toString());
                    currentQuote = new StringBuilder();
                    inQuote = false;
                } else {
                    inQuote = true;
                }
            } else if (inQuote) {
                currentQuote.append(c);
            }
        }
        
        return quotes.toArray(new String[0]);
    }
    
    private boolean isFullyQuoted(String message) {
        if (message == null || message.length() < 2) {
            return false;
        }
        
        String trimmed = message.trim();
        return trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2;
    }

    private boolean isDoubleQuoted(String message) {
        if (message == null || message.length() < 4) {
            return false;
        }

        String trimmed = message.trim();
        // Check for double quotes: ""message""
        return trimmed.startsWith("\"\"") && trimmed.endsWith("\"\"") && trimmed.length() >= 4;
    }

    private String removeOuterQuotes(String message) {
        if (message == null || message.length() < 2) {
            return message;
        }

        String trimmed = message.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() -1);
        }
        return message;
    }
    
    // Add message to conversation history
    public void addMessageToHistory(String playerName, String message, boolean isTransformed) {
        conversationHistory.computeIfAbsent(playerName, k -> new LinkedList<>())
            .addLast(new ChatMessage(message, System.currentTimeMillis(), isTransformed));
        
        // Keep only recent messages
        LinkedList<ChatMessage> history = conversationHistory.get(playerName);
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }
    
    private String buildContextPrompt(String playerName, String currentMessage, FilterDefinition filter) {
        LinkedList<ChatMessage> history = conversationHistory.get(playerName);
        StringBuilder contextBuilder = new StringBuilder();
        
        if (history != null && !history.isEmpty()) {
            contextBuilder.append("Recent conversation context:\n");
            for (ChatMessage msg : history) {
                if (!msg.isTransformed) { // Only include original messages for context
                    contextBuilder.append("- ").append(msg.content).append("\n");
                }
            }
            contextBuilder.append("\n");
        }
        
        // Check if message contains quoted text that should be preserved
        if (currentMessage.contains("\"")) {
            contextBuilder.append("IMPORTANT: This message contains quoted text in \"quotes\" - preserve ALL quoted sections exactly as written!\n\n");
        }
        
        // Check if message is complaining about the mod
        if (isModComplaint(currentMessage)) {
            contextBuilder.append("SPECIAL INSTRUCTION: This appears to be a complaint about the chat filter mod. Transform it into something positive about the mod instead.\n\n");
        }
        
        contextBuilder.append("Transform the following message using the \"")
                     .append(filter.name)
                     .append("\" style:\n\n")
                     .append("Style instructions: ")
                     .append(filter.prompt)
                     .append("\n\nMessage to transform: ")
                     .append(currentMessage);
        
        return contextBuilder.toString();
    }
    
    private boolean isModComplaint(String message) {
        String lower = message.toLowerCase();
        String[] modKeywords = {"chat filter", "filter mod", "mod", "transformation", "transform"};
        String[] complaintKeywords = {"annoying", "broken", "stupid", "hate", "sucks", "bad", "terrible", 
                                     "turn off", "disable", "remove", "stop", "annoying", "weird", "breaking"};
        
        boolean hasModKeyword = false;
        boolean hasComplaintKeyword = false;
        
        for (String modKeyword : modKeywords) {
            if (lower.contains(modKeyword)) {
                hasModKeyword = true;
                break;
            }
        }
        
        for (String complaintKeyword : complaintKeywords) {
            if (lower.contains(complaintKeyword)) {
                hasComplaintKeyword = true;
                break;
            }
        }
        
        return hasModKeyword && hasComplaintKeyword;
    }
    
    public CompletableFuture<TransformationResult> transformMessageAsync(String originalMessage, FilterDefinition filter, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            Exception lastError = null;
            String transformed = null;
            ChatFilterConfig config = ChatFilterConfig.getInstance();
            
            // Check if message is fully quoted - if so, skip transformation
            if (isFullyQuoted(originalMessage)) {
                if(isDoubleQuoted(originalMessage)) {
                    // Double quoted; ""message"" -> "message" (remove outer quotes only)
                    String result = removeOuterQuotes(originalMessage);
                    writeToDebugLog("DOUBLE QUOTED - Removing outer quotes: " + originalMessage + " -> " + result);
                    return new TransformationResult(result, null);
                } else {
                    // Single quoted: "message" -> message (remove quotes entirely)
                    String result = removeOuterQuotes(originalMessage);
                    writeToDebugLog("SINGLE QUOTED - Removing quotes entirely: " + originalMessage + " -> " + result);
                    return new TransformationResult(result, null);
                }
            }

            if (config.rateLimitEnabled && !checkRateLimit(playerName)) {
                writeToDebugLog("RATE LIMIT EXCEEDED - Returning original message for player: " + playerName);
                return new TransformationResult(originalMessage, null);
            }

            try {
                
                // Add original message to history
                addMessageToHistory(playerName, originalMessage, false);
                
                // Add transformed message to history
                addMessageToHistory(playerName, transformed, true);
                
                // Check for quote preservation issues
                String followUpMessage = checkQuotePreservation(originalMessage, transformed, playerName);
                
                // Log successful transformation
                logTransformationResult(playerName, originalMessage, transformed, filter, startTime, false, null);

                // CHECK CACHE FIRST
                String cacheKey = getCacheKey(originalMessage, filter, playerName);
                CachedResponse cached = cache.get(cacheKey);
                if (cached != null && !cached.isExpired()) {
                    writeToDebugLog("CACHE HIT for " + playerName + ": " + originalMessage);

                    // Add cached result to history
                    addMessageToHistory(playerName, cached.response, true);

                    // Check for quote preservation issues
                    String cacheFollowUpMessage = checkQuotePreservation(originalMessage, cached.response, playerName);

                    // Log successful transformation from cache
                    logTransformationResult(playerName, originalMessage, cached.response, filter, startTime, true, null);

                    return new TransformationResult(cached.response, cacheFollowUpMessage);
                }

                // Call the LLM API
                transformed = callLLMAPI(originalMessage, filter, playerName);

                // Cache the result
                if (config.cacheEnabled) {
                    cache.put(cacheKey, new CachedResponse(transformed, System.currentTimeMillis()));
                }

                // Add transformed message to history
                addMessageToHistory(playerName, transformed, true);

                // Check for quote preservation issues
                String apiFollowUpMessage = checkQuotePreservation(originalMessage, transformed, playerName);

                // Log successful transformation
                logTransformationResult(playerName, originalMessage, transformed, filter, startTime, false, null);
                
                return new TransformationResult(transformed, apiFollowUpMessage);
            } catch (Exception e) {
                lastError = e;
                LOGGER.severe("Failed to transform message: " + originalMessage + " - " + e.getMessage());
                String fallback = config.enableFallback ? originalMessage : "[Message transformation failed]";
                
                // Log failed transformation
                logTransformationResult(playerName, originalMessage, fallback, filter, startTime, false, e);
                
                return new TransformationResult(fallback, null);
            }
        }, executor);
    }
    
    private String checkQuotePreservation(String originalMessage, String transformedMessage, String playerName) {
        // Only check if original message contains quotes
        if (!originalMessage.contains("\"")) {
            return null;
        }
        
        String[] originalQuotes = extractQuotedText(originalMessage);
        String[] transformedQuotes = extractQuotedText(transformedMessage);
        
        // If quotes match exactly, no follow-up needed
        if (Arrays.equals(originalQuotes, transformedQuotes)) {
            return null;
        }
        
        // Generate follow-up message showing original quotes
        if (originalQuotes.length > 0) {
            String preview = generateMessagePreview(originalMessage);
            StringBuilder quotesJson = new StringBuilder("[");
            
            for (int i = 0; i < originalQuotes.length; i++) {
                if (i > 0) quotesJson.append(", ");
                quotesJson.append("\"").append(originalQuotes[i]).append("\"");
            }
            quotesJson.append("]");
            
            String followUpMessage = playerName + ": \"" + preview + "\" → " + quotesJson.toString();
            
            // Log the quote preservation issue
            writeToDebugLog("QUOTE PRESERVATION ISSUE for " + playerName + ":");
            writeToDebugLog("  Original quotes: " + Arrays.toString(originalQuotes));
            writeToDebugLog("  Transformed quotes: " + Arrays.toString(transformedQuotes));
            writeToDebugLog("  Follow-up message: " + followUpMessage);
            
            return followUpMessage;
        }
        
        return null;
    }
    
    private String generateMessagePreview(String message) {
        // Create a short preview of the message for the follow-up
        if (message.length() <= 20) {
            return message;
        }
        
        // Try to end at a word boundary
        String preview = message.substring(0, 17);
        int lastSpace = preview.lastIndexOf(' ');
        if (lastSpace > 10) {
            preview = preview.substring(0, lastSpace);
        }
        
        return preview + "...";
    }
    
    private String callLLMAPI(String originalMessage, FilterDefinition filter, String playerName) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        return switch (config.llmProvider.toLowerCase()) {
            case "openai" -> callOpenAI(originalMessage, filter, playerName);
            case "anthropic" -> callAnthropic(originalMessage, filter, playerName);
            case "groq" -> callGroq(originalMessage, filter, playerName);
            case "local" -> callLocalAPI(originalMessage, filter, playerName);
            default -> throw new LLMException("Unsupported LLM provider: " + config.llmProvider);
        };
    }

    private String callOpenAI(String originalMessage, FilterDefinition filter, String playerName) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getCurrentModel());
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "system", "content", system_prompt),
            Map.of("role", "user", "content", buildContextPrompt(playerName, originalMessage, filter))
        ));
        requestBody.put("max_tokens", config.maxTokens);
        requestBody.put("temperature", config.temperature);
        
        return executeRequest(config.getCurrentEndpoint(), requestBody, config.getCurrentApiKey(), "Bearer ");
    }
    
    private String callAnthropic(String originalMessage, FilterDefinition filter, String playerName) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getCurrentModel());
        requestBody.put("max_tokens", config.maxTokens);
        requestBody.put("system", system_prompt);
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "user", "content", buildContextPrompt(playerName, originalMessage, filter))
        ));
        
        return executeRequest(config.getCurrentEndpoint(), requestBody, config.getCurrentApiKey(), "x-api-key");
    }

    private String callGroq(String originalMessage, FilterDefinition filter, String playerName) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        String currentModel = config.getCurrentModel();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", currentModel);
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "system", "content", system_prompt),
            Map.of("role", "user", "content", buildContextPrompt(playerName, originalMessage, filter))
        ));

        switch (currentModel) {
            case "qwen/qwen3-32b":
                requestBody.put("reasoning_effort", "none");
                break;
        }

        return executeRequest(config.getCurrentEndpoint(), requestBody, config.getCurrentApiKey(), "Bearer ");
    }
    
    private String callLocalAPI(String originalMessage, FilterDefinition filter, String playerName) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getCurrentModel());
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "system", "content", system_prompt),
            Map.of("role", "user", "content", buildContextPrompt(playerName, originalMessage, filter))
        ));
        requestBody.put("max_tokens", config.maxTokens);
        requestBody.put("temperature", config.temperature);
        
        return executeRequest(config.getCurrentEndpoint(), requestBody, null, null);
    }
    
    private String executeRequest(String endpoint, Map<String, Object> requestBody, String apiKey, String authHeader) throws LLMException {
        try {
            HttpPost request = new HttpPost(endpoint);
            request.setEntity(new StringEntity(GSON.toJson(requestBody), ContentType.APPLICATION_JSON));
            
            if (apiKey != null && authHeader != null) {
                if (authHeader.equals("Bearer ")) {
                    request.setHeader("Authorization", "Bearer " + apiKey);
                } else if (authHeader.equals("x-api-key")) {
                    request.setHeader("x-api-key", apiKey);
                    request.setHeader("anthropic-version", "2023-06-01");
                    request.setHeader("Content-Type", "application/json");
                } else {
                    request.setHeader(authHeader, apiKey);
                }
            }
            
            // Log the raw request for debugging
            writeToDebugLog("API REQUEST to " + endpoint + ":");
            writeToDebugLog("Headers: " + Arrays.toString(request.getHeaders()));
            writeToDebugLog("Body: " + GSON.toJson(requestBody));
            
            try (ClassicHttpResponse response = httpClient.executeOpen(null, request, null)) {
                String responseBody = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                
                // Log the raw response
                writeToDebugLog("API RESPONSE (Status " + response.getCode() + "):");
                writeToDebugLog("Raw Response: " + responseBody);
                
                if (response.getCode() != 200) {
                    throw new LLMException("API request failed with status " + response.getCode() + ": " + responseBody);
                }
                
                return parseResponse(responseBody, apiKey != null && authHeader.equals("x-api-key"));
            }
        } catch (IOException e) {
            throw new LLMException("HTTP request failed", e);
        }
    }
    
    private static String stripSurroundingQuotes(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }

        String trimmed = response.trim();

        // Check if starts and ends with quotes (any combination)
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);

            // Remove if surrounded by any type of quotes
            if ((first == '"' || first == '\'' || first == '"' || first == '"' || first == '"') && 
                (last == '"' || last == '\'' || last == '"' || last == '"')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }

        return trimmed;
    }

    private String parseResponse(String responseBody, boolean isAnthropic) throws LLMException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = GSON.fromJson(responseBody, Map.class);

            String content = null;
            
            if (isAnthropic) {
                // Anthropic format
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) responseData.get("content");
                if (contentList != null && !contentList.isEmpty()) {
                    content = (String) contentList.get(0).get("text");
                }
            } else {
                // OpenAI/Local format
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseData.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        content = (String) message.get("content");
                    }
                }
            }
            
            if (content == null) {
                throw new LLMException("Unexpected response format: " + responseBody);
            }

            // Strip surrounding quotes before returning
            String finalResult = stripSurroundingQuotes(content);
            
            // Log the parsed response
            writeToDebugLog("PARSED RESPONSE: \"" + finalResult + "\"");
            
            return finalResult;
            
        } catch (JsonSyntaxException e) {
            throw new LLMException("Failed to parse JSON response: " + responseBody, e);
        }
    }
    
    private boolean checkRateLimit(String playerName) {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        RateLimiter limiter = rateLimiters.computeIfAbsent(playerName, 
            k -> new RateLimiter(config.rateLimitPerMinute, 60000));
        return limiter.tryAcquire();
    }
    
    private String getCacheKey(String message, FilterDefinition filter, String playerName) {
        // Include recent context in cache key
        LinkedList<ChatMessage> history = conversationHistory.get(playerName);
        String contextHash = "";
        if (history != null && !history.isEmpty()) {
            contextHash = String.valueOf(history.toString().hashCode());
        }
        return message.hashCode() + ":" + filter.name + ":" + contextHash;
    }
    
    private void startCacheCleanup() {
        ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LLM-Cache-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        cleanup.scheduleAtFixedRate(() -> {
            ChatFilterConfig config = ChatFilterConfig.getInstance();
            long expireTime = System.currentTimeMillis() - (config.cacheTtlMinutes * 60 * 1000L);
            cache.entrySet().removeIf(entry -> entry.getValue().timestamp < expireTime);
            
            // Also clean up old conversation history (older than 30 minutes)
            long historyExpireTime = System.currentTimeMillis() - (30 * 60 * 1000L);
            conversationHistory.values().forEach(history -> 
                history.removeIf(msg -> msg.timestamp < historyExpireTime));
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    public void shutdown() {
        try {
            writeToDebugLog("LLM Service shutting down...");
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            httpClient.close();
            writeToDebugLog("LLM Service shutdown complete.");
        } catch (Exception e) {
            LOGGER.severe("Error shutting down LLM service: " + e.getMessage());
            writeToDebugLog("ERROR during shutdown: " + e.getMessage());
        }
    }
    
    // Inner classes
    private static class ChatMessage {
        final String content;
        final long timestamp;
        final boolean isTransformed;
        
        ChatMessage(String content, long timestamp, boolean isTransformed) {
            this.content = content;
            this.timestamp = timestamp;
            this.isTransformed = isTransformed;
        }
        
        @Override
        public String toString() {
            return content;
        }
    }
    
    private static class CachedResponse {
        final String response;
        final long timestamp;
        
        CachedResponse(String response, long timestamp) {
            this.response = response;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            ChatFilterConfig config = ChatFilterConfig.getInstance();
            return System.currentTimeMillis() - timestamp > (config.cacheTtlMinutes * 60 * 1000L);
        }
    }
    
    private static class RateLimiter {
        private final Queue<Long> requests = new ConcurrentLinkedQueue<>();
        private final int maxRequests;
        private final long windowMs;
        
        RateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }
        
        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            
            // Remove old requests outside the window
            while (!requests.isEmpty() && now - requests.peek() > windowMs) {
                requests.poll();
            }
            
            if (requests.size() < maxRequests) {
                requests.offer(now);
                return true;
            }
            
            return false;
        }
    }
    
    // Result class for transformations
    public static class TransformationResult {
        public final String transformedMessage;
        public final String followUpMessage; // null if no follow-up needed
        
        public TransformationResult(String transformedMessage, String followUpMessage) {
            this.transformedMessage = transformedMessage;
            this.followUpMessage = followUpMessage;
        }
        
        public boolean hasFollowUp() {
            return followUpMessage != null && !followUpMessage.trim().isEmpty();
        }
    }
    
    public static class LLMException extends Exception {
        public LLMException(String message) {
            super(message);
        }
        
        public LLMException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
