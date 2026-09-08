---
applyTo: 'app/src/test/**,app/src/androidTest/**'
---

# Testing Guidelines

## Available tooling

| Scope | Libraries |
|---|---|
| Unit (`app/src/test`) | JUnit 4, MockK, `kotlinx-coroutines-test` |
| Instrumented (`app/src/androidTest`) | AndroidX JUnit, Espresso, Compose UI Test, MockK Android, `room-testing`, Macrobenchmark, UI Automator |

Prefer a JVM unit test. Reach for an instrumented test only when you genuinely need a device —
Room migrations, Compose interaction, or real playback.

## Naming & structure

```kotlin
@Test
fun `skipping a track before threshold records no history row`() { … }
```

Backtick names, describing behavior and expected outcome. Arrange / Act / Assert, with one logical
assertion per test. Do not assert on incidental implementation details.

## Coroutines & Flow

- Use `runTest` — never `runBlocking` in tests.
- Inject dispatchers rather than hardcoding `Dispatchers.IO`; use `StandardTestDispatcher` /
  `UnconfinedTestDispatcher` in tests.
- Assert on `Flow` emissions explicitly rather than sampling `.first()` and hoping. Prefer collecting
  into a list with a bounded `take(n)`, or Turbine if it is added later.
- Advance virtual time (`advanceTimeBy`, `advanceUntilIdle`) instead of real `delay`. No test should
  sleep.

## Determinism

Tests must not depend on wall-clock time, timezone, locale, device state, or network.

- Inject a time source. `StatsEngine.calculateStartTimeForFilter` uses `Calendar` with subtle
  boundary semantics — parameterize across timezones and DST transitions rather than trusting the
  CI machine's default.
- No real network. Stub the Retrofit interface or use a fake repository.
- No reliance on test execution order.

## Room

- Build in-memory: `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`.
- Migration tests use `MigrationTestHelper` and must assert **data survives**, not just that the
  schema validates. Cover every version pair (issue #74).
- Test the DAO's real SQL. Do not mock the DAO when the query itself is the thing under test.

## Compose

- Address nodes by `testTag`, not by display string — strings are localized and change.
- Centralize tags in a constants object, following the existing `PrismTestTags`.
- Use `composeTestRule.waitUntil` rather than `Thread.sleep`.
- Prefer testing a stateless composable with hoisted state directly over driving a full ViewModel.

## Priority coverage gaps

These are the highest-risk untested paths (issue #73). New tests here are especially welcome:

1. **Queue snapshot round-trip** — `serializeQueueSnapshot` / `deserializeQueueSnapshot` encode 18
   positional fields with `\u001F` / `\u001E` separators and index-based parsing. Test unicode,
   empty fields, and metadata that itself contains the separator characters.
2. **Stats aggregation windows** — every filter, across timezones and DST boundaries.
3. **Library cache diffing** — add / remove / modify / no-change.
4. **AutoEq parsing** — malformed input must surface an error, not be silently swallowed.

## What not to do

- Do not write a test that only asserts a mock was called, with no behavioral assertion.
- Do not weaken an assertion to make a failing test pass.
- Do not add `@Ignore` without a linked issue explaining why.
- Do not test private functions — test through the public surface.
- Do not delete or skip a failing test to satisfy AIRP Phase 3. A failing test is a finding.

