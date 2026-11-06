# TigerPlayer Dashboard Sub-compartment (`ui/dashboard/`)

This directory houses high-level analytical dashboard viewmodels and state holders.

---

## 1. Plain-English Overview

Think of the **Dashboard Sub-compartment** as the "analytics data provider" of TigerPlayer:
- It aggregates listening history, scrobble statistics, top artists, and listening hour metrics to feed dashboard widgets and cards.

---

## 2. Component Breakdown

### `DashboardViewModel.kt`
* **Role:** Observes `HistoryRepository` and `StatsEngine` to produce reactive state for home dashboard analytics widgets.

---

## 3. Design Guidelines

1. **Reactive Aggregation:** Combines listening history database flows using `StateFlow` operators.

---

## 4. Tabulated Jargon Glossary

| Jargon Term | Plain-English Definition | Role in Dashboard Sub-compartment | Failure Consequence |
| :--- | :--- | :--- | :--- |
| **Analytics Aggregation** | Summarizing raw listening logs into top artist totals. | Executed by `DashboardViewModel.kt`. | Dashboard shows empty or uncalculated metrics. |
