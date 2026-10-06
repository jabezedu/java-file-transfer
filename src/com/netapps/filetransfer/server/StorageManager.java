package com.netapps.filetransfer.server;

import com.netapps.filetransfer.common.FileInfo;
import com.netapps.filetransfer.common.HashUtil;
import com.netapps.filetransfer.common.ProtocolConstants;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages per-user storage isolation, path traversal sanitization,
 * free disk space verification, and file system operations.
 */
public class StorageManager {

    private final File baseStorageDir;

    /**
     * Constructs a StorageManager using the default storage root directory.
     */
    public StorageManager() {
        this(new File("storage"));
    }

    /**
     * Constructs a StorageManager with a custom storage root directory.
     *
     * @param baseDir root directory
     */
    public StorageManager(File baseDir) {
        this.baseStorageDir = baseDir;
        if (!this.baseStorageDir.exists()) {
            this.baseStorageDir.mkdirs();
        }
    }

    /**
     * Resolves and creates the user-isolated storage directory.
     *
     * @param username client username
     * @return File referencing user directory
     * @throws IOException if directory cannot be verified
     */
    public File getUserDirectory(String username) throws IOException {
        String safeUser = sanitizeFilename(username);
        File userDir = new File(baseStorageDir, safeUser);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        return userDir.getCanonicalFile();
    }

    /**
     * Sanitizes a file name or path segment preventing path traversal attacks.
     *
     * @param name proposed filename
     * @return safe base filename
     * @throws IllegalArgumentException if filename contains illegal characters or traversal sequences
     */
    public static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        String clean = name.trim();

        // Strip path separators if provided
        int lastSlash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1).trim();
        }

        if (clean.isEmpty() || clean.equals(".") || clean.equals("..") || clean.contains("..") || clean.contains("/") || clean.contains("\\")) {
            throw new IllegalArgumentException("Illegal filename or traversal detected: " + name);
        }

        return clean;
    }

    /**
     * Resolves a file safely inside a user directory.
     *
     * @param username client username
     * @param filename file name
     * @return canonical File reference
     * @throws IOException on path traversal attempt or I/O failure
     */
    public File resolveUserFile(String username, String filename) throws IOException {
        String safeName = sanitizeFilename(filename);
        File userDir = getUserDirectory(username);
        File targetFile = new File(userDir, safeName).getCanonicalFile();

        // Enforce path traversal containment
        if (!targetFile.getPath().startsWith(userDir.getPath())) {
            throw new IOException("Security violation: path traversal detected for file " + filename);
        }
        return targetFile;
    }

    /**
     * Resolves the .part partial download or upload file safely.
     *
     * @param username client username
     * @param filename base filename
     * @return canonical File for partial transfer
     * @throws IOException on traversal or I/O failure
     */
    public File resolvePartFile(String username, String filename) throws IOException {
        String safeName = sanitizeFilename(filename) + ProtocolConstants.PART_EXTENSION;
        return resolveUserFile(username, safeName);
    }

    /**
     * Lists all complete files in the user's isolated storage directory.
     *
     * @param username client username
     * @return list of FileInfo entries
     * @throws IOException on directory read error
     */
    public List<FileInfo> listFiles(String username) throws IOException {
        File userDir = getUserDirectory(username);
        List<FileInfo> result = new ArrayList<>();

        File[] files = userDir.listFiles();
        if (files != null) {
            for (File f : files) {
                // Ignore directories and temporary .part files
                if (f.isFile() && !f.getName().endsWith(ProtocolConstants.PART_EXTENSION)) {
                    result.add(new FileInfo(f.getName(), f.length(), f.lastModified()));
                }
            }
        }
        return result;
    }

    /**
     * Retrieves full file info including calculated SHA-256 hash.
     *
     * @param username client username
     * @param filename target file name
     * @return FileInfo with hash, or null if file does not exist
     * @throws IOException on file error
     */
    public FileInfo getFileInfo(String username, String filename) throws IOException {
        File file = resolveUserFile(username, filename);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        String hash = HashUtil.computeFileHash(file);
        return new FileInfo(file.getName(), file.length(), file.lastModified(), hash);
    }

    /**
     * Deletes a file from the user's storage directory.
     *
     * @param username client username
     * @param filename file name to remove
     * @return true if deleted successfully, false otherwise
     * @throws IOException on path traversal
     */
    public boolean deleteFile(String username, String filename) throws IOException {
        File file = resolveUserFile(username, filename);
        if (file.exists() && file.isFile()) {
            return file.delete();
        }
        return false;
    }

    /**
     * Verifies that sufficient storage capacity remains on the host file system.
     *
     * @param requiredBytes bytes needed for incoming transfer
     * @return true if disk has adequate space plus safety headroom
     */
    public boolean hasSufficientFreeSpace(long requiredBytes) {
        long usable = baseStorageDir.getUsableSpace();
        return usable >= (requiredBytes + ProtocolConstants.MIN_DISK_HEADROOM);
    }
}
