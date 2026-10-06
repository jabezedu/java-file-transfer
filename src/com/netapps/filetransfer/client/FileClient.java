package com.netapps.filetransfer.client;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Client network layer communicating with the FileServer over a binary TCP stream.
 * Supports authentication, file directory queries, resumable streaming uploads/downloads,
 * and SHA-256 integrity checking.
 */
public class FileClient {

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean connected = false;
    private String currentUsername;

    /**
     * Constructs an unconnected FileClient instance.
     */
    public FileClient() {
    }

    /**
     * Establishes a TCP connection and authenticates with the server.
     *
     * @param host     server IP address or hostname
     * @param port     server TCP port
     * @param username client username
     * @param password client password
     * @throws IOException on connection or authentication failure
     */
    public synchronized void connect(String host, int port, String username, String password) throws IOException {
        if (connected) {
            disconnect();
        }

        socket = new Socket(host, port);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        // Send magic header and AUTH command
        out.writeInt(ProtocolConstants.MAGIC);
        out.writeInt(Command.AUTH.getCode());
        out.writeUTF(username);
        out.writeUTF(password);
        out.flush();

        // Read authentication response
        int status = in.readInt();
        String message = in.readUTF();

        if (status != ProtocolConstants.STATUS_OK) {
            disconnect();
            throw new IOException("Server rejected login: " + message);
        }

        this.connected = true;
        this.currentUsername = username;
    }

    /**
     * Checks if the client is currently connected.
     *
     * @return true if connected
     */
    public synchronized boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    /**
     * Retrieves the username authenticated for this session.
     *
     * @return username string
     */
    public String getCurrentUsername() {
        return currentUsername;
    }

    /**
     * Queries the server for the list of files in the user's remote storage.
     *
     * @return list of FileInfo entries
     * @throws IOException on communication failure
     */
    public synchronized List<FileInfo> listFiles() throws IOException {
        ensureConnected();
        out.writeInt(Command.LIST.getCode());
        out.flush();

        int status = in.readInt();
        if (status != ProtocolConstants.STATUS_OK) {
            throw new IOException("Server error during LIST operation (status " + status + ")");
        }

        int count = in.readInt();
        List<FileInfo> files = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = in.readUTF();
            long size = in.readLong();
            long lastMod = in.readLong();
            files.add(new FileInfo(name, size, lastMod));
        }
        return files;
    }

    /**
     * Retrieves metadata and SHA-256 checksum for a remote file.
     *
     * @param filename remote file name
     * @return FileInfo instance with SHA-256
     * @throws IOException if file does not exist or transfer fails
     */
    public synchronized FileInfo getFileInfo(String filename) throws IOException {
        ensureConnected();
        out.writeInt(Command.INFO.getCode());
        out.writeUTF(filename);
        out.flush();

        int status = in.readInt();
        if (status == ProtocolConstants.STATUS_NOT_FOUND) {
            String msg = in.readUTF();
            throw new IOException("File not found: " + msg);
        }
        if (status != ProtocolConstants.STATUS_OK) {
            String msg = in.readUTF();
            throw new IOException("Server error during INFO: " + msg);
        }

        String name = in.readUTF();
        long size = in.readLong();
        long lastMod = in.readLong();
        String hash = in.readUTF();
        return new FileInfo(name, size, lastMod, hash);
    }

    /**
     * Requests deletion of a remote file.
     *
     * @param filename remote file name
     * @throws IOException on deletion rejection or failure
     */
    public synchronized void deleteFile(String filename) throws IOException {
        ensureConnected();
        out.writeInt(Command.DELETE.getCode());
        out.writeUTF(filename);
        out.flush();

        int status = in.readInt();
        String message = in.readUTF();
        if (status != ProtocolConstants.STATUS_OK) {
            throw new IOException(message);
        }
    }

    /**
     * Uploads a local file to the server using 8 KB streaming chunks and resume support.
     *
     * @param localFile local file to send
     * @param listener  callback listener for progress updates
     * @param task      active transfer task tracking cancellation
     * @throws IOException on transfer error
     */
    public synchronized void uploadFile(File localFile, TransferListener listener, TransferTask task) throws IOException {
        ensureConnected();
        if (!localFile.exists() || !localFile.isFile()) {
            throw new IOException("Local file does not exist: " + localFile.getPath());
        }

        long totalFileSize = localFile.length();
        String filename = localFile.getName();

        // 1. Send UPLOAD command
        out.writeInt(Command.UPLOAD.getCode());
        out.writeUTF(filename);
        out.writeLong(totalFileSize);
        out.flush();

        // 2. Read server response (status and resume offset)
        int status = in.readInt();
        if (status != ProtocolConstants.STATUS_OK) {
            String errMsg = in.readUTF();
            if (listener != null) {
                listener.onError(filename, errMsg);
            }
            throw new IOException("Upload rejected by server: " + errMsg);
        }

        long resumeOffset = in.readLong();
        long bytesToSend = totalFileSize - resumeOffset;
        byte[] buffer = new byte[ProtocolConstants.BUFFER_SIZE];

        // 3. Stream data from resumeOffset
        try (FileInputStream fis = new FileInputStream(localFile)) {
            if (resumeOffset > 0) {
                long skipped = fis.skip(resumeOffset);
                if (skipped != resumeOffset) {
                    throw new IOException("Failed to skip to resume offset: " + resumeOffset);
                }
            }

            long sent = 0;
            while (sent < bytesToSend) {
                if (task != null && task.isCancelled()) {
                    if (listener != null) {
                        listener.onCancelled(filename);
                    }
                    disconnect();
                    return;
                }

                int toRead = (int) Math.min(buffer.length, bytesToSend - sent);
                int read = fis.read(buffer, 0, toRead);
                if (read == -1) {
                    throw new IOException("Unexpected end of local file during upload");
                }

                out.write(buffer, 0, read);
                sent += read;
                long totalTransferred = resumeOffset + sent;

                if (task != null) {
                    task.setTransferredBytes(totalTransferred);
                    double speed = task.computeSpeedMBs(sent);
                    long eta = task.computeEtaSeconds(totalTransferred, speed);
                    if (listener != null) {
                        listener.onProgress(totalTransferred, totalFileSize, speed, eta);
                    }
                }
            }
            out.flush();
        }

        // 4. Send expected SHA-256 for full file
        String localHash = HashUtil.computeFileHash(localFile);
        out.writeUTF(localHash);
        out.flush();

        // 5. Read server verification result
        int verifyStatus = in.readInt();
        String verifyMsg = in.readUTF();

        if (verifyStatus == ProtocolConstants.STATUS_OK) {
            if (listener != null) {
                listener.onCompleted(filename, verifyMsg);
            }
        } else {
            if (listener != null) {
                listener.onError(filename, verifyMsg);
            }
            throw new IOException("Upload integrity check failed: " + verifyMsg);
        }
    }

    /**
     * Downloads a remote file to a local destination directory with resume support.
     *
     * @param remoteFilename remote file name
     * @param localDestDir   destination folder
     * @param listener       callback listener for progress updates
     * @param task           active transfer task tracking cancellation
     * @throws IOException on transfer error
     */
    public synchronized void downloadFile(String remoteFilename, File localDestDir, TransferListener listener, TransferTask task) throws IOException {
        ensureConnected();
        if (!localDestDir.exists()) {
            localDestDir.mkdirs();
        }

        File targetFile = new File(localDestDir, remoteFilename);
        File partFile = new File(localDestDir, remoteFilename + ProtocolConstants.PART_EXTENSION);

        // Determine resume offset from local .part file if present
        long existingOffset = 0;
        if (partFile.exists()) {
            existingOffset = partFile.length();
        }

        // 1. Send DOWNLOAD command
        out.writeInt(Command.DOWNLOAD.getCode());
        out.writeUTF(remoteFilename);
        out.writeLong(existingOffset);
        out.flush();

        // 2. Read response
        int status = in.readInt();
        if (status != ProtocolConstants.STATUS_OK) {
            String errMsg = in.readUTF();
            if (listener != null) {
                listener.onError(remoteFilename, errMsg);
            }
            throw new IOException("Download rejected by server: " + errMsg);
        }

        long totalFileSize = in.readLong();
        long serverResumeOffset = in.readLong();
        String expectedHash = in.readUTF();

        // If server adjusted resume offset, ensure local partial matches
        if (serverResumeOffset == 0 && partFile.exists()) {
            partFile.delete();
            existingOffset = 0;
        } else {
            existingOffset = serverResumeOffset;
        }

        long bytesToReceive = totalFileSize - existingOffset;
        byte[] buffer = new byte[ProtocolConstants.BUFFER_SIZE];

        // 3. Receive streaming chunks in append mode
        try (FileOutputStream fos = new FileOutputStream(partFile, true)) {
            long received = 0;
            while (received < bytesToReceive) {
                if (task != null && task.isCancelled()) {
                    if (listener != null) {
                        listener.onCancelled(remoteFilename);
                    }
                    out.writeInt(ProtocolConstants.STATUS_INTERNAL_ERROR);
                    out.flush();
                    return;
                }

                int toRead = (int) Math.min(buffer.length, bytesToReceive - received);
                int read = in.read(buffer, 0, toRead);
                if (read == -1) {
                    throw new IOException("Premature EOF during download after " + received + " bytes");
                }

                fos.write(buffer, 0, read);
                received += read;
                long totalTransferred = existingOffset + received;

                if (task != null) {
                    task.setTransferredBytes(totalTransferred);
                    double speed = task.computeSpeedMBs(received);
                    long eta = task.computeEtaSeconds(totalTransferred, speed);
                    if (listener != null) {
                        listener.onProgress(totalTransferred, totalFileSize, speed, eta);
                    }
                }
            }
            fos.flush();
        }

        // 4. Verify completed file integrity via SHA-256
        String actualHash = HashUtil.computeFileHash(partFile);
        if (actualHash.equalsIgnoreCase(expectedHash)) {
            // Success: inform server and rename .part to final
            out.writeInt(ProtocolConstants.STATUS_OK);
            out.flush();

            Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            if (listener != null) {
                listener.onCompleted(remoteFilename, "Verified SHA-256: " + actualHash);
            }
        } else {
            // Mismatch: notify server, purge corrupt partial file
            out.writeInt(ProtocolConstants.STATUS_INTEGRITY_MISMATCH);
            out.flush();

            partFile.delete();
            String mismatchMsg = "SHA-256 mismatch: expected " + expectedHash + " but computed " + actualHash;
            if (listener != null) {
                listener.onError(remoteFilename, mismatchMsg);
            }
            throw new IOException(mismatchMsg);
        }
    }

    /**
     * Terminates the connection to the server gracefully.
     */
    public synchronized void disconnect() {
        if (!connected) {
            return;
        }
        connected = false;

        try {
            if (out != null) {
                out.writeInt(Command.QUIT.getCode());
                out.flush();
            }
        } catch (Exception ignored) {
        }

        StreamUtil.closeQuietly(in);
        StreamUtil.closeQuietly(out);
        StreamUtil.closeQuietly(socket);
    }

    /**
     * Throws an IOException if client is not currently connected.
     *
     * @throws IOException if disconnected
     */
    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Client is not connected to a server");
        }
    }
}
