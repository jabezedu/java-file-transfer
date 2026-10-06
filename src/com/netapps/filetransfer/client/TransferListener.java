package com.netapps.filetransfer.client;

/**
 * Callback listener interface for observing file upload and download progress,
 * real-time throughput, ETA calculations, and terminal status events.
 */
public interface TransferListener {

    /**
     * Fired periodically as chunks are transmitted or received.
     *
     * @param transferredBytes total bytes transferred thus far
     * @param totalBytes       total file size in bytes
     * @param speedMBs         instantaneous or rolling transfer rate in MB/s
     * @param etaSeconds       estimated time remaining in seconds
     */
    void onProgress(long transferredBytes, long totalBytes, double speedMBs, long etaSeconds);

    /**
     * Fired when a transfer successfully concludes and checksum is verified.
     *
     * @param filename name of the transferred file
     * @param message  success details
     */
    void onCompleted(String filename, String message);

    /**
     * Fired when a transfer fails due to an I/O, integrity, or server error.
     *
     * @param filename target file name
     * @param error    failure description
     */
    void onError(String filename, String error);

    /**
     * Fired when an in-progress transfer is cancelled by user request.
     *
     * @param filename name of cancelled file
     */
    void onCancelled(String filename);
}
