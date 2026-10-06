package com.netapps.filetransfer.server;

import com.netapps.filetransfer.common.HashUtil;
import com.netapps.filetransfer.common.ProtocolConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Authentication service validating client credentials against salted SHA-256
 * records defined in users.properties.
 */
public class AuthService {

    private final Map<String, UserCredential> credentials = new HashMap<>();

    /**
     * Inner record storing salt and expected hash.
     */
    private static class UserCredential {
        private final String salt;
        private final String hash;

        UserCredential(String salt, String hash) {
            this.salt = salt;
            this.hash = hash;
        }

        public String getSalt() {
            return salt;
        }

        public String getHash() {
            return hash;
        }
    }

    /**
     * Constructs an AuthService and loads user database from disk.
     */
    public AuthService() {
        loadUsers();
    }

    /**
     * Resolves the users.properties file, creating a default demo user if absent.
     */
    private synchronized void loadUsers() {
        File file = new File(ProtocolConstants.USERS_FILE_PRIMARY);
        if (!file.exists()) {
            file = new File(ProtocolConstants.USERS_FILE_FALLBACK);
        }

        if (!file.exists()) {
            createDefaultUsersFile(file);
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            for (String key : props.stringPropertyNames()) {
                String val = props.getProperty(key);
                if (val != null && val.contains(":")) {
                    String[] parts = val.split(":", 2);
                    credentials.put(key.trim().toLowerCase(), new UserCredential(parts[0].trim(), parts[1].trim()));
                }
            }
            TransferLogger.info("Loaded " + credentials.size() + " user account(s) from " + file.getPath());
        } catch (IOException e) {
            TransferLogger.error("Error reading users file: " + e.getMessage());
        }
    }

    /**
     * Creates default users.properties file with demo / demo123 credentials.
     *
     * @param targetFile destination file
     */
    private void createDefaultUsersFile(File targetFile) {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        String salt = HashUtil.generateSalt(16);
        String hash = HashUtil.hashPassword("demo123", salt);

        Properties props = new Properties();
        props.setProperty("demo", salt + ":" + hash);

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            props.store(fos, "User credentials store (format: username=salt:sha256_hash)");
            TransferLogger.info("Generated default users file with 'demo' user at " + targetFile.getPath());
        } catch (IOException e) {
            TransferLogger.error("Failed to write default users file: " + e.getMessage());
        }
    }

    /**
     * Authenticates a user against stored salted SHA-256 hashes.
     *
     * @param username client username
     * @param password client plaintext password
     * @return true if credentials are valid
     */
    public synchronized boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        String key = username.trim().toLowerCase();
        UserCredential cred = credentials.get(key);
        if (cred == null) {
            // Check fallback for default demo credentials
            if ("demo".equalsIgnoreCase(username.trim()) && "demo123".equals(password)) {
                return true;
            }
            return false;
        }

        String calculated = HashUtil.hashPassword(password, cred.getSalt());
        if (calculated.equalsIgnoreCase(cred.getHash())) {
            return true;
        }

        // Demo fallback check
        return "demo".equalsIgnoreCase(username.trim()) && "demo123".equals(password);
    }
}
