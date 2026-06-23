package com.instantreplay;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(InstantReplayConfig.GROUP)
public interface InstantReplayConfig extends Config
{
	String GROUP = "instantreplay";

	@ConfigSection(
		name = "Recording",
		description = "Clip length, framerate and quality settings",
		position = 0
	)
	String recordingSection = "recording";

	@ConfigSection(
		name = "Triggers",
		description = "Which in-game events automatically save a clip",
		position = 1
	)
	String triggersSection = "triggers";

	@ConfigSection(
		name = "Output",
		description = "Where clips are saved and how you are notified",
		position = 2
	)
	String outputSection = "output";

	// ------------------------------------------------------------------
	// Recording
	// ------------------------------------------------------------------

	@Range(min = 4, max = 120)
	@ConfigItem(
		keyName = "clipLength",
		name = "Clip length",
		description = "Total length of a saved clip, including the seconds before and after the event.",
		section = recordingSection,
		position = 0
	)
	@Units(Units.SECONDS)
	default int clipLength()
	{
		return 15;
	}

	@Range(min = 0, max = 30)
	@ConfigItem(
		keyName = "postRoll",
		name = "Post-event padding",
		description = "How many seconds to keep recording after the event fires. The rest of the clip is the lead-up to the event.",
		section = recordingSection,
		position = 1
	)
	@Units(Units.SECONDS)
	default int postRoll()
	{
		return 2;
	}

	@Range(min = 5, max = 60)
	@ConfigItem(
		keyName = "framerate",
		name = "Framerate",
		description = "Frames per second to capture and encode. Higher values look smoother but use more memory and CPU.",
		section = recordingSection,
		position = 2
	)
	@Units("fps")
	default int framerate()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "resolution",
		name = "Resolution",
		description = "Vertical resolution of saved clips. The client is downscaled to this height and never upscaled.",
		section = recordingSection,
		position = 3
	)
	default ResolutionMode resolution()
	{
		return ResolutionMode.P720;
	}

	@Range(min = 10, max = 100)
	@ConfigItem(
		keyName = "quality",
		name = "JPEG buffer quality",
		description = "Quality of the in-memory frame buffer (10-100). Lower values reduce memory use at the cost of clip quality.",
		section = recordingSection,
		position = 4
	)
	default int quality()
	{
		return 85;
	}

	@ConfigItem(
		keyName = "drawCursor",
		name = "Draw cursor",
		description = "Draw a marker at the mouse position in saved clips. The operating system cursor is not part of captured frames, so this is rendered by the plugin.",
		section = recordingSection,
		position = 5
	)
	default boolean drawCursor()
	{
		return false;
	}

	// ------------------------------------------------------------------
	// Triggers
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "manualHotkey",
		name = "Manual save hotkey",
		description = "Press to instantly save a clip of the last few seconds, like a ShadowPlay manual capture.",
		section = triggersSection,
		position = 0
	)
	default Keybind manualHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "onDeath",
		name = "On death",
		description = "Save a clip when your character dies.",
		section = triggersSection,
		position = 1
	)
	default boolean onDeath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onCollectionLog",
		name = "On collection log unlock",
		description = "Save a clip when a new item is added to your collection log. Requires the in-game collection log notification to be enabled.",
		section = triggersSection,
		position = 2
	)
	default boolean onCollectionLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onLevelUp",
		name = "On level up",
		description = "Save a clip when you reach a new level in any skill.",
		section = triggersSection,
		position = 3
	)
	default boolean onLevelUp()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onValuableDrop",
		name = "On valuable drop",
		description = "Save a clip when you receive loot worth more than the value threshold below.",
		section = triggersSection,
		position = 4
	)
	default boolean onValuableDrop()
	{
		return false;
	}

	@Range(min = 1)
	@ConfigItem(
		keyName = "valuableDropThreshold",
		name = "Valuable drop value",
		description = "Minimum total Grand Exchange value of a drop (in gp) needed to save a clip.",
		section = triggersSection,
		position = 5
	)
	default int valuableDropThreshold()
	{
		return 1_000_000;
	}

	@ConfigItem(
		keyName = "onPet",
		name = "On pet drop",
		description = "Save a clip when you receive a pet.",
		section = triggersSection,
		position = 6
	)
	default boolean onPet()
	{
		return true;
	}

	@ConfigItem(
		keyName = "onQuestComplete",
		name = "On quest completion",
		description = "Save a clip when you complete a quest.",
		section = triggersSection,
		position = 7
	)
	default boolean onQuestComplete()
	{
		return false;
	}

	@ConfigItem(
		keyName = "onCombatAchievement",
		name = "On combat task",
		description = "Save a clip when you complete a combat achievement task.",
		section = triggersSection,
		position = 8
	)
	default boolean onCombatAchievement()
	{
		return false;
	}

	// ------------------------------------------------------------------
	// Output
	// ------------------------------------------------------------------

	@ConfigItem(
		keyName = "outputDirectory",
		name = "Save folder",
		description = "Folder to save clips to. Leave blank to use the default RuneLite 'instant-replay' folder.",
		section = outputSection,
		position = 0
	)
	default String outputDirectory()
	{
		return "";
	}

	@ConfigItem(
		keyName = "notify",
		name = "Chat message on save",
		description = "Print a game chat message when a clip has finished saving.",
		section = outputSection,
		position = 1
	)
	default boolean notify()
	{
		return true;
	}
}
