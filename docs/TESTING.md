# Manual Testing Plan: Java Resumable File Transfer System

This test plan details verification steps for the binary TCP file transfer server and Swing client.

---

## Test Cases

### 1. Server Startup & Default Account Provisioning
- **Steps**:
  1. Delete `users.properties` if present to test auto-generation.
  2. Launch server using `scripts/run-server.bat` (or `.sh`).
- **Expected Results**:
  - Console prints: `Starting FileServer on port 6000...`
  - File `server/users.properties` is created with a salted hash entry for `demo`.
  - Console outputs: `FileServer actively listening on port 6000`.

### 2. Successful Authentication (Demo User)
- **Steps**:
  1. Launch client GUI via `scripts/run-client.bat` (or `.sh`).
  2. In the top bar, leave defaults: `Host: 127.0.0.1`, `Port: 6000`, `User: demo`, `Pass: demo123`.
  3. Click "Login".
- **Expected Results**:
  - Top status updates to green: `Connected as: demo`.
  - Action buttons (`Upload File`, `Download`, `Delete`, `Refresh`) become enabled.
  - File table automatically queries and displays contents of `./storage/demo/`.

### 3. Authentication Failure & Socket Teardown
- **Steps**:
  1. Disconnect and enter `User: demo`, `Pass: wrongpass999`.
  2. Click "Login".
- **Expected Results**:
  - Error dialog displays: `Failed to connect or authenticate: Server rejected login: Authentication failed: invalid username or password`.
  - Top status displays red: `Login Failed`.
  - Server console and `./logs/transfers.log` record failed authentication attempt.
  - Socket is severed immediately.

### 4. Adding a New User via HashUtil
- **Steps**:
  1. Compile project and run HashUtil CLI:
     `java -cp out com.netapps.filetransfer.common.HashUtil alice secretPass`
  2. Copy the resulting line into `server/users.properties`:
     `alice=<salt>:<hash>`
  3. Restart or authenticate using `User: alice`, `Pass: secretPass`.
- **Expected Results**:
  - Login succeeds.
  - Client accesses an isolated empty folder `./storage/alice/`.

### 5. Path Traversal Attack Prevention
- **Steps**:
  1. Attempt to request file info or download targeting traversal paths:
     `../../windows/win.ini` or `../demo/secret.txt`.
- **Expected Results**:
  - Server rejects the request with status `400` (`STATUS_BAD_REQUEST`).
  - No file outside `./storage/<user>/` can be read, written, or deleted.

### 6. File Upload & SHA-256 Checksum Verification
- **Steps**:
  1. Click "Upload File" and pick a local file (e.g. `document.pdf`, ~5 MB).
- **Expected Results**:
  - Transfer panel activates with progress bar advancing from 0% to 100%.
  - Real-time throughput (e.g. `12.50 MB/s`) and remaining ETA display.
  - Server streams bytes directly to `./storage/demo/document.pdf.part`.
  - Upon completion, server verifies SHA-256 against client digest.
  - File is atomically renamed to `./storage/demo/document.pdf`.
  - Transfer history records: `[SUCCESS] document.pdf - Upload successful...`.

### 7. Resumable Upload Test
- **Steps**:
  1. Upload a large file (~50 MB).
  2. At ~30%, click "Cancel" on the Transfer Panel.
  3. Notice that `./storage/demo/<file>.part` exists on the server with ~15 MB.
  4. Click "Upload File" again selecting the identical file.
- **Expected Results**:
  - Server reports existing offset (~15 MB).
  - Client skips the initial 15 MB and resumes streaming from that offset.
  - Progress bar starts at ~30% and finishes the remaining 70%.
  - Full file SHA-256 is verified and file successfully saved.

### 8. Corrupted Upload Integrity Mismatch Test
- **Steps**:
  1. Start an upload, cancel it halfway.
  2. Modify a byte inside `./storage/demo/<file>.part` manually.
  3. Resume upload from client.
- **Expected Results**:
  - Server finishes streaming, computes total SHA-256, and detects checksum mismatch.
  - Server deletes the corrupted `.part` file.
  - Client displays error: `Integrity mismatch: expected <hash> but computed <bad_hash>`.
  - Bad file is purged from disk.

### 9. Resumable Download Test
- **Steps**:
  1. Select a file in the remote file table and click "Download".
  2. Choose a destination folder (default `./received`).
  3. Interrupt the network or cancel transfer at 50%.
  4. Verify `./received/<file>.part` exists on disk.
  5. Select the file again and click "Download".
- **Expected Results**:
  - Client announces existing local byte offset to the server.
  - Server begins streaming starting precisely from that byte.
  - Progress bar resumes from 50% to 100%.
  - Final SHA-256 matches and file is saved to `./received/<file>`.

### 10. File Deletion Confirmation
- **Steps**:
  1. Select a file from the table and click "Delete".
  2. Confirmation prompt appears: `Are you sure you want to delete '<file>' from server storage?`.
  3. Click "Yes".
- **Expected Results**:
  - File is removed from `./storage/demo/`.
  - Table automatically refreshes and removes entry.
  - Transfer history logs `[DELETED] <file>`.
