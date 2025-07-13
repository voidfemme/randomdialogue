package com.randomdialogue.filter;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.junit.jupiter.api.Assertions.*;

class FilterManagerTest {

    @TempDir
    Path tempDir;

    private FilterManager filterManager;
    private Path testFiltersFile;

    @BeforeEach
    void setUp() throws IOException {
        testFiltersFile = tempDir.resolve("filters.json");

        // Ensure the file does not exist before each test
        Files.deleteIfExists(testFiltersFile);

        // Create a new FilterManager instance for testing, pointing to our temp file
        filterManager = new FilterManager(tempDir);
    }

    private void createFiltersFile(Map<String, FilterDefinition> filters) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(filters);
        Files.writeString(testFiltersFile, json);
    }

    @Test
    void testReloadFilters_PreservesCustomFilters() throws Exception {
        // 1. Create a file with some custom filters and some that override defaults
        Map<String, FilterDefinition> initialFilters = Map.of(
                "CUSTOM_ONE", new FilterDefinition("CUSTOM_ONE", "Custom prompt 1", "C1", "RED", true),
                "PIRATE", new FilterDefinition("PIRATE", "Custom Pirate prompt", "🏴‍☠️", "BLUE", true), // Overrides
                                                                                                         // default
                "TEST_DISABLED",
                new FilterDefinition("TEST_DISABLED", "A disabled custom filter", "🚫", "BLACK", false));
        createFiltersFile(initialFilters);

        // 2. Load filters for the first time (should merge with defaults)
        filterManager.loadFilters();

        // Verify custom filters are present and default override is applied
        assertNotNull(filterManager.getFilter("CUSTOM_ONE"), "Custom filter CUSTOM_ONE should be present");
        assertEquals("Custom prompt 1", filterManager.getFilter("CUSTOM_ONE").prompt);
        assertNotNull(filterManager.getFilter("PIRATE"), "PIRATE filter should be present");
        assertEquals("Custom Pirate prompt", filterManager.getFilter("PIRATE").prompt,
                "PIRATE filter should have custom prompt");
        assertNotNull(filterManager.getFilter("ROBOT"), "Default filter ROBOT should also be present"); // Check a
                                                                                                        // default

        // 3. Add another custom filter after initial load
        filterManager.addCustomFilter("NEW_CUSTOM", "Newly added custom filter", "✨", "GREEN");
        assertNotNull(filterManager.getFilter("NEW_CUSTOM"), "Newly added custom filter should be present");

        // 4. Reload filters (this is the critical step for the bug)
        filterManager.reloadFilters();

        // 5. Verify all filters (initial custom, overridden default, and newly added)
        // are still present and correct
        assertNotNull(filterManager.getFilter("CUSTOM_ONE"), "CUSTOM_ONE should persist after reload");
        assertEquals("Custom prompt 1", filterManager.getFilter("CUSTOM_ONE").prompt);

        assertNotNull(filterManager.getFilter("PIRATE"), "PIRATE filter (custom override) should persist after reload");
        assertEquals("Custom Pirate prompt", filterManager.getFilter("PIRATE").prompt,
                "PIRATE filter should retain custom prompt after reload");

        assertNotNull(filterManager.getFilter("NEW_CUSTOM"), "NEW_CUSTOM should persist after reload");
        assertEquals("Newly added custom filter", filterManager.getFilter("NEW_CUSTOM").prompt);

        assertNotNull(filterManager.getFilter("ROBOT"), "Default filter ROBOT should still be present after reload");
        // Ensure a default filter's prompt is still its default value (not overridden
        // unless specified)
        assertNotEquals("Custom Pirate prompt", filterManager.getFilter("ROBOT").prompt,
                "ROBOT filter should have its default prompt");
    }

    @Test
    void testInitialLoad_CreatesDefaultsWhenMissing() {
        // No file exists initially, so constructor should create it with defaults
        assertTrue(Files.exists(testFiltersFile));
        assertFalse(filterManager.getAllFilters().isEmpty(), "Default filters should be created");

        // Should have some expected default filters
        assertNotNull(filterManager.getFilter("PIRATE"), "Default filter 'PIRATE' should exist");
        assertNotNull(filterManager.getFilter("ROBOT"), "Default filter 'ROBOT' should exist");
    }

    @Test
    void testCustomFilterPersistence() throws Exception {
        // Add a custom filter
        filterManager.addCustomFilter("TEST_CUSTOM", "Be super custom", "🧪", "PURPLE");

        // Verify it exists
        assertNotNull(filterManager.getFilter("TEST_CUSTOM"));

        // A new FilterManager instance should load from the file
        FilterManager newFilterManager = new FilterManager(tempDir);

        // Verify it persists
        assertNotNull(newFilterManager.getFilter("TEST_CUSTOM"), "Custom filter should persist across instances");
        assertEquals("Be super custom", newFilterManager.getFilter("TEST_CUSTOM").prompt);
    }
}
