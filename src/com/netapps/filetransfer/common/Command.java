package com.netapps.filetransfer.common;

/**
 * Enumeration of binary protocol operations supported between client and server.
 */
public enum Command {

    /**
     * Authenticate session using username and password.
     */
    AUTH(1),

    /**
     * List files stored in user's isolated storage directory.
     */
    LIST(2),

    /**
     * Upload a file with streaming and resume support.
     */
    UPLOAD(3),

    /**
     * Download a file with streaming and resume support.
     */
    DOWNLOAD(4),

    /**
     * Delete a stored file from the user's directory.
     */
    DELETE(5),

    /**
     * Retrieve file metadata and full SHA-256 checksum.
     */
    INFO(6),

    /**
     * Disconnect and close the session.
     */
    QUIT(7);

    private final int code;

    Command(int code) {
        this.code = code;
    }

    /**
     * Returns the integer opcode transmitted over the wire.
     *
     * @return opcode value
     */
    public int getCode() {
        return code;
    }

    /**
     * Resolves a Command enum from its opcode.
     *
     * @param code opcode received
     * @return matching Command or null if unrecognized
     */
    public static Command fromCode(int code) {
        for (Command c : values()) {
            if (c.code == code) {
                return c;
            }
        }
        return null;
    }
}
