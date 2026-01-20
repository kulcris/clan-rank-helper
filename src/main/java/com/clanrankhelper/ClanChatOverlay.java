package com.clanrankhelper;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ClanChatOverlay extends Overlay
{
    private final Client client;
    private final ClanRankHelperPlugin plugin;
    private final ClanRankHelperConfig config;

    private static final int CLAN_SETTINGS_GROUP_ID = 693;

    // Viewport/panel and name column (per your inspector)
    private static final int CLAN_MEMBER_PANEL_CHILD = 9;  // viewport
    private static final int CLAN_MEMBER_NAME_CHILD  = 10; // names live here

    // Row alignment tolerance in pixels
    private static final int ROW_Y_TOLERANCE = 15;

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
        if (pendingChanges == null || pendingChanges.isEmpty())
        {
            return null;
        }

        Widget root = client.getWidget(CLAN_SETTINGS_GROUP_ID, 0);
        if (root == null || root.isHidden())
        {
            return null;
        }

        Widget memberPanel = client.getWidget(CLAN_SETTINGS_GROUP_ID, CLAN_MEMBER_PANEL_CHILD);
        if (memberPanel == null || memberPanel.isHidden())
        {
            return null;
        }

        Rectangle viewport = memberPanel.getBounds();
        if (viewport == null || viewport.width <= 0 || viewport.height <= 0)
        {
            return null;
        }

        Widget nameColumn = client.getWidget(CLAN_SETTINGS_GROUP_ID, CLAN_MEMBER_NAME_CHILD);
        if (nameColumn == null || nameColumn.isHidden())
        {
            return null;
        }

        // All row text under panel (used for rank lookup)
        List<Widget> allRowTextWidgets = new ArrayList<>();
        collectAllTextWidgets(memberPanel, allRowTextWidgets, newSetIdentity());
        if (allRowTextWidgets.isEmpty())
        {
            return null;
        }

        // Only names from the name column
        List<Widget> nameTextWidgets = new ArrayList<>();
        collectAllTextWidgets(nameColumn, nameTextWidgets, newSetIdentity());
        if (nameTextWidgets.isEmpty())
        {
            return null;
        }

        Widget[] rowWidgetArray = allRowTextWidgets.toArray(new Widget[0]);

        for (Widget nameWidget : nameTextWidgets)
        {
            if (nameWidget == null)
            {
                continue;
            }

            String raw = nameWidget.getText();
            if (raw == null || raw.isEmpty())
            {
                continue;
            }

            Rectangle nameBounds = nameWidget.getBounds();
            if (nameBounds == null)
            {
                continue;
            }

            // Only process visible rows
            if (!viewport.intersects(nameBounds))
            {
                continue;
            }

            String playerName = extractCleanText(raw);
            if (playerName == null || playerName.isEmpty())
            {
                continue;
            }

            if (isPlayerIgnored(playerName))
            {
                continue;
            }

            String normalizedName = normalizeName(playerName);

            String targetRank = null;
            for (Map.Entry<String, String> e : pendingChanges.entrySet())
            {
                if (normalizeName(e.getKey()).equals(normalizedName))
                {
                    targetRank = e.getValue();
                    break;
                }
            }

            if (targetRank == null)
            {
                continue;
            }

            // New: rank detection without a whitelist
            String currentRank = findCurrentRankForPlayer(rowWidgetArray, nameWidget);
            if (currentRank == null || currentRank.isEmpty())
            {
                continue;
            }

            if (isRankIgnored(currentRank))
            {
                plugin.markConfirmedOk(playerName);
                continue;
            }

            if (!currentRank.equalsIgnoreCase(targetRank))
            {
                plugin.markNeedsChange(playerName);
                renderHighlight(graphics, nameWidget, targetRank);
            }
            else
            {
                plugin.markConfirmedOk(playerName);
            }
        }

        return null;
    }

    // -----------------------
    // Rank detection (NO whitelist)
    // -----------------------
    private String findCurrentRankForPlayer(Widget[] allWidgets, Widget nameWidget)
    {
        Rectangle nameBounds = nameWidget.getBounds();
        if (nameBounds == null)
        {
            return null;
        }

        String playerNameClean = extractCleanText(nameWidget.getText());
        String playerNameNorm = normalizeName(playerNameClean);

        // Prefer widgets to the RIGHT of the name text (after the name ends)
        final int nameRightX = nameBounds.x + nameBounds.width;

        Widget best = null;
        int bestDx = Integer.MAX_VALUE;

        for (Widget w : allWidgets)
        {
            if (w == null || w == nameWidget)
            {
                continue;
            }

            String txt = w.getText();
            if (txt == null || txt.isEmpty())
            {
                continue;
            }

            Rectangle b = w.getBounds();
            if (b == null)
            {
                continue;
            }

            // Same row
            if (Math.abs(b.y - nameBounds.y) > ROW_Y_TOLERANCE)
            {
                continue;
            }

            // Must be to the right of the name (not just right of name x)
            if (b.x < nameRightX)
            {
                continue;
            }

            String clean = extractCleanText(txt);
            if (clean == null || clean.isEmpty())
            {
                continue;
            }

            // Don’t treat the name itself as the rank
            if (normalizeName(clean).equals(playerNameNorm))
            {
                continue;
            }

            // Only accept text that looks like a rank
            if (!isLikelyRankText(clean))
            {
                continue;
            }

            int dx = b.x - nameRightX;
            if (dx < bestDx)
            {
                bestDx = dx;
                best = w;
            }
        }

        return best == null ? null : extractCleanText(best.getText());
    }
    private boolean isLikelyRankText(String text)
    {
        String s = text.trim();
        if (s.isEmpty())
        {
            return false;
        }

        // Too long to be a rank label (tune if your ranks are long)
        if (s.length() > 20)
        {
            return false;
        }

        String lower = s.toLowerCase();

        // Reject worlds (examples: "W477", "w 477", "world 477")
        if (lower.matches("^w\\s*\\d+$") || lower.matches("^world\\s*\\d+$"))
        {
            return false;
        }

        // Reject pure numbers / times / dates-ish
        if (lower.matches("^\\d+$"))
        {
            return false;
        }
        if (lower.matches("^\\d{1,2}:\\d{2}.*$")) // 10:42, 3:15pm, etc.
        {
            return false;
        }

        // Reject common UI/status strings that are not ranks (add more if you see false positives)
        if (lower.equals("online") || lower.equals("offline") || lower.equals("muted") || lower.equals("banned"))
        {
            return false;
        }

        // Must contain at least one letter
        if (!lower.matches(".*[a-z].*"))
        {
            return false;
        }

        // Allowed characters (letters/digits/spaces/'-/)
        // This still allows “Deputy Owner”, “Co-Leader”, etc.
        return s.matches("^[A-Za-z][A-Za-z0-9 '\\-]{0,19}$");
    }
    // -----------------------
    // Widget collection
    // -----------------------
    private Set<Widget> newSetIdentity()
    {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private void collectAllTextWidgets(Widget root, List<Widget> out, Set<Widget> visited)
    {
        if (root == null || visited.contains(root))
        {
            return;
        }
        visited.add(root);

        String t = root.getText();
        if (t != null && !t.isEmpty())
        {
            out.add(root);
        }

        Widget[] dyn = root.getDynamicChildren();
        if (dyn != null)
        {
            for (Widget c : dyn)
            {
                collectAllTextWidgets(c, out, visited);
            }
        }

        Widget[] stat = root.getStaticChildren();
        if (stat != null)
        {
            for (Widget c : stat)
            {
                collectAllTextWidgets(c, out, visited);
            }
        }

        Widget[] nested = root.getNestedChildren();
        if (nested != null)
        {
            for (Widget c : nested)
            {
                collectAllTextWidgets(c, out, visited);
            }
        }
    }

    // -----------------------
    // Ignore lists
    // -----------------------
    private boolean isPlayerIgnored(String playerName)
    {
        String ignoredPlayers = config.ignoredPlayers();
        if (ignoredPlayers == null || ignoredPlayers.trim().isEmpty())
        {
            return false;
        }

        String normalizedName = normalizeName(playerName);

        String[] ignored = ignoredPlayers.split("[,\\n\\r]+");
        for (String ignoredPlayer : ignored)
        {
            String n = normalizeName(ignoredPlayer.trim());
            if (!n.isEmpty() && n.equals(normalizedName))
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

    // -----------------------
    // Rendering
    // -----------------------
    private void renderHighlight(Graphics2D graphics, Widget widget, String targetRank)
    {
        Color highlightColor = getRankColor(targetRank);

        Rectangle bounds = widget.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        graphics.setColor(new Color(
                highlightColor.getRed(),
                highlightColor.getGreen(),
                highlightColor.getBlue(),
                60
        ));
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        graphics.setColor(highlightColor);
        graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        String rankAbbr = getRankAbbreviation(targetRank);
        FontMetrics fm = graphics.getFontMetrics();
        int textWidth = fm.stringWidth(rankAbbr);

        graphics.setColor(Color.BLACK);
        graphics.fillRect(bounds.x + bounds.width - textWidth - 6, bounds.y, textWidth + 6, bounds.height);

        graphics.setColor(highlightColor);
        graphics.drawString(rankAbbr, bounds.x + bounds.width - textWidth - 3, bounds.y + bounds.height - 3);
    }

    // -----------------------
    // Text cleanup / normalization
    // -----------------------
    private String extractCleanText(String widgetText)
    {
        if (widgetText == null)
        {
            return null;
        }

        String cleaned = widgetText.replaceAll("<col=[^>]*>", "").replaceAll("</col>", "");
        cleaned = cleaned.replaceAll("<img=[^>]*>", "");
        cleaned = cleaned.trim();

        return cleaned;
    }

    private String normalizeName(String name)
    {
        if (name == null)
        {
            return "";
        }

        return extractCleanText(name).toLowerCase().trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
    }

    // -----------------------
    // Colors / abbreviations
    // -----------------------
    private Color getRankColor(String rank)
    {
        Color customColor = getCustomRankColor(rank);
        if (customColor != null)
        {
            return customColor;
        }

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
                        // ignore invalid hex
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
                String abbr = rank.length() > 3 ? rank.substring(0, 3) : rank;
                return abbr.toUpperCase();
        }
    }
}
