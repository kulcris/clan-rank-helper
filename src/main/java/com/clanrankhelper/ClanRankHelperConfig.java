package com.clanrankhelper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.Color;

@ConfigGroup("clanrankhelper")
public interface ClanRankHelperConfig extends Config
{
    @ConfigSection(
        name = "API Settings",
        description = "Configure the API connection",
        position = 0
    )
    String apiSection = "api";

    @ConfigSection(
            name = "Sheets Customization ",
            description = "Configure the API connection",
            position = 1
    )
    String sheetsSection = "SheetsCustom";

    @ConfigSection(
        name = "Display Settings",
        description = "Configure how rank changes are displayed",
        position = 2,
        closedByDefault = true
    )
    String displaySection = "display";

    @ConfigSection(
        name = "Rank Colors",
        description = "Customize colors for each rank",
        position = 3,
        closedByDefault = true
    )
    String rankColorsSection = "rankColors";

    @ConfigItem(
        keyName = "apiUrl",
        name = "API URL",
        description = "URL to fetch pending rank changes. Must return JSON array: [{\"mainRSN\": \"PlayerName\", \"osrsName\": \"TargetRank\"}, ...]",
        section = apiSection,
        position = 0,
        warning = "This plugin submits your IP address to a 3rd party website not controlled or verified by the RuneLite Developers."
    )
    default String apiUrl()
    {
        return "";
    }

    @ConfigItem(
        keyName = "googleSheetsUrl",
        name = "Google Sheets URL",
        description = "URL to a public Google Sheet. Column A = RSN, Column B = Rank (must be the in-game rank name, not clan-specific name). Leave empty to use API URL instead.",
        section = apiSection,
        position = 1,
        warning = "This plugin submits your IP address to a 3rd party website not controlled or verified by the RuneLite Developers."
    )
    default String googleSheetsUrl()
    {
        return "";
    }

    @ConfigItem(
            keyName = "sheetsRsnColumn",
            name = "Sheets RSN column",
            description = "number-based column index in the google sheet that contains the RSN/name (e.g., 1 = column A).",
            section = sheetsSection,
            position = 3
    )
    default int sheetsRsnColumn()
    {
        return 1;
    }

    @ConfigItem(
            keyName = "sheetsRankColumn",
            name = "Sheets rank column",
            description = "number-based column index in the google sheet that contains the target rank (e.g., 2 = column B).",
            section = sheetsSection,
            position = 4
    )
    default int sheetsRankColumn()
    {
        return 2;
    }

    @ConfigItem(
            keyName = "sheetsHasHeader",
            name = "Sheets has header row",
            description = "If enabled, the first row will be treated as a header and skipped.",
            section = sheetsSection,
            position = 5
    )
    default boolean sheetsHasHeader()
    {
        return true;
    }

    @ConfigItem(
        keyName = "refreshInterval",
        name = "Refresh Interval (minutes)",
        description = "How often to refresh rank data from the API/Google Sheets",
        section = apiSection,
        position = 2
    )
    default int refreshInterval()
    {
        return 5;
    }

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Overlay Panel",
        description = "Show a panel listing all pending rank changes",
        section = displaySection,
        position = 0
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightInChat",
        name = "Highlight in Member List",
        description = "Highlight players who need rank changes in the member list",
        section = displaySection,
        position = 1
    )
    default boolean highlightInChat()
    {
        return true;
    }

    @ConfigItem(
        keyName = "highlightColor",
        name = "Default Highlight Color",
        description = "Default color for unknown ranks",
        section = displaySection,
        position = 2
    )
    default Color highlightColor()
    {
        return new Color(255, 200, 0); // Gold/yellow
    }

    @ConfigItem(
        keyName = "maxDisplayed",
        name = "Max Displayed in Overlay",
        description = "Maximum number of pending changes to show in the overlay (0 for all)",
        section = displaySection,
        position = 3
    )
    default int maxDisplayed()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "ignoredRanks",
        name = "Ignored Current Ranks",
        description = "Comma-separated list of current ranks to ignore (e.g. Owner,Deputy Owner,Therapist)",
        section = displaySection,
        position = 4
    )
    default String ignoredRanks()
    {
        return "";
    }

    @ConfigItem(
        keyName = "ignoredTargetRanks",
        name = "Ignored Target Ranks",
        description = "Comma-separated list of target ranks to ignore in the overlay (e.g. Recruit,Corporal)",
        section = displaySection,
        position = 5
    )
    default String ignoredTargetRanks()
    {
        return "";
    }

    @ConfigItem(
        keyName = "ignoredPlayers",
        name = "Ignored Players",
        description = "Comma-separated list of player RSNs to ignore (e.g. Steve ffs,SomePlayer)",
        section = displaySection,
        position = 6
    )
    default String ignoredPlayers()
    {
        return "";
    }

    @ConfigItem(
        keyName = "resetData",
        name = "Reset Checked Data",
        description = "Toggle ON to reset all checked/confirmed data",
        section = displaySection,
        position = 7
    )
    default boolean resetData()
    {
        return false;
    }

    // ==================== Rank Colors Section ====================

    @ConfigItem(
        keyName = "recruitColor",
        name = "Recruit",
        description = "Color for Recruit rank",
        section = rankColorsSection,
        position = 0
    )
    default Color recruitColor()
    {
        return new Color(144, 238, 144); // Light green
    }

    @ConfigItem(
        keyName = "corporalColor",
        name = "Corporal",
        description = "Color for Corporal rank",
        section = rankColorsSection,
        position = 1
    )
    default Color corporalColor()
    {
        return new Color(100, 149, 237); // Cornflower blue
    }

    @ConfigItem(
        keyName = "sergeantColor",
        name = "Sergeant",
        description = "Color for Sergeant rank",
        section = rankColorsSection,
        position = 2
    )
    default Color sergeantColor()
    {
        return new Color(255, 165, 0); // Orange
    }

    @ConfigItem(
        keyName = "cadetColor",
        name = "Cadet",
        description = "Color for Cadet rank",
        section = rankColorsSection,
        position = 3
    )
    default Color cadetColor()
    {
        return new Color(138, 43, 226); // Purple
    }

    @ConfigItem(
        keyName = "lieutenantColor",
        name = "Lieutenant",
        description = "Color for Lieutenant rank",
        section = rankColorsSection,
        position = 4
    )
    default Color lieutenantColor()
    {
        return new Color(255, 215, 0); // Gold
    }

    @ConfigItem(
        keyName = "captainColor",
        name = "Captain",
        description = "Color for Captain rank",
        section = rankColorsSection,
        position = 5
    )
    default Color captainColor()
    {
        return new Color(255, 69, 0); // Red-orange
    }

    @ConfigItem(
        keyName = "veteranColor",
        name = "Veteran",
        description = "Color for Veteran rank",
        section = rankColorsSection,
        position = 6
    )
    default Color veteranColor()
    {
        return new Color(192, 192, 192); // Silver
    }

    @ConfigItem(
        keyName = "generalColor",
        name = "General",
        description = "Color for General rank",
        section = rankColorsSection,
        position = 7
    )
    default Color generalColor()
    {
        return new Color(255, 0, 0); // Red
    }

    @ConfigItem(
        keyName = "masterColor",
        name = "Master",
        description = "Color for Master rank",
        section = rankColorsSection,
        position = 8
    )
    default Color masterColor()
    {
        return new Color(255, 0, 255); // Magenta
    }

    @ConfigItem(
        keyName = "customRankColors",
        name = "Custom Rank Colors",
        description = "Custom colors for ranks. Format: RankName:#HEXCOLOR,RankName2:#HEXCOLOR2 (e.g. Oracle:#FF5500,Teacher:#00FF00)",
        section = rankColorsSection,
        position = 9
    )
    default String customRankColors()
    {
        return "";
    }
}
