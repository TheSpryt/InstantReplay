package com.instantreplay;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.awt.AWTSequenceEncoder;

/**
 * Encodes a list of buffered JPEG frames into an H.264 MP4 file using JCodec.
 * JCodec is pure Java, so no native libraries or external processes are needed.
 */
@Slf4j
class ClipEncoder
{
	/**
	 * Decode the buffered frames and write them to {@code out} as an MP4 at the
	 * given framerate. Runs on a background thread; may take a moment for long
	 * or high-resolution clips.
	 */
	static void encode(File out, List<RecordedFrame> frames, int fps) throws IOException
	{
		if (frames.isEmpty())
		{
			throw new IOException("No frames to encode");
		}

		AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(out, Math.max(1, fps));
		try
		{
			for (RecordedFrame frame : frames)
			{
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.jpeg));
				if (image == null)
				{
					continue;
				}
				encoder.encodeImage(image);
			}
		}
		finally
		{
			encoder.finish();
		}
	}
}
