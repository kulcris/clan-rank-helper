package com.sanity.clanrankhelper;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.runelite.client.events.ConfigChanged;

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

    @Getter
    private final Map<String, String> pendingRankChanges = new ConcurrentHashMap<>();
    
    // Set of player names (lowercase) who have been confirmed to need a rank change
    @Getter
    private final Set<String> confirmedNeedsChange = ConcurrentHashMap.newKeySet();
    
    // Set of player names (lowercase) who have been confirmed to NOT need a change
    @Getter
    private final Set<String> confirmedOk = ConcurrentHashMap.newKeySet();

    private ScheduledExecutorService executor;
    private RankDataFetcher rankDataFetcher;
    
    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("sanityclanrankhelper"))
        {
            return;
        }
        
        if (event.getKey().equals("resetData") && config.resetData())
        {
            log.info("Resetting confirmed data...");
            confirmedNeedsChange.clear();
            confirmedOk.clear();
        }
    }

    @Override
    protected void startUp() throws Exception
    {
        log.info("Sanity Clan Rank Helper started!");
        overlayManager.add(overlay);
        overlayManager.add(clanChatOverlay);
        
        rankDataFetcher = new RankDataFetcher(this);
        executor = Executors.newSingleThreadScheduledExecutor();
        
        // Fetch immediately on startup
        executor.submit(rankDataFetcher::fetchRankData);
        
        // Then refresh periodically based on config
        scheduleRefresh();
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Sanity Clan Rank Helper stopped!");
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
            rankDataFetcher::fetchRankData,
            refreshMinutes,
            refreshMinutes,
            TimeUnit.MINUTES
        );
    }

    public void updateRankData(Map<String, String> newData)
    {
        pendingRankChanges.clear();
        pendingRankChanges.putAll(newData);
        confirmedNeedsChange.clear();
        confirmedOk.clear();
        log.info("Updated rank data: {} pending changes", pendingRankChanges.size());
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
    
    public void markDoesNotNeedChange(String playerName)
    {
        // Deprecated - use markConfirmedOk instead
        markConfirmedOk(playerName);
    }

    public String getApiUrl()
    {
        return config.apiUrl();
    }

    @Provides
    ClanRankHelperConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ClanRankHelperConfig.class);
    }
}
