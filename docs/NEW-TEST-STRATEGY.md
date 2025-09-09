# New Test Strategy - Minimal Coverage for Core Functionality

## 🎯 **OVERVIEW**

**Goal**: Create minimal, semantic test coverage that validates core accessibility mode functionality.

**Strategy**: Focus on user-facing workflows rather than implementation details. Tests are designed for our **reactive router implementation** (not the proactive rebase approach from ChatGPT design).

**Key Implementation Note**: Our router uses `routeIfNeeded()` called reactively in `onStart()` methods, not proactive rebasing in settings. Tests reflect this actual behavior.

**Critical Technology Constraint**: **No Robolectric** - SQLCipher cannot load in JVM environment. All database-touching tests must use `androidTest` on emulator/device.

**Coverage**: Essential user journeys + accessibility compliance + integration points.

**Testing Philosophy**:
- **Quality First**: Focus on user-facing workflows and accessibility compliance
- **Defensive Approach**: Reasonable coverage to catch upstream breaking changes
- **Manual Verification**: Critical for accessibility features and complex user flows

---

## 📋 **SEMANTIC TEST CASES**

### **🎯 Core User Journeys**

#### **Test Case 1.1: Enable Accessibility Mode**
```
Given: Signal app with existing conversations
When: User enables accessibility mode in settings and returns
Then: Accessibility mode launches with conversation view (reactive routing)
And: Standard Signal UI elements are simplified
```

**Implementation**: Espresso E2E test (matches our reactive router implementation)
- Navigate to accessibility settings
- Enable accessibility mode toggle
- Navigate back from settings
- Verify router triggers and accessibility activity launches
- Verify simplified conversation UI displays
- Verify proper thread ID is passed if conversation selected

#### **Test Case 1.2: Select Conversation**
```
Given: Accessibility mode settings screen
When: User selects a conversation from available chats
Then: Selected conversation is saved for accessibility mode
And: Selection persists across app restarts
```

**Implementation**: Espresso integration test
- Open accessibility settings
- Select conversation from list
- Verify selection is saved to preferences
- Restart app and verify selection persists

#### **Test Case 1.3: Exit with Production Gesture**
```
Given: Accessibility mode active with conversation
When: User performs configured exit gesture (e.g., corner hold)
Then: Settings activity opens
And: Accessibility mode is still enabled for next launch
```

**Implementation**: Espresso integration test + custom gesture helper
- Launch accessibility mode with selected conversation
- Perform exit gesture (corner touch & hold)
- Verify settings activity opens
- Verify accessibility mode remains enabled in preferences

#### **Test Case 1.4: Send Message**
```
Given: Accessibility mode active with conversation
When: User types and sends a message
Then: Message appears in conversation
And: Message is sent successfully via Signal
```

**Implementation**: Espresso integration test
- Launch accessibility mode
- Type message in input field
- Tap send button
- Verify message appears in conversation list
- Verify message is sent (network call successful)

#### **Test Case 1.5: Receive Message**
```
Given: Accessibility mode active
When: New message arrives in selected conversation
Then: Message appears in conversation automatically
And: UI updates without manual refresh
```

**Implementation**: Espresso integration test + mock message
- Launch accessibility mode
- Simulate incoming message (via test helper)
- Verify message appears in conversation
- Verify UI scrolls to show new message

---

### **♿ Accessibility Compliance**

#### **Test Case 2.1: TalkBack Navigation**
```
Given: Accessibility mode with TalkBack enabled
When: User navigates conversation with TalkBack
Then: All elements announce correctly
And: Navigation follows logical tab order
```

**Implementation**: Accessibility test with UiAutomator
- Enable TalkBack
- Launch accessibility mode
- Verify content descriptions exist
- Verify logical navigation order
- Verify no TalkBack errors

#### **Test Case 2.2: Screen Reader Compatibility**
```
Given: Accessibility mode with screen reader
When: User interacts with UI elements
Then: Screen reader announces meaningful information
And: No accessibility violations detected
```

**Implementation**: Accessibility scanner integration test
- Launch accessibility mode
- Run accessibility scanner
- Verify no accessibility errors
- Verify proper labeling and descriptions

---

### **🔗 Integration Points**

#### **Test Case 3.1: Signal Router Integration (Reactive)**
```
Given: Signal main activity running
When: Accessibility mode is enabled in another activity (e.g., settings)
Then: On next activity start, Signal routes to accessibility activity
And: Router handles thread ID validation and mode state
```

**Implementation**: Integration test reflecting our actual implementation
- Launch main activity (accessibility mode disabled)
- Enable accessibility mode in background (simulate settings change)
- Trigger activity restart (simulate app switch/navigation)
- Verify router detects mode change and launches accessibility activity
- Verify proper thread ID is passed if conversation selected

#### **Test Case 3.2: Settings Persistence**
```
Given: Accessibility mode configured
When: App is killed and restarted
Then: All accessibility settings persist
And: Mode launches automatically if enabled
```

**Implementation**: Integration test with app restart
- Configure accessibility mode settings
- Force stop app (simulate device restart)
- Relaunch app
- Verify settings are preserved
- Verify accessibility mode launches if enabled

### **Manual Testing Checklist (Critical for Accessibility)**
- [ ] **Large Controls**: Verify touch targets meet minimum size requirements
- [ ] **High Contrast**: Test visibility in high contrast mode
- [ ] **Screen Reader**: Manual verification with TalkBack enabled
- [ ] **Keyboard Navigation**: Test accessibility mode without touch
- [ ] **Gesture Areas**: Verify gesture detection zones are appropriately sized

---

## 🏗️ **TEST IMPLEMENTATION STRATEGY**

### **Test Pyramid Structure (SQLCipher-Issue-Aware)**
```
E2E Tests (15%)    - User journeys, critical workflows (androidTest)
Integration (30%)  - Component interactions, Signal integration (androidTest)
Unit Tests (55%)   - Core logic without database dependencies (test)
```

### **Technology Stack**
- **Espresso**: UI interactions and assertions (androidTest)
- **JUnit 4**: Test framework (compatible with Android)
- **AndroidJUnit4**: For instrumented tests on device/emulator
- **MockK**: Limited use (cannot mock SignalDatabase/SignalStore)
- **UiAutomator**: System-level UI testing

### **Test Organization (SQLCipher Constraints)**
```
app/src/test/                    # JVM unit tests (NO database access)
├── AccessibilityModeGestureDetectorTest.kt  # Pure logic tests
└── AccessibilityModeRouterTest.kt          # Router logic tests

app/src/androidTest/            # Instrumented tests (real database)
├── AccessibilityModeWorkflowTest.kt         # E2E user journeys
├── AccessibilitySettingsTest.kt             # Settings integration
├── AccessibilityRouterIntegrationTest.kt    # Router + database
└── AccessibilityComplianceTest.kt           # TalkBack testing
```

---

## 📊 **TEST EXECUTION & REPORTING**

### **Local Development**
```bash
# Run all accessibility tests
./gradlew testAccessibility

# Run specific test suites
./gradlew testAccessibilityUnit
./gradlew testAccessibilityIntegration
./gradlew testAccessibilityE2E
```

### **CI/CD Integration**
- **Unit tests**: Run on every PR
- **Integration tests**: Run on merge to main
- **E2E tests**: Run nightly on device farm
- **Accessibility tests**: Run weekly audit

### **Test Results**
- **JUnit XML**: For CI integration
- **HTML Reports**: Human-readable test results
- **Coverage Reports**: Track code coverage
- **Accessibility Audit**: Automated compliance reports

---

## 🎯 **TEST QUALITY CRITERIA**

### **Reliability**
- ✅ **Deterministic**: Tests pass consistently
- ✅ **Isolated**: No test dependencies or side effects
- ✅ **Fast**: Complete within reasonable time limits
- ✅ **Maintainable**: Easy to understand and modify

### **Coverage**
- ✅ **User Journeys**: All critical user workflows covered
- ✅ **Edge Cases**: Error conditions and boundary cases
- ✅ **Accessibility**: Screen reader compatibility
- ✅ **Integration**: Signal component interactions

### **Value**
- ✅ **Meaningful**: Tests validate user value, not implementation
- ✅ **Regression Prevention**: Catches functionality regressions
- ✅ **Documentation**: Tests serve as living documentation
- ✅ **Confidence**: Reliable signal for deployment readiness

---

## ⚠️ **TESTING RISKS & MITIGATION**

### **Primary Risk: Upstream Signal Changes**
- **Impact**: Breaking changes in reused Signal components
- **Mitigation**: Focus tests on integration points, monitor Signal releases
- **Strategy**: Comprehensive androidTest coverage of component interactions

### **Secondary Risk: Accessibility Service Variations**
- **Impact**: Different behavior across Android versions/devices
- **Mitigation**: Test on multiple Android versions, document compatibility
- **Strategy**: Manual testing checklist for accessibility features

### **Mitigation Approach**
- **Defensive Testing**: Test user-facing behavior, not internal implementation
- **Regression Prevention**: Automated tests catch upstream changes
- **Manual Verification**: Accessibility features require manual testing
- **Documentation**: Clear test cases serve as regression specifications

---

## 📈 **IMPLEMENTATION ROADMAP**

### **Phase 1: Foundation (Current)**
- [x] Test strategy designed (SQLCipher-aware)
- [x] Test infrastructure set up (Signal test framework integrated)
- [x] Basic androidTest framework implemented (specification tests created)

### **Phase 2: Testing Foundation Completion**
- [x] SignalStore integration tests (AccessibilitySettingsPersistenceTest created)
- [x] Database integration tests (AccessibilityDatabaseIntegrationTest created)
- [x] Gesture detection tests (AccessibilityGestureDetectPrionTest created)
- [x] Router integration tests (AccessibilityRouterIntegrationTest created)

### **Phase 3: Implementation Validation**
- [ ] Tests validate actual implementation (not just specifications)
- [ ] Integration tests with real Signal components
- [ ] End-to-end user workflow validation
- [ ] Performance and accessibility compliance

### **Phase 4: Production Readiness**
- [ ] Accessibility compliance tests (TalkBack)
- [ ] Cross-device compatibility tests
- [ ] Settings persistence validation
- [ ] Performance benchmarks

### **Phase 5: Maintenance & Documentation**
- [ ] Test documentation updated
- [ ] CI/CD pipeline configured (emulator-based)
- [ ] Test maintenance procedures established

---

## 🎯 **SUCCESS METRICS**

### **Test Coverage**
- **User Journeys**: 100% of critical workflows
- **Integration Points**: All Signal component interactions
- **Accessibility**: Full TalkBack compatibility
- **Error Scenarios**: Common failure modes covered

### **Test Quality**
- **Pass Rate**: >95% consistent pass rate
- **Execution Time**: Unit tests <30s, Integration <2min, Full suite <5min
- **Flakiness**: <1% intermittent failures
- **Code Coverage**: 90%+ for new accessibility code
- **Maintenance**: <30 minutes/month maintenance effort

### **Developer Experience**
- **Feedback Speed**: <2 minutes for unit test feedback
- **Debugging**: Clear error messages and stack traces
- **Documentation**: Tests serve as implementation examples
- **Confidence**: Reliable signal for code quality

---

*This test strategy focuses on semantic validation of user value rather than exhaustive implementation testing. Tests are designed to work with both current and future simplified implementations.*


---

# Review & Improvements (2025‑09‑07)

Overall the above strategy appears solid: it keeps the focus on user‑visible behaviours and acknowledges the SQLCipher/Robolectric boundary. Given what we’ve learned while bringing up CI on arm64/amd64 and the API‑35 aapt2 gap, here are targeted improvements that increase reliability and cut feedback time without bloating the plan.

## A. Scope & Architecture Clarifications

1) **Robolectric is allowed for logic** (still no DB):
   - Keep “no SQLCipher in JVM” but **permit Robolectric** for non‑DB logic (gesture detectors, router, confirmation control) to get sub‑second feedback.
   - Add a note: *“Robolectric permitted where no `net.sqlcipher.*` or `androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory` are referenced.”*
   - (Optional) Add a tiny shadow to neutralise any library loader if it’s touched by your `Application` class.

2) **Reactive router is the source of truth**:
   - Make that explicit in test names (e.g., `ReactiveRouter_WhenEnabled_RebasesOnNextStart`).
   - Add one guard test that asserts **no direct startActivity to Settings** from Settings save flow (ensures you don’t drift back to proactive rebasing later).

## B. CI‑Aware Build Controls (arm64 vs amd64)

3) **compileSdk/targetSdk override for arm64** (build‑only workaround):
   - Document the init‑script override you’re already using in CI:
     - *Arm64:* `COMPILE_SDK=34 TARGET_SDK=34` + `-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2`.
     - *Amd64:* no override; let AGP fetch its aapt2 and build with 35.
   - Add an appendix snippet showing the init script (so the strategy is executable).

4) **Task wiring in Gradle** (make the CLI in this doc real):
   - Create aggregate tasks in the build for the groups you list. Example in root `build.gradle[.kts]`:
     ```kotlin
     tasks.register("testAccessibilityUnit") { dependsOn(":app:testDebugUnitTest") }
     tasks.register("testAccessibilityIntegration") { dependsOn(":app:connectedDebugAndroidTest") }
     tasks.register("testAccessibilityE2E") { dependsOn(":app:connectedDebugAndroidTest") }
     tasks.register("testAccessibility") { dependsOn("testAccessibilityUnit", "testAccessibilityIntegration") }
     ```
   - If you keep multiple modules, point these to the specific module test tasks you intend.

## C. Determinism & Flake Controls

5) **Device/emulator normalisation** (instrumented tests):
   - Disable animations (`window/transition/animator = 0`).
   - Force 24‑hour time and English/`en-US` (or a single locale you assert against).
   - Ensure bright‑mode/contrast mode is consistent for pixel checks.

6) **Espresso waiting discipline**:
   - Prefer **IdlingResources** or **FlowIdling** wrappers over sleeps for:
     - message send completion,
     - router transitions (expose a `LiveData/Flow` signal or a simple `IdlingResource` held by the router).

7) **Gesture synthesis helper**:
   - Add a small, reusable helper that posts two‑pointer events with drift/timing controls for both gestures. Put it under `androidTest` utilities and use it in all gesture tests so behaviour changes are centralised.

## D. Data & Isolation

8) **Hermetic inbound message** tests:
   - For *Receive Message* flows, prefer a **fake inbound bus** (or a dedicated test hook in your repository layer) over trying to light up real Signal network in CI. The goal is UI reaction, not transport.

9) **Database state seeding**:
   - For instrumented tests, seed a minimal set of entities (thread + 1‑2 messages) via your DAO/repository before launching activities. Avoid UI‑driven setup in every test.

10) **Settings store contract tests**:
   - Unit tests (JVM) should assert the exact keys written (gesture A/B, PIN on/off, durations) and default migration when keys are absent.

## E. Accessibility Coverage Enhancements

11) **Adopt the Accessibility Test Framework (ATF)**:
   - Use ATF checks in `AccessibilityComplianceTest` to catch missing labels, touch target sizes, and traversal order problems programmatically.

12) **TalkBack scripting**:
   - Add a couple of scripted TalkBack focus moves (UiAutomator) that assert specific announcements at key points (enter confirmation screen; complete hold; PIN shown when enabled).

## F. Reporting & Dev UX

13) **Artifacts for fast triage**:
   - Always capture: `logcat`, `window_dump.xml`, and a screenshot on failure. Link these from CI output.

14) **Realistic time budgets**:
   - Update expectations: a clean smoke on emulator typically lands ~6–10 minutes with snapshotting; unit suite < 30 s; instrumentation slice 2–4 minutes.

## G. Concrete Additions to the Test List

- **Edge cases to add**:
  - Exit gesture with **one finger per corner mismatched** (e.g., TL + BR but >150 ms apart) → must not trigger.
  - Confirm slider **interruptions** (home button, incoming call UI) → when returning, overlay auto‑dismisses unless hold completes within the window.
  - **Thread deleted** between selection and launch → router falls back to onboarding or shows an inline prompt; no crash.
  - **Multiple rapid toggles** in Settings (debounced writes) → only one effective state change; router resolves to a single root.

- **Architectural guardrails**:
  - ArchUnit (or simple reflection test) banning `net.sqlcipher.*` in JVM tests.
  - Ensure router uses `NEW_TASK|CLEAR_TASK` rebases only in the intended places; fail the build if accidental `startActivity` without flags is introduced in routing code.

## H. Documented Fallbacks (to keep CI green)

- If arm64 resource linking against API 35 fails, CI auto‑falls back to `COMPILE_SDK=34 TARGET_SDK=34` and marks the job **yellow** (non‑blocking) while amd64 remains the gating job at 35. Add a brief note in this doc so future readers understand the split.

---

### Appendix: Init Script for Forcing compileSdk/targetSdk (CI‑only, already implemented in ci-smoke.sh)

```groovy
// /tmp/force-sdk.gradle
gradle.beforeProject { p ->
  def android = p.extensions.findByName("android"); if (android == null) return
  def c = System.getenv("COMPILE_SDK")?.toInteger()
  def t = System.getenv("TARGET_SDK")?.toInteger()
  if (c != null) {
    if (android.hasProperty("compileSdk")) android.compileSdk = c
    else if (android.metaClass.respondsTo(android,'compileSdkVersion', Integer)) android.compileSdkVersion c
    println "OVERRIDE ${p.path}: compileSdk -> ${c}"
  }
  if (t != null) {
    if (android.defaultConfig.hasProperty("targetSdk")) android.defaultConfig.targetSdk = t
    else if (android.defaultConfig.metaClass.respondsTo(android.defaultConfig,'targetSdkVersion', Integer))
      android.defaultConfig.targetSdkVersion t
    println "OVERRIDE ${p.path}: targetSdk -> ${t}"
  }
}
```

This keeps your original strategy intact, adds precise controls for the arm64 gap, and tightens determinism and discoverability. It should give you the maximum regression protection per minute of CI time.
