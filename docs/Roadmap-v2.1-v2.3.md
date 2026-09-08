# TigerPlayer Roadmap — v2.1 → v2.3

> Generated from the September 2026 codebase review.
> Live tracking: [Milestones](https://github.com/tyejaedon/TigerPlayer/milestones)

## Legend

| Label | Meaning |
|---|---|
| `P0-blocker` | Cannot ship without this |
| `P1-high` | Should not ship without this |
| `P2-medium` | Planned, not blocking |
| `parity` | Table-stakes feature other players already have |
| `differentiator` | Signature capability unique to TigerPlayer |
| `tech-debt` | Refactoring / maintainability |

---

## Milestone: v2.1 "Foundation" — due 2026-10-31

**Theme:** correctness, data integrity, security, release config.

Nothing here is optional. The analytics surfaces currently present fabricated data, and the app cannot
survive a schema change.

### P0 — release blockers

| # | Issue | Area |
|---|---|---|
| [#42](https://github.com/tyejaedon/TigerPlayer/issues/42) | Listening stats systematically inflated | data |
| [#43](https://github.com/tyejaedon/TigerPlayer/issues/43) | Room database has no migrations | data |
| [#44](https://github.com/tyejaedon/TigerPlayer/issues/44) | Navidrome URLs malformed + expiring tokens cached | data / security |
| [#45](https://github.com/tyejaedon/TigerPlayer/issues/45) | `versionCode = 1` and `com.example` package name | build |
| [#46](https://github.com/tyejaedon/TigerPlayer/issues/46) | Spotify client secret shipped in APK → PKCE | security |

### P1 — high

| # | Issue | Area |
|---|---|---|
| [#47](https://github.com/tyejaedon/TigerPlayer/issues/47) | Scoped network security config | security |
| [#48](https://github.com/tyejaedon/TigerPlayer/issues/48) | `MediaControllerManager` leak + receiver export flag | playback |
| [#49](https://github.com/tyejaedon/TigerPlayer/issues/49) | Incremental library indexing | library |
| [#50](https://github.com/tyejaedon/TigerPlayer/issues/50) | Folder browsing / custom directories / `.nomedia` | library |
| [#51](https://github.com/tyejaedon/TigerPlayer/issues/51) | Sleep timer | playback |
| [#53](https://github.com/tyejaedon/TigerPlayer/issues/53) | Offload toggle restarts playback, wrong volume restore | dsp |
| [#54](https://github.com/tyejaedon/TigerPlayer/issues/54) | `getUnifiedTracks` stale emission + data race | data |
| [#55](https://github.com/tyejaedon/TigerPlayer/issues/55) | Position not persisted while playing | playback |

### P2

| # | Issue | Area |
|---|---|---|
| [#52](https://github.com/tyejaedon/TigerPlayer/issues/52) | Playback speed and pitch control | playback |

---

## Milestone: v2.2 "Parity" — due 2026-12-31

**Theme:** close the table-stakes gap.

Users treat these as non-negotiable defaults. A reviewer will not reach the fluid visualizer if the sleep
timer and folder browsing are missing.

| # | Issue | Priority |
|---|---|---|
| [#56](https://github.com/tyejaedon/TigerPlayer/issues/56) | ReplayGain volume normalization | P1 |
| [#57](https://github.com/tyejaedon/TigerPlayer/issues/57) | Synced lyrics parsing + auto-scroll | P1 |
| [#58](https://github.com/tyejaedon/TigerPlayer/issues/58) | M3U/M3U8 import + export | P1 |
| [#60](https://github.com/tyejaedon/TigerPlayer/issues/60) | `MediaLibraryService` → Android Auto / Wear / Assistant | P1 |
| [#59](https://github.com/tyejaedon/TigerPlayer/issues/59) | Backup and restore | P2 |
| [#61](https://github.com/tyejaedon/TigerPlayer/issues/61) | Glance home screen widgets | P2 |
| [#62](https://github.com/tyejaedon/TigerPlayer/issues/62) | Last.fm scrobbling | P2 |
| [#63](https://github.com/tyejaedon/TigerPlayer/issues/63) | Tag editor | P2 |
| [#64](https://github.com/tyejaedon/TigerPlayer/issues/64) | Artwork lookup negative cache | P2 |

---

## Milestone: v2.3 "Signature" — due 2027-03-31

**Theme:** lean into the uncontested differentiators.

| # | Issue | Priority |
|---|---|---|
| [#66](https://github.com/tyejaedon/TigerPlayer/issues/66) | Per-device automatic EQ profiles | P1 |
| [#65](https://github.com/tyejaedon/TigerPlayer/issues/65) | Waveform seekbar | P2 |
| [#67](https://github.com/tyejaedon/TigerPlayer/issues/67) | True overlapping crossfade | P2 |
| [#68](https://github.com/tyejaedon/TigerPlayer/issues/68) | Smart playlists / rules engine | P2 |
| [#69](https://github.com/tyejaedon/TigerPlayer/issues/69) | Year in Review shareable export | P2 |
| [#70](https://github.com/tyejaedon/TigerPlayer/issues/70) | Bit-perfect / signal path indicator | P2 |
| [#71](https://github.com/tyejaedon/TigerPlayer/issues/71) | OSS release readiness + F-Droid | P2 |

---

## Milestone: Continuous / Tech Debt — no due date

| # | Issue | Priority |
|---|---|---|
| [#72](https://github.com/tyejaedon/TigerPlayer/issues/72) | Split the `PlayerViewModel` god object | P1 |
| [#73](https://github.com/tyejaedon/TigerPlayer/issues/73) | Test coverage for the four highest-risk paths | P1 |
| [#74](https://github.com/tyejaedon/TigerPlayer/issues/74) | Room migration tests | P1 |
| [#75](https://github.com/tyejaedon/TigerPlayer/issues/75) | Dependency risk (vendored AAR, alpha lib, forced stdlib) | P2 |
| [#76](https://github.com/tyejaedon/TigerPlayer/issues/76) | Code hygiene checklist | P2 |

---

## Dependency graph

```
#42 stats correctness ──┬─> #62 Last.fm scrobbling
                        ├─> #68 smart playlists
                        └─> #69 Year in Review

#43 Room migrations ────┬─> #74 migration tests
                        ├─> #56 ReplayGain (adds columns)
                        └─> #59 backup/restore

#45 package rename ─────┬─> #46 Spotify PKCE (dashboard re-registration)
                        └─> #71 F-Droid submission

#49 incremental index ──┬─> #50 folder browsing
                        └─> #63 tag editor (targeted rescan)

#60 MediaLibraryService ──> #61 widgets (shared browse abstraction)

#75 dependency risk ──────> #71 F-Droid submission

#72 split ViewModel ──────> #73 test coverage
```

**Critical path:** `#42` and `#43` gate the largest number of downstream items. Start there.

---

## Competitive gap tracker

Snapshot at time of review. Update as parity issues close.

| Capability | TigerPlayer | Auxio | Retro/Metro | Symphony | Gramophone | Namida | Fossify | Tracked by |
|---|---|---|---|---|---|---|---|---|
| Folder browsing | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | #50 |
| Sleep timer | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | #51 |
| ReplayGain | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ | ❌ | #56 |
| Tag editor | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ | #63 |
| M3U import/export | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | #58 |
| Android Auto / Wear | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | #60 |
| Home screen widgets | partial | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | #61 |
| Last.fm scrobbling | ❌ | ❌ | ✅ | ❌ | ✅ | ✅ | ❌ | #62 |
| Synced lyrics | partial | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ | #57 |
| Backup/restore | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | #59 |
| Playback speed | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | #52 |
| **In-app parametric EQ** | ✅ | system | system | system | system | ✅ | system | — |
| **Audio-reactive haptics** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | — |
| **GPU fluid visualizer** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | — |
| **Listening analytics** | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | — |
| **Subsonic/Navidrome** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | — |
| **Per-device auto EQ** | planned | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | #66 |

**Read:** the differentiators (bottom block) are genuinely uncontested. The gap is entirely in the top block —
features users treat as defaults. v2.2 exists to close it.

