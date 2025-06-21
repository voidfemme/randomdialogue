package com.chatfilter;

import com.chatfilter.config.FilterMode;
import com.chatfilter.filter.ChatFilter;
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
import org.bukkit.ChatColor;
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
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
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
            default:
                return showHelp(sender);
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String[] subcommands = {"mode", "enable", "disable", "set", "reroll", "status", "list", "reload"};
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
            sender.sendMessage(ChatColor.AQUA + "Current filter mode: " + ChatColor.YELLOW + currentMode.name().toLowerCase());
            return true;
        }
        
        if (!sender.hasPermission("randomdialogue.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to change the server mode.");
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
                case DISABLED -> ChatColor.RED + "Chat filters have been DISABLED!";
                case MANUAL -> ChatColor.GREEN + "Chat filters set to MANUAL mode - players can choose their own!";
                case DAILY_RANDOM -> ChatColor.GOLD + "Chat filters set to DAILY RANDOM mode - new personality each day!";
                case SESSION_RANDOM -> ChatColor.AQUA + "Chat filters set to SESSION RANDOM mode - new personality each login!";
                case CHAOS_MODE -> ChatColor.DARK_RED + "" + ChatColor.BOLD + "CHAOS MODE ACTIVATED! Every message is a different personality!";
            };
            
            sender.getServer().broadcastMessage(message);
            sender.sendMessage(ChatColor.GREEN + "Filter mode changed from " + oldMode.name().toLowerCase() + " to " + newMode.name().toLowerCase());
            
            LOGGER.info("Filter mode changed from " + oldMode + " to " + newMode + " by " + sender.getName());
            return true;
            
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid mode. Available modes: " + 
                             Arrays.stream(FilterMode.values())
                                   .map(m -> m.name().toLowerCase())
                                   .collect(Collectors.joining(", ")));
            return true;
        }
    }
    
    private boolean enablePlayerFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /chatfilter enable <player>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        if (playerManager.getCurrentMode() == FilterMode.DISABLED) {
            sender.sendMessage(ChatColor.RED + "Chat filters are currently disabled on the server.");
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
        sender.sendMessage(ChatColor.GREEN + "Chat filters enabled for " + targetPlayer.getName());
        return true;
    }
    
    private boolean disablePlayerFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /chatfilter disable <player>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), false);
        sender.sendMessage(ChatColor.GREEN + "Chat filters disabled for " + targetPlayer.getName());
        return true;
    }
    
    private boolean handleSetCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /chatfilter set <player> <filter>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        FilterDefinition filter = FilterManager.getInstance().getFilter(args[2].toUpperCase());
        if (filter != null && filter.enabled) {
            playerManager.setPlayerFilter(targetPlayer.getUniqueId(), filter);
            playerManager.setPlayerEnabled(targetPlayer.getUniqueId(), true);
            
            sender.sendMessage(ChatColor.GREEN + "Filter set for " + targetPlayer.getName() + " to: " + filter.getChatColor() + 
                             filter.getDisplayName() + ChatColor.GREEN + " " + filter.emoji);
            return true;
        } else {
            sender.sendMessage(ChatColor.RED + "Invalid filter. Use " + ChatColor.AQUA + "/chatfilter list" + 
                             ChatColor.RED + " to see available filters.");
            return true;
        }
    }
    
    private boolean rerollFilter(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /chatfilter reroll <player>");
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        if (playerManager.rerollPlayerFilter(targetPlayer)) {
            sender.sendMessage(ChatColor.GREEN + "Filter rerolled for " + targetPlayer.getName());
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Reroll is not available in " + 
                             playerManager.getCurrentMode().name().toLowerCase() + " mode.");
        }
        return true;
    }
    
    private boolean showStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            FilterMode currentMode = playerManager.getCurrentMode();
            sender.sendMessage(ChatColor.AQUA + "=== Server Status ===");
            sender.sendMessage(ChatColor.AQUA + "Current server mode: " + ChatColor.YELLOW + currentMode.name().toLowerCase());
            sender.sendMessage(ChatColor.AQUA + "Enabled players: " + ChatColor.YELLOW + playerManager.getEnabledPlayers().size());
            return true;
        }
        
        Player targetPlayer = sender.getServer().getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return true;
        }
        
        PlayerFilterManager.PlayerFilterStats stats = playerManager.getPlayerStats(targetPlayer.getUniqueId());
        sender.sendMessage(ChatColor.AQUA + "=== " + targetPlayer.getName() + "'s Filter Status ===");
        sender.sendMessage(ChatColor.AQUA + "Server Mode: " + ChatColor.YELLOW + stats.mode.name().toLowerCase());
        sender.sendMessage(ChatColor.AQUA + "Enabled: " + (stats.enabled ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        
        if (stats.enabled && stats.currentFilter != null) {
            sender.sendMessage(ChatColor.AQUA + "Current Filter: " + stats.currentFilter.getChatColor() + 
                             stats.currentFilter.getDisplayName() + ChatColor.AQUA + " " + stats.currentFilter.emoji);
        }
        
        if (stats.lastAssignedDate != null) {
            sender.sendMessage(ChatColor.AQUA + "Last Assigned: " + ChatColor.YELLOW + stats.lastAssignedDate);
        }
        
        return true;
    }
    
    private boolean listFilters(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== Available Chat Filters ===");
        for (FilterDefinition filter : FilterManager.getInstance().getEnabledFilters()) {
            sender.sendMessage(filter.getChatColor() + filter.getDisplayName() + ChatColor.RESET + " " + 
                             filter.emoji + ChatColor.GRAY + " - " + filter.name.toLowerCase());
        }
        return true;
    }

    private boolean showPlayerFilters(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== Current Filter Assignments ===");
        boolean anyPlayersOnline = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            anyPlayersOnline = true;

            PlayerFilterStats stats = playerManager.getPlayerStats(player.getUniqueId());

            // Get the player's filter stats
            String status = stats.enabled ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
            String filterName = stats.currentFilter != null ? stats.currentFilter.getName() : "None";

            sender.sendMessage(ChatColor.YELLOW + player.getName() +
                               ChatColor.WHITE + ": " + status + " " +
                               ChatColor.AQUA + filterName +
                               ChatColor.GRAY + " (" + stats.mode + ")");
        }

        if (!anyPlayersOnline) {
            sender.sendMessage(ChatColor.GRAY + "No players online.");
        }

        sender.sendMessage(ChatColor.GOLD + "==================================");
        return true;
    }
    
    private boolean reloadFilters(CommandSender sender) {
        FilterManager.getInstance().reloadFilters();
        sender.sendMessage(ChatColor.GREEN + "Filters reloaded from JSON configuration.");
        return true;
    }
    
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "=== Random Dialogue Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter status [player]" + ChatColor.WHITE + " - Show server or player status");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter list" + ChatColor.WHITE + " - List available filters");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter mode <mode>" + ChatColor.WHITE + " - Change server mode");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter enable <player>" + ChatColor.WHITE + " - Enable filters for player");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter disable <player>" + ChatColor.WHITE + " - Disable filters for player");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter set <player> <filter>" + ChatColor.WHITE + " - Set filter for player");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter reroll <player>" + ChatColor.WHITE + " - Reroll filter for player");
        sender.sendMessage(ChatColor.YELLOW + "/chatfilter reload" + ChatColor.WHITE + " - Reload filters from JSON");
        return true;
    }
}
