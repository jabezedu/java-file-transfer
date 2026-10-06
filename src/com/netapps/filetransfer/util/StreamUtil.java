package com.netapps.filetransfer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility functions for safe resource closure and stream chunk manipulation.
 */
public final class StreamUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private StreamUtil() {
    }

    /**
     * Closes an AutoCloseable resource silently ignoring null values and IOExceptions.
     *
     * @param closeable resource to close
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Copies an exact number of bytes from an input stream to an output stream.
     *
     * @param in              source stream
     * @param out             destination stream
     * @param buffer          pre-allocated byte buffer
     * @param bytesToTransfer exact byte count to read and write
     * @throws IOException on read or write failures
     */
    public static void copyExact(InputStream in, OutputStream out, byte[] buffer, long bytesToTransfer) throws IOException {
        long remaining = bytesToTransfer;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, toRead);
            if (read == -1) {
                throw new IOException("Premature end of stream, expected " + remaining + " more bytes");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
        out.flush();
    }
}
