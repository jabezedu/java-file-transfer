# Java Resumable File Transfer System
A High-Performance Binary TCP File Transfer Service with Resumption and SHA-256 Integrity in Java 17

## Overview

Java Resumable File Transfer System is a standalone client-server file transmission system built using pure Java 17. It implements a binary protocol over TCP using `DataInputStream` and `DataOutputStream`, featuring salted SHA-256 authentication, per-user isolated storage vaults, path-traversal immunization, streaming 8 KB chunking without memory saturation, partial transfer checkpointing (`.part` files), and end-to-end checksum verification.

## Features

- Binary TCP Protocol: Compact, big-endian binary framing with magic header identification (`0x4E465458`).
- Salted SHA-256 Authentication: User verification against salted SHA-256 hashes loaded from `server/users.properties`.
- Per-User Storage Isolation: Dedicated storage sandbox for each user (`./storage/<username>/`).
- Hardened Path Traversal Security: Canonical path enforcement blocking illegal separators, directory traversals, and escaping.
- Resumable Streaming Transfers: Uploads and downloads utilize temporary `.part` files; interrupted transfers resume precisely from the last confirmed byte offset.
- End-to-End Cryptographic Integrity: Full-file SHA-256 digests are computed while streaming and validated upon completion; corrupt transfers are purged automatically.
- Memory-Safe 8 KB Chunking: Constant memory overhead regardless of file magnitude.
- Real-Time Throughput & ETA Calculation: Live calculation of MB/s transmission rates and remaining time.
- Asynchronous Non-Blocking Swing GUI: Background worker threads prevent GUI freezing during network transmission.
- Audit Logging: Continuous logging to console and `./logs/transfers.log`.

## Architecture

```
+-------------------------------------------------------------+
|                      Swing Client GUI                       |
|  [MainWindow] -> [LoginPanel] / [FileTable] / [TransferPanel]|
|        |                                                    |
|  [Background Worker Threads]                                |
|        v                                                    |
|  [FileClient] <--> Local Disk Cache (./received/ & .part)   |
+--------|----------------------------------------------------+
         | TCP Socket (Binary DataStream Protocol)
+--------v----------------------------------------------------+
|                         File Server                         |
|  [FileServer] -> ServerSocket accept loop                   |
|        |                                                    |
|  [ExecutorService Thread Pool]                              |
|        |                                                    |
|  [ClientSession] <--> [AuthService]     (users.properties)  |
|        |         <--> [StorageManager]  (./storage/<user>/) |
|        v                                                    |
|  [HashUtil]       (SHA-256 Checksums)                       |
|  [TransferLogger] (Console + ./logs/transfers.log)          |
+-------------------------------------------------------------+
```

### Package Breakdown

- `com.netapps.filetransfer.common`: Binary commands, file metadata models, protocol constants, and SHA-256 hashing utilities.
- `com.netapps.filetransfer.server`: Socket listener, multi-threaded client sessions, authentication provider, storage isolation manager, and audit logger.
- `com.netapps.filetransfer.client`: Client networking engine, progress callback listener, and transfer task state controller.
- `com.netapps.filetransfer.ui`: Swing graphical frontend, login panel, remote file table model, transfer progress dashboard, and cancellation handles.
- `com.netapps.filetransfer.util`: Byte unit formatting, transfer speed calculators, and stream I/O utilities.

## Folder Structure

```
java-file-transfer/
├── .gitignore
├── README.md
├── docs/
│   ├── PROTOCOL.md
│   └── TESTING.md
├── scripts/
│   ├── build.bat
│   ├── build.sh
│   ├── run-client.bat
│   ├── run-client.sh
│   ├── run-server.bat
│   └── run-server.sh
├── server/
│   └── users.properties
└── src/
    └── com/
        └── netapps/
            └── filetransfer/
                ├── common/
                │   ├── Command.java
                │   ├── FileInfo.java
                │   ├── HashUtil.java
                │   └── ProtocolConstants.java
                ├── server/
                │   ├── AuthService.java
                │   ├── ClientSession.java
                │   ├── FileServer.java
                │   ├── StorageManager.java
                │   └── TransferLogger.java
                ├── client/
                │   ├── FileClient.java
                │   ├── TransferListener.java
                │   └── TransferTask.java
                ├── ui/
                │   ├── FileTableModel.java
                │   ├── LoginPanel.java
                │   ├── MainWindow.java
                │   └── TransferPanel.java
                └── util/
                    └── FormatUtil.java
                    └── StreamUtil.java
```

## Requirements

- Java Development Kit (JDK) 17 or higher.
- No third-party dependencies, Maven, or Gradle required.

## Build and Run

### Windows

1. Compile all source files into `./out`:
   ```cmd
   scripts\build.bat
   ```

2. Start the file transfer server (default port 6000):
   ```cmd
   scripts\run-server.bat
   ```
   To specify an alternative port:
   ```cmd
   scripts\run-server.bat 7000
   ```

3. Launch the Swing client application:
   ```cmd
   scripts\run-client.bat
   ```

### Linux and macOS

1. Make scripts executable and build:
   ```bash
   chmod +x scripts/*.sh
   ./scripts/build.sh
   ```

2. Start the file transfer server:
   ```bash
   ./scripts/run-server.sh
   ```
   Or on custom port:
   ```bash
   ./scripts/run-server.sh 7000
   ```

3. Launch the Swing client application:
   ```bash
   ./scripts/run-client.sh
   ```

## Demo Credentials

- Username: `demo`
- Password: `demo123`

To generate credentials for new users, execute the built-in CLI utility:
```cmd
java -cp out com.netapps.filetransfer.common.HashUtil <username> <password>
```
Append the output line directly to `server/users.properties`.

## Usage Walkthrough

1. Start the server via `scripts/run-server.bat` (or `.sh`).
2. Launch the client via `scripts/run-client.bat` (or `.sh`).
3. Enter server credentials (`demo` / `demo123`) and click "Login".
4. Upload: Click "Upload File" and select any local file. Watch real-time MB/s throughput, progress bar, and ETA.
5. Download: Select a file in the table and click "Download". Files are saved to `./received/`.
6. Resume: Cancel an in-progress transfer at any point. Re-trigger the same transfer to resume from the last byte.
7. Delete: Select a file and click "Delete". Confirm the dialog to remove it from the vault.

## Configuration Options

- Default Port: `6000` (configurable via first CLI argument).
- Streaming Buffer: `8,192` bytes (8 KB chunk size).
- Maximum File Size: `500 MB` (configured in `ProtocolConstants.MAX_FILE_SIZE`).
- Minimum Disk Headroom: `10 MB` free space margin.
- Server Logs: `./logs/transfers.log`.
- Server Storage Vault: `./storage/<username>/`.

## Limitations and Ideas for Improvement

- Transport Layer Security: Raw TCP streams are unencrypted on the wire; SSL/TLS socket factories (`SSLServerSocket` / `SSLSocket`) would prevent packet sniffing.
- Compression: GZIP or Deflater stream wrapping could compress text payloads before network transmission.
- Parallel Multi-Part Transfers: Segmenting massive files into concurrent multi-socket parallel streams could maximize 10GbE network throughput.
