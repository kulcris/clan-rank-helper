package com.clanrankhelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
    name = "Clan Rank Helper",
    description = "Shows clan members who need rank changes based on API data",
    tags = {"clan", "rank", "management"}
)
public class ClanRankHelperPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClanRankHelperConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClanRankHelperOverlay overlay;

    @Inject
    private ClanChatOverlay clanChatOverlay;

    @Inject
    private Gson gson;

    @Getter
    private final Map<String, String> pendingRankChanges = new ConcurrentHashMap<>();
    
    @Getter
    private final Set<String> confirmedNeedsChange = ConcurrentHashMap.newKeySet();
    
    @Getter
    private final Set<String> confirmedOk = ConcurrentHashMap.newKeySet();

    private ScheduledExecutorService executor;
    
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("clanrankhelper"))
        {
            return;
        }
        
        if (event.getKey().equals("resetData") && config.resetData())
        {
            log.debug("Resetting confirmed data");
            confirmedNeedsChange.clear();
            confirmedOk.clear();
        }
        
        // Re-fetch when API URL changes
        if (event.getKey().equals("apiUrl"))
        {
            log.debug("API URL changed, re-fetching data");
            executor.submit(this::fetchRankData);
        }
        
        // Re-fetch when Google Sheets URL changes
        if (event.getKey().equals("googleSheetsUrl"))
        {
            log.debug("Google Sheets URL changed, re-fetching data");
            executor.submit(this::fetchRankData);
        }
    }

    @Override
    protected void startUp() throws Exception
    {
        log.debug("Clan Rank Helper starting up ===");
        overlayManager.add(overlay);
        overlayManager.add(clanChatOverlay);
        log.debug("Overlays added");
        
        executor = Executors.newSingleThreadScheduledExecutor();
        
        // Fetch immediately on startup
        log.debug("Submitting fetch task");
        executor.submit(this::fetchRankData);
        
        // Then refresh periodically based on config
        scheduleRefresh();
        log.debug("Clan Rank Helper started ===");
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.debug("Clan Rank Helper stopped");
        overlayManager.remove(overlay);
        overlayManager.remove(clanChatOverlay);
        
        if (executor != null)
        {
            executor.shutdown();
        }
        pendingRankChanges.clear();
        confirmedNeedsChange.clear();
        confirmedOk.clear();
    }

    private void scheduleRefresh()
    {
        int refreshMinutes = config.refreshInterval();
        executor.scheduleAtFixedRate(
            this::fetchRankData,
            refreshMinutes,
            refreshMinutes,
            TimeUnit.MINUTES
        );
    }

    private void fetchRankData()
    {
        // Check if Google Sheets URL is configured (takes priority)
        String sheetsUrl = config.googleSheetsUrl();
        if (sheetsUrl != null && !sheetsUrl.trim().isEmpty())
        {
            fetchFromGoogleSheets(sheetsUrl.trim());
            return;
        }
        
        // Otherwise use API URL
        String apiUrl = config.apiUrl();
        if (apiUrl != null && !apiUrl.trim().isEmpty())
        {
            fetchFromApi(apiUrl.trim());
            return;
        }
        
        log.debug("Clan Rank Helper: No API URL or Google Sheets URL configured");
    }
    
    private void fetchFromGoogleSheets(String sheetsUrl)
    {
        try
        {
            // Convert Google Sheets URL to CSV export URL
            // Input: https://docs.google.com/spreadsheets/d/SHEET_ID/edit?usp=sharing
            // Output: https://docs.google.com/spreadsheets/d/SHEET_ID/export?format=csv
            
            String csvUrl = convertToCsvUrl(sheetsUrl);
            if (csvUrl == null)
            {
                log.error("Clan Rank Helper: Invalid Google Sheets URL format");
                return;
            }
            
            log.debug("Clan Rank Helper: Fetching from Google Sheets: {}", csvUrl);
            
            URL url = new URL(csvUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RuneLite-ClanRankHelper");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            
            int responseCode = conn.getResponseCode();
            log.debug("Clan Rank Helper: Response code: {}", responseCode);
            
            if (responseCode != 200)
            {
                log.error("Clan Rank Helper: Google Sheets returned non-200 response: {}", responseCode);
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            Map<String, String> rankMap = new HashMap<>();
            String line;
            boolean firstLine = true;
            
            while ((line = reader.readLine()) != null)
            {
                // Skip header row if it looks like a header
                if (firstLine)
                {
                    firstLine = false;
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.contains("rsn") || lowerLine.contains("name") || lowerLine.contains("rank"))
                    {
                        continue; // Skip header
                    }
                }
                
                // Parse CSV line (handle quoted values)
                String[] parts = parseCsvLine(line);
                if (parts.length >= 2)
                {
                    String rsn = parts[0].trim();
                    String rank = parts[1].trim();
                    
                    if (!rsn.isEmpty() && !rank.isEmpty())
                    {
                        rankMap.put(rsn.toLowerCase(), rank);
                    }
                }
            }
            reader.close();
            conn.disconnect();
            
            log.debug("Clan Rank Helper: Fetched {} entries from Google Sheets", rankMap.size());
            updateRankData(rankMap);
            
        }
        catch (Exception e)
        {
            log.error("Clan Rank Helper: Failed to fetch from Google Sheets - {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
    
    private String convertToCsvUrl(String sheetsUrl)
    {
        // Handle various Google Sheets URL formats
        // https://docs.google.com/spreadsheets/d/SHEET_ID/edit...
        // https://docs.google.com/spreadsheets/d/SHEET_ID/...
        
        try
        {
            if (sheetsUrl.contains("/spreadsheets/d/"))
            {
                int startIdx = sheetsUrl.indexOf("/spreadsheets/d/") + 16;
                int endIdx = sheetsUrl.indexOf("/", startIdx);
                if (endIdx == -1)
                {
                    endIdx = sheetsUrl.indexOf("?", startIdx);
                }
                if (endIdx == -1)
                {
                    endIdx = sheetsUrl.length();
                }
                
                String sheetId = sheetsUrl.substring(startIdx, endIdx);
                
                // Check if there's a gid parameter for specific sheet
                String gid = "";
                if (sheetsUrl.contains("gid="))
                {
                    int gidStart = sheetsUrl.indexOf("gid=") + 4;
                    int gidEnd = sheetsUrl.indexOf("&", gidStart);
                    if (gidEnd == -1) gidEnd = sheetsUrl.length();
                    gid = "&gid=" + sheetsUrl.substring(gidStart, gidEnd);
                }
                
                return "https://docs.google.com/spreadsheets/d/" + sheetId + "/export?format=csv" + gid;
            }
        }
        catch (Exception e)
        {
            log.error("Clan Rank Helper: Error parsing Google Sheets URL", e);
        }
        return null;
    }
    
    private String[] parseCsvLine(String line)
    {
        // Simple CSV parser that handles quoted values
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++)
        {
            char c = line.charAt(i);
            
            if (c == '"')
            {
                inQuotes = !inQuotes;
            }
            else if (c == ',' && !inQuotes)
            {
                result.add(current.toString());
                current = new StringBuilder();
            }
            else
            {
                current.append(c);
            }
        }
        result.add(current.toString());
        
        return result.toArray(new String[0]);
    }
    
    private void fetchFromApi(String apiUrl)
    {
        try
        {
            log.debug("Clan Rank Helper: Fetching from {}", apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "RuneLite-ClanRankHelper");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            log.debug("Clan Rank Helper: Connecting...");
            int responseCode = conn.getResponseCode();
            log.debug("Clan Rank Helper: Response code: {}", responseCode);
            
            if (responseCode != 200)
            {
                log.error("Clan Rank Helper: API returned non-200 response: {}", responseCode);
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
            {
                response.append(line);
            }
            reader.close();
            conn.disconnect();

            log.debug("Clan Rank Helper: Got response, parsing JSON...");
            
            // Parse the JSON response
            Type listType = new TypeToken<List<RankEntry>>(){}.getType();
            List<RankEntry> entries = gson.fromJson(response.toString(), listType);

            if (entries == null)
            {
                log.error("Clan Rank Helper: Failed to parse JSON - entries is null");
                return;
            }

            // Convert to map: playerName -> targetRank
            Map<String, String> rankMap = new HashMap<>();
            for (RankEntry entry : entries)
            {
                if (entry.mainRSN != null && !entry.mainRSN.isEmpty())
                {
                    String normalizedName = entry.mainRSN.toLowerCase().trim();
                    rankMap.put(normalizedName, entry.osrsName);
                }
            }

            log.debug("Clan Rank Helper: Fetched {} pending rank changes", rankMap.size());
            updateRankData(rankMap);

        }
        catch (Exception e)
        {
            log.error("Clan Rank Helper: Failed to fetch rank data", e);
        }
    }

    private void updateRankData(Map<String, String> newData)
    {
        pendingRankChanges.clear();
        pendingRankChanges.putAll(newData);
        confirmedNeedsChange.clear();
        confirmedOk.clear();
        log.debug("Updated rank data: {} pending changes", pendingRankChanges.size());
    }
    
    public void markNeedsChange(String playerName)
    {
        String lower = playerName.toLowerCase();
        confirmedNeedsChange.add(lower);
        confirmedOk.remove(lower);
    }
    
    public void markConfirmedOk(String playerName)
    {
        String lower = playerName.toLowerCase();
        confirmedOk.add(lower);
        confirmedNeedsChange.remove(lower);
    }

    @Provides
    ClanRankHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ClanRankHelperConfig.class);
    }

    // Inner class for JSON parsing
    private static class RankEntry
    {
        String mainRSN;
        String osrsName;
    }
}
