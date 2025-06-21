package com.chatfilter.filter;

import org.bukkit.ChatColor;

// Legacy enum wrapper - now delegates to FilterManager
public enum ChatFilter {
    OPPOSITE("OPPOSITE");
    
    private final String filterName;
    
    ChatFilter(String filterName) {
        this.filterName = filterName;
    }
    
    private FilterDefinition getDefinition() {
        FilterDefinition def = FilterManager.getInstance().getFilter(filterName);
        if (def == null) {
            // Fallback for missing filters
            return new FilterDefinition(filterName, "Rewrite this message", "❓", "WHITE", true);
        }
        return def;
    }
    
    public String getPrompt() {
        return getDefinition().prompt;
    }
    
    public String getEmoji() {
        return getDefinition().emoji;
    }
    
    public ChatColor getColor() {
        return getDefinition().getChatColor();
    }
    
    public String getDisplayName() {
        return getDefinition().getDisplayName();
    }
    
    public String getFullPrompt(String originalMessage) {
        return getDefinition().getFullPrompt(originalMessage);
    }
    
    // Static methods to work with dynamic filters
    public static FilterDefinition getFilterByName(String name) {
        return FilterManager.getInstance().getFilter(name);
    }
    
    public static FilterDefinition[] getAllFilters() {
        return FilterManager.getInstance().getEnabledFilters().toArray(new FilterDefinition[0]);
    }
    
    public static String[] getFilterNames() {
        return FilterManager.getInstance().getEnabledFilterNames().toArray(new String[0]);
    }
}