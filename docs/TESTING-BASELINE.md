Signal Android — Upstream Baseline Testing Patterns
===============================================

Purpose
-------
This document captures canonical testing patterns observed in the upstream Signal Android repository (baseline). It excludes local feature/test helpers (accessibility branch additions) so we can align new tests to upstream conventions.

Key upstream principles
----------------------
- Prefer pure-JVM unit tests (fakes) for algorithmic logic and timing state machines.
- Use Robolectric only for focused Android integration tests that require Context/Resources/Views/Handler behavior.
- Avoid changing production code for tests. Use test-only shadows, rules, or mocks instead.

Canonical building blocks
------------------------
- Application / Context
  - Use `ApplicationProvider.getApplicationContext()`.
  - For Robolectric: `@Config(manifest = Config.NONE)` or `@Config(application = Application::class)`.

- Test rules & helpers
  - `SignalDatabaseRule` — in-memory Signal-like DB for DB tests.
  - `MockAppDependenciesRule` — initializes and isolates `AppDependencies` with mock providers.
  - `RxPluginsRule`, migration rules, and other test utilities live under `app/src/test/java/.../testutil`.

- Mocking & singletons
  - Use MockK: `mockk()`, `mockkObject(...)`, `every { ... } returns ...`, `unmockkObject(...)`.
  - Mock singletons rather than editing production singletons.

- Robolectric specifics
  - Call `shadowOf(Looper.getMainLooper()).idle()` to execute queued runnables posted to the main Handler.
  - Inject Android services with `Shadows.shadowOf(app as Application).setSystemService(...)`.
  - Prevent native/encrypted libs from loading with test shadows (do not edit production loader code).

When to use which test type
---------------------------
- Pure-JVM unit tests: state machines, timing windows, algorithms. Create simple fakes to assert behavior deterministically.
- Robolectric integration tests: UI/layout interactions, resource-dependent logic, service integration. Keep these minimal and focused.

Representative upstream examples (inspected)
------------------------------------------
- UI / Robolectric: `stories/StoryFirstTimeNavigationViewTest.kt` (uses `shadowOf(Looper.getMainLooper()).idle()` and Glide mocking)
- DB / rules: `testutil/SignalDatabaseRule.kt`, `testutil/MockAppDependenciesRule.kt`
- Pure-JVM fake pattern: `AccessibilityGestureRouterUnitTest.kt` (fake detector used for timing tests)

Recommended approach for accessibility tests
------------------------------------------
1. Algorithmic / store-backed tests (pure-JVM)
   - All algorithmic or KeyValue-backed tests (timing/state machines, pure logic, and simple KV reads/writes) should be pure-JVM unit tests. Do not use Robolectric for these: mock singletons (e.g. `mockkObject(SignalStore)`) and keep tests fast and deterministic.

2. Focused Robolectric integration tests (UI / notifier / handlers)
   - Reserve Robolectric for tests that genuinely require Android framework behaviour (Context/Resources/Handler/NotificationManager interaction).
   - When using Robolectric for these integration tests, follow upstream conventions exactly:
     - Use the canonical test Application and dependency rule:
       - `@Config(manifest = Config.NONE, application = org.thoughtcrime.securesms.testing.accessibility.AccessibilityTestApplication::class)`
       - `@get:Rule val appDependencies = MockAppDependenciesRule()`
     - Inject Android services and idle the main looper after posted work (e.g. `Shadows.shadowOf(Looper.getMainLooper()).idle()`).
     - Prefer test shadows for native/encrypted loaders rather than editing production loader code.

3. Mocking and singletons
   - Mock singletons with MockK (`mockkObject(...)`) and provide the minimal fields/methods your test needs. For heavier integration tests touching `AppDependencies`, use `MockAppDependenciesRule` to control and clear global state.

Next actions I can perform
-------------------------
- Convert existing accessibility gesture tests into a pure-JVM fake + one canonical Robolectric integration test.
- Or make a minimal patch that updates current Robolectric tests to strictly follow upstream patterns (mock ViewParent, idle looper, use test rules) and re-run tests.

Signal Android - Baseline Testing Patterns
========================================

This document captures the canonical testing patterns used across the Signal Android repository. It is intended to serve as a short reference so new tests (including accessibility tests) follow the same, well-understood conventions.

Principles
----------
- Prefer pure-JVM unit tests (fakes) for algorithmic logic; they are fast, deterministic, and do not rely on Android or native infrastructure.
- Use Robolectric tests only for Android integration behaviours that need Context/Resources/Handler/Views.
- Avoid changing production code to make tests pass; prefer test-only shadows, rules, or mocks.

Common test infrastructure
--------------------------
- Test runner and application
  - `@RunWith(RobolectricTestRunner::class)`
  - `@Config(manifest = Config.NONE, application = org.thoughtcrime.securesms.testing.accessibility.AccessibilityTestApplication::class, shadows = [...])`
  - `org.thoughtcrime.securesms.testing.TestApplication` is the canonical test Application class.

- Application and dependency rules
  - `MockAppDependenciesRule` — initializes `AppDependencies` with a `MockApplicationDependencyProvider()` and clears/re-mocks public properties after tests to isolate global state.
  - `SignalDatabaseRule` / `SignalDatabaseMigrationRule` — create in-memory Signal-like databases for DB tests.

- Robolectric and posted work
  - Tests that rely on `Handler(Looper.getMainLooper())` or post runnables must call `shadowOf(Looper.getMainLooper()).idle()` to execute queued work.

- Avoiding native/encrypted libs
  - SQLCipher/other native libraries are guarded via test-only Shadows (e.g., `ShadowSqlCipherLibraryLoader`) or by mocking the loader. Prefer shadows rather than editing production loader code.

- Mocking patterns
  - MockK is the standard mocking library. Use `mockk()`, `mockkObject(...)`, `every { ... } returns ...`, and `unmockkObject(...)`.
  - For singletons (SignalStore, SignalDatabase, etc.) use `mockkObject()` and provide test doubles.

Practical patterns to copy
-------------------------
1. Pure-JVM fake for gesture/timing logic
   - Example: implement `AccessibilityModeExitGestureDetectorFake` and test triple-tap timing purely on timestamps (see `AccessibilityGestureRouterUnitTest`).

2. Robolectric integration test for View/Handler interactions
   - Use `TestApplication` and `ApplicationProvider.getApplicationContext()`.
   - Inject Android services via `Shadows.shadowOf(app as Application).setSystemService(...)`.
   - Stub `View.parent` (mock `ViewParent`) if production code calls `getParent()`.
   - Idle main looper after touch sequences: `shadowOf(Looper.getMainLooper()).idle()`.

Tests inspected during this analysis
----------------------------------
The following tests were opened and reviewed to extract patterns and examples. (This is not a complete list of all tests in the repo; it is a focused set covering database, Robolectric, mocking, and gesture-related tests.)

- Accessibility (accessibility package)
  - `AccessibilityGestureDetectionTest.kt` (integration-style gesture test) — observed issues and current implementation
  - `AccessibilityGestureDetectorTest.kt` (spec placeholder)
  - `AccessibilityGestureRouterUnitTest.kt` (pure-JVM fake detector)
  - `AccessibilityRouterTest.kt` (Robolectric router tests)
  - `OnlyRunAccessibilityGestureTest.kt` (helper launcher)

- Test utilities and rules
  - `testutil/MockAppDependenciesRule.kt`
  - `testutil/SignalDatabaseRule.kt`
  - `testing/TestApplication.kt`
  - `testing/ShadowSqlCipherLibraryLoader.kt` (test shadow)

- Examples of Robolectric usage elsewhere
  - `stories/StoryFirstTimeNavigationViewTest.kt` — uses `shadowOf(Looper.getMainLooper()).idle()` and mocks Glide; good example for idling and mocking Android frameworks
  - Many other tests in `app/src/test` use `RobolectricTestRunner` and `@Config` patterns (see repo-wide grep for `RobolectricTestRunner`).

Recommendations for accessibility tests
-------------------------------------
1. For gesture/timing logic: prefer a pure-JVM fake and unit tests that assert timing windows. This avoids View/Looper complexity entirely.
2. For one end-to-end integration test: follow the Robolectric canonical pattern:
   - Use `TestApplication` and `ApplicationProvider.getApplicationContext()`.
   - Inject `AccessibilityManager` via `Shadows`.
   - Provide `ViewParent` mocks for `View` instances used in tests.
   - After posting touch sequences, call `shadowOf(Looper.getMainLooper()).idle()` to ensure posted runnables run.
3. Do not change `SqlCipherLibraryLoader` in production; prefer a Robolectric `Shadow` or test shadow as used elsewhere.

Next steps suggested
-------------------
- Convert logic-heavy accessibility tests into pure-JVM fakes where possible.
- Keep a single Robolectric integration test that follows the repo patterns above; apply `MockAppDependenciesRule` if tests depend on `AppDependencies` singletons.

If you'd like, I will now:
- (A) Convert `AccessibilityGestureDetectionTest` into two parts: a pure-JVM timing test (fake) and a small Robolectric integration test that follows the repo patterns; or
- (B) Make minimal edits to the existing Robolectric test to follow repo patterns (mock ViewParent, shadow main looper), re-run tests, and report results.

-- End of document
