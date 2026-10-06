package com.netapps.filetransfer.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility for SHA-256 digest computations, streaming file verification,
 * and salted password hashing.
 */
public final class HashUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Private constructor to prevent instantiation.
     */
    private HashUtil() {
    }

    /**
     * Creates a new SHA-256 MessageDigest instance.
     *
     * @return MessageDigest configured for SHA-256
     */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in current JVM", e);
        }
    }

    /**
     * Converts a raw byte array to a lowercase hexadecimal string.
     *
     * @param bytes byte array
     * @return hex representation
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Generates a random cryptographic salt encoded in hexadecimal.
     *
     * @param numBytes byte count of entropy
     * @return hex salt string
     */
    public static String generateSalt(int numBytes) {
        byte[] salt = new byte[numBytes];
        RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * Computes a salted SHA-256 hash of a password using the specified salt.
     * Formula: SHA-256(salt + password)
     *
     * @param password raw user password
     * @param salt     hexadecimal salt string
     * @return hexadecimal SHA-256 hash
     */
    public static String hashPassword(String password, String salt) {
        MessageDigest md = newDigest();
        if (salt != null) {
            md.update(salt.getBytes(StandardCharsets.UTF_8));
        }
        if (password != null) {
            md.update(password.getBytes(StandardCharsets.UTF_8));
        }
        return bytesToHex(md.digest());
    }

    /**
     * Computes the SHA-256 checksum of an entire file using 8 KB streaming chunks.
     *
     * @param file target file on disk
     * @return lowercase hex SHA-256 string
     * @throws IOException on read failure
     */
    public static String computeFileHash(File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IOException("File does not exist or is not a regular file: " + file.getPath());
        }

        MessageDigest md = newDigest();
        try (InputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[ProtocolConstants.BUFFER_SIZE];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }
        return bytesToHex(md.digest());
    }

    /**
     * CLI utility to generate a formatted user entry for users.properties.
     * Usage: java com.netapps.filetransfer.common.HashUtil <username> <password>
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java com.netapps.filetransfer.common.HashUtil <username> <password>");
            System.out.println("Generates a salted SHA-256 user entry suitable for users.properties.");
            return;
        }

        String username = args[0].trim();
        String password = args[1].trim();

        String salt = generateSalt(16);
        String hash = hashPassword(password, salt);

        System.out.println("# Entry for users.properties:");
        System.out.println(username + "=" + salt + ":" + hash);
    }
}
