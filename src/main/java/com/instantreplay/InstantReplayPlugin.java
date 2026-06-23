package com.instantreplay;

import com.google.inject.Provides;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.LootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Instant Replay",
	description = "Automatically saves a video clip of the moments around in-game events like deaths and collection log unlocks",
	tags = {"record", "recording", "video", "clip", "replay", "death", "collection", "capture", "highlight"}
)
public class InstantReplayPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private InstantReplayConfig config;

	private ClipRecorder recorder;
	private final Map<Skill, Integer> levels = new EnumMap<>(Skill.class);

	private final HotkeyListener manualHotkey = new HotkeyListener(() -> config.manualHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (recorder != null)
			{
				recorder.trigger("manual");
			}
		}
	};

	@Provides
	InstantReplayConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InstantReplayConfig.class);
	}

	@Override
	protected void startUp()
	{
		recorder = new ClipRecorder(config, drawManager, this::canCapture, this::onClipSaved, this::onClipError);
		recorder.start();
		keyManager.registerKeyListener(manualHotkey);
		clientThread.invokeLater(this::snapshotLevels);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(manualHotkey);
		if (recorder != null)
		{
			recorder.stop();
			recorder = null;
		}
		levels.clear();
	}

	private boolean canCapture()
	{
		return client.getGameState() == GameState.LOGGED_IN;
	}

	// ------------------------------------------------------------------
	// Triggers
	// ------------------------------------------------------------------

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (config.onDeath() && event.getActor() == client.getLocalPlayer() && recorder != null)
		{
			recorder.trigger("death");
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == Skill.OVERALL)
		{
			return;
		}

		int level = event.getLevel();
		Integer previous = levels.put(skill, level);
		if (config.onLevelUp() && previous != null && level > previous && recorder != null)
		{
			recorder.trigger("level-" + skill.getName().toLowerCase());
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!config.onValuableDrop() || recorder == null)
		{
			return;
		}

		long value = 0;
		for (ItemStack stack : event.getItems())
		{
			value += (long) itemManager.getItemPrice(stack.getId()) * stack.getQuantity();
		}

		if (value >= config.valuableDropThreshold())
		{
			recorder.trigger("loot");
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (recorder == null)
		{
			return;
		}

		switch (event.getType())
		{
			case GAMEMESSAGE:
			case SPAM:
			case MESBOX:
				break;
			default:
				return;
		}

		String message = event.getMessage().toLowerCase();

		if (config.onCollectionLog() && message.contains("added to your collection log"))
		{
			recorder.trigger("collection-log");
		}
		else if (config.onPet()
			&& (message.contains("funny feeling like you") || message.contains("weird sneaking into your backpack")))
		{
			recorder.trigger("pet");
		}
		else if (config.onQuestComplete() && message.contains("you've completed a quest"))
		{
			recorder.trigger("quest");
		}
		else if (config.onCombatAchievement() && message.contains("combat task"))
		{
			recorder.trigger("combat-task");
		}
	}

	// ------------------------------------------------------------------
	// Lifecycle plumbing
	// ------------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::snapshotLevels);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// The framerate sets the capture scheduler's period, so restart on change.
		if (InstantReplayConfig.GROUP.equals(event.getGroup())
			&& "framerate".equals(event.getKey())
			&& recorder != null)
		{
			recorder.stop();
			recorder.start();
		}
	}

	private void snapshotLevels()
	{
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				levels.put(skill, client.getRealSkillLevel(skill));
			}
		}
	}

	private void onClipSaved(File file)
	{
		log.info("Instant Replay saved clip to {}", file);
		if (config.notify())
		{
			clientThread.invokeLater(() -> client.addChatMessage(
				net.runelite.api.ChatMessageType.GAMEMESSAGE,
				"",
				"Instant Replay saved: " + file.getName(),
				null));
		}
	}

	private void onClipError(String message)
	{
		log.warn("Instant Replay failed to save a clip: {}", message);
	}
}
