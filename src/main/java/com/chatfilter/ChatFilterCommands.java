package com.chatfilter;

import com.chatfilter.config.FilterMode;
import com.chatfilter.filter.ChatFilter;
import com.chatfilter.config.ChatFilterConfig;
import com.chatfilter.filter.FilterDefinition;
import com.chatfilter.filter.FilterManager;
import com.chatfilter.player.PlayerFilterManager;
import com.chatfilter.player.PlayerFilterManager.PlayerFilterStats;
import com.chatfilter.service.LLMService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.logging.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class ChatFilterCommands implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = Logger.getLogger(ChatFilterCommands.class.getName());
    
    private final PlayerFilterManager playerManager;
    private final LLMService llmService;
    
    public ChatFilterCommands(PlayerFilterManager playerManager, LLMService llmService) {
        this.playerManager = playerManager;
        this.llmService = llmService;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Admin-only access
        if (!sender.hasPermission("randomdialogue.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            return showHelp(sender);
        }
        
        switch (args[0].toLowerCase()) {
            case "mode":
                return handleModeCommand(sender, args);
            case "enable":
                return enablePlayerFilter(sender, args);
            case "disable":
                return disablePlayerFilter(sender, args);
            case "set":
                return handleSetCommand(sender, args);
            case "reroll":
                return rerollFilter(sender, args);
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
            default:
                return showHelp(sender);
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String[] subcommands = {"mode", "enable", "disable", "set", "reroll", "status", "list", "who", "players", "reload", "llm_info"};
            for (String sub : subcommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
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
                // Player names for set command
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
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
    
    private boolean handleModeCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            FilterMode currentMode = playerManager.getCurrentMode();
            sender.sendMessage(Component.text("Current filter mode: ", NamedTextColor.AQUA).append(Component.text(currentMode.name().toLowerCase(), NamedTextColor.YELLOW)));
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
            sender.sendMessage(Component.text("Filter mode changed from ", NamedTextColor.GREEN) + oldMode.name().toLowerCase() + " to " + newMode.name().toLowerCase());
            
            LOGGER.info("Filter mode changed from " + oldMode + " to " + newMode + " by " + sender.getName());
            return true;
            
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid mode. Available modes: ", NamedTextColor.RED) + 
                             Arrays.stream(FilterMode.values())
                                   .map(m -> m.name().toLowerCase())
                                   .collect(Collectors.joining(", ")));
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
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED) + args[1]);
            return true;
        }
        
        if (playerManager.getCurrentMode() == FilterMode.DISABLED) {
            sender.sendMessage(Component.text("Chat filters are currently disabled on the server.", NamedTextColor.RED));
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
        sender.sendMessage(Component.text("Chat filters enabled for ", NamedTextColor.GREEN) + targetPlayer.getName());
        return true;
    }
    
    private boolean disablePlayerFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /chatfilter disable <player>", NamedTextColor.RED));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED) + args[1]);
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), false);
        sender.sendMessage(Component.text("Chat filters disabled for ", NamedTextColor.GREEN) + targetPlayer.getName());
        return true;
    }
    
    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /chatfilter set <player> <filter>", NamedTextColor.RED));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED) + args[1]);
            return true;
        }
        
        FilterDefinition filter = FilterManager.getInstance().getFilter(args[2].toUpperCase());
        if (filter != null && filter.enabled) {
            playerManager.setPlayerFilter(targetPlayer.getUniqueId(), filter);
            playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
            
            sender.sendMessage(Component.text("Filter set for ", NamedTextColor.GREEN) + targetPlayer.getName() + " to: " + filter.getChatColor() + 
                             filter.getDisplayName() + Component.text(" ", NamedTextColor.GREEN) + filter.emoji);
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
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED) + args[1]);
            return true;
        }
        
        if (playerManager.rerollPlayerFilter(targetPlayer)) {
            sender.sendMessage(Component.text("Filter rerolled for ", NamedTextColor.GREEN) + targetPlayer.getName());
        } else {
            sender.sendMessage(Component.text("Reroll is not available in ", NamedTextColor.YELLOW) + 
                             playerManager.getCurrentMode().name().toLowerCase() + " mode.");
        }
        return true;
    }
    
    private boolean showStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            FilterMode currentMode = playerManager.getCurrentMode();
            sender.sendMessage(Component.text("=== Server Status ===", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Current server mode: ", NamedTextColor.AQUA).append(Component.text(currentMode.name().toLowerCase(), NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("Enabled players: ", NamedTextColor.AQUA).append(Component.text(String.valueOf(playerManager.getEnabledPlayers().size()), NamedTextColor.YELLOW)));
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player not found: ", NamedTextColor.RED) + args[1]);
            return true;
        }
        
        PlayerFilterManager.PlayerFilterStats stats = playerManager.getPlayerStats(targetPlayer.getUniqueId());
        sender.sendMessage(Component.text("=== ", NamedTextColor.AQUA) + targetPlayer.getName() + "'s Filter Status ===");
        sender.sendMessage(Component.text("Server Mode: ", NamedTextColor.AQUA).append(Component.text(stats.mode.name().toLowerCase(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Enabled: ", NamedTextColor.AQUA)
            .append(stats.enabled ? Component.text("Yes", NamedTextColor.GREEN) : Component.text("No", NamedTextColor.RED)));
        
        if (stats.enabled && stats.currentFilter != null) {
            sender.sendMessage(Component.text("Current Filter: ", NamedTextColor.AQUA)
                .append(Component.text(stats.currentFilter.getDisplayName()).color(stats.currentFilter.getChatColor())));
            sender.sendMessage(Component.text("Current Filter: ", NamedTextColor.AQUA)
                .append(Component.text(stats.currentFilter.getDisplayName(), stats.currentFilter.getChatColor()))
                .append(Component.text(" " + stats.currentFilter.emoji, NamedTextColor.AQUA)));
        }
        
        if (stats.lastAssignedDate != null) {
            sender.sendMessage(Component.text("Last Assigned: ", NamedTextColor.AQUA).append(Component.text(stats.lastAssignedDate, NamedTextColor.YELLOW)));
        }
        
        return true;
    }
    
    private boolean listFilters(CommandSender sender) {
        sender.sendMessage(Component.text("=== Available Chat Filters ===", NamedTextColor.AQUA));
        for (FilterDefinition filter : FilterManager.getInstance().getEnabledFilters()) {
            sender.sendMessage(filter.getChatColor() + filter.getDisplayName() + " " + 
                             filter.emoji + Component.text(" - ", NamedTextColor.GRAY) + filter.name.toLowerCase());
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
                .append(statusComponent).append(Component.text(" ", NamedTextColor.WHITE))
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
        FilterManager.getInstance().reloadFilters();
        sender.sendMessage(Component.text("Filters reloaded from JSON configuration.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        ChatFilterConfig config = ChatFilterConfig.getInstance();

        sender.sendMessage(Component.text("Provider: ", NamedTextColor.AQUA).append(Component.text(config.llmProvider, NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Model: ", NamedTextColor.AQUA).append(Component.text(config.getCurrentModel(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Endpoint: ", NamedTextColor.AQUA).append(Component.text(config.getCurrentEndpoint(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Current Mode: ", NamedTextColor.AQUA).append(Component.text(playerManager.getCurrentMode().name().toLowerCase(), NamedTextColor.YELLOW)));
        
        if (sender instanceof Player player) {
            FilterDefinition currentFilter = playerManager.getPlayerFilter(player.getUniqueId());
            sender.sendMessage(Component.text("Your Current Filter: ", NamedTextColor.AQUA)
                .append(Component.text(currentFilter.getDisplayName(), currentFilter.getChatColor()))
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
        sender.sendMessage(Component.text("=== Random Dialogue Admin Commands ===", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("/chatfilter status [player]", NamedTextColor.YELLOW).append(Component.text(" - Show server or player status", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter list", NamedTextColor.YELLOW).append(Component.text(" - List available filters", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter mode <mode>", NamedTextColor.YELLOW).append(Component.text(" - Change server mode", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter enable <player>", NamedTextColor.YELLOW).append(Component.text(" - Enable filters for player", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter disable <player>", NamedTextColor.YELLOW).append(Component.text(" - Disable filters for player", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter set <player> <filter>", NamedTextColor.YELLOW).append(Component.text(" - Set filter for player", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter reroll <player>", NamedTextColor.YELLOW).append(Component.text(" - Reroll filter for player", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter who", NamedTextColor.YELLOW).append(Component.text(" - Show every player's current filter status", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter players", NamedTextColor.YELLOW).append(Component.text(" - Show every player's current filter status", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter llm_info", NamedTextColor.YELLOW).append(Component.text(" - Show current LLM information", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/chatfilter reload", NamedTextColor.YELLOW).append(Component.text(" - Reload filters from JSON", NamedTextColor.WHITE)));
        return true;
    }
}
