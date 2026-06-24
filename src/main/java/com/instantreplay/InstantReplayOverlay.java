package com.instantreplay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Compact on-screen indicator showing whether Instant Replay is armed, actively
 * recording a triggered clip, or has just finished saving one (a brief flash).
 */
class InstantReplayOverlay extends OverlayPanel
{
	private static final long SAVE_FLASH_MS = 2500;

	private static final Color ARMED_COLOR = new Color(160, 160, 160);
	private static final Color RECORDING_COLOR = new Color(220, 40, 40);
	private static final Color SAVED_COLOR = new Color(60, 200, 90);

	private final InstantReplayConfig config;
	private final BooleanSupplier armed;
	private final BooleanSupplier recording;
	private final LongSupplier lastSavedAt;

	InstantReplayOverlay(InstantReplayConfig config, BooleanSupplier armed, BooleanSupplier recording,
		LongSupplier lastSavedAt)
	{
		this.config = config;
		this.armed = armed;
		this.recording = recording;
		this.lastSavedAt = lastSavedAt;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStatusOverlay() || !armed.getAsBoolean())
		{
			return null;
		}

		long sinceSave = System.currentTimeMillis() - lastSavedAt.getAsLong();
		boolean flashing = sinceSave >= 0 && sinceSave < SAVE_FLASH_MS;

		final Color color;
		final String status;
		if (flashing)
		{
			color = SAVED_COLOR;
			status = "Saved";
		}
		else if (recording.getAsBoolean())
		{
			color = RECORDING_COLOR;
			status = "Recording";
		}
		else
		{
			color = ARMED_COLOR;
			status = "Armed";
		}

		panelComponent.getChildren().clear();
		panelComponent.setPreferredSize(new Dimension(125, 0));
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Instant Replay")
			.color(color)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("●")
			.leftColor(color)
			.right(status)
			.rightColor(color)
			.build());

		return super.render(graphics);
	}
}
