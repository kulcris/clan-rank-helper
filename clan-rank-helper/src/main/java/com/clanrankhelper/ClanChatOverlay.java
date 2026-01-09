package com.clanrankhelper;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;
import java.util.Map;

public class ClanChatOverlay extends Overlay
{
    private final Client client;
    private final ClanRankHelperPlugin plugin;
    private final ClanRankHelperConfig config;

    // Clan settings interface group ID (693) and member list child
    // You may need to adjust these using RuneLite's Developer Tools > Widget Inspector
    private static final int CLAN_SETTINGS_GROUP_ID = 693;
    private static final int CLAN_SETTINGS_MEMBERS_LIST_CHILD = 11;

    @Inject
    public ClanChatOverlay(Client client, ClanRankHelperPlugin plugin, ClanRankHelperConfig config)
    {
        super(plugin);
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightInChat())
        {
            return null;
        }

        Map<String, String> pendingChanges = plugin.getPendingRankChanges();
        if (pendingChanges.isEmpty())
        {
            return null;
        }

        // Only scan when the Clan Settings Member List window is open
        // This is widget group 693 - check if it's visible and has the member list structure
        Widget clanSettingsWidget = client.getWidget(693, 0);
        if (clanSettingsWidget == null || clanSettingsWidget.isHidden())
        {
            // Member list window is not open, don't scan
            return null;
        }
        
        // Additional check: look for the "Member list" title or the member list content
        // Widget 693,1 typically contains the title bar
        Widget titleWidget = client.getWidget(693, 1);
        boolean isMemberListOpen = false;
        
        if (titleWidget != null)
        {
            // Check children for title text
            Widget[] children = titleWidget.getDynamicChildren();
            if (children != null)
            {
                for (Widget child : children)
                {
                    if (child != null && child.getText() != null && 
                        child.getText().toLowerCase().contains("member list"))
                    {
                        isMemberListOpen = true;
                        break;
                    }
                }
            }
            // Also check static children
            if (!isMemberListOpen)
            {
                children = titleWidget.getStaticChildren();
                if (children != null)
                {
                    for (Widget child : children)
                    {
                        if (child != null && child.getText() != null && 
                            child.getText().toLowerCase().contains("member list"))
                        {
                            isMemberListOpen = true;
                            break;
                        }
                    }
                }
            }
        }
        
        // Fallback: check if widget 693 has the member list structure (many children with names/ranks)
        if (!isMemberListOpen)
        {
            // Check widget 693,11 which typically contains the member list
            Widget memberListWidget = client.getWidget(693, 11);
            if (memberListWidget != null && !memberListWidget.isHidden())
            {
                Widget[] dynamicChildren = memberListWidget.getDynamicChildren();
                if (dynamicChildren != null && dynamicChildren.length > 10)
                {
                    // Has many children, likely the member list
                    isMemberListOpen = true;
                }
            }
        }
        
        if (!isMemberListOpen)
        {
            return null;
        }

        // Scan the clan settings widget and its children
        for (int childId = 0; childId < 30; childId++)
        {
            Widget widget = client.getWidget(693, childId);
            if (widget != null && !widget.isHidden())
            {
                checkWidgetAndChildren(graphics, widget, pendingChanges);
            }
        }

        return null;
    }
    
    private void checkWidgetAndChildren(Graphics2D graphics, Widget widget, Map<String, String> pendingChanges)
    {
        if (widget == null)
        {
            return;
        }
        
        // Collect all text widgets from all child types
        java.util.List<Widget> allTextWidgets = new java.util.ArrayList<>();
        
        // Check dynamic children
        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null)
        {
            for (Widget child : dynamicChildren)
            {
                if (child != null && child.getText() != null && !child.getText().isEmpty())
                {
                    allTextWidgets.add(child);
                }
            }
        }
        
        // Check static children
        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null)
        {
            for (Widget child : staticChildren)
            {
                if (child != null && child.getText() != null && !child.getText().isEmpty())
                {
                    allTextWidgets.add(child);
                }
            }
        }
        
        // Check nested children
        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null)
        {
            for (Widget child : nestedChildren)
            {
                if (child != null && child.getText() != null && !child.getText().isEmpty())
                {
                    allTextWidgets.add(child);
                }
            }
        }
        
        if (allTextWidgets.isEmpty())
        {
            return;
        }
        
        // Convert to array for the rank finder
        Widget[] widgetArray = allTextWidgets.toArray(new Widget[0]);
        
        // Now process each widget to find player names
        for (Widget child : allTextWidgets)
        {
            String widgetText = child.getText();
            String playerName = extractPlayerName(widgetText);
            
            if (playerName == null || playerName.isEmpty())
            {
                continue;
            }
            
            String normalizedName = playerName.toLowerCase().trim().replace(" ", "").replace("-", "").replace("_", "");
            
            // Check if this player is in our list (try both with and without spaces)
            String targetRank = null;
            for (Map.Entry<String, String> entry : pendingChanges.entrySet())
            {
                String apiName = entry.getKey().replace(" ", "").replace("-", "").replace("_", "");
                if (apiName.equals(normalizedName))
                {
                    targetRank = entry.getValue();
                    break;
                }
            }
            
            if (targetRank != null)
            {
                // Check if player is in the ignored list
                if (isPlayerIgnored(playerName))
                {
                    continue;
                }
                
                // Now find their current rank by looking at nearby widgets
                String currentRank = findCurrentRankForPlayer(widgetArray, child);
                
                // If we couldn't find the current rank, skip
                if (currentRank == null)
                {
                    continue;
                }
                
                // Check if current rank is in the ignored list
                if (isRankIgnored(currentRank))
                {
                    plugin.markConfirmedOk(playerName);
                    continue;
                }
                
                // Only highlight if ranks don't match
                if (!currentRank.equalsIgnoreCase(targetRank))
                {
                    plugin.markNeedsChange(playerName);
                    renderHighlight(graphics, child, targetRank);
                }
                else
                {
                    plugin.markConfirmedOk(playerName);
                }
            }
        }
    }
    
    private boolean isPlayerIgnored(String playerName)
    {
        String ignoredPlayers = config.ignoredPlayers();
        if (ignoredPlayers == null || ignoredPlayers.trim().isEmpty())
        {
            return false;
        }
        
        String normalizedName = playerName.toLowerCase().replace(" ", "").replace("-", "").replace("_", "");
        
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
    
    private boolean isRankIgnored(String rank)
    {
        String ignoredRanks = config.ignoredRanks();
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
    
    private String findCurrentRankForPlayer(Widget[] allWidgets, Widget nameWidget)
    {
        if (nameWidget == null)
        {
            return null;
        }
        
        Rectangle nameBounds = nameWidget.getBounds();
        if (nameBounds == null)
        {
            return null;
        }
        
        // Look for a rank widget on the same row (similar Y position)
        // Include common ranks plus any ranks from the ignored list in config
        java.util.Set<String> possibleRanks = new java.util.HashSet<>();
        
        // Default OSRS clan ranks
        String[] defaultRanks = {"Recruit", "Corporal", "Sergeant", "Cadet", "Lieutenant", "Captain", 
                                  "Veteran", "General", "Master", "Owner", "Deputy Owner", "Guest"};
        for (String r : defaultRanks) {
            possibleRanks.add(r.toLowerCase());
        }
        
        // Custom clan ranks (add common ones)
        String[] customRanks = {"Therapist", "Defiler", "Overseer", "Coordinator", "Organiser", "Admin",
                                 "Dogsbody", "Quester", "Oracle", "Teacher", "Artisan", "Medic", "Scout", 
                                 "Guard", "Ranger", "Warrior", "Mage", "Archer", "Champion", "Hero", 
                                 "Legend", "Elder", "Sage", "Mentor", "Initiate", "Novice", "Apprentice", 
                                 "Journeyman", "Expert", "Adept", "Warden", "Sentinel", "Marshal", 
                                 "Commander", "Chief", "Leader", "Founder", "Member", "Friend", "Minion",
                                 "Achiever", "Adventurer", "Collector", "Competitor", "Skiller", "Slayer",
                                 "Banker", "Crafter", "Farmer", "Fisher", "Hunter", "Miner", "Smith",
                                 "Woodcutter", "Cook", "Fletcher", "Runecrafter", "Thief", "Assassin"};
        for (String r : customRanks) {
            possibleRanks.add(r.toLowerCase());
        }
        
        // Also add any ranks from the ignored list (so we can detect them)
        String ignoredRanksConfig = config.ignoredRanks();
        if (ignoredRanksConfig != null && !ignoredRanksConfig.trim().isEmpty()) {
            String[] ignored = ignoredRanksConfig.split("[,\\n\\r]+");
            for (String r : ignored) {
                if (!r.trim().isEmpty()) {
                    possibleRanks.add(r.trim().toLowerCase());
                }
            }
        }
        
        // First try: look in the passed widgets array
        for (Widget widget : allWidgets)
        {
            if (widget == null || widget.getText() == null || widget.getText().isEmpty())
            {
                continue;
            }
            
            if (widget == nameWidget)
            {
                continue;
            }
            
            Rectangle widgetBounds = widget.getBounds();
            if (widgetBounds == null)
            {
                continue;
            }
            
            if (Math.abs(widgetBounds.y - nameBounds.y) <= 15)
            {
                String text = extractPlayerName(widget.getText());
                if (text != null && possibleRanks.contains(text.toLowerCase()))
                {
                    return text;
                }
            }
        }
        
        // Second try: scan all widgets in the clan settings interface (multiple widget groups)
        for (int widgetGroupId = 690; widgetGroupId <= 710; widgetGroupId++)
        {
            Widget group = client.getWidget(widgetGroupId, 0);
            if (group == null)
            {
                continue;
            }
            
            // Check all possible child indices
            for (int childIdx = 0; childIdx <= 50; childIdx++)
            {
                Widget parentWidget = client.getWidget(widgetGroupId, childIdx);
                if (parentWidget == null)
                {
                    continue;
                }
                
                // Scan dynamic children
                Widget[] dynamicChildren = parentWidget.getDynamicChildren();
                if (dynamicChildren != null)
                {
                    for (Widget child : dynamicChildren)
                    {
                        String rank = checkWidgetForRank(child, nameBounds, nameWidget, possibleRanks);
                        if (rank != null)
                        {
                            return rank;
                        }
                    }
                }
                
                // Scan static children
                Widget[] staticChildren = parentWidget.getStaticChildren();
                if (staticChildren != null)
                {
                    for (Widget child : staticChildren)
                    {
                        String rank = checkWidgetForRank(child, nameBounds, nameWidget, possibleRanks);
                        if (rank != null)
                        {
                            return rank;
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    private String checkWidgetForRank(Widget widget, Rectangle nameBounds, Widget nameWidget, java.util.Set<String> possibleRanks)
    {
        if (widget == null || widget == nameWidget)
        {
            return null;
        }
        
        String text = widget.getText();
        if (text == null || text.isEmpty())
        {
            return null;
        }
        
        Rectangle widgetBounds = widget.getBounds();
        if (widgetBounds == null)
        {
            return null;
        }
        
        // Check if on same row (within 15 pixels vertically)
        if (Math.abs(widgetBounds.y - nameBounds.y) <= 15)
        {
            String cleanText = extractPlayerName(text);
            if (cleanText != null && possibleRanks.contains(cleanText.toLowerCase()))
            {
                return cleanText;
            }
        }
        
        return null;
    }
    
    private void renderHighlight(Graphics2D graphics, Widget widget, String targetRank)
    {
        Color highlightColor = getRankColor(targetRank);
        
        Rectangle bounds = widget.getBounds();
        if (bounds != null && bounds.width > 0 && bounds.height > 0)
        {
            // Draw background highlight
            graphics.setColor(new Color(
                highlightColor.getRed(),
                highlightColor.getGreen(),
                highlightColor.getBlue(),
                60
            ));
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            
            // Draw border
            graphics.setColor(highlightColor);
            graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            
            // Draw small rank indicator on the right
            String rankAbbr = getRankAbbreviation(targetRank);
            FontMetrics fm = graphics.getFontMetrics();
            int textWidth = fm.stringWidth(rankAbbr);
            
            graphics.setColor(Color.BLACK);
            graphics.fillRect(bounds.x + bounds.width - textWidth - 6, bounds.y, textWidth + 6, bounds.height);
            
            graphics.setColor(highlightColor);
            graphics.drawString(rankAbbr, bounds.x + bounds.width - textWidth - 3, bounds.y + bounds.height - 3);
        }
    }
    
    private void renderWidgetIfMatch(Graphics2D graphics, Widget widget, Map<String, String> pendingChanges)
    {
        // This method is no longer used but kept for compatibility
    }

    private String extractPlayerName(String widgetText)
    {
        if (widgetText == null)
        {
            return null;
        }
        
        // Remove color tags: <col=xxxxxx>text</col>
        String cleaned = widgetText.replaceAll("<col=[^>]*>", "").replaceAll("</col>", "");
        // Remove any img tags: <img=x>
        cleaned = cleaned.replaceAll("<img=[^>]*>", "");
        // Trim whitespace
        cleaned = cleaned.trim();
        
        return cleaned;
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

    private String getRankAbbreviation(String rank)
    {
        if (rank == null || rank.isEmpty())
        {
            return "?";
        }
        
        switch (rank)
        {
            case "Recruit":
                return "RCT";
            case "Corporal":
                return "CPL";
            case "Sergeant":
                return "SGT";
            case "Cadet":
                return "CDT";
            case "Lieutenant":
                return "LT";
            case "Captain":
                return "CPT";
            case "Veteran":
                return "VET";
            case "General":
                return "GEN";
            case "Master":
                return "MST";
            default:
                // For unknown ranks, return first 3 characters (uppercase)
                String abbr = rank.length() > 3 ? rank.substring(0, 3) : rank;
                return abbr.toUpperCase();
        }
    }
}
