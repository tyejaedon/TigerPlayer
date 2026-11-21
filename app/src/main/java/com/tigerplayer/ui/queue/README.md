# TigerPlayer Queue UI Sub-compartment (`ui/queue/`)

This directory houses the playback queue management UI, track reordering list, and upcoming track list.

---

## 1. Plain-English Overview

Think of the **Queue Sub-compartment** as the "up next playlist manager" of TigerPlayer:
- It shows the list of songs scheduled to play next.
- It lets you drag to reorder songs, remove tracks from the queue, or clear the queue.

---

## 2. Component Breakdown

### `QueueScreen.kt` & `QueueViewModel.kt`
* **Role:** Interactive queue view displaying currently playing track, upcoming items, drag-to-reorder affordances, and queue management actions.

---

## 3. UI Guidelines

1. **Reorder Stability:** Drag-and-drop reordering updates `MediaControllerManager` queue state reactively.
2. **Current Track Highlighting:** The active track is distinctly styled with animation and equalizer icons.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Queue Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Playback Queue** | Ordered list of tracks queued for sequential playback. | Displayed and managed in `QueueScreen.kt`. | User cannot view or change upcoming songs. |
