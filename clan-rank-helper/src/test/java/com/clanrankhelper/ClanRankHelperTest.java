package com.clanrankhelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ClanRankHelperTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(ClanRankHelperPlugin.class);
        RuneLite.main(args);
    }
}
