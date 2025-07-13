package com.randomdialogue.config;

public enum FilterMode {
    DISABLED("Filtering is completely disabled"),
    MANUAL("Players can choose their own filters"),
    DAILY_RANDOM("Random filter assigned daily"),
    SESSION_RANDOM("Random filter assigned per session"),
    CHAOS_MODE("Random filter for every message");
    
    private final String description;
    
    FilterMode(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
