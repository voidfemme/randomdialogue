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
            sender.sendMessage(NamedTextColor.RED + "You don't have permission to use this command.");
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
            sender.sendMessage(NamedTextColor.AQUA + "Current filter mode: " + NamedTextColor.YELLOW + currentMode.name().toLowerCase());
            return true;
        }
        
        if (!sender.hasPermission("randomdialogue.admin")) {
            sender.sendMessage(NamedTextColor.RED + "You don't have permission to change the server mode.");
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
            
            String message = switch (newMode) {
                case DISABLED -> NamedTextColor.RED + "Chat filters have been DISABLED!";
                case MANUAL -> NamedTextColor.GREEN + "Chat filters set to MANUAL mode - players can choose their own!";
                case DAILY_RANDOM -> NamedTextColor.GOLD + "Chat filters set to DAILY RANDOM mode - new personality each day!";
                case SESSION_RANDOM -> NamedTextColor.AQUA + "Chat filters set to SESSION RANDOM mode - new personality each login!";
                case CHAOS_MODE -> NamedTextColor.DARK_RED + "" + TextDecoration.BOLD + "CHAOS MODE ACTIVATED! Every message is a different personality!";
            };
            
            sender.getServer().broadcast(Component.text(message));
            sender.sendMessage(NamedTextColor.GREEN + "Filter mode changed from " + oldMode.name().toLowerCase() + " to " + newMode.name().toLowerCase());
            
            LOGGER.info("Filter mode changed from " + oldMode + " to " + newMode + " by " + sender.getName());
            return true;
            
        } catch (IllegalArgumentException e) {
            sender.sendMessage(NamedTextColor.RED + "Invalid mode. Available modes: " + 
                             Arrays.stream(FilterMode.values())
                                   .map(m -> m.name().toLowerCase())
                                   .collect(Collectors.joining(", ")));
            return true;
        }
    }
    
    private boolean enablePlayerFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(NamedTextColor.RED + "Usage: /chatfilter enable <player>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(NamedTextColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        if (playerManager.getCurrentMode() == FilterMode.DISABLED) {
            sender.sendMessage(NamedTextColor.RED + "Chat filters are currently disabled on the server.");
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
        sender.sendMessage(NamedTextColor.GREEN + "Chat filters enabled for " + targetPlayer.getName());
        return true;
    }
    
    private boolean disablePlayerFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(NamedTextColor.RED + "Usage: /chatfilter disable <player>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(NamedTextColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), false);
        sender.sendMessage(NamedTextColor.GREEN + "Chat filters disabled for " + targetPlayer.getName());
        return true;
    }
    
    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(NamedTextColor.RED + "Usage: /chatfilter set <player> <filter>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(NamedTextColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        FilterDefinition filter = FilterManager.getInstance().getFilter(args[2].toUpperCase());
        if (filter != null && filter.enabled) {
            playerManager.setPlayerFilter(targetPlayer.getUniqueId(), filter);
            playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
            
            sender.sendMessage(NamedTextColor.GREEN + "Filter set for " + targetPlayer.getName() + " to: " + filter.getChatColor() + 
                             filter.getDisplayName() + NamedTextColor.GREEN + " " + filter.emoji);
            return true;
        } else {
            sender.sendMessage(NamedTextColor.RED + "Invalid filter. Use " + NamedTextColor.AQUA + "/chatfilter list" + 
                             NamedTextColor.RED + " to see available filters.");
            return true;
        }
    }
    
    private boolean rerollFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(NamedTextColor.RED + "Usage: /chatfilter reroll <player>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(NamedTextColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        if (playerManager.rerollPlayerFilter(targetPlayer)) {
            sender.sendMessage(NamedTextColor.GREEN + "Filter rerolled for " + targetPlayer.getName());
        } else {
            sender.sendMessage(NamedTextColor.YELLOW + "Reroll is not available in " + 
                             playerManager.getCurrentMode().name().toLowerCase() + " mode.");
        }
        return true;
    }
    
    private boolean showStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            FilterMode currentMode = playerManager.getCurrentMode();
            sender.sendMessage(NamedTextColor.AQUA + "=== Server Status ===");
            sender.sendMessage(NamedTextColor.AQUA + "Current server mode: " + NamedTextColor.YELLOW + currentMode.name().toLowerCase());
            sender.sendMessage(NamedTextColor.AQUA + "Enabled players: " + NamedTextColor.YELLOW + playerManager.getEnabledPlayers().size());
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(NamedTextColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        PlayerFilterManager.PlayerFilterStats stats = playerManager.getPlayerStats(targetPlayer.getUniqueId());
        sender.sendMessage(NamedTextColor.AQUA + "=== " + targetPlayer.getName() + "'s Filter Status ===");
        sender.sendMessage(NamedTextColor.AQUA + "Server Mode: " + NamedTextColor.YELLOW + stats.mode.name().toLowerCase());
        sender.sendMessage(NamedTextColor.AQUA + "Enabled: " + (stats.enabled ? NamedTextColor.GREEN + "Yes" : NamedTextColor.RED + "No"));
        
        if (stats.enabled && stats.currentFilter != null) {
            sender.sendMessage(NamedTextColor.AQUA + "Current Filter: " + stats.currentFilter.getChatColor() + 
                             stats.currentFilter.getDisplayName() + NamedTextColor.AQUA + " " + stats.currentFilter.emoji);
        }
        
        if (stats.lastAssignedDate != null) {
            sender.sendMessage(NamedTextColor.AQUA + "Last Assigned: " + NamedTextColor.YELLOW + stats.lastAssignedDate);
        }
        
        return true;
    }
    
    private boolean listFilters(CommandSender sender) {
        sender.sendMessage(NamedTextColor.AQUA + "=== Available Chat Filters ===");
        for (FilterDefinition filter : FilterManager.getInstance().getEnabledFilters()) {
            sender.sendMessage(filter.getChatColor() + filter.getDisplayName() + " " + 
                             filter.emoji + NamedTextColor.GRAY + " - " + filter.name.toLowerCase());
        }
        return true;
    }

    private boolean showPlayerFilters(CommandSender sender) {
        sender.sendMessage(NamedTextColor.AQUA + "=== Current Filter Assignments ===");
        boolean anyPlayersOnline = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            anyPlayersOnline = true;

            PlayerFilterStats stats = playerManager.getPlayerStats(player.getUniqueId());

            // Get the player's filter stats
            String status = stats.enabled ? NamedTextColor.GREEN + "✓" : NamedTextColor.RED + "✗";
            String filterName = stats.currentFilter != null ? stats.currentFilter.getName() : "None";

            sender.sendMessage(NamedTextColor.YELLOW + player.getName() +
                               NamedTextColor.WHITE + ": " + status + " " +
                               NamedTextColor.AQUA + filterName +
                               NamedTextColor.GRAY + " (" + stats.mode + ")");
        }

        if (!anyPlayersOnline) {
            sender.sendMessage(NamedTextColor.GRAY + "No players online.");
        }

        sender.sendMessage(NamedTextColor.GOLD + "==================================");
        return true;
    }
    
    private boolean reloadFilters(CommandSender sender) {
        FilterManager.getInstance().reloadFilters();
        sender.sendMessage(NamedTextColor.GREEN + "Filters reloaded from JSON configuration.");
        return true;
    }

    private boolean handleInfoCommand(CommandSender sender, String[] args) {
        ChatFilterConfig config = ChatFilterConfig.getInstance();

        sender.sendMessage(NamedTextColor.GOLD + "=== Chat Filter Information ===");
        sender.sendMessage(NamedTextColor.AQUA + "Provider: " + NamedTextColor.WHITE + config.llmProvider);
        sender.sendMessage(NamedTextColor.AQUA + "Model: " + NamedTextColor.WHITE + config.getCurrentModel());
        sender.sendMessage(NamedTextColor.AQUA + "Endpoint: " + NamedTextColor.WHITE + config.getCurrentEndpoint());
        sender.sendMessage(NamedTextColor.AQUA + "Current Mode: " + NamedTextColor.YELLOW + playerManager.getCurrentMode().name().toLowerCase());
        
        if (sender instanceof Player player) {
            FilterDefinition currentFilter = playerManager.getPlayerFilter(player.getUniqueId());
            sender.sendMessage(NamedTextColor.AQUA + "Your Current Filter: " + currentFilter.getChatColor() + 
                             currentFilter.getDisplayName() + " " + currentFilter.emoji);
        }
        
        sender.sendMessage(NamedTextColor.GRAY + "Your messages are transformed using AI to add personality.");
        sender.sendMessage(NamedTextColor.GRAY + "Original messages are not stored permanently.");
        sender.sendMessage(NamedTextColor.GREEN + "Use " + NamedTextColor.AQUA + "/chatfilter help" + 
                         NamedTextColor.GREEN + " for more commands.");
        
        return true;
    }
    
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(NamedTextColor.AQUA + "=== Random Dialogue Admin Commands ===");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter status [player]" + NamedTextColor.WHITE + " - Show server or player status");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter list" + NamedTextColor.WHITE + " - List available filters");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter mode <mode>" + NamedTextColor.WHITE + " - Change server mode");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter enable <player>" + NamedTextColor.WHITE + " - Enable filters for player");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter disable <player>" + NamedTextColor.WHITE + " - Disable filters for player");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter set <player> <filter>" + NamedTextColor.WHITE + " - Set filter for player");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter reroll <player>" + NamedTextColor.WHITE + " - Reroll filter for player");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter who" + NamedTextColor.WHITE + " - Show every player's current filter status");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter players" + NamedTextColor.WHITE + " - Show every player's current filter status");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter llm_info" + NamedTextColor.WHITE + " - Show current LLM information");
        sender.sendMessage(NamedTextColor.YELLOW + "/chatfilter reload" + NamedTextColor.WHITE + " - Reload filters from JSON");
        return true;
    }
}
