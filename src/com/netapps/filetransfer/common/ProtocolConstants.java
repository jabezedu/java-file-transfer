package com.netapps.filetransfer.common;

/**
 * Protocol constants, magic bytes, buffer limits, and status codes for the file transfer system.
 */
public final class ProtocolConstants {

    /**
     * Default TCP listening port for file transfer server.
     */
    public static final int DEFAULT_PORT = 6000;

    /**
     * Streaming chunk buffer size (8 KB). Whole files are never loaded into memory.
     */
    public static final int BUFFER_SIZE = 8192;

    /**
     * Protocol magic preamble integer header ("NFTX").
     */
    public static final int MAGIC = 0x4E465458;

    /**
     * Maximum allowed file size for a single upload (500 MB default).
     */
    public static final long MAX_FILE_SIZE = 500L * 1024L * 1024L;

    /**
     * Minimum free disk space safety headroom (10 MB).
     */
    public static final long MIN_DISK_HEADROOM = 10L * 1024L * 1024L;

    /**
     * Extension appended to in-progress resumable partial files.
     */
    public static final String PART_EXTENSION = ".part";

    /**
     * Server transfer log output destination.
     */
    public static final String LOG_FILE_PATH = "logs/transfers.log";

    /**
     * Primary user credentials property file path.
     */
    public static final String USERS_FILE_PRIMARY = "server/users.properties";

    /**
     * Fallback user credentials property file path.
     */
    public static final String USERS_FILE_FALLBACK = "users.properties";

    // --- Status Codes ---

    /**
     * Operation completed successfully.
     */
    public static final int STATUS_OK = 200;

    /**
     * Resumed or partial content acknowledged.
     */
    public static final int STATUS_PARTIAL = 206;

    /**
     * Invalid request syntax, illegal filename traversal, or malformed parameters.
     */
    public static final int STATUS_BAD_REQUEST = 400;

    /**
     * Authentication failed (bad credentials or user unknown).
     */
    public static final int STATUS_AUTH_FAILED = 401;

    /**
     * Target resource or file not found on server.
     */
    public static final int STATUS_NOT_FOUND = 404;

    /**
     * File size exceeds maximum allowed upload threshold.
     */
    public static final int STATUS_FILE_TOO_LARGE = 413;

    /**
     * SHA-256 checksum verification failed (data corruption).
     */
    public static final int STATUS_INTEGRITY_MISMATCH = 422;

    /**
     * Internal server I/O or processing error.
     */
    public static final int STATUS_INTERNAL_ERROR = 500;

    /**
     * Server drive does not have sufficient free disk space.
     */
    public static final int STATUS_DISK_FULL = 507;

    /**
     * Private constructor to prevent instantiation.
     */
    private ProtocolConstants() {
    }
}
