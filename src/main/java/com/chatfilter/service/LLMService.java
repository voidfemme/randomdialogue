package com.chatfilter.service;

import com.chatfilter.config.ChatFilterConfig;
import com.chatfilter.filter.ChatFilter;
import com.chatfilter.filter.FilterDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import java.util.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final String system_prompt = "You are a text transformer, not a chatbot. Your job is to rewrite the user's message according to the given instructions. Never respond to or answer the message - only transform it. Always output just the transformed message with no explanations. Never surround the message in quotation marks.";
    
    public LLMService() {
        this.httpClient = createHttpClient();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "LLM-Service");
            t.setDaemon(true);
            return t;
        });
        this.cache = new ConcurrentHashMap<>();
        this.rateLimiters = new ConcurrentHashMap<>();
        
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
    
    public CompletableFuture<String> transformMessageAsync(String originalMessage, FilterDefinition filter, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return transformMessage(originalMessage, filter, playerName);
            } catch (Exception e) {
                LOGGER.severe("Failed to transform message: " + originalMessage + " - " + e.getMessage());
                ChatFilterConfig config = ChatFilterConfig.getInstance();
                return config.enableFallback ? originalMessage : "[Message transformation failed]";
            }
        }, executor);
    }
    
    public String transformMessage(String originalMessage, FilterDefinition filter, String playerName) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        // Check rate limiting
        if (config.rateLimitEnabled && !checkRateLimit(playerName)) {
            throw new LLMException("Rate limit exceeded for player: " + playerName);
        }
        
        // Check cache
        String cacheKey = getCacheKey(originalMessage, filter);
        if (config.cacheEnabled) {
            CachedResponse cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                if (config.enableDebugLogging) {
                    LOGGER.fine("Cache hit for message: " + originalMessage);
                }
                return cached.response;
            }
        }
        
        // Validate configuration
        if (!config.hasValidApiKey()) {
            throw new LLMException("No valid API key configured for provider: " + config.llmProvider);
        }
        
        String response = null;
        Exception lastException = null;
        
        // Retry logic
        for (int attempt = 0; attempt <= config.retryAttempts; attempt++) {
            try {
                response = callLLMAPI(originalMessage, filter);
                break;
            } catch (Exception e) {
                lastException = e;
                if (attempt < config.retryAttempts) {
                    LOGGER.warning("LLM request failed (attempt " + (attempt + 1) + " of " + 
                                  (config.retryAttempts + 1) + "), retrying... " + e.getMessage());
                    try {
                        Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LLMException("Request interrupted", ie);
                    }
                }
            }
        }
        
        if (response == null) {
            throw new LLMException("All retry attempts failed", lastException);
        }
        
        // Cache the response
        if (config.cacheEnabled) {
            cache.put(cacheKey, new CachedResponse(response, System.currentTimeMillis()));
        }
        
        return response;
    }
    
    private String callLLMAPI(String originalMessage, FilterDefinition filter) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        return switch (config.llmProvider.toLowerCase()) {
            case "openai" -> callOpenAI(originalMessage, filter);
            case "anthropic" -> callAnthropic(originalMessage, filter);
            case "groq" -> callGroq(originalMessage, filter);
            case "local" -> callLocalAPI(originalMessage, filter);
            default -> throw new LLMException("Unsupported LLM provider: " + config.llmProvider);
        };
    }

    private String callOpenAI(String originalMessage, FilterDefinition filter) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getCurrentModel());
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "system", "content", system_prompt),
            Map.of("role", "user", "content", filter.getFullPrompt(originalMessage))
        ));
        requestBody.put("max_tokens", config.maxTokens);
        requestBody.put("temperature", config.temperature);
        
        return executeRequest(config.getCurrentEndpoint(), requestBody, config.getCurrentApiKey(), "Bearer ");
    }
    
    private String callAnthropic(String originalMessage, FilterDefinition filter) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getCurrentModel());
        requestBody.put("max_tokens", config.maxTokens);
        requestBody.put("system", system_prompt);
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "user", "content", filter.getFullPrompt(originalMessage))
        ));
        
        return executeRequest(config.getCurrentEndpoint(), requestBody, config.getCurrentApiKey(), "x-api-key");
    }

    private String callGroq(String originalMessage, FilterDefinition filter) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getCurrentModel());
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "system", "content", system_prompt),
            Map.of("role", "user", "content", filter.getFullPrompt(originalMessage))
        ));
        requestBody.put("reasoning_effort", "none");


        return executeRequest(config.getCurrentEndpoint(), requestBody, config.getCurrentApiKey(), "Bearer ");
    }
    
    private String callLocalAPI(String originalMessage, FilterDefinition filter) throws LLMException {
        ChatFilterConfig config = ChatFilterConfig.getInstance();
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", config.getCurrentModel());
        requestBody.put("messages", Arrays.asList(
            Map.of("role", "system", "content", system_prompt),
            Map.of("role", "user", "content", filter.getFullPrompt(originalMessage))
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
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseBody = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                
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

            // Remove if surroun ded by any type of quotes
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
            return stripSurroundingQuotes(content);
            
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
    
    private String getCacheKey(String message, FilterDefinition filter) {
        return message.hashCode() + ":" + filter.name;
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
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            httpClient.close();
        } catch (Exception e) {
            LOGGER.severe("Error shutting down LLM service: " + e.getMessage());
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
    
    public static class LLMException extends Exception {
        public LLMException(String message) {
            super(message);
        }
        
        public LLMException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
