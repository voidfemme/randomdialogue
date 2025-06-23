package com.chatfilter;

import com.chatfilter.config.FilterMode;
import com.chatfilter.filter.ChatFilter;
import com.chatfilter.config.ChatFilterConfig;
import com.chatfilter.filter.FilterDefinition;
import com.chatfilter.filter.FilterManager;
import com.chatfilter.player.PlayerFilterManager;
import com.chatfilter.player.PlayerFilterManager.PlayerFilterStats;
import com.chatfilter.service.LLMService;
import com.chatfilter.config.ValidationResult;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class ChatFilterCommands implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = Logger.getLogger(ChatFilterCommands.class.getName());
    
    private static ChatFilterCommands instance;
    private final PlayerFilterManager playerManager;
    private final LLMService llmService;
    
    public ChatFilterCommands(PlayerFilterManager playerManager, LLMService llmService) {
        this.playerManager = playerManager;
        this.llmService = llmService;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        if (args.length == 0) {
            return showHelp(sender);
        }
        
        switch (args[0].toLowerCase()) {
            case "mode":
                return handleModeCommand(sender, args);
            case "enable":
                if (args.length == 1) {
                    // Player enabling for themselves
                    return enablePlayerFilterSelf(sender);
                } else {
                    // Admin enabling for another player
                    if (!sender.hasPermission("randomdialogue.admin")) {
                        sender.sendMessage(Component.text("You can only enable filters for yourself.", NamedTextColor.RED));
                        return true;
                    }
                return enablePlayerFilter(sender, args);
                }
            case "disable":
                if (args.length == 1) {
                    // Player disabling for themselves
                    return disablePlayerFilterSelf(sender);
                } else {
                    // Admin disabling for another player
                    if (!sender.hasPermission("randomdialogue.admin")) {
                        sender.sendMessage(Component.text("You can only disable filters for yourself.", NamedTextColor.RED));
                        return true;
                    }
                    return disablePlayerFilter(sender, args);
                }
            case "set":
                if (args.length == 2) {
                    // Player setting their own filter: /chatfilter set <filter>
                    return setPlayerOwnFilter(sender, args[1]);
                } else if (args.length == 3) {
                    // Admin setting for another: /chatfilter set <player> <filter>
                    if (!sender.hasPermission("randomdialogue.admin")) {
                        sender.sendMessage(Component.text("You can only set filters for yourself.", NamedTextColor.RED));
                        return true;
                    }
                return handleSetCommand(sender, args);
                } else {
                    sender.sendMessage(Component.text("Usage: /chatfilter set <filter> OR /chatfilter set <player> <filter>", NamedTextColor.RED));
                    return true;
                }
            case "reroll":
                if (args.length == 1) {
                    //Player rerolling for themselves
                    return rerollFilterSelf(sender);
                } else {
                    // Admin rerolling for another player
                    if (!sender.hasPermission("randomdialogue.admin")) {
                        sender.sendMessage(Component.text("You can only reroll filters for yourself.", NamedTextColor.RED));
                        return true;
                    }
                return rerollFilter(sender, args);
                }
            case "status":
                return showStatus(sender, args);
            case "list":
                return listFilters(sender);
            case "who":
            case "players":
                return showPlayerFilters(sender);
            case "reload":
                return reloadFilters(sender);
            case "llm_info":
                return handleInfoCommand(sender, args);
            case "reload_config":
                return reloadConfig(sender);
            case "reload_all":
                return reloadAll(sender);
            default:
                return showHelp(sender);
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String[] subcommands = {"enable", "disable", "set", "reroll", "status", "list", "who", "players", "llm_info"};
            String[] adminCommands = {"mode", "reload", "reload_config", "reload_all"};
            for (String sub : subcommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }

            if (sender.hasPermission("randomdialogue.admin")) {
                for (String sub : adminCommands) {
                    if (sub.startsWith(args[0].toLowerCase())) {
                        completions.add(sub);
                    }
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("mode")) {
                for (FilterMode mode : FilterMode.values()) {
                    if (mode.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(mode.name().toLowerCase());
                    }
                }
            } else if (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable") || 
                       args[0].equalsIgnoreCase("reroll") || args[0].equalsIgnoreCase("status")) {
                // Player names for admin commands
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("set")) {
                // For set command with 2 args, this could be either:
                // 1. /chatfilter set <filter> (player setting own filter)
                // 2. /chatfilter set <player> (admin setting for player, need player names)

                // Show both filter names and player names
                for (String filterName : FilterManager.getInstance().getEnabledFilterNames()) {
                    if (filterName.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(filterName.toLowerCase());
                    }
                }

                // Also show player names for admin usage
                if (sender.hasPermission("randomdialogue.admin")) {
                    for (Player player : sender.getServer().getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // Filter names for set command
            for (String filterName : FilterManager.getInstance().getEnabledFilterNames()) {
                if (filterName.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(filterName.toLowerCase());
                }
            }
        }
        
        return completions;
    }
    
    private boolean disablePlayerFilterSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can disable filters for themselves.", NamedTextColor.RED));
            return true;
        }

        playerManager.setPlayerEnabled(player.getUniqueId(), false);
        sender.sendMessage(Component.text("Chat filters disabled for you!", NamedTextColor.GREEN));
        return true;
    }
    
    private boolean handleModeCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            FilterMode currentMode = playerManager.getCurrentMode();
            sender.sendMessage(Component.text("Current filter mode: ", NamedTextColor.AQUA)
                .append(Component.text(currentMode.name().toLowerCase(), NamedTextColor.YELLOW)));
            return true;
        }
        
        if (!sender.hasPermission("randomdialogue.admin")) {
            sender.sendMessage(Component.text("You don't have permission to change the server mode.", NamedTextColor.RED));
            return true;
        }
        
        try {
            FilterMode newMode = FilterMode.valueOf(args[1].toUpperCase());
            FilterMode oldMode = playerManager.getCurrentMode();
            playerManager.setCurrentMode(newMode);

            // Clear manual overrides to reset manually set players list
            if (newMode == FilterMode.CHAOS_MODE ||
                newMode == FilterMode.DAILY_RANDOM ||
                newMode == FilterMode.SESSION_RANDOM) {
                playerManager.clearAllManualOverrides();
            }
            
            Component message = switch (newMode) {
                case DISABLED -> Component.text("Chat filters have been DISABLED!", NamedTextColor.RED);
                case MANUAL -> Component.text("Chat filters set to MANUAL mode - players can choose their own!", NamedTextColor.GREEN);
                case DAILY_RANDOM -> Component.text("Chat filters set to DAILY RANDOM mode - new personality each day!", NamedTextColor.GOLD);
                case SESSION_RANDOM -> Component.text("Chat filters set to SESSION RANDOM mode - new personality each login!", NamedTextColor.AQUA);
                case CHAOS_MODE -> Component.text("CHAOS MODE ACTIVATED! Every message is a different personality!", NamedTextColor.DARK_RED, TextDecoration.BOLD);
            };
            
            sender.getServer().broadcast(message);
            sender.sendMessage(Component.text("Filter mode changed from ", NamedTextColor.GREEN)
                .append(Component.text(oldMode.name().toLowerCase(), NamedTextColor.YELLOW))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(newMode.name().toLowerCase(), NamedTextColor.BLUE)));
            
            LOGGER.info("Filter mode changed from " + oldMode + " to " + newMode + " by " + sender.getName());
            return true;
            
        } catch (IllegalArgumentException e) {
            String modesList = Arrays.stream(FilterMode.values())
                .map(m -> m.name().toLowerCase())
                .collect(Collectors.joining(", "));

            sender.sendMessage(Component.text("Invalid mode. Available modes: ", NamedTextColor.RED)
                .append(Component.text(modesList, NamedTextColor.YELLOW)));
            return true;
        }
    }
    
    private boolean enablePlayerFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /chatfilter enable <player>", NamedTextColor.RED));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED)
                .append(Component.text(args[1], NamedTextColor.YELLOW)));
            return true;
        }
        
        if (playerManager.getCurrentMode() == FilterMode.DISABLED) {
            sender.sendMessage(Component.text("Chat filters are currently disabled on the server.", NamedTextColor.RED));
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
        sender.sendMessage(Component.text("Chat filters enabled for ", NamedTextColor.GREEN)
            .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW)));
        return true;
    }

    private boolean enablePlayerFilterSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can enable filters for themselves.", NamedTextColor.RED));
            return true;
        }

        if (playerManager.getCurrentMode() == FilterMode.DISABLED) {
            sender.sendMessage(Component.text("Chat filters are currently disabled on the server.", NamedTextColor.RED));
            return true;
        }

        playerManager.setPlayerEnabled(player.getUniqueId(), true);
        sender.sendMessage(Component.text("Chat filters enabled for you!", NamedTextColor.GREEN));
        return true;
    }
    
    private boolean disablePlayerFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /chatfilter disable <player>", NamedTextColor.RED));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED)
                .append(Component.text(args[1], NamedTextColor.YELLOW)));
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), false);
        sender.sendMessage(Component.text("Chat filters disabled for ", NamedTextColor.GREEN)
            .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW)));
        return true;
    }
    
    private boolean setPlayerOwnFilter(CommandSender sender, String filterName) {
        // Only players can use this command
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set their own filter.", NamedTextColor.RED));
            return true;
        }

        // Check if filters are disabled server-wide
        if (playerManager.getCurrentMode() == FilterMode.DISABLED) {
            sender.sendMessage(Component.text("Chat filters are currently disabled on the server.", NamedTextColor.RED));
            return true;
        }

        // Check if player can manually set filters in current mode
        FilterMode currentMode = playerManager.getCurrentMode();
        if (currentMode != FilterMode.MANUAL) {
            sender.sendMessage(Component.text("Filter selection is not available in ", NamedTextColor.YELLOW)
                .append(Component.text(currentMode.name().toLowerCase(), NamedTextColor.RED))
                .append(Component.text(" mode.", NamedTextColor.YELLOW)));
            return true;
        }

        // Find the filter
        FilterDefinition filter = FilterManager.getInstance().getFilter(filterName.toUpperCase());
        if (filter == null || !filter.enabled) {
            sender.sendMessage(Component.text("Invalid filter. Use ", NamedTextColor.RED)
                .append(Component.text("/chatfilter list", NamedTextColor.AQUA))
                .append(Component.text(" to see available filters.", NamedTextColor.RED)));
            return true;
        }

        // Set the filter for the player
        playerManager.setPlayerFilter(player.getUniqueId(), filter);
        playerManager.setPlayerEnabled(player.getUniqueId(), true);

        sender.sendMessage(Component.text("Your filter has been set to: ", NamedTextColor.GREEN)
            .append(Component.text(filter.getDisplayName()).color(filter.getChatColor()))
            .append(Component.text(" " + filter.emoji, NamedTextColor.GREEN)));

        return true;
    }

    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /chatfilter set <player> <filter>", NamedTextColor.RED));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED)
                .append(Component.text(args[1], NamedTextColor.YELLOW)));
            return true;
        }
        
        FilterDefinition filter = FilterManager.getInstance().getFilter(args[2].toUpperCase());
        if (filter != null && filter.enabled) {
            playerManager.setPlayerFilter(targetPlayer.getUniqueId(), filter);
            playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
            
            Component message = Component.text("Filter set for ", NamedTextColor.GREEN)
                .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" to: ", NamedTextColor.GREEN))
                .append(Component.text(filter.getDisplayName()).color(filter.getChatColor()))
                .append(Component.text(" " + filter.emoji));

            sender.sendMessage(message);
            return true;
        } else {
            sender.sendMessage(Component.text("Invalid filter. Use ", NamedTextColor.RED)
                .append(Component.text("/chatfilter list", NamedTextColor.AQUA))
                .append(Component.text(" to see available filters.", NamedTextColor.RED)));
            return true;
        }
    }
    
    private boolean rerollFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /chatfilter reroll <player>", NamedTextColor.RED));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED)
                .append(Component.text(args[1], NamedTextColor.YELLOW)));
            return true;
        }
        
        if (playerManager.rerollPlayerFilter(targetPlayer)) {
            sender.sendMessage(Component.text("Filter rerolled for ", NamedTextColor.GREEN)
                .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(Component.text("Reroll is not available in ", NamedTextColor.YELLOW)
                .append(Component.text(playerManager.getCurrentMode().name().toLowerCase(), NamedTextColor.RED))
                .append(Component.text(" mode.", NamedTextColor.YELLOW)));
        }
        return true;
    }

    private boolean rerollFilterSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can disable filters for themselves.", NamedTextColor.RED));
            return true;
        }

        if (playerManager.getCurrentMode() == FilterMode.DISABLED) {
            sender.sendMessage(Component.text("Chat filters are currently disabled on the server.", NamedTextColor.RED));
            return true;
        }

        if (playerManager.rerollPlayerFilter(player)) {
            FilterDefinition newFilter = playerManager.getPlayerFilter(player.getUniqueId());
            sender.sendMessage(Component.text("Your filter has been rerolled to: ", NamedTextColor.GREEN)
                .append(Component.text(newFilter.getDisplayName()).color(newFilter.getChatColor()))
                .append(Component.text(" " + newFilter.emoji, NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("Reroll is not available in ", NamedTextColor.YELLOW)
                .append(Component.text(playerManager.getCurrentMode().name().toLowerCase(), NamedTextColor.RED))
                .append(Component.text(" mode.", NamedTextColor.YELLOW)));
        }
        return true;
    }
    
    private boolean showStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            FilterMode currentMode = playerManager.getCurrentMode();
            sender.sendMessage(Component.text("=== Server Status ===", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Current server mode: ", NamedTextColor.AQUA)
                .append(Component.text(currentMode.name().toLowerCase(), NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Enabled players: ", NamedTextColor.AQUA)
                .append(Component.text(String.valueOf(playerManager.getEnabledPlayers().size()), NamedTextColor.YELLOW)));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED)
                .append(Component.text(args[1], NamedTextColor.YELLOW)));
            return true;
        }
        
        PlayerFilterManager.PlayerFilterStats stats = playerManager.getPlayerStats(targetPlayer.getUniqueId());
        sender.sendMessage(Component.text("=== ", NamedTextColor.AQUA)
            .append(Component.text(targetPlayer.getName(), NamedTextColor.YELLOW))
            .append(Component.text("'s Filter Status ===")));
        sender.sendMessage(Component.text("Server Mode: ", NamedTextColor.AQUA)
            .append(Component.text(stats.mode.name().toLowerCase(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Enabled: ", NamedTextColor.AQUA)
            .append(stats.enabled ? Component.text("Yes", NamedTextColor.GREEN) : Component.text("No", NamedTextColor.RED)));
        
        if (stats.enabled && stats.currentFilter != null) {
            sender.sendMessage(Component.text("Current Filter: ", NamedTextColor.AQUA)
                .append(Component.text(stats.currentFilter.getDisplayName()).color(stats.currentFilter.getChatColor())));
            sender.sendMessage(Component.text("Current Filter: ", NamedTextColor.AQUA)
                .append(Component.text(stats.currentFilter.getDisplayName()).color(stats.currentFilter.getChatColor()))
                .append(Component.text(" " + stats.currentFilter.emoji, NamedTextColor.AQUA)));
        }
        
        if (stats.lastAssignedDate != null) {
            sender.sendMessage(Component.text("Last Assigned: ", NamedTextColor.AQUA)
                .append(Component.text(stats.lastAssignedDate, NamedTextColor.YELLOW)));
        }
        
        return true;
    }
    
    private boolean listFilters(CommandSender sender) {
        sender.sendMessage(Component.text("=== Available Chat Filters ===", NamedTextColor.AQUA));
        for (FilterDefinition filter : FilterManager.getInstance().getEnabledFilters()) {
            sender.sendMessage(Component.text(filter.name.toLowerCase(), NamedTextColor.GRAY)
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(filter.getDisplayName()).color(filter.getChatColor()))
                .append(Component.text(" " + filter.emoji, NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean showPlayerFilters(CommandSender sender) {
        sender.sendMessage(Component.text("=== Current Filter Assignments ===", NamedTextColor.AQUA));
        boolean anyPlayersOnline = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            anyPlayersOnline = true;

            PlayerFilterStats stats = playerManager.getPlayerStats(player.getUniqueId());

            // Get the player's filter stats
            Component statusComponent = stats.enabled ? Component.text("✓", NamedTextColor.GREEN) : Component.text("✗", NamedTextColor.RED);
            String filterName = stats.currentFilter != null ? stats.currentFilter.getName() : "None";

            sender.sendMessage(Component.text(player.getName(), NamedTextColor.YELLOW)
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(statusComponent)
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Component.text(filterName, NamedTextColor.AQUA))
                .append(Component.text(" (" + stats.mode + ")", NamedTextColor.GRAY)));
        }

        if (!anyPlayersOnline) {
            sender.sendMessage(Component.text("No players online.", NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("==================================", NamedTextColor.GOLD));
        return true;
    }
    
    private boolean reloadFilters(CommandSender sender) {
        try {
            FilterManager.getInstance().reloadFilters();
            sender.sendMessage(Component.text("Filters reloaded from JSON configuration.", NamedTextColor.GREEN));
            return true;
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload filters: " + e.getMessage(), NamedTextColor.RED));
            LOGGER.severe("Filter reload failed: " + e.getMessage());
            return false;
        }
    }

    public static void resetInstance() {
        instance = null;
    }

    private boolean reloadConfig(CommandSender sender) {

        if (!sender.hasPermission("randomdialogue.admin")) {
            sender.sendMessage(Component.text("You don't have permission to change the server mode.", NamedTextColor.RED));
            return true;
        }

        try {
            sender.sendMessage(Component.text("Reloading main configuration...", NamedTextColor.YELLOW));
            
            // Force reload the config
            ChatFilterConfig.resetInstance();
            ChatFilterConfig config = ChatFilterConfig.getInstance();
            
            // Validate the reloaded config
            ValidationResult result = config.validateConfiguration();
            if (result.hasErrors()) {
                sender.sendMessage(Component.text("Configuration reload failed with errors:", NamedTextColor.RED));
                for (String error : result.getErrors()) {
                    sender.sendMessage(Component.text(" - " + error, NamedTextColor.RED));
                }
                return false;
            } 

            if (result.hasWarnings()) {
                sender.sendMessage(Component.text("Configuration reloaded with warnings:", NamedTextColor.YELLOW));
                for (String warning : result.getWarnings()) {
                    sender.sendMessage(Component.text("  - " + warning, NamedTextColor.YELLOW));
                }
            } else {
                sender.sendMessage(Component.text("Configuration reloaded successfully!", NamedTextColor.GREEN));
            }
            
            // Restart LLM service with new config
            ChatFilterMod plugin = ChatFilterMod.getInstance();
            if (plugin.getLLMService() != null) {
                plugin.getLLMService().shutdown();
                plugin.reinitializeLLMService();
                sender.sendMessage(Component.text("LLM Service restarted with new configuration.", NamedTextColor.GREEN));
            }
            
            return true;
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage(), NamedTextColor.RED));
            LOGGER.severe("Config reload failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean reloadAll(CommandSender sender) {
        if (!sender.hasPermission("randomdialogue.admin")) {
            sender.sendMessage(Component.text("reload_config is only available for admin."));
            return true;
        }

        boolean configSuccess = reloadConfig(sender);
        boolean filtersSuccess = reloadFilters(sender);

        
        if (configSuccess && filtersSuccess) {
            sender.sendMessage(Component.text("All configurations reloaded successfully!", NamedTextColor.GREEN));
            return true;
        } else {
            sender.sendMessage(Component.text("Some reloads failed. Check console for details.", NamedTextColor.RED));
            return false;
        }
    }

    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        ChatFilterConfig config = ChatFilterConfig.getInstance();

        sender.sendMessage(Component.text("Provider: ", NamedTextColor.AQUA)
            .append(Component.text(config.llmProvider, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Model: ", NamedTextColor.AQUA)
            .append(Component.text(config.getCurrentModel(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Endpoint: ", NamedTextColor.AQUA)
            .append(Component.text(config.getCurrentEndpoint(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Current Mode: ", NamedTextColor.AQUA).
            append(Component.text(playerManager.getCurrentMode().name().toLowerCase(), NamedTextColor.YELLOW)));
        
        if (sender instanceof Player player) {
            FilterDefinition currentFilter = playerManager.getPlayerFilter(player.getUniqueId());
            sender.sendMessage(Component.text("Your Current Filter: ", NamedTextColor.AQUA)
                .append(Component.text(currentFilter.getDisplayName()).color(currentFilter.getChatColor()))
                .append(Component.text(" " + currentFilter.emoji)));
        }
        
        sender.sendMessage(Component.text("Your messages are transformed using AI to add personality.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Original messages are not stored permanently.", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Use ", NamedTextColor.GREEN)
            .append(Component.text("/chatfilter help", NamedTextColor.AQUA))
            .append(Component.text(" for more commands.", NamedTextColor.GREEN)));
        return true;
    }
    
    private boolean showHelp(CommandSender sender) {
        boolean isAdmin = sender.hasPermission("randomdialogue.admin");
        
        if (isAdmin) {
            sender.sendMessage(Component.text("=== Random Dialogue Commands (Admin) ===", NamedTextColor.AQUA));
        } else {
            sender.sendMessage(Component.text("=== Random Dialogue Commands ===", NamedTextColor.AQUA));
        }
        
        // Player commands (everyone can use)
        sender.sendMessage(Component.text("/chatfilter status [player]", NamedTextColor.YELLOW)
            .append(Component.text(" - Show server or player status", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter list", NamedTextColor.YELLOW)
            .append(Component.text(" - List available filters", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter set <filter>", NamedTextColor.YELLOW)
            .append(Component.text(" - Set your own filter", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter enable", NamedTextColor.YELLOW)
            .append(Component.text(" - Enable your filter", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter disable", NamedTextColor.YELLOW)
            .append(Component.text(" - Disable your filter", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter reroll", NamedTextColor.YELLOW)
            .append(Component.text(" - Get a random filter", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter who", NamedTextColor.YELLOW)
            .append(Component.text(" - Show all player filters", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter llm_info", NamedTextColor.YELLOW)
            .append(Component.text(" - Show AI model information", NamedTextColor.WHITE)));
        
        // Quote system instructions
        sender.sendMessage(Component.text("--- Quote System ---", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("\"Full message in quotes\"", NamedTextColor.GRAY)
            .append(Component.text(" - Bypass transformation entirely", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("I said \"quoted text\" here", NamedTextColor.GRAY)
            .append(Component.text(" - Preserve quotes during transformation", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Note: ", NamedTextColor.YELLOW)
            .append(Component.text("Quote preservation messages show if quotes are modified", NamedTextColor.WHITE)));
        
        // Admin-only commands
        if (isAdmin) {
            sender.sendMessage(Component.text("--- Admin Commands ---", NamedTextColor.RED));
            sender.sendMessage(Component.text("/chatfilter mode <mode>", NamedTextColor.YELLOW)
                .append(Component.text(" - Change server mode", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/chatfilter set <player> <filter>", NamedTextColor.YELLOW)
                .append(Component.text(" - Set filter for another player", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/chatfilter enable <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Enable filter for another player", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/chatfilter reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload filter configurations", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/chatfilter reload_config", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration file", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("/chatfilter reload_all", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload both filter configuration (filters.json) and main configuration file (chat-filter.json)")));
        }
        
        return true;
    }
}
