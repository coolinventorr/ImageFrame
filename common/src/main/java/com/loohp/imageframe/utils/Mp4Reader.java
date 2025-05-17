package com.loohp.imageframe.utils;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Mp4Reader {

    /**
     * Decode an MP4 input stream into a list of image frames.
     * <p>
     * Frame delays are approximated at 50ms per frame when timing information is
     * unavailable.
     */
    public static List<GifReader.ImageFrame> readMp4(InputStream stream, long sizeLimit) throws IOException {
        ByteArrayOutputStream buffer = new SizeLimitedByteArrayOutputStream(sizeLimit);
        try {
            int nRead;
            byte[] data = new byte[4096];
            while ((nRead = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        } finally {
            stream.close();
        }

        byte[] array = buffer.toByteArray();
        FrameGrab grab;
        try {
            grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(new ByteArrayInputStream(array)));
        } catch (Exception e) {
            throw new IOException("Unable to read MP4", e);
        }

        List<GifReader.ImageFrame> frames = new ArrayList<>();
        Picture picture;
        try {
            while ((picture = grab.getNativeFrame()) != null) {
                BufferedImage img = AWTUtil.toBufferedImage(picture);
                frames.add(new GifReader.ImageFrame(img, 50));
            }
        } catch (Exception ignore) {
            // End of stream or decoding error
        }
        return frames;
    }

    /**
     * Extract audio from an MP4 input stream as OGG Vorbis bytes.
     * <p>
     * This implementation currently returns an empty byte array.
     * TODO: implement actual audio extraction.
     */
    public static byte[] extractAudioOgg(InputStream stream, long sizeLimit) {
        return new byte[0];
    }
}
