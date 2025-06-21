package com.chatfilter.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Collection;

public class ChatFilterTest {
    
    private FilterManager filterManager;
    
    @BeforeEach
    void setUp() {
        filterManager = FilterManager.getInstance();
    }
    
    @Test
    void testFilterDisplayNames() {
        // Test with any available filters, not hardcoded ones
        Collection<FilterDefinition> filters = filterManager.getAllFilters();
        
        for (FilterDefinition filter : filters) {
            assertNotNull(filter.getDisplayName());
            assertFalse(filter.getDisplayName().isEmpty());
            
            // Display name should be lowercase with spaces instead of underscores
            String expected = filter.name.toLowerCase().replace('_', ' ');
            assertEquals(expected, filter.getDisplayName());
        }
    }
    
    @Test
    void testFilterStructure() {
        Collection<FilterDefinition> filters = filterManager.getAllFilters();
        assertFalse(filters.isEmpty(), "Should have at least some filters loaded");
        
        for (FilterDefinition filter : filters) {
            assertNotNull(filter.name, "Filter name should not be null");
            assertNotNull(filter.prompt, "Filter prompt should not be null");
            assertNotNull(filter.emoji, "Filter emoji should not be null");
            assertNotNull(filter.color, "Filter color should not be null");
            
            assertFalse(filter.name.isEmpty(), "Filter name should not be empty");
            assertFalse(filter.prompt.isEmpty(), "Filter prompt should not be empty");
        }
    }
    
    @Test
    void testSpecificFiltersIfTheyExist() {
        // Test specific filters only if they exist in the config
        testFilterIfExists("PIRATE", "pirate");
        testFilterIfExists("SHAKESPEAREAN", "shakespeare");
        testFilterIfExists("CORPORATE_SPEAK", "corporate", "business");
        testFilterIfExists("ROBOT", "robot");
    }
    
    private void testFilterIfExists(String filterName, String... expectedWords) {
        FilterDefinition filter = filterManager.getFilter(filterName);
        if (filter != null) {
            String prompt = filter.prompt.toLowerCase(); // Use field directly, not getPrompt()
            boolean foundExpectedWord = false;
            
            for (String word : expectedWords) {
                if (prompt.contains(word.toLowerCase())) {
                    foundExpectedWord = true;
                    break;
                }
            }
            
            assertTrue(foundExpectedWord, 
                "Filter " + filterName + " should contain one of: " + String.join(", ", expectedWords));
        }
    }
    
    @Test
    void testFilterEnabled() {
        Collection<FilterDefinition> enabledFilters = filterManager.getEnabledFilters();
        assertFalse(enabledFilters.isEmpty(), "Should have at least some enabled filters");
        
        for (FilterDefinition filter : enabledFilters) {
            assertTrue(filter.enabled, "All filters in enabled list should be enabled");
        }
    }
    
    @Test
    void testFilterManager() {
        assertNotNull(filterManager.getAllFilters());
        assertFalse(filterManager.getAllFilters().isEmpty());
        
        // Test that we can get a filter (since there's no getRandomFilter method)
        Collection<FilterDefinition> enabledFilters = filterManager.getEnabledFilters();
        if (!enabledFilters.isEmpty()) {
            FilterDefinition firstFilter = enabledFilters.iterator().next();
            assertNotNull(firstFilter, "Should be able to get a filter");
            assertTrue(firstFilter.enabled, "Filter should be enabled");
        }
    }
    
    @Test
    void testFullPromptGeneration() {
        Collection<FilterDefinition> enabledFilters = filterManager.getEnabledFilters();
        if (!enabledFilters.isEmpty()) {
            FilterDefinition filter = enabledFilters.iterator().next();
            String testMessage = "Hello world!";
            String fullPrompt = filter.getFullPrompt(testMessage);
            
            assertNotNull(fullPrompt);
            assertTrue(fullPrompt.contains(testMessage), "Full prompt should contain original message");
            assertTrue(fullPrompt.contains(filter.prompt), "Full prompt should contain filter prompt");
        }
    }
    
    @Test
    void testChatColorConversion() {
        Collection<FilterDefinition> filters = filterManager.getAllFilters();
        
        for (FilterDefinition filter : filters) {
            // Should not throw exception
            assertNotNull(filter.getChatColor(), "Should be able to get ChatColor for " + filter.name);
        }
    }
    
    @Test
    void testLegacyEnumCompatibility() {
        // Test that the legacy enum still works for OPPOSITE
        assertEquals("OPPOSITE", ChatFilter.OPPOSITE.name());
        assertNotNull(ChatFilter.OPPOSITE.getPrompt());
        assertNotNull(ChatFilter.OPPOSITE.getEmoji());
        assertNotNull(ChatFilter.OPPOSITE.getColor());
        assertNotNull(ChatFilter.OPPOSITE.getDisplayName());
        
        // Test static methods
        assertNotNull(ChatFilter.getAllFilters());
        assertNotNull(ChatFilter.getFilterNames());
    }
    
    @Test
    void testFilterManagerMethods() {
        // Test the actual methods that exist in FilterManager
        assertNotNull(filterManager.getFilterNames());
        assertNotNull(filterManager.getEnabledFilterNames());
        assertFalse(filterManager.getFilterNames().isEmpty());
        
        // Test adding and removing filters
        String testFilterName = "TEST_FILTER";
        filterManager.addCustomFilter(testFilterName, "Test prompt", "🧪", "WHITE");
        assertTrue(filterManager.getFilterNames().contains(testFilterName));
        
        filterManager.removeFilter(testFilterName);
        assertFalse(filterManager.getFilterNames().contains(testFilterName));
    }
    
    @Test
    void testFilterEnabling() {
        // Test enabling/disabling filters
        Collection<FilterDefinition> filters = filterManager.getAllFilters();
        if (!filters.isEmpty()) {
            FilterDefinition firstFilter = filters.iterator().next();
            String filterName = firstFilter.name;
            
            // Test disable
            filterManager.setFilterEnabled(filterName, false);
            FilterDefinition disabledFilter = filterManager.getFilter(filterName);
            assertFalse(disabledFilter.enabled);
            
            // Test re-enable
            filterManager.setFilterEnabled(filterName, true);
            FilterDefinition enabledFilter = filterManager.getFilter(filterName);
            assertTrue(enabledFilter.enabled);
        }
    }
}
