package com.netapps.filetransfer.server;

import com.netapps.filetransfer.common.ProtocolConstants;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe audit logger writing transfer events, logins, and errors
 * to standard console and ./logs/transfers.log.
 */
public final class TransferLogger {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static PrintWriter fileWriter;

    static {
        try {
            File logFile = new File(ProtocolConstants.LOG_FILE_PATH);
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            fileWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)), true);
        } catch (IOException e) {
            System.err.println("[TransferLogger] Warning: Could not initialize log file: " + e.getMessage());
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private TransferLogger() {
    }

    /**
     * Logs an informational message.
     *
     * @param message text to record
     */
    public static void info(String message) {
        log("INFO", message);
    }

    /**
     * Logs a warning message.
     *
     * @param message text to record
     */
    public static void warn(String message) {
        log("WARN", message);
    }

    /**
     * Logs an error message.
     *
     * @param message text to record
     */
    public static void error(String message) {
        log("ERROR", message);
    }

    /**
     * Synchronously appends the log entry to console and file.
     *
     * @param level   severity level string
     * @param message body message
     */
    private static void log(String level, String message) {
        String timestamp = TIME_FORMATTER.format(Instant.now());
        String entry = String.format("[%s] [%s] %s", timestamp, level, message);

        synchronized (LOCK) {
            System.out.println(entry);
            if (fileWriter != null) {
                fileWriter.println(entry);
            }
        }
    }

    /**
     * Flushes and terminates the file output stream.
     */
    public static void close() {
        synchronized (LOCK) {
            if (fileWriter != null) {
                fileWriter.flush();
                fileWriter.close();
                fileWriter = null;
            }
        }
    }
}
