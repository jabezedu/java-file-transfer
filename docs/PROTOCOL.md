# Binary File Transfer Protocol (NFTX) Specification

## Overview

The Secure Resumable File Transfer System uses a binary, stateful stream protocol layered directly over TCP. Data primitives are encoded using standard big-endian format compatible with Java's `DataInputStream` and `DataOutputStream`.

Default TCP Port: `6000`  
Protocol Magic Header: `0x4E465458` (`"NFTX"`)  
Buffer Chunk Size: `8,192` bytes (8 KB)

---

## Status Codes

| Code | Constant | Meaning |
|---|---|---|
| `200` | `STATUS_OK` | Request succeeded |
| `206` | `STATUS_PARTIAL` | Resumed / partial chunk acknowledged |
| `400` | `STATUS_BAD_REQUEST` | Syntax error, illegal filename, or path traversal detected |
| `401` | `STATUS_AUTH_FAILED` | Invalid credentials or unrecognized user |
| `404` | `STATUS_NOT_FOUND` | Requested remote file does not exist |
| `413` | `STATUS_FILE_TOO_LARGE`| Payload exceeds maximum file threshold (500 MB) |
| `422` | `STATUS_INTEGRITY_MISMATCH` | Computed SHA-256 checksum does not match expected digest |
| `500` | `STATUS_INTERNAL_ERROR`| Unhandled server I/O error |
| `507` | `STATUS_DISK_FULL` | Insufficient free space on server volume |

---

## Command Opcodes

| Opcode | Name | Description |
|---|---|---|
| `1` | `AUTH` | Authenticate session credentials |
| `2` | `LIST` | List stored files in isolated user directory |
| `3` | `UPLOAD` | Upload file with resume support and streaming SHA-256 verification |
| `4` | `DOWNLOAD` | Download file with resume support and streaming SHA-256 verification |
| `5` | `DELETE` | Remove a stored file from remote user directory |
| `6` | `INFO` | Query file metadata and SHA-256 digest |
| `7` | `QUIT` | Gracefully close connection |

---

## Message Formats

### 1. Connection Handshake & Authentication

```
Client -> Server:
  [int: 4 bytes]  Magic (0x4E465458)
  [int: 4 bytes]  Command Opcode (1 = AUTH)
  [UTF: variable] Username
  [UTF: variable] Password

Server -> Client:
  [int: 4 bytes]  Status Code (200 on success, 401 on failure)
  [UTF: variable] Human-readable status message
```

*Note: If authentication fails, server terminates connection immediately.*

---

### 2. Directory Listing (LIST)

```
Client -> Server:
  [int: 4 bytes]  Command Opcode (2 = LIST)

Server -> Client:
  [int: 4 bytes]  Status Code (200)
  [int: 4 bytes]  File Count (N)
  Repeat N times:
    [UTF: variable] File Name
    [long: 8 bytes] File Size in Bytes
    [long: 8 bytes] Last Modification Epoch Milliseconds
```

---

### 3. File Info Query (INFO)

```
Client -> Server:
  [int: 4 bytes]  Command Opcode (6 = INFO)
  [UTF: variable] File Name

Server -> Client:
  [int: 4 bytes]  Status Code (200 or 404)
  If 200:
    [UTF: variable] File Name
    [long: 8 bytes] File Size
    [long: 8 bytes] Last Modified Milliseconds
    [UTF: variable] Hexadecimal SHA-256 Checksum
  If not 200:
    [UTF: variable] Error message
```

---

### 4. Resumable Upload (UPLOAD)

```
Client -> Server:
  [int: 4 bytes]  Command Opcode (3 = UPLOAD)
  [UTF: variable] File Name (sanitized against traversal)
  [long: 8 bytes] Total File Size in Bytes

Server -> Client:
  [int: 4 bytes]  Status Code (200 to proceed, 413, 507, etc.)
  If 200:
    [long: 8 bytes] Resume Offset (bytes already written to <name>.part)

Client -> Server:
  [raw bytes]     Stream (Total Size - Resume Offset) in 8 KB chunks
  [UTF: variable] Hexadecimal SHA-256 Checksum of entire original file

Server -> Client:
  [int: 4 bytes]  Status Code (200 if verified, 422 if checksum mismatch)
  [UTF: variable] Status description
```

---

### 5. Resumable Download (DOWNLOAD)

```
Client -> Server:
  [int: 4 bytes]  Command Opcode (4 = DOWNLOAD)
  [UTF: variable] File Name
  [long: 8 bytes] Client Existing Offset (bytes already in local <name>.part)

Server -> Client:
  [int: 4 bytes]  Status Code (200 or 404)
  If 200:
    [long: 8 bytes] Total File Size in Bytes
    [long: 8 bytes] Server Resume Offset
    [UTF: variable] Hexadecimal SHA-256 Checksum of entire remote file
    [raw bytes]     Stream (Total Size - Server Resume Offset) in 8 KB chunks

Client -> Server:
  [int: 4 bytes]  Verification Status (200 if client verified SHA-256, 422 on mismatch)
```

---

### 6. Delete File (DELETE)

```
Client -> Server:
  [int: 4 bytes]  Command Opcode (5 = DELETE)
  [UTF: variable] File Name

Server -> Client:
  [int: 4 bytes]  Status Code (200 if deleted, 404 if not found)
  [UTF: variable] Status description
```

---

### 7. Graceful Termination (QUIT)

```
Client -> Server:
  [int: 4 bytes]  Command Opcode (7 = QUIT)

Server -> Client:
  [int: 4 bytes]  Status Code (200)
  [UTF: variable] Goodbye message
```

---

## Detailed Transfer Example

### Resumed Download Scenario

1. User requested download of `archive.iso` (10,000,000 bytes).
2. Client has a partial file `./received/archive.iso.part` measuring 4,000,000 bytes.
3. Client issues `DOWNLOAD` with offset `4000000`.
4. Server validates file existence and responds with:
   - `totalSize = 10000000`
   - `resumeOffset = 4000000`
   - `sha256 = "e3b0c442..."`
5. Server streams remaining 6,000,000 bytes using 8 KB buffer slices.
6. Client appends incoming bytes to `archive.iso.part`.
7. Once 10,000,000 bytes are reached, client computes SHA-256 of the complete `archive.iso.part`.
8. Checksums match: client informs server with status `200`, and renames `archive.iso.part` -> `archive.iso`.
