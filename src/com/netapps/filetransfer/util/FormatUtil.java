package com.netapps.filetransfer.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utility formatting byte sizes, speeds, dates, and remaining ETA times for display.
 */
public final class FormatUtil {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Private constructor to prevent instantiation.
     */
    private FormatUtil() {
    }

    /**
     * Formats a byte quantity into a human readable string (B, KB, MB, GB).
     *
     * @param bytes byte count
     * @return formatted size string
     */
    public static String formatByteSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Formats transfer speed in megabytes per second (MB/s).
     *
     * @param mbPerSec speed in MB/s
     * @return formatted speed string
     */
    public static String formatSpeed(double mbPerSec) {
        if (mbPerSec < 0.01) {
            return "0.00 MB/s";
        }
        return String.format(Locale.US, "%.2f MB/s", mbPerSec);
    }

    /**
     * Formats remaining transfer time in mm:ss or hh:mm:ss.
     *
     * @param seconds total remaining seconds
     * @return formatted ETA string
     */
    public static String formatEta(long seconds) {
        if (seconds < 0) {
            return "--:--";
        }
        if (seconds < 60) {
            return String.format("%02ds", seconds);
        }
        long mins = seconds / 60;
        long remSecs = seconds % 60;
        if (mins < 60) {
            return String.format("%02d:%02d", mins, remSecs);
        }
        long hours = mins / 60;
        long remMins = mins % 60;
        return String.format("%02d:%02d:%02d", hours, remMins, remSecs);
    }

    /**
     * Formats an epoch millisecond timestamp into a standard date string.
     *
     * @param epochMillis millisecond timestamp
     * @return formatted date string
     */
    public static String formatDate(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }
}
