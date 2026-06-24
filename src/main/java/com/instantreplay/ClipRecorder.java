package com.instantreplay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.DrawManager;

/**
 * Maintains a rolling buffer of recent frames and, when triggered, encodes a
 * clip spanning the lead-up to the trigger plus a configurable post-event tail.
 *
 * <p>All buffer state is confined to a single "processor" thread so no locking
 * is required: frame delivery, triggers and post-roll timing are all funnelled
 * through it as tasks. Capture sampling is driven by a scheduler, and the final
 * MP4 encode runs on its own thread so it never stalls capture.
 */
@Slf4j
class ClipRecorder
{
	private static final long COOLDOWN_MS = 1500;
	private static final long EVICT_SLACK_MS = 750;

	private final InstantReplayConfig config;
	private final DrawManager drawManager;
	private final BooleanSupplier canCapture;
	private final Supplier<Point> mousePosition;
	private final Consumer<File> onSaved;
	private final Consumer<String> onError;

	private ScheduledExecutorService scheduler;
	private ThreadPoolExecutor processor;
	private ThreadPoolExecutor encoder;

	// Mirrors the processor-thread `capturing` flag for cross-thread reads (overlay).
	private volatile boolean recording;

	// --- processor-thread-confined state ---
	private final Deque<RecordedFrame> buffer = new ArrayDeque<>();
	private boolean capturing;
	private long postRollEndMs;
	private List<RecordedFrame> activeClip;
	private String activeReason;
	private long lastClipMs;

	ClipRecorder(InstantReplayConfig config, DrawManager drawManager, BooleanSupplier canCapture,
		Supplier<Point> mousePosition, Consumer<File> onSaved, Consumer<String> onError)
	{
		this.config = config;
		this.drawManager = drawManager;
		this.canCapture = canCapture;
		this.mousePosition = mousePosition;
		this.onSaved = onSaved;
		this.onError = onError;
	}

	void start()
	{
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> namedDaemon(r, "instant-replay-capture"));
		processor = singleThread("instant-replay-processor");
		encoder = singleThread("instant-replay-encoder");

		long periodMs = Math.max(1, 1000L / Math.max(1, config.framerate()));
		scheduler.scheduleAtFixedRate(this::captureTick, 0, periodMs, TimeUnit.MILLISECONDS);
	}

	void stop()
	{
		shutdown(scheduler);
		shutdown(processor);
		shutdown(encoder);
		scheduler = null;
		processor = null;
		encoder = null;
		buffer.clear();
		capturing = false;
		recording = false;
		activeClip = null;
	}

	/** Whether a triggered clip is currently being captured (including its post-roll tail). */
	boolean isRecording()
	{
		return recording;
	}

	/** Request a clip; safe to call from any thread (e.g. the client thread). */
	void trigger(String reason)
	{
		final ThreadPoolExecutor p = processor;
		if (p != null && !p.isShutdown())
		{
			p.execute(() -> beginClip(reason));
		}
	}

	// ------------------------------------------------------------------
	// Capture pipeline
	// ------------------------------------------------------------------

	private void captureTick()
	{
		try
		{
			if (canCapture.getAsBoolean())
			{
				drawManager.requestNextFrameListener(this::onFrameImage);
			}
			// Finalise on time even if frames stop arriving (e.g. on logout).
			final ThreadPoolExecutor p = processor;
			if (p != null && !p.isShutdown())
			{
				p.execute(this::checkPostRollTimeout);
			}
		}
		catch (Exception e)
		{
			log.debug("capture tick failed", e);
		}
	}

	private void onFrameImage(Image image)
	{
		final ThreadPoolExecutor p = processor;
		if (p == null || p.isShutdown())
		{
			return;
		}
		// Drop frames if the processor is falling behind rather than pile up memory.
		if (p.getQueue().size() > 4)
		{
			return;
		}
		final long now = System.currentTimeMillis();
		// Read the mouse position on the render thread; the OS cursor is not part
		// of the captured frame, so we draw our own marker at this point.
		final Point mouse = config.drawCursor() ? mousePosition.get() : null;
		p.execute(() -> processFrame(image, now, mouse));
	}

	private void processFrame(Image image, long now, Point mouse)
	{
		try
		{
			BufferedImage scaled = scale(image, mouse);
			byte[] jpeg = toJpeg(scaled);
			RecordedFrame frame = new RecordedFrame(now, jpeg);

			buffer.addLast(frame);
			evictOld(now);

			if (capturing)
			{
				activeClip.add(frame);
				if (now >= postRollEndMs)
				{
					finishClip();
				}
			}
		}
		catch (Exception e)
		{
			log.debug("frame processing failed", e);
		}
	}

	private void checkPostRollTimeout()
	{
		if (capturing && System.currentTimeMillis() >= postRollEndMs)
		{
			finishClip();
		}
	}

	private void evictOld(long now)
	{
		long preRollMs = preRollMs() + EVICT_SLACK_MS;
		while (!buffer.isEmpty() && now - buffer.peekFirst().timestampMs > preRollMs)
		{
			buffer.removeFirst();
		}
	}

	// ------------------------------------------------------------------
	// Clip lifecycle (processor thread)
	// ------------------------------------------------------------------

	private void beginClip(String reason)
	{
		long now = System.currentTimeMillis();
		if (capturing || now - lastClipMs < COOLDOWN_MS)
		{
			return;
		}
		activeClip = new ArrayList<>(buffer);
		activeReason = reason;
		capturing = true;
		recording = true;
		postRollEndMs = now + postRollMs();
		if (postRollMs() == 0)
		{
			finishClip();
		}
	}

	private void finishClip()
	{
		if (!capturing)
		{
			return;
		}
		capturing = false;
		recording = false;
		lastClipMs = System.currentTimeMillis();

		final List<RecordedFrame> clip = activeClip;
		final String reason = activeReason;
		final int fps = config.framerate();
		activeClip = null;
		activeReason = null;

		final ThreadPoolExecutor e = encoder;
		if (e != null && !e.isShutdown() && clip != null && !clip.isEmpty())
		{
			e.execute(() -> encodeAndSave(clip, reason, fps));
		}
	}

	private void encodeAndSave(List<RecordedFrame> frames, String reason, int fps)
	{
		try
		{
			File dir = outputDir();
			//noinspection ResultOfMethodCallIgnored
			dir.mkdirs();
			String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date())
				+ "_" + sanitise(reason) + ".mp4";
			File out = new File(dir, name);

			ClipEncoder.encode(out, frames, fps);
			onSaved.accept(out);
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("Failed to save Instant Replay clip", ex);
			onError.accept(ex.getMessage());
		}
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private long preRollMs()
	{
		int total = Math.max(4, config.clipLength());
		int post = Math.min(config.postRoll(), total - 1);
		return Math.max(1, total - post) * 1000L;
	}

	private long postRollMs()
	{
		int total = Math.max(4, config.clipLength());
		int post = Math.min(Math.max(0, config.postRoll()), total - 1);
		return post * 1000L;
	}

	private File outputDir()
	{
		String configured = config.outputDirectory();
		if (configured != null && !configured.trim().isEmpty())
		{
			return new File(configured.trim());
		}
		return new File(RuneLite.RUNELITE_DIR, "instant-replay");
	}

	private BufferedImage scale(Image image, Point mouse)
	{
		int sw = image.getWidth(null);
		int sh = image.getHeight(null);
		if (sw <= 0 || sh <= 0)
		{
			throw new IllegalStateException("frame not ready");
		}

		int targetH = config.resolution().getHeight();
		if (targetH <= 0 || targetH >= sh)
		{
			targetH = sh; // never upscale
		}
		int targetW = Math.round((float) sw * targetH / sh);

		// H.264 requires even dimensions.
		targetW = Math.max(2, targetW - (targetW % 2));
		targetH = Math.max(2, targetH - (targetH % 2));

		BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(image, 0, 0, targetW, targetH, null);

		if (mouse != null && mouse.x >= 0 && mouse.y >= 0)
		{
			drawCursor(g, Math.round((float) mouse.x * targetW / sw), Math.round((float) mouse.y * targetH / sh));
		}

		g.dispose();
		return dst;
	}

	/** Draws a simple arrow pointer with the tip at (x, y). */
	private static void drawCursor(Graphics2D g, int x, int y)
	{
		int[] xs = {x, x, x + 4, x + 7, x + 9, x + 6, x + 11};
		int[] ys = {y, y + 16, y + 12, y + 18, y + 17, y + 11, y + 11};
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.WHITE);
		g.fillPolygon(xs, ys, xs.length);
		g.setColor(Color.BLACK);
		g.drawPolygon(xs, ys, xs.length);
	}

	private byte[] toJpeg(BufferedImage image) throws IOException
	{
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		param.setCompressionQuality(Math.max(10, Math.min(100, config.quality())) / 100f);

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos))
		{
			writer.setOutput(ios);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally
		{
			writer.dispose();
		}
		return baos.toByteArray();
	}

	private static String sanitise(String reason)
	{
		if (reason == null)
		{
			return "clip";
		}
		return reason.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private static ThreadPoolExecutor singleThread(String name)
	{
		return (ThreadPoolExecutor) Executors.newFixedThreadPool(1, r -> namedDaemon(r, name));
	}

	private static Thread namedDaemon(Runnable r, String name)
	{
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		return t;
	}

	private static void shutdown(java.util.concurrent.ExecutorService service)
	{
		if (service != null)
		{
			service.shutdownNow();
		}
	}
}
