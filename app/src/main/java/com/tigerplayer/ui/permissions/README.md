# TigerPlayer Permissions Sub-compartment (`ui/permissions/`)

This directory houses runtime permission requests, storage permission onboarding, and notification permission flows.

---

## 1. Plain-English Overview

Think of the **Permissions Sub-compartment** as the "permission doorkeeper" of TigerPlayer:
- It guides the user through granting required Android runtime permissions (e.g., `READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, Storage Access Framework directory access).

---

## 2. Component Breakdown

### `PermissionsScreen.kt`
* **Role:** Onboarding composable rendering permission explanations, rationale dialogs, and permission request triggers.

---

## 3. Security & Policy Guidelines

1. **Clear Rationale:** Displays plain-English explanations before launching system permission dialogs.
2. **Granular Handling:** Handles permission denial gracefully, offering settings fallback links.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Permissions Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Runtime Permission** | Android permission requested while using the app (API 23+). | Requested in `PermissionsScreen.kt`. | SecurityException or inability to scan media files. |
