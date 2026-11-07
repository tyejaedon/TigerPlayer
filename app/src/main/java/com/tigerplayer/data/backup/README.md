# TigerPlayer Backup Sub-compartment (`data/backup/`)

This directory houses the **Backup & Restore Manager** responsible for exporting and importing user playlists, listening history, audio device presets, and settings.

---

## 1. Plain-English Overview

Think of the **Backup Sub-compartment** as the "safe and exporter" of TigerPlayer:
- It lets you save a snapshot of all your custom playlists, listening history, equalizer profiles, and application preferences to a portable `.json` backup file.
- It supports optional passphrase encryption so your server details and preferences are protected when saved to cloud storage or SD cards.
- When restoring on a new phone, it reconstitutes your playlists, device settings, and history seamlessly.

---

## 2. Core Components Breakdown

### `BackupManager.kt`
* **Purpose:** Orchestrates JSON serialization, database extraction, AES passphrase encryption, and restoration.
* **Key Methods:**
  - `exportBackup(outputStream, passphrase)`: Reads playlists, history, and settings, serializes to JSON, encrypts, and writes to output stream.
  - `importBackup(inputStream, passphrase)`: Decrypts JSON payload, validates schema version, and merges or restores database entities.

### `BackupModels.kt`
* **Purpose:** Data classes representing export schema structures (`BackupPayload`, `PlaylistBackupItem`, `DevicePresetBackupItem`, `HistoryBackupItem`).

---

## 3. Security & Versioning Guidelines

1. **Passphrase Encryption:** AES-256-GCM encryption is applied when a passphrase is set.
2. **Backward Compatibility:** Export schema includes `version` integers to handle future entity upgrades gracefully.
3. **Atomic Restore:** Database restore operations execute inside Room transactions to prevent half-imported corrupted states.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Backup Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Backup Payload** | Complete JSON bundle containing app settings, playlists, and history. | Portable file export format. | Data loss when switching phones. |
| **AES-GCM** | Advanced Encryption Standard with Galois/Counter Mode. | Encrypts export file when user sets a passphrase. | Unprotected personal listening history and server credentials. |
