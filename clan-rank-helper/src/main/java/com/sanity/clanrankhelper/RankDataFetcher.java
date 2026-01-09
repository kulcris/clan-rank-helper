package com.sanity.clanrankhelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RankDataFetcher
{
    private final ClanRankHelperPlugin plugin;
    private final Gson gson = new Gson();

    public RankDataFetcher(ClanRankHelperPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void fetchRankData()
    {
        try
        {
            String apiUrl = plugin.getApiUrl();
            log.debug("Fetching rank data from: {}", apiUrl);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "RuneLite-ClanRankHelper");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200)
            {
                log.error("API returned non-200 response: {}", responseCode);
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

            // Parse the JSON response
            Type listType = new TypeToken<List<RankEntry>>(){}.getType();
            List<RankEntry> entries = gson.fromJson(response.toString(), listType);

            // Convert to map: playerName -> targetRank
            Map<String, String> rankMap = new HashMap<>();
            for (RankEntry entry : entries)
            {
                if (entry.mainRSN != null && !entry.mainRSN.isEmpty())
                {
                    // Normalize the name (lowercase, remove extra spaces)
                    String normalizedName = entry.mainRSN.toLowerCase().trim();
                    rankMap.put(normalizedName, entry.osrsName);
                }
            }

            log.info("Fetched {} pending rank changes from API", rankMap.size());
            plugin.updateRankData(rankMap);

        }
        catch (Exception e)
        {
            log.error("Failed to fetch rank data from API", e);
        }
    }

    // Inner class to represent the API response structure
    private static class RankEntry
    {
        String mainRSN;   // The player's RSN
        String osrsName;  // The target rank (e.g., "Recruit", "Corporal", etc.)
    }
}
