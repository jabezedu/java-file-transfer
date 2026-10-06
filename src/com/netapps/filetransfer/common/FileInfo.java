package com.netapps.filetransfer.common;

/**
 * Immutable metadata representing a stored or remote file.
 */
public final class FileInfo {

    private final String name;
    private final long size;
    private final long lastModified;
    private final String sha256;

    /**
     * Constructs a FileInfo instance without a precomputed hash.
     *
     * @param name         file name
     * @param size         file size in bytes
     * @param lastModified last modification epoch milliseconds
     */
    public FileInfo(String name, long size, long lastModified) {
        this(name, size, lastModified, "");
    }

    /**
     * Constructs a FileInfo instance with a precomputed hash.
     *
     * @param name         file name
     * @param size         file size in bytes
     * @param lastModified last modification epoch milliseconds
     * @param sha256       hexadecimal SHA-256 hash or empty string
     */
    public FileInfo(String name, long size, long lastModified, String sha256) {
        this.name = name != null ? name : "";
        this.size = size >= 0 ? size : 0;
        this.lastModified = lastModified >= 0 ? lastModified : 0;
        this.sha256 = sha256 != null ? sha256 : "";
    }

    /**
     * Gets the file name.
     *
     * @return file name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the file size in bytes.
     *
     * @return size in bytes
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets the last modified epoch milliseconds.
     *
     * @return epoch milliseconds
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Gets the SHA-256 hash string.
     *
     * @return hex SHA-256 string or empty
     */
    public String getSha256() {
        return sha256;
    }

    @Override
    public String toString() {
        return "FileInfo{name='" + name + "', size=" + size + ", lastModified=" + lastModified + ", sha256='" + sha256 + "'}";
    }
}
