package com.netapps.filetransfer.server;

import com.netapps.filetransfer.common.ProtocolConstants;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main TCP File Server accepting authenticated connections and dispatching
 * binary protocol operations using an ExecutorService thread pool.
 */
public class FileServer {

    private final int port;
    private final AuthService authService;
    private final StorageManager storageManager;
    private final ExecutorService sessionPool = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private volatile boolean running = true;

    /**
     * Constructs a FileServer instance.
     *
     * @param port TCP listening port
     */
    public FileServer(int port) {
        this.port = port;
        this.authService = new AuthService();
        this.storageManager = new StorageManager(new File("storage"));
    }

    /**
     * Starts listening for client socket connections.
     */
    public void start() {
        TransferLogger.info("Starting FileServer on port " + port + "...");

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "FileServer-ShutdownHook"));

        try {
            serverSocket = new ServerSocket(port);
            TransferLogger.info("FileServer actively listening on port " + port);

            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    TransferLogger.info("Incoming connection accepted from " + socket.getRemoteSocketAddress());
                    ClientSession session = new ClientSession(socket, authService, storageManager);
                    sessionPool.execute(session);
                } catch (IOException e) {
                    if (running) {
                        TransferLogger.error("Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            TransferLogger.error("Failed to bind ServerSocket on port " + port + ": " + e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Stops the server gracefully, closing sockets and shutting down the thread pool.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        TransferLogger.info("Shutting down FileServer...");

        sessionPool.shutdown();
        try {
            if (!sessionPool.awaitTermination(3, TimeUnit.SECONDS)) {
                sessionPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            sessionPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        TransferLogger.info("FileServer stopped.");
        TransferLogger.close();
    }

    /**
     * Entry point for running the file transfer server from command line.
     *
     * @param args optional command line arguments (port number)
     */
    public static void main(String[] args) {
        int port = ProtocolConstants.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0] + ". Using default: " + ProtocolConstants.DEFAULT_PORT);
            }
        }

        FileServer server = new FileServer(port);
        server.start();
    }
}
