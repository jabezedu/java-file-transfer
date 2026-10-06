package com.netapps.filetransfer.server;

import com.netapps.filetransfer.common.Command;
import com.netapps.filetransfer.common.FileInfo;
import com.netapps.filetransfer.common.HashUtil;
import com.netapps.filetransfer.common.ProtocolConstants;
import com.netapps.filetransfer.util.StreamUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Handles a single client connection session: authentication, command dispatch,
 * resumable streaming transfers, and SHA-256 integrity validation.
 */
public class ClientSession implements Runnable {

    private final Socket socket;
    private final AuthService authService;
    private final StorageManager storageManager;

    private DataInputStream in;
    private DataOutputStream out;
    private String authenticatedUser;
    private volatile boolean running = true;

    /**
     * Constructs a ClientSession for an accepted socket.
     *
     * @param socket         accepted TCP client socket
     * @param authService    authentication provider
     * @param storageManager per-user storage manager
     */
    public ClientSession(Socket socket, AuthService authService, StorageManager storageManager) {
        this.socket = socket;
        this.authService = authService;
        this.storageManager = storageManager;
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            if (!handleHandshakeAndAuth()) {
                return;
            }

            // Command processing loop
            while (running) {
                int cmdCode;
                try {
                    cmdCode = in.readInt();
                } catch (IOException e) {
                    // Client disconnected
                    break;
                }

                Command cmd = Command.fromCode(cmdCode);
                if (cmd == null) {
                    sendError(ProtocolConstants.STATUS_BAD_REQUEST, "Unknown command code: " + cmdCode);
                    continue;
                }

                switch (cmd) {
                    case LIST:
                        handleList();
                        break;
                    case INFO:
                        handleInfo();
                        break;
                    case DELETE:
                        handleDelete();
                        break;
                    case UPLOAD:
                        handleUpload();
                        break;
                    case DOWNLOAD:
                        handleDownload();
                        break;
                    case QUIT:
                        handleQuit();
                        return;
                    default:
                        sendError(ProtocolConstants.STATUS_BAD_REQUEST, "Unsupported command: " + cmd);
                        break;
                }
            }

        } catch (IOException e) {
            TransferLogger.info("Session closed for " + (authenticatedUser != null ? authenticatedUser : socket.getRemoteSocketAddress()) + ": " + e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Performs magic check and user credentials authentication.
     *
     * @return true if authenticated successfully
     * @throws IOException on socket error
     */
    private boolean handleHandshakeAndAuth() throws IOException {
        int magic = in.readInt();
        if (magic != ProtocolConstants.MAGIC) {
            TransferLogger.warn("Invalid protocol magic from " + socket.getRemoteSocketAddress());
            out.writeInt(ProtocolConstants.STATUS_BAD_REQUEST);
            out.writeUTF("Invalid protocol magic");
            out.flush();
            return false;
        }

        int authCmdCode = in.readInt();
        if (authCmdCode != Command.AUTH.getCode()) {
            TransferLogger.warn("Expected AUTH command from " + socket.getRemoteSocketAddress());
            out.writeInt(ProtocolConstants.STATUS_BAD_REQUEST);
            out.writeUTF("Expected AUTH command");
            out.flush();
            return false;
        }

        String username = in.readUTF();
        String password = in.readUTF();

        boolean authOk = authService.authenticate(username, password);
        if (!authOk) {
            TransferLogger.warn("Authentication failed for user: " + username + " from " + socket.getRemoteSocketAddress());
            out.writeInt(ProtocolConstants.STATUS_AUTH_FAILED);
            out.writeUTF("Authentication failed: invalid username or password");
            out.flush();
            return false;
        }

        this.authenticatedUser = username.trim().toLowerCase();
        TransferLogger.info("User '" + authenticatedUser + "' logged in successfully from " + socket.getRemoteSocketAddress());

        out.writeInt(ProtocolConstants.STATUS_OK);
        out.writeUTF("Authentication successful. Welcome " + authenticatedUser);
        out.flush();
        return true;
    }

    /**
     * Lists files in the authenticated user's isolated storage folder.
     *
     * @throws IOException on socket or filesystem error
     */
    private void handleList() throws IOException {
        List<FileInfo> files = storageManager.listFiles(authenticatedUser);
        out.writeInt(ProtocolConstants.STATUS_OK);
        out.writeInt(files.size());
        for (FileInfo f : files) {
            out.writeUTF(f.getName());
            out.writeLong(f.getSize());
            out.writeLong(f.getLastModified());
        }
        out.flush();
    }

    /**
     * Retrieves file metadata and SHA-256 hash.
     *
     * @throws IOException on socket or filesystem error
     */
    private void handleInfo() throws IOException {
        String filename = in.readUTF();
        try {
            FileInfo info = storageManager.getFileInfo(authenticatedUser, filename);
            if (info == null) {
                out.writeInt(ProtocolConstants.STATUS_NOT_FOUND);
                out.writeUTF("File not found: " + filename);
            } else {
                out.writeInt(ProtocolConstants.STATUS_OK);
                out.writeUTF(info.getName());
                out.writeLong(info.getSize());
                out.writeLong(info.getLastModified());
                out.writeUTF(info.getSha256());
            }
        } catch (IllegalArgumentException e) {
            out.writeInt(ProtocolConstants.STATUS_BAD_REQUEST);
            out.writeUTF("Invalid filename: " + e.getMessage());
        }
        out.flush();
    }

    /**
     * Deletes a file from the user's isolated folder.
     *
     * @throws IOException on socket or filesystem error
     */
    private void handleDelete() throws IOException {
        String filename = in.readUTF();
        try {
            boolean deleted = storageManager.deleteFile(authenticatedUser, filename);
            if (deleted) {
                out.writeInt(ProtocolConstants.STATUS_OK);
                out.writeUTF("File deleted successfully: " + filename);
                TransferLogger.info("User '" + authenticatedUser + "' deleted file: " + filename);
            } else {
                out.writeInt(ProtocolConstants.STATUS_NOT_FOUND);
                out.writeUTF("File not found or cannot be deleted: " + filename);
            }
        } catch (IllegalArgumentException e) {
            out.writeInt(ProtocolConstants.STATUS_BAD_REQUEST);
            out.writeUTF("Invalid filename: " + e.getMessage());
        }
        out.flush();
    }

    /**
     * Handles resumable, buffered upload from client to server storage.
     *
     * @throws IOException on transfer or socket error
     */
    private void handleUpload() throws IOException {
        String filename = in.readUTF();
        long totalFileSize = in.readLong();

        // 1. Validate filename
        File targetFile;
        File partFile;
        try {
            targetFile = storageManager.resolveUserFile(authenticatedUser, filename);
            partFile = storageManager.resolvePartFile(authenticatedUser, filename);
        } catch (IllegalArgumentException | IOException e) {
            sendError(ProtocolConstants.STATUS_BAD_REQUEST, "Invalid file name: " + e.getMessage());
            return;
        }

        // 2. Validate maximum file size
        if (totalFileSize > ProtocolConstants.MAX_FILE_SIZE) {
            sendError(ProtocolConstants.STATUS_FILE_TOO_LARGE, "File size (" + totalFileSize + " bytes) exceeds maximum allowed limit (" + ProtocolConstants.MAX_FILE_SIZE + " bytes)");
            return;
        }

        // 3. Check server free disk space
        if (!storageManager.hasSufficientFreeSpace(totalFileSize)) {
            sendError(ProtocolConstants.STATUS_DISK_FULL, "Insufficient storage space on server");
            return;
        }

        // 4. Resume check: see how many bytes already exist in .part file
        long existingOffset = 0;
        if (partFile.exists()) {
            existingOffset = partFile.length();
            if (existingOffset > totalFileSize) {
                // Inconsistent partial file, reset to start
                partFile.delete();
                existingOffset = 0;
            }
        }

        // Send ready acknowledgement and resume offset
        out.writeInt(ProtocolConstants.STATUS_OK);
        out.writeLong(existingOffset);
        out.flush();

        // 5. Stream incoming chunks
        long bytesToReceive = totalFileSize - existingOffset;
        byte[] buffer = new byte[ProtocolConstants.BUFFER_SIZE];

        try (FileOutputStream fos = new FileOutputStream(partFile, true)) {
            long received = 0;
            while (received < bytesToReceive) {
                int toRead = (int) Math.min(buffer.length, bytesToReceive - received);
                int read = in.read(buffer, 0, toRead);
                if (read == -1) {
                    throw new IOException("Unexpected EOF during upload after " + received + " bytes");
                }
                fos.write(buffer, 0, read);
                received += read;
            }
            fos.flush();
        }

        // 6. Read client calculated expected SHA-256 for the entire file
        String expectedHash = in.readUTF();

        // 7. Calculate actual SHA-256 of completed .part file on disk
        String actualHash = HashUtil.computeFileHash(partFile);

        if (actualHash.equalsIgnoreCase(expectedHash)) {
            // Hash matches! Rename .part to final target file (replacing any previous version)
            Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            out.writeInt(ProtocolConstants.STATUS_OK);
            out.writeUTF("Upload successful. Integrity verified: " + actualHash);
            out.flush();
            TransferLogger.info("Upload completed for user '" + authenticatedUser + "': " + filename + " (" + totalFileSize + " bytes, SHA-256: " + actualHash + ")");
        } else {
            // Mismatch: remove corrupted partial file
            partFile.delete();
            out.writeInt(ProtocolConstants.STATUS_INTEGRITY_MISMATCH);
            out.writeUTF("Integrity mismatch: expected " + expectedHash + " but computed " + actualHash);
            out.flush();
            TransferLogger.error("Upload integrity mismatch for user '" + authenticatedUser + "': " + filename + ". Deleted corrupted file.");
        }
    }

    /**
     * Handles resumable, buffered download from server storage to client.
     *
     * @throws IOException on transfer or socket error
     */
    private void handleDownload() throws IOException {
        String filename = in.readUTF();
        long clientOffset = in.readLong();

        File targetFile;
        try {
            targetFile = storageManager.resolveUserFile(authenticatedUser, filename);
        } catch (IllegalArgumentException | IOException e) {
            sendError(ProtocolConstants.STATUS_BAD_REQUEST, "Invalid file name: " + e.getMessage());
            return;
        }

        if (!targetFile.exists() || !targetFile.isFile()) {
            out.writeInt(ProtocolConstants.STATUS_NOT_FOUND);
            out.writeUTF("File not found on server: " + filename);
            out.flush();
            return;
        }

        long totalFileSize = targetFile.length();
        if (clientOffset < 0 || clientOffset > totalFileSize) {
            clientOffset = 0; // Reset invalid offset
        }

        // Compute full file SHA-256
        String fullFileHash = HashUtil.computeFileHash(targetFile);

        out.writeInt(ProtocolConstants.STATUS_OK);
        out.writeLong(totalFileSize);
        out.writeLong(clientOffset);
        out.writeUTF(fullFileHash);
        out.flush();

        // Stream bytes from clientOffset to end
        long bytesToSend = totalFileSize - clientOffset;
        byte[] buffer = new byte[ProtocolConstants.BUFFER_SIZE];

        try (FileInputStream fis = new FileInputStream(targetFile)) {
            if (clientOffset > 0) {
                long skipped = fis.skip(clientOffset);
                if (skipped != clientOffset) {
                    throw new IOException("Failed to skip to resume offset " + clientOffset);
                }
            }

            long sent = 0;
            while (sent < bytesToSend) {
                int toRead = (int) Math.min(buffer.length, bytesToSend - sent);
                int read = fis.read(buffer, 0, toRead);
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                sent += read;
            }
            out.flush();
        }

        // Wait for client verification ACK
        int clientAckStatus = in.readInt();
        if (clientAckStatus == ProtocolConstants.STATUS_OK) {
            TransferLogger.info("Download completed for user '" + authenticatedUser + "': " + filename + " (" + totalFileSize + " bytes)");
        } else {
            TransferLogger.warn("Client reported download failure/cancellation (status " + clientAckStatus + ") for file: " + filename);
        }
    }

    /**
     * Handles voluntary client disconnect command.
     *
     * @throws IOException on socket error
     */
    private void handleQuit() throws IOException {
        out.writeInt(ProtocolConstants.STATUS_OK);
        out.writeUTF("Session closed");
        out.flush();
        running = false;
        TransferLogger.info("User '" + authenticatedUser + "' logged out");
    }

    /**
     * Transmits an error status code and explanatory message to the client.
     *
     * @param status error status code
     * @param msg    explanatory error message
     * @throws IOException on transmission failure
     */
    private void sendError(int status, String msg) throws IOException {
        out.writeInt(status);
        out.writeUTF(msg);
        out.flush();
    }

    /**
     * Terminates socket streams and connection.
     */
    public void close() {
        running = false;
        StreamUtil.closeQuietly(in);
        StreamUtil.closeQuietly(out);
        StreamUtil.closeQuietly(socket);
    }
}
