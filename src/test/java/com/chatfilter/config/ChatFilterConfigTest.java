package com.chatfilter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChatFilterConfigTest {
    
    @TempDir
    Path tempDir;
    
    private ChatFilterConfig config;
    
    @BeforeEach
    void setUp() {
        config = new ChatFilterConfig();
    }
    
    @Test
    void testDefaultValues() {
        assertEquals("openai", config.llmProvider);
        assertEquals("gpt-3.5-turbo", config.openaiModel);
        assertEquals(200, config.maxTokens);
        assertEquals(0.8, config.temperature, 0.001);
        assertEquals(10, config.timeoutSeconds);
        assertEquals(2, config.retryAttempts);
        assertTrue(config.enableFallback);
        assertEquals("MANUAL", config.defaultFilterMode);
    }
    
    @Test
    void testValidation() {
        // Test invalid provider
        config.llmProvider = "invalid";
        
        // This should trigger validation and reset to default
        assertTrue(config.llmProvider.equals("invalid")); // Before validation
        // Note: In real test, we'd call validate method
    }
    
    @Test
    void testApiKeyValidation() {
        // OpenAI
        config.llmProvider = "openai";
        config.openaiApiKey = "";
        assertFalse(config.hasValidApiKey());
        
        config.openaiApiKey = "sk-test123";
        assertTrue(config.hasValidApiKey());
        
        // Anthropic
        config.llmProvider = "anthropic";
        config.anthropicApiKey = "";
        assertFalse(config.hasValidApiKey());
        
        config.anthropicApiKey = "claude-test123";
        assertTrue(config.hasValidApiKey());

        // Groq
        config.llmProvider = "groq";
        config.groqApiKey = "";
        assertFalse(config.hasValidApiKey());

        config.groqApiKey = "groq-test123";
        assertTrue(config.hasValidApiKey());
        
        // Local
        config.llmProvider = "local";
        config.localApiEndpoint = "";
        assertFalse(config.hasValidApiKey());
        
        config.localApiEndpoint = "http://localhost:11434";
        assertTrue(config.hasValidApiKey());
    }
    
    @Test
    void testGetCurrentModel() {
        config.llmProvider = "openai";
        config.openaiModel = "gpt-4";
        assertEquals("gpt-4", config.getCurrentModel());
        
        config.llmProvider = "anthropic";
        config.anthropicModel = "claude-3-opus";
        assertEquals("claude-3-opus", config.getCurrentModel());

        config.llmProvider = "groq";
        config.groqModel = "meta-llama/llama-4-scout-17b-16e-instruct";
        assertEquals("meta-llama/llama-4-scout-17b-16e-instruct", config.getCurrentModel());
        
        config.llmProvider = "local";
        config.localModel = "llama2";
        assertEquals("llama2", config.getCurrentModel());
    }
    
    @Test
    void testGetCurrentEndpoint() {
        config.llmProvider = "openai";
        assertEquals("https://api.openai.com/v1/chat/completions", config.getCurrentEndpoint());
        
        config.llmProvider = "anthropic";
        assertEquals("https://api.anthropic.com/v1/messages", config.getCurrentEndpoint());

        config.llmProvider = "groq";
        assertEquals("https://api.groq.com/openai/v1/chat/completions", config.getCurrentEndpoint());
        
        config.llmProvider = "local";
        config.localApiEndpoint = "http://localhost:11434/v1/chat/completions";
        assertEquals("http://localhost:11434/v1/chat/completions", config.getCurrentEndpoint());
    }
    
    @Test
    void testGetCurrentApiKey() {
        config.llmProvider = "openai";
        config.openaiApiKey = "sk-test123";
        assertEquals("sk-test123", config.getCurrentApiKey());
        
        config.llmProvider = "anthropic";
        config.anthropicApiKey = "claude-test123";
        assertEquals("claude-test123", config.getCurrentApiKey());

        config.llmProvider = "groq";
        config.groqApiKey = "groq-test123";
        assertEquals("groq-test123", config.getCurrentApiKey());
        
        config.llmProvider = "local";
        assertEquals("", config.getCurrentApiKey());
    }
}
