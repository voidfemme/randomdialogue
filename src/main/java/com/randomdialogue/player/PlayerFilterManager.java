package com.randomdialogue.player;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import com.randomdialogue.config.RandomDialogueConfig;
import com.randomdialogue.config.FilterMode;

import com.randomdialogue.filter.FilterDefinition;
import com.randomdialogue.filter.FilterManager;

public class PlayerFilterManager {
    private static final Logger LOGGER = Logger.getLogger(PlayerFilterManager.class.getName());
    private static final Random RANDOM = new Random();

    private FilterMode currentMode = FilterMode.MANUAL;

    // Player state tracking
    private final Map<UUID, FilterDefinition> playerFilters = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playersEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, FilterDefinition> dailyAssignedFilters = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastAssignedDate = new ConcurrentHashMap<>();
    private final Map<UUID, FilterDefinition> sessionFilters = new ConcurrentHashMap<>();
    private final Set<UUID> manuallySetPlayers = new HashSet<>();

    private final RandomDialogueConfig config;
    private final FilterManager filterManager;

    private final Map<UUID, Boolean> allowLLMProcessing = new ConcurrentHashMap<>();

    public PlayerFilterManager(RandomDialogueConfig config, FilterManager filterManager) {
        this.config = config;
        this.filterManager = filterManager;
        try {
            this.currentMode = FilterMode.valueOf(config.defaultFilterMode);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid default filter mode in config: " + config.defaultFilterMode + ", using MANUAL");
            this.currentMode = FilterMode.MANUAL;
        }
    }

    public void onPlayerJoin(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        if (currentMode == FilterMode.DISABLED) {
            playersEnabled.put(playerId, false);
            return;
        }

        switch (currentMode) {
            case SESSION_RANDOM -> handleSessionRandom(player, playerId);
            case DAILY_RANDOM -> handleDailyRandom(player, playerId);
            case CHAOS_MODE -> handleChaosMode(player, playerId);
            case MANUAL -> handleManualMode(player, playerId);
        }

        LOGGER.info("Player " + playerName + " joined with filter mode: " + currentMode);
    }

    public void onPlayerLeave(Player player) {
        UUID playerId = player.getUniqueId();

        // Clean up session-specific data, but keep daily assignments
        sessionFilters.remove(playerId);

        // Only remove enabled state for manual mode
        if (currentMode == FilterMode.MANUAL) {
            playersEnabled.remove(playerId);
            playerFilters.remove(playerId);
        }
    }

    private void handleSessionRandom(Player player, UUID playerId) {
        FilterDefinition randomFilter = getRandomFilter();
        sessionFilters.put(playerId, randomFilter);
        playerFilters.put(playerId, randomFilter);
        playersEnabled.put(playerId, true);

        // Silent mode - no messages to players
    }

    private void handleDailyRandom(Player player, UUID playerId) {
        String today = getCurrentDateString();
        String playerLastDate = lastAssignedDate.get(playerId);

        if (!today.equals(playerLastDate)) {
            // Assign new daily filter
            FilterDefinition randomFilter = getRandomFilter();
            dailyAssignedFilters.put(playerId, randomFilter);
            lastAssignedDate.put(playerId, today);
            playerFilters.put(playerId, randomFilter);
            playersEnabled.put(playerId, true);

            // Silent mode - no messages to players
        } else {
            // Use existing daily filter
            FilterDefinition todaysFilter = dailyAssignedFilters.get(playerId);
            if (todaysFilter != null) {
                playerFilters.put(playerId, todaysFilter);
                playersEnabled.put(playerId, true);

                // Silent mode - no messages to players
            }
        }
    }

    private void handleChaosMode(Player player, UUID playerId) {
        playersEnabled.put(playerId, true);

        // Silent mode - no messages to players
    }

    private void handleManualMode(Player player, UUID playerId) {
        // In stealth mode, manual mode just enables players silently
        playersEnabled.put(playerId, true);

        // Assign random filter silently
        FilterDefinition randomFilter = getRandomFilter();
        playerFilters.put(playerId, randomFilter);

        // Silent mode - no messages to players
    }

    public void clearAllManualOverrides() {
        manuallySetPlayers.clear();
    }

    public FilterDefinition getPlayerFilter(UUID playerId) {
        if (currentMode == FilterMode.CHAOS_MODE) {
            // Only randomize if player doesn't have a manually set filter
            if (manuallySetPlayers.contains(playerId)) {
                return playerFilters.get(playerId);
            }
            return getRandomFilter();
        }

        return playerFilters.getOrDefault(playerId, filterManager.getFilter("OPPOSITE"));
    }

    public boolean isPlayerEnabled(UUID playerId) {
        return currentMode != FilterMode.DISABLED &&
                playersEnabled.getOrDefault(playerId, false);
    }

    public void setPlayerFilter(UUID playerId, FilterDefinition filter) {
        playerFilters.put(playerId, filter);
        manuallySetPlayers.add(playerId);
    }

    public void setPlayerFilter(UUID playerId, String filterName) {
        FilterDefinition filter = filterManager.getFilter(filterName);
        if (filter != null) {
            setPlayerFilter(playerId, filter);
        }
    }

    public void setPlayerEnabled(UUID playerId, boolean enabled) {
        if (currentMode == FilterMode.MANUAL) {
            playersEnabled.put(playerId, enabled);
            if (enabled && !playerFilters.containsKey(playerId)) {
                playerFilters.put(playerId, filterManager.getFilter("OPPOSITE"));
            }
        }
    }

    public boolean isLLMAllowed(UUID playerId) {
        // Default to true (opt-out)
        return allowLLMProcessing.getOrDefault(playerId, true);
    }

    public void setLLMAllowed(UUID playerId, boolean allowed) {
        allowLLMProcessing.put(playerId, allowed);
    }

    public boolean rerollPlayerFilter(Player player) {
        UUID playerId = player.getUniqueId();

        switch (currentMode) {
            case DAILY_RANDOM -> {
                FilterDefinition newFilter = getRandomFilter();
                dailyAssignedFilters.put(playerId, newFilter);
                playerFilters.put(playerId, newFilter);
                lastAssignedDate.put(playerId, getCurrentDateString());

                // Silent mode - no messages to players
                return true;
            }
            case SESSION_RANDOM -> {
                FilterDefinition newFilter = getRandomFilter();
                sessionFilters.put(playerId, newFilter);
                playerFilters.put(playerId, newFilter);

                // Silent mode - no messages to players
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public FilterMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(FilterMode mode) {
        FilterMode oldMode = this.currentMode;
        this.currentMode = mode;

        LOGGER.info("Filter mode changed from " + oldMode + " to " + mode);

        // Handle mode transitions
        if (mode == FilterMode.DISABLED) {
            // Disable all players
            playersEnabled.clear();
        } else if (oldMode == FilterMode.DISABLED) {
            // Mode was re-enabled, but don't auto-enable players in manual mode
            if (mode != FilterMode.MANUAL) {
                // For non-manual modes, we'll re-assign filters when players send messages
            }
        }

        // Update config
        config.defaultFilterMode = mode.name();
        config.saveConfig();
    }

    public List<UUID> getEnabledPlayers() {
        return playersEnabled.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList();
    }

    public Map<UUID, FilterDefinition> getCurrentPlayerFilters() {
        return new HashMap<>(playerFilters);
    }

    private FilterDefinition getRandomFilter() {
        FilterDefinition[] filters = filterManager.getEnabledFilters().toArray(new FilterDefinition[0]);
        if (filters.length == 0) {
            return filterManager.getFilter("OPPOSITE"); // fallback
        }
        return filters[RANDOM.nextInt(filters.length)];
    }

    private String getCurrentDateString() {
        return LocalDate.now().toString();
    }

    private void sendFilterMessage(Player player, String prefix, FilterDefinition filter, String suffix) {
        player.sendMessage(Component.text(prefix, NamedTextColor.GRAY)
                .append(Component.text(filter.getDisplayName(), NamedTextColor.RED))
                .append(Component.text(suffix, NamedTextColor.GRAY)));
    }

    public void clearPlayerData(UUID playerId) {
        playerFilters.remove(playerId);
        playersEnabled.remove(playerId);
        dailyAssignedFilters.remove(playerId);
        lastAssignedDate.remove(playerId);
        sessionFilters.remove(playerId);
        allowLLMProcessing.remove(playerId);
    }

    public PlayerFilterStats getPlayerStats(UUID playerId) {
        return new PlayerFilterStats(
                playerId,
                isPlayerEnabled(playerId),
                getPlayerFilter(playerId),
                currentMode,
                lastAssignedDate.get(playerId));
    }

    public static class PlayerFilterStats {
        public final UUID playerId;
        public final boolean enabled;
        public final FilterDefinition currentFilter;
        public final FilterMode mode;
        public final String lastAssignedDate;

        PlayerFilterStats(UUID playerId, boolean enabled, FilterDefinition currentFilter, FilterMode mode,
                String lastAssignedDate) {
            this.playerId = playerId;
            this.enabled = enabled;
            this.currentFilter = currentFilter;
            this.mode = mode;
            this.lastAssignedDate = lastAssignedDate;
        }
    }
}
