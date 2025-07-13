package com.randomdialogue.filter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.randomdialogue.RandomDialogueMod;

public class FilterManager {
    private static final Logger LOGGER = Logger.getLogger(FilterManager.class.getName());
    private static final String FILTERS_FILE = "filters.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataFolderPath;
    private Map<String, FilterDefinition> filters = new HashMap<>();

    public FilterManager(Path dataFolderPath) {
        this.dataFolderPath = dataFolderPath;
        loadFilters();
    }

    public void loadFilters() {
        Path filtersFile = getFiltersFile();
        Map<String, FilterDefinition> loadedFilters = new HashMap<>();

        if (Files.exists(filtersFile)) {
            try {
                String json = Files.readString(filtersFile);
                Type mapType = new TypeToken<Map<String, FilterDefinition>>() {
                }.getType();

                Map<String, FilterDefinition> fileFilters = GSON
                        .fromJson(json, mapType);

                if (fileFilters != null) {
                    loadedFilters.putAll(fileFilters);
                }
                LOGGER.info("Loaded " + loadedFilters.size()
                        + " filters from "
                        + filtersFile);
            } catch (IOException e) {
                LOGGER.severe("I/O error when reading " + filtersFile
                        + ": "
                        + e.getMessage()
                        + ". Attempting to use default filters.");
            } catch (OutOfMemoryError e) {
                LOGGER.severe(filtersFile + " is extremely large! Ran out of memory to read: "
                        + e.getMessage()
                        + ". Attempting to use default filters. By the way, how did you even come up with that many filters???");
            } catch (SecurityException e) {
                LOGGER.severe("Security manager denied access to "
                        + filtersFile
                        + ". Attempting to create default filters.");
            }
        } else {
            LOGGER.info("Filters file not found at " + filtersFile
                    + ". Attempting to create default filters.");
        }

        if (loadedFilters.isEmpty()) {
            // No valid file content - use all defaults
            loadedFilters.putAll(getDefaultFilters());
            LOGGER.info("No valid filters file found, using all default filters");
        } else {
            // File has content - add any NEW defaults that weren't in the file
            Map<String, FilterDefinition> defaults = getDefaultFilters();

            for (String defaultKey : defaults.keySet()) {
                if (!loadedFilters.containsKey(defaultKey)) {
                    loadedFilters.put(defaultKey, defaults
                            .get(defaultKey));
                    LOGGER.info("Added new default filter: "
                            + defaultKey);
                }
            }

            LOGGER.info("Merged file filters with new defaults. Total: "
                    + loadedFilters.size());
        }

        this.filters = loadedFilters;

        if (this.filters.isEmpty()) {
            createDefaultFilters();
        }
        saveFilters();

        // Result: this.filters contains exactly what was loaded/merged
    }

    private Map<String, FilterDefinition> getDefaultFilters() {
        Map<String, FilterDefinition> defaultFilters = new HashMap<>();
        // Create all the default filters
        defaultFilters.put("TRUMP_STYLE", new FilterDefinition("TRUMP_STYLE",
                "Transform this message using Donald Trump's most controversial political talking points and rally rhetoric. Keep it brief and conversational - rewrite the message, don't expand it into a speech.\nSPECIAL NOTE FOR TRUMP_STYLE: Keep it brief! Transform the TONE only. For 'thanks' say something like 'Thanks, tremendous help!' NOT 'You're welcome, folks...'",
                "🇺🇸",
                "GOLD",
                true));
        defaultFilters.put("OPPOSITE",
                new FilterDefinition("OPPOSITE",
                        "Rewrite this to mean the exact opposite while keeping it natural",
                        "🔄",
                        "AQUA",
                        true));
        defaultFilters.put("OVERLY_KIND", new FilterDefinition("OVERLY_KIND",
                "Rewrite this to be extremely kind, supportive, and wholesome. Add compliments where possible",
                "💖",
                "LIGHT_PURPLE",
                true));
        defaultFilters.put("DRAMATICALLY_SAD", new FilterDefinition("DRAMATICALLY_SAD",
                "Rewrite this as if written by someone who's having the worst day ever, very melancholic and dramatic",
                "😭",
                "BLUE",
                true));
        defaultFilters.put("CORPORATE_SPEAK", new FilterDefinition("CORPORATE_SPEAK",
                "Rewrite this in corporate business jargon with lots of synergy and paradigm shifts",
                "💼",
                "GRAY",
                true));
        defaultFilters.put("PIRATE", new FilterDefinition("PIRATE",
                "Rewrite this as if spoken by an enthusiastic pirate, with 'arrr' and nautical terms",
                "🏴‍☠️",
                "GOLD",
                true));
        defaultFilters.put("SHAKESPEAREAN", new FilterDefinition("SHAKESPEAREAN",
                "Rewrite this in elaborate Shakespearean English with flowery language",
                "🎭",
                "DARK_PURPLE",
                true));
        defaultFilters.put("OVERLY_EXCITED", new FilterDefinition("OVERLY_EXCITED",
                "Rewrite this with MAXIMUM ENTHUSIASM!!! Use lots of exclamation points and caps",
                "🎉",
                "YELLOW",
                true));
        defaultFilters.put("CONSPIRACY_THEORIST", new FilterDefinition("CONSPIRACY_THEORIST",
                "Rewrite this as if everything is a conspiracy and add suspicious undertones\nSPECIAL NOTE FOR CONSPIRACY_THEORIST: Keep the original message structure. Add suspicious tone bot don't expand into speeches. For 'good morning' say 'Morning, sheeple.' NOT long conspiracy speeches.",
                "👁️",
                "RED",
                true));
        defaultFilters.put("GRANDMA", new FilterDefinition("GRANDMA",
                "Rewrite this as if spoken by a sweet grandma who's worried about everyone\nSPECIAL NOTE FOR GRANDMA: Transform the greeting style only. For 'hello everyone' say 'Hello, dear hearts' NOT 'Hello! How are you all?'",
                "👵",
                "GREEN",
                true));
        defaultFilters.put("ROBOT", new FilterDefinition("ROBOT",
                "Rewrite this as if spoken by a formal robot trying to understand human emotions\nSPECIAL NOTE FOR ROBOT: Keep it SHORT and robotic. For 'good morning' say 'GOOD MORNING. GREETING INITIATED.' NOT long explanations about human emotions.",
                "🤖",
                "DARK_GRAY",
                true));
        defaultFilters.put("VALLEY_GIRL", new FilterDefinition("VALLEY_GIRL",
                "Rewrite this in valley girl speak with lots of 'like' and 'totally'",
                "💅",
                "LIGHT_PURPLE",
                true));
        defaultFilters.put("NOIR_DETECTIVE", new FilterDefinition("NOIR_DETECTIVE",
                "Rewrite this as if spoken by a 1940s film noir detective, dark and mysterious",
                "🕵️",
                "DARK_RED",
                true));
        defaultFilters.put("YOUR_MOM_JOKE", new FilterDefinition("YOUR_MOM_JOKE",
                "Turn this into a 'your mom' joke. Be creative and make it relate to the original message somehow",
                "🤱",
                "RED",
                true));
        defaultFilters.put("PASSIVE_AGGRESSIVE", new FilterDefinition("PASSIVE_AGGRESSIVE",
                "Rewrite this to be extremely passive-aggressive, with fake politeness hiding obvious annoyance",
                "😤",
                "DARK_GREEN",
                true));
        defaultFilters.put("OVERSHARING", new FilterDefinition("OVERSHARING",
                "Rewrite this but add way too much personal information that nobody asked for",
                "📢",
                "LIGHT_PURPLE",
                true));
        defaultFilters.put("CONSPIRACY_FLAT_EARTH", new FilterDefinition(
                "CONSPIRACY_FLAT_EARTH",
                "Rewrite this but somehow relate it to flat earth theories and government cover-ups",
                "🌍",
                "DARK_BLUE",
                true));
        defaultFilters.put("MILLENNIAL_CRISIS", new FilterDefinition("MILLENNIAL_CRISIS",
                "Rewrite this with millennial existential dread, student loans, and avocado toast references",
                "☕",
                "GRAY",
                true));
        defaultFilters.put("BOOMER_COMPLAINTS", new FilterDefinition("BOOMER_COMPLAINTS",
                "Rewrite this like an angry boomer complaining about 'kids these days' and technology",
                "👴",
                "DARK_GRAY",
                true));
        defaultFilters.put("INFLUENCER", new FilterDefinition("INFLUENCER",
                "Rewrite this like a social media influencer trying to sell something, with lots of hashtags",
                "📱",
                "GOLD",
                true));
        defaultFilters.put("CAVEMAN", new FilterDefinition("CAVEMAN",
                "Rewrite this in simple caveman speak with basic words and concepts",
                "🦴",
                "DARK_AQUA",
                true));
        defaultFilters.put("SEDUCTIVE", new FilterDefinition("SEDUCTIVE",
                "Rewrite this message in a highly seductive, sultry tone with heavy innuendo, suggestive language, and provocative flirtation. Make it sound steamy and alluring",
                "😘",
                "LIGHT_PURPLE",
                true));
        return defaultFilters;
    }

    private void createDefaultFilters() {
        filters.clear();
        filters.putAll(getDefaultFilters());
    }

    public void saveFilters() {
        Path filtersFile = getFiltersFile();

        try {
            // Ensure directory exists
            Files.createDirectories(filtersFile.getParent());

            String json = GSON.toJson(filters);
            Files.writeString(filtersFile, json, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Saved " + filters.size() + " filters to " + filtersFile);

        } catch (IOException e) {
            LOGGER.severe("Failed to save filters to " + filtersFile + ": "
                    + e.getMessage());
        }
    }

    private void addFilter(String name, String prompt, String emoji, String color,
            boolean enabled) {
        filters.put(name, new FilterDefinition(name, prompt, emoji, color,
                enabled));
    }

    public FilterDefinition getFilter(String name) {
        return filters.get(name.toUpperCase());
    }

    public Collection<FilterDefinition> getAllFilters() {
        return filters.values();
    }

    public Collection<FilterDefinition> getEnabledFilters() {
        return filters.values().stream()
                .filter(f -> f.enabled)
                .toList();
    }

    public Set<String> getFilterNames() {
        return filters.keySet();
    }

    public Set<String> getEnabledFilterNames() {
        return filters.entrySet().stream()
                .filter(entry -> entry.getValue().enabled)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void addCustomFilter(String name, String prompt, String emoji, String color) {
        filters.put(name.toUpperCase(), new FilterDefinition(name.toUpperCase(),
                prompt,
                emoji,
                color,
                true));
        saveFilters();
        LOGGER.info("Added custom filter: " + name);
    }

    public boolean removeFilter(String name) {
        FilterDefinition removed = filters.remove(name.toUpperCase());
        if (removed != null) {
            saveFilters();
            LOGGER.info("Removed filter: " + name);
            return true;
        }
        return false;
    }

    public void setFilterEnabled(String name, boolean enabled) {
        FilterDefinition filter = filters.get(name.toUpperCase());
        if (filter != null) {
            filter.enabled = enabled;
            saveFilters();
            LOGGER.info("Set filter " + name + " enabled: " + enabled);
        }
    }

    public void reloadFilters() {
        LOGGER.info("Reloading filters from file...");
        loadFilters();
    }

    private Path getFiltersFile() {
        return dataFolderPath.resolve(FILTERS_FILE);
    }
}
