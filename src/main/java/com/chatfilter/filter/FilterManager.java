package com.chatfilter.filter;

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

import com.chatfilter.ChatFilterMod;

public class FilterManager {
    private static final Logger LOGGER = Logger.getLogger(FilterManager.class.getName());
    private static final String FILTERS_FILE = "filters.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FilterManager instance;
    private Map<String, FilterDefinition> filters = new HashMap<>();

    private FilterManager() {
        loadFilters();
    }

    public static FilterManager getInstance() {
        if (instance == null) {
            instance = new FilterManager();
        }
        return instance;
    }

    public void loadFilters() {
        Path filtersFile = getFiltersFile();

        if (Files.exists(filtersFile)) {
            try {
                String json = Files.readString(filtersFile);
                Type mapType = new TypeToken<Map<String, FilterDefinition>>() {
                }.getType();
                Map<String, FilterDefinition> loadedFilters = GSON.fromJson(json, mapType);

                if (loadedFilters != null) {
                    filters = loadedFilters;
                    LOGGER.info("Loaded " + filters.size() + " filters from " + filtersFile);
                } else {
                    createDefaultFilters();
                }
            } catch (Exception e) {
                LOGGER.severe("Failed to load filters from " + filtersFile + ": " + e.getMessage());
                createDefaultFilters();
            }
        } else {
            LOGGER.info("Filters file not found, creating default filters at " + filtersFile);
            createDefaultFilters();
        }
    }

    public void saveFilters() {
        Path filtersFile = getFiltersFile();

        try {
            // Ensure directory exists
            Files.createDirectories(filtersFile.getParent());

            String json = GSON.toJson(filters);
            Files.writeString(filtersFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Saved " + filters.size() + " filters to " + filtersFile);

        } catch (IOException e) {
            LOGGER.severe("Failed to save filters to " + filtersFile + ": " + e.getMessage());
        }
    }

    private void createDefaultFilters() {
        filters.clear();

        // Create all the default filters
        addFilter("TRUMP_STYLE",
                "Transform this message using Donald Trump's most controversial political talking points and rally rhetoric. Keep it brief and conversational - rewrite the message, don't expand it into a speech.\nSPECIAL NOTE FOR TRUMP_STYLE: Keep it brief! Transform the TONE only. For 'thanks' say something like 'Thanks, tremendous help!' NOT 'You're welcome, folks...'",
                "🇺🇸", "GOLD", true);
        addFilter("OPPOSITE", "Rewrite this to mean the exact opposite while keeping it natural", "🔄", "AQUA", true);
        addFilter("OVERLY_KIND",
                "Rewrite this to be extremely kind, supportive, and wholesome. Add compliments where possible", "💖",
                "LIGHT_PURPLE", true);
        addFilter("DRAMATICALLY_SAD",
                "Rewrite this as if written by someone who's having the worst day ever, very melancholic and dramatic",
                "😭", "BLUE", true);
        addFilter("CORPORATE_SPEAK",
                "Rewrite this in corporate business jargon with lots of synergy and paradigm shifts", "💼", "GRAY",
                true);
        addFilter("PIRATE", "Rewrite this as if spoken by an enthusiastic pirate, with 'arrr' and nautical terms",
                "🏴‍☠️", "GOLD", true);
        addFilter("SHAKESPEAREAN", "Rewrite this in elaborate Shakespearean English with flowery language", "🎭",
                "DARK_PURPLE", true);
        addFilter("OVERLY_EXCITED", "Rewrite this with MAXIMUM ENTHUSIASM!!! Use lots of exclamation points and caps",
                "🎉", "YELLOW", true);
        addFilter("CONSPIRACY_THEORIST",
                "Rewrite this as if everything is a conspiracy and add suspicious undertones\nSPECIAL NOTE FOR CONSPIRACY_THEORIST: Keep the original message structure. Add suspicious tone bot don't expand into speeches. For 'good morning' say 'Morning, sheeple.' NOT long conspiracy speeches.",
                "👁️", "RED", true);
        addFilter("GRANDMA",
                "Rewrite this as if spoken by a sweet grandma who's worried about everyone\nSPECIAL NOTE FOR GRANDMA: Transform the greeting style only. For 'hello everyone' say 'Hello, dear hearts' NOT 'Hello! How are you all?'",
                "👵", "GREEN", true);
        addFilter("ROBOT",
                "Rewrite this as if spoken by a formal robot trying to understand human emotions\nSPECIAL NOTE FOR ROBOT: Keep it SHORT and robotic. For 'good morning' say 'GOOD MORNING. GREETING INITIATED.' NOT long explanations about human emotions.",
                "🤖", "DARK_GRAY", true);
        addFilter("VALLEY_GIRL", "Rewrite this in valley girl speak with lots of 'like' and 'totally'", "💅",
                "LIGHT_PURPLE", true);
        addFilter("NOIR_DETECTIVE", "Rewrite this as if spoken by a 1940s film noir detective, dark and mysterious",
                "🕵️", "DARK_RED", true);
        addFilter("YOUR_MOM_JOKE",
                "Turn this into a 'your mom' joke. Be creative and make it relate to the original message somehow",
                "🤱", "RED", true);
        addFilter("PASSIVE_AGGRESSIVE",
                "Rewrite this to be extremely passive-aggressive, with fake politeness hiding obvious annoyance", "😤",
                "DARK_GREEN", true);
        addFilter("OVERSHARING", "Rewrite this but add way too much personal information that nobody asked for", "📢",
                "LIGHT_PURPLE", true);
        addFilter("CONSPIRACY_FLAT_EARTH",
                "Rewrite this but somehow relate it to flat earth theories and government cover-ups", "🌍", "DARK_BLUE",
                true);
        addFilter("MILLENNIAL_CRISIS",
                "Rewrite this with millennial existential dread, student loans, and avocado toast references", "☕",
                "GRAY", true);
        addFilter("BOOMER_COMPLAINTS",
                "Rewrite this like an angry boomer complaining about 'kids these days' and technology", "👴",
                "DARK_GRAY", true);
        addFilter("INFLUENCER",
                "Rewrite this like a social media influencer trying to sell something, with lots of hashtags", "📱",
                "GOLD", true);
        addFilter("CAVEMAN", "Rewrite this in simple caveman speak with basic words and concepts", "🦴", "DARK_AQUA",
                true);
        addFilter("SEDUCTIVE",
                "Rewrite this message in a highly seductive, sultry tone with heavy innuendo, suggestive language, and provocative flirtation. Make it sound steamy and alluring",
                "😘", "LIGHT_PURPLE", true);

        saveFilters();
    }

    private void addFilter(String name, String prompt, String emoji, String color, boolean enabled) {
        filters.put(name, new FilterDefinition(name, prompt, emoji, color, enabled));
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
        filters.put(name.toUpperCase(), new FilterDefinition(name.toUpperCase(), prompt, emoji, color, true));
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
        return ChatFilterMod.getInstance().getDataFolder().toPath().resolve(FILTERS_FILE);
    }
}
