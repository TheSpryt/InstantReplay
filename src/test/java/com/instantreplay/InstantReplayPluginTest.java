package com.instantreplay;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher: runs a RuneLite client with this plugin side-loaded.
 * Use {@code ./gradlew run}.
 */
public class InstantReplayPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(InstantReplayPlugin.class);
		RuneLite.main(args);
	}
}
