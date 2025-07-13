package com.randomdialogue.filter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.Objects;

public class FilterDefinition {
    public String name;
    public String prompt;
    public String emoji;
    public String color;
    public boolean enabled;

    public FilterDefinition() {
        // Default constructor for JSON deserialization
    }

    public FilterDefinition(String name, String prompt, String emoji, String color, boolean enabled) {
        this.name = name;
        this.prompt = prompt;
        this.emoji = emoji;
        this.color = color;
        this.enabled = enabled;
    }

    public NamedTextColor getChatColor() {
        try {
            return NamedTextColor.NAMES.value(color.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NamedTextColor.WHITE; // fallback
        }
    }

    public String getFullPrompt(String originalMessage) {
        boolean hasEmojis = originalMessage.matches(".*[\\p{So}\\p{Sk}].*");
        String emojiInstruction = hasEmojis ? " You may use emojis since the original message contains them."
                : " Do NOT use any emojis in your response.";

        return prompt +
                ". Transform ONLY the tone/style, never the meaning or content" +
                ". Keep it roughly the same length" +
                ". Retain the original point of view (first person 'I', second person 'you', third person, etc.)" +
                ". Do not add new information or context" +
                emojiInstruction +
                ". ONLY respond with the transformed message: \"" +
                originalMessage + "\"";
    }

    public String getDisplayName() {
        return name.toLowerCase().replace('_', ' ');
    }

    public String getName() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FilterDefinition that = (FilterDefinition) o;
        return name.equalsIgnoreCase(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase());
    }
}
