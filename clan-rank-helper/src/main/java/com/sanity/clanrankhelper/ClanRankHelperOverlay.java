package com.sanity.clanrankhelper;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ClanRankHelperOverlay extends OverlayPanel
{
    private final ClanRankHelperPlugin plugin;
    private final ClanRankHelperConfig config;

    // Define rank order for sorting
    private static final Map<String, Integer> RANK_ORDER = Map.of(
        "Recruit", 1,
        "Corporal", 2,
        "Sergeant", 3,
        "Cadet", 4,
        "Lieutenant", 5,
        "Captain", 6,
        "Veteran", 7,
        "General", 8,
        "Master", 9
    );

    @Inject
    public ClanRankHelperOverlay(ClanRankHelperPlugin plugin, ClanRankHelperConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        Map<String, String> pendingChanges = plugin.getPendingRankChanges();
        if (pendingChanges.isEmpty())
        {
            return null;
        }

        // Build list of pending changes - show all confirmed to need a change
        List<Map.Entry<String, String>> displayList = new ArrayList<>();
        
        // Get the sets of confirmed members
        java.util.Set<String> confirmedNeedsChange = plugin.getConfirmedNeedsChange();
        java.util.Set<String> confirmedOk = plugin.getConfirmedOk();
        
        for (Map.Entry<String, String> entry : pendingChanges.entrySet())
        {
            // Skip if target rank is in ignored list
            if (isTargetRankIgnored(entry.getValue()))
            {
                continue;
            }
            
            // Skip if player is in ignored list
            if (isPlayerIgnored(entry.getKey()))
            {
                continue;
            }
            
            String playerNameLower = entry.getKey().toLowerCase();
            
            // Only show if confirmed to need a change
            if (confirmedNeedsChange.contains(playerNameLower))
            {
                displayList.add(entry);
            }
        }
        
        // Calculate how many members have been checked
        int totalFromApi = pendingChanges.size();
        int checkedCount = 0;
        for (Map.Entry<String, String> entry : pendingChanges.entrySet())
        {
            String playerNameLower = entry.getKey().toLowerCase();
            if (confirmedNeedsChange.contains(playerNameLower) || confirmedOk.contains(playerNameLower))
            {
                checkedCount++;
            }
        }

        if (displayList.isEmpty())
        {
            // Show status message
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Rank Helper")
                .color(Color.GREEN)
                .build());
            
            if (checkedCount < totalFromApi)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Checked: " + checkedCount + "/" + totalFromApi)
                    .leftColor(Color.YELLOW)
                    .build());
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Scroll member list")
                    .leftColor(Color.GRAY)
                    .build());
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("to check all")
                    .leftColor(Color.GRAY)
                    .build());
            }
            else
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("All ranks correct!")
                    .leftColor(Color.GREEN)
                    .build());
            }
            return super.render(graphics);
        }

        // Sort by rank order
        displayList.sort(Comparator.comparingInt(e -> RANK_ORDER.getOrDefault(e.getValue(), 99)));

        // Title
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Ranks Needed")
            .color(Color.YELLOW)
            .build());

        // Display count
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Need change: " + displayList.size())
            .leftColor(Color.WHITE)
            .build());
        
        if (checkedCount < totalFromApi)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Checked: " + checkedCount + "/" + totalFromApi)
                .leftColor(Color.GRAY)
                .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("─────────────────")
            .leftColor(Color.DARK_GRAY)
            .build());

        // Limit display if configured
        int maxDisplay = config.maxDisplayed();
        int count = 0;

        for (Map.Entry<String, String> entry : displayList)
        {
            if (maxDisplay > 0 && count >= maxDisplay)
            {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("... and " + (displayList.size() - maxDisplay) + " more")
                    .leftColor(Color.GRAY)
                    .build());
                break;
            }

            String playerName = entry.getKey();
            String targetRank = entry.getValue();
            Color rankColor = getRankColor(targetRank);

            // Capitalize first letter of each word in player name for display
            String displayName = capitalizeWords(playerName);

            panelComponent.getChildren().add(LineComponent.builder()
                .left(displayName)
                .leftColor(Color.WHITE)
                .right("→ " + targetRank)
                .rightColor(rankColor)
                .build());

            count++;
        }

        return super.render(graphics);
    }

    private Color getRankColor(String rank)
    {
        // First check custom rank colors
        Color customColor = getCustomRankColor(rank);
        if (customColor != null)
        {
            return customColor;
        }
        
        // Then check built-in ranks
        switch (rank.toLowerCase())
        {
            case "recruit":
                return config.recruitColor();
            case "corporal":
                return config.corporalColor();
            case "sergeant":
                return config.sergeantColor();
            case "cadet":
                return config.cadetColor();
            case "lieutenant":
                return config.lieutenantColor();
            case "captain":
                return config.captainColor();
            case "veteran":
                return config.veteranColor();
            case "general":
                return config.generalColor();
            case "master":
                return config.masterColor();
            default:
                return config.highlightColor();
        }
    }
    
    private Color getCustomRankColor(String rank)
    {
        String customColors = config.customRankColors();
        if (customColors == null || customColors.trim().isEmpty())
        {
            return null;
        }
        
        String[] pairs = customColors.split(",");
        for (String pair : pairs)
        {
            String[] parts = pair.split(":");
            if (parts.length == 2)
            {
                String rankName = parts[0].trim();
                String hexColor = parts[1].trim();
                
                if (rankName.equalsIgnoreCase(rank))
                {
                    try
                    {
                        return Color.decode(hexColor);
                    }
                    catch (NumberFormatException e)
                    {
                        // Invalid hex, skip
                    }
                }
            }
        }
        return null;
    }

    private String capitalizeWords(String str)
    {
        if (str == null || str.isEmpty())
        {
            return str;
        }
        
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : str.toCharArray())
        {
            if (Character.isWhitespace(c))
            {
                capitalizeNext = true;
                result.append(c);
            }
            else if (capitalizeNext)
            {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            }
            else
            {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    private boolean isTargetRankIgnored(String rank)
    {
        String ignoredRanks = config.ignoredTargetRanks();
        if (ignoredRanks == null || ignoredRanks.trim().isEmpty())
        {
            return false;
        }
        
        // Split by comma, newline, or both
        String[] ignored = ignoredRanks.split("[,\\n\\r]+");
        for (String ignoredRank : ignored)
        {
            if (ignoredRank.trim().equalsIgnoreCase(rank.trim()))
            {
                return true;
            }
        }
        return false;
    }
    
    private boolean isPlayerIgnored(String playerName)
    {
        String ignoredPlayers = config.ignoredPlayers();
        if (ignoredPlayers == null || ignoredPlayers.trim().isEmpty())
        {
            return false;
        }
        
        String normalizedName = playerName.toLowerCase().replace(" ", "").replace("-", "").replace("_", "");
        
        // Split by comma, newline, or both
        String[] ignored = ignoredPlayers.split("[,\\n\\r]+");
        for (String ignoredPlayer : ignored)
        {
            String normalizedIgnored = ignoredPlayer.trim().toLowerCase().replace(" ", "").replace("-", "").replace("_", "");
            if (normalizedIgnored.equals(normalizedName))
            {
                return true;
            }
        }
        return false;
    }
}
