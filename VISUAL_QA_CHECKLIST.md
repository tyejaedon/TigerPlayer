# TigerPlayer Visual QA Checklist (Home / Player / Library)

Date baseline: 2026-06-27

## Capture Rules
- Use the same device brightness and dark mode setting for all captures.
- Capture each screen in both `Balanced` and `High` Neon Contrast from Settings.
- Save screenshots in this naming format:
  - `home-balanced.png`, `home-high.png`
  - `player-balanced.png`, `player-high.png`
  - `library-balanced.png`, `library-high.png`

## Home Screen
- [ ] Hero cards remain readable (title/subtitle contrast >= comfortable at arm's length).
- [ ] Premium cards with RGB border show clear edge glow without clipping text.
- [ ] Non-premium glass cards do not show RGB border and preserve hierarchy.
- [ ] Scroll performance feels smooth (no visible flicker on glow/shadow while scrolling).

## Player Screen
- [ ] Mini player RGB border is visible and symmetric around all corners.
- [ ] Waveform/progress thread remains visible against the glass background.
- [ ] Play/pause and favorite icons are legible in both contrast modes.
- [ ] Full player transitions do not flash white or lose neon accents.

## Library Screen
- [ ] List cards and section containers keep stable glass contrast in both modes.
- [ ] Typography remains clear over dark surfaces with no muddy gray text.
- [ ] Selected tabs/filters maintain clear active state color separation.
- [ ] Album art edges are not over-bloomed by nearby shadows.

## Regression Quick Checks
- [ ] Toggle `Balanced -> High -> Balanced` twice; no stale colors remain.
- [ ] Background/foreground app cycle keeps the selected contrast mode.
- [ ] Orientation change does not alter border hierarchy unexpectedly.
- [ ] No screen shows fully white burn patches or crushed black detail.

## Notes
- If any failure appears, attach the affected screenshot and mention:
  - Screen
  - Contrast mode
  - Expected vs actual behavior

