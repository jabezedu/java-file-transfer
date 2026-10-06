package com.netapps.filetransfer.client;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates the runtime state, progress tracking, and cancellation handle
 * for an active background upload or download transfer.
 */
public class TransferTask {

    private final String filename;
    private final long totalBytes;
    private final boolean upload;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile long transferredBytes = 0;
    private volatile long startTimeMillis = 0;

    /**
     * Constructs a TransferTask instance.
     *
     * @param filename   name of file
     * @param totalBytes expected total size in bytes
     * @param upload     true if upload, false if download
     */
    public TransferTask(String filename, long totalBytes, boolean upload) {
        this.filename = filename;
        this.totalBytes = totalBytes;
        this.upload = upload;
        this.startTimeMillis = System.currentTimeMillis();
    }

    /**
     * Flags the transfer to cancel at the next streaming chunk iteration.
     */
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Checks if cancellation has been requested.
     *
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Gets the name of the file being transferred.
     *
     * @return file name
     */
    public String getFilename() {
        return filename;
    }

    /**
     * Gets total size in bytes.
     *
     * @return file size
     */
    public long getTotalBytes() {
        return totalBytes;
    }

    /**
     * Checks if transfer is an upload.
     *
     * @return true if upload, false if download
     */
    public boolean isUpload() {
        return upload;
    }

    /**
     * Gets current transferred bytes.
     *
     * @return bytes transferred
     */
    public long getTransferredBytes() {
        return transferredBytes;
    }

    /**
     * Updates transferred byte count.
     *
     * @param bytes new transferred byte total
     */
    public void setTransferredBytes(long bytes) {
        this.transferredBytes = bytes;
    }

    /**
     * Calculates current throughput rate in MB/s.
     *
     * @param currentBytes bytes transferred so far
     * @return speed in MB/s
     */
    public double computeSpeedMBs(long currentBytes) {
        long elapsedMillis = Math.max(1, System.currentTimeMillis() - startTimeMillis);
        double seconds = elapsedMillis / 1000.0;
        double mb = currentBytes / (1024.0 * 1024.0);
        return mb / seconds;
    }

    /**
     * Calculates estimated remaining time in seconds based on current rate.
     *
     * @param currentBytes bytes transferred so far
     * @param speedMBs     current transfer speed in MB/s
     * @return ETA in seconds or -1 if indeterminate
     */
    public long computeEtaSeconds(long currentBytes, double speedMBs) {
        if (speedMBs <= 0.001 || totalBytes <= currentBytes) {
            return 0;
        }
        double remainingBytes = totalBytes - currentBytes;
        double remainingMB = remainingBytes / (1024.0 * 1024.0);
        return (long) (remainingMB / speedMBs);
    }
}
