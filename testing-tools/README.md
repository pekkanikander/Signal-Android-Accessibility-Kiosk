# Testing tools — Local CI & quick jobs

This document explains the `testing-tools/` helpers and the recommended local workflow for running the small accessibility-focused CI jobs (`ci-quick`, `ci-smoke`) using `act` and Docker. It aims to be practical and reproducible on macOS (including Apple Silicon) and Linux developer machines.

### Purpose
- Provide minimal scripts that build the app variants required for the accessibility mode E2E/unit tests.
- Provide a small CI-friendly wrapper (`ci-quick.sh`) that installs APKs, runs selected instrumentation tests, and collects artifacts.
- Provide helper tooling to decode a base64 `DEBUG_KEYSTORE_B64` into a `debug.keystore` and `keystore.debug.properties` so CI runs can produce installable, consistently-signed debug APKs.
- Provide developer helpers for running `act` locally and copying artifacts out of the ephemeral containers.

### Files of interest
- `ci-quick.sh` — small quick smoke runner: builds targeted variants, installs APKs onto a reachable emulator/device, runs 3 accessibility instrumentation tests and one unit test, collects `logcat` and instrumentation output, and creates `/tmp/quick-smoke.tar.gz` inside the runner environment.
- `ci-smoke.sh` — larger smoke script (kept as-is) for broader accessibility smoke runs.
- `ci-emulator-shell.sh` — helper used by smoke workflows to set up and verify emulator state.
- `decode-debug-keystore.sh` — decodes `DEBUG_KEYSTORE_B64` into `debug.keystore` at repo root and writes `keystore.debug.properties` expected by the Gradle scripts.
- `act-run-fetch.sh` — host-side helper that runs `act` and attempts to copy artifacts out of the container into a host directory (best-effort; relies on container artifact placement).

### Prerequisites
- Docker (or another OCI runtime) installed and running.
- `act` installed for local GitHub Actions emulation. Use the version you prefer but be aware of platform/architecture mismatches when running emulators.
- A reachable Android emulator or device for instrumentation tests. For local `act` runs we currently rely on your host emulator being reachable via ADB (recommended) so instrumentation tests can run.
- Optional: `DEBUG_KEYSTORE_B64` if you want CI to sign debug APKs with the same debug keystore used by your local device; otherwise APK install may fail due to signature mismatch.

### Environment variables
- `GITHUB_TOKEN` — required by `act` for some actions; load it from `.env` when running locally: `export "$(grep GITHUB_TOKEN .env)"` or `set -a && source .env && set +a`.
- `DEBUG_KEYSTORE_B64` — base64 of an Android `debug.keystore`. If set, the CI scripts will decode it into `debug.keystore` and write `keystore.debug.properties` to repo root so Gradle uses that for signing. Keep the raw value out of VCS.
- `ADB_SERVER_SOCKET` — optional; when using an emulator listening on a host socket, set this so the container can reach the host ADB server, e.g. `tcp:host.docker.internal:5037`.
- `SKIP_UPLOAD` — set to `true` locally to skip artifact upload steps in the workflow.

### How to run the quick job locally (recommended flow)
1. Ensure Docker is running and your emulator is started and reachable by `adb devices`.
2. Export any environment variables needed. Example:

```
export "$(grep GITHUB_TOKEN .env)"                         # if using .env
export DEBUG_KEYSTORE_B64="$(base64 -i ~/.android/debug.keystore | tr -d '\n')"  # optional
export ADB_SERVER_SOCKET=tcp:host.docker.internal:5037      # if using host ADB socket
export SKIP_UPLOAD=true
```

3. Run `act` yourself, or use the helper that wraps `act` and fetches artifacts:

```
./testing-tools/act-run-fetch.sh .github/workflows/ci-quick.yml quick ./quick-smoke-host
```

- Notes: `act-run-fetch.sh` attempts a best-effort `docker cp` from the container. Because act removes job containers quickly, it is fragile. If that fails, see the suggestions in the Troubleshooting section below.

Alternatively, run the script directly in a shell (when the workflow environment already has an emulator available):

```
chmod +x testing-tools/ci-quick.sh
./testing-tools/ci-quick.sh emulator-5554
```

This runs the same steps the workflow does but from your checked-out repo. It requires your Docker/container to be able to reach the emulator (see `ADB_SERVER_SOCKET`).

### How to decode the debug keystore
If you need deterministic debug signing (to avoid `INSTALL_FAILED_UPDATE_INCOMPATIBLE` with an already-installed app), decode and write the keystore via:

```
chmod +x testing-tools/decode-debug-keystore.sh
./testing-tools/decode-debug-keystore.sh
```

This writes `debug.keystore` and `keystore.debug.properties` to the repository root. Gradle's `app/build.gradle.kts` looks for `keystore.debug.properties` at the repo root.

### Recommended `act` flags (local)
- Use `--container-architecture linux/arm64` on Apple Silicon when your image supports arm64.
- Provide `--container-options "--privileged --add-host=host.docker.internal:host-gateway"` so containers can access host services like the emulator and adb server.
- Example `act` invocation used in testing:

```
act --container-architecture linux/arm64 \
    --container-options "--privileged --add-host=host.docker.internal:host-gateway" \
    -P ubuntu-latest=signal-act-android:ci-arm64 -j quick -W .github/workflows/ci-quick.yml --pull=false --env CI=true --env ADB_SERVER_SOCKET=tcp:host.docker.internal:5037 --env SKIP_UPLOAD=true
```

Adjust carefully; we avoid bind-mounting the whole workspace to keep container isolation.

### Where artifacts are written
- The `ci-quick.sh` script writes outputs to `/tmp/quick-smoke` inside the container and then creates `/tmp/quick-smoke.tar.gz` inside the container. `act-run-fetch.sh` attempts to copy that tarball into a host directory.

### Troubleshooting
- Problem: `docker cp` fails because container is removed. Solution: use `act` with `--reuse` and run `act-run-fetch.sh` immediately; or use the dedicated host-mounted artifacts directory approach (`--container-options "-v $(pwd)/ci-artifacts:/tmp/ci-artifacts:rw"`) so artifacts are written directly to the host. Recommended: create and bind-mount a dedicated `ci-artifacts` host folder if you need deterministic artifact retrieval.
- Problem: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Solution: decode a `DEBUG_KEYSTORE_B64` into `debug.keystore` at repo root before building or running the workflow.
- Problem: `adb` not reachable. Solution: export `ADB_SERVER_SOCKET` to point at `host.docker.internal` or the machine's ADB socket; ensure emulator is running and `adb devices` shows the emulator.

### Security and hygiene
- Never commit `debug.keystore` or `keystore.debug.properties` to the repo. Store `DEBUG_KEYSTORE_B64` in CI secrets only and never check it in.
- Add `.env` to `.gitignore` (already present) to prevent accidental commits of tokens.

### Testing-tools README ownership
If you update the local runner image or change where artifacts are written, update this README accordingly. Keep the documentation concise and oriented at reproducing runs on macOS + act.

----
Last updated: autogenerated by assistant — review and adjust host paths and commands for your environment before running.

# Manual Testing Framework for Accessibility Mode

## Overview

This directory contains comprehensive manual testing tools and procedures for validating Accessibility Mode functionality across different scenarios, devices, and conditions.

## Directory Structure

```
testing-tools/
├── gesture-testing/           # Gesture detection testing tools
│   ├── emulator-gesture-test.sh     # Automated gesture testing script
│   └── gesture-test-results/        # Test result logs
├── accessibility-testing/     # TalkBack and accessibility testing
│   ├── talkback-control.sh           # Automated TalkBack control & testing
│   ├── talkback-testing-procedures.md  # Comprehensive TalkBack test procedures
│   └── accessibility-test-results/   # Accessibility test results
├── performance-testing/       # Performance benchmarking tools
│   ├── performance-benchmark.sh      # Automated performance benchmarks
│   └── benchmark-results/            # Performance test results
├── cross-device-testing/      # Cross-device compatibility testing
│   ├── run-device-tests.sh           # Automated cross-device testing
│   ├── cross-device-test-matrix.md   # Device and version compatibility matrix
│   └── device-test-results/          # Cross-device test results
└── README.md                  # This file
```

## Quick Start

### 1. Setup Testing Environment
```bash
# Make gesture test script executable
chmod +x testing-tools/gesture-testing/emulator-gesture-test.sh

# Make performance benchmark script executable
chmod +x testing-tools/performance-testing/performance-benchmark.sh

# Create results directories
mkdir -p testing-tools/gesture-testing/gesture-test-results
mkdir -p testing-tools/accessibility-testing/accessibility-test-results
mkdir -p testing-tools/performance-testing/benchmark-results
mkdir -p testing-tools/cross-device-testing/device-test-results
```

### 1.a Emulator snapshot helper

If you rely on a preconfigured emulator state (installed & registered Signal), use the snapshot helper to save and restore that state.

Save current running emulator state to a named snapshot:
```bash
# Save snapshot on first connected device (named "signal_preconfigured")
./testing-tools/emulator-snapshot-tool.sh save signal_preconfigured
```

Load (restore) a previously saved snapshot on the running emulator:
```bash
./testing-tools/emulator-snapshot-tool.sh load signal_preconfigured
```

Notes:
- The emulator must be running and support snapshots. If you have multiple devices, pass the device serial as the first argument.
- You can list snapshots with `adb -s <device> emu avd snapshot list` or `emulator -list-avds` for AVDs.

### 2. Basic Gesture Testing
```bash
# Test triple tap gesture on default emulator
./testing-tools/gesture-testing/emulator-gesture-test.sh triple-tap-debug

# Test all gestures with stress testing
./testing-tools/gesture-testing/emulator-gesture-test.sh stress-test emulator-5554 10
```

### 3. Performance Benchmarking
```bash
**WARNING — destructive actions:** By default these scripts will NOT clear app data or uninstall apps. Always run in dry-run first.

To run benchmark (safe dry-run):
```bash
# Dry-run (no destructive actions)
DRY_RUN=true ./testing-tools/performance-testing/performance-benchmark.sh full-suite emulator-5554
```

To allow destructive actions (explicit opt-in):
```bash
# This will clear app data between iterations and may remove registration
ALLOW_DATA_CLEAR=true ./testing-tools/performance-testing/performance-benchmark.sh startup-time emulator-5554
```
```

### 4. Accessibility Testing
```bash
# Enable TalkBack automatically
./testing-tools/accessibility-testing/talkback-control.sh enable-talkback emulator-5554

# Run automated accessibility tests
./testing-tools/accessibility-testing/talkback-control.sh run-accessibility-tests emulator-5554

# Disable TalkBack when done
./testing-tools/accessibility-testing/talkback-control.sh disable-talkback emulator-5554
```

### 5. Cross-Device Testing
```bash
# Run compatibility tests on all connected devices
./testing-tools/cross-device-testing/run-device-tests.sh compatibility all

# Run full test suite on specific devices
./testing-tools/cross-device-testing/run-device-tests.sh full-suite emulator-5554,pixel-device
```

## Test Categories

### 1. Gesture Testing
**Purpose**: Validate gesture detection works reliably
- **Tools**: `emulator-gesture-test.sh`
- **Coverage**: All 4 gesture types (triple tap, opposite corners, two-finger header, single-finger edge)
- **Automation**: ✅ **Fully automated** with environment verification
- **Results**: Logged to `gesture-test-results/`

### 2. Accessibility Testing
**Purpose**: Ensure TalkBack and screen reader compatibility
- **Automated Tools**: `talkback-control.sh` for TalkBack control and testing
- **Manual Procedures**: `talkback-testing-procedures.md`
- **Coverage**: Navigation, announcements, touch targets, gesture conflicts
- **Automation**: ✅ **Semi-automated** with TalkBack enable/disable and test execution
- **Results**: Logged to `accessibility-test-results/`

### 3. Performance Testing
**Purpose**: Validate performance meets requirements
- **Tools**: `performance-benchmark.sh`
- **Metrics**: Startup time, memory usage, CPU usage, gesture latency
- **Automation**: ✅ **Fully automated** with environment verification
- **Results**: JSON format in `benchmark-results/`

### 4. Cross-Device Testing
**Purpose**: Ensure compatibility across Android ecosystem
- **Automated Tools**: `run-device-tests.sh` for multi-device testing
- **Compatibility Matrix**: `cross-device-test-matrix.md`
- **Coverage**: Android 8.0-14, various device types, OEM skins
- **Automation**: ✅ **Fully automated** device discovery and test orchestration
- **Results**: JSON summaries in `device-test-results/`

## Test Execution Workflow

### Phase 1: Setup and Environment
1. Prepare test device/emulator
2. Install Signal app with Accessibility Mode
3. Configure test data (conversations, messages)
4. Enable necessary permissions

### Phase 2: Automated Testing
1. Run gesture detection tests
2. Execute performance benchmarks
3. Validate basic functionality

## CI Smoke Job (recommended)

Add a CI job that runs lightweight, non-destructive checks to catch regressions early. Example steps for the CI job:

- Checkout repo and install adb/android SDK (CI runner must have emulator support)
- Start a dedicated emulator image (headless) and wait for boot
- Install the app under test (production build recommended)
- Run gesture script in dry-run mode to ensure adb commands are valid:
  ```bash
  DRY_RUN=true ./testing-tools/gesture-testing/emulator-gesture-test.sh triple-tap-debug emulator-5554
  ```
- Run accessibility check that only verifies TalkBack presence (non-destructive):
  ```bash
  ./testing-tools/accessibility-testing/talkback-control.sh check-talkback emulator-5554
  ```
- Collect and upload logs/artifacts if checks fail

Notes:
- CI smoke job must **not** enable destructive operations (no ALLOW_DATA_CLEAR=true).
- Prefer an emulator snapshot to reset state between runs.

### Phase 3: Manual Testing
1. Follow TalkBack testing procedures
2. Test accessibility features manually
3. Verify cross-device compatibility
4. Document any issues found

### Phase 4: Results Analysis
1. Review automated test results
2. Analyze performance metrics
3. Document manual test findings
4. Update compatibility matrix

## Success Criteria

### Functional Testing
- ✅ All automated tests pass
- ✅ Gesture detection works on target devices
- ✅ Accessibility features function correctly
- ✅ No crashes or force closes

### Performance Testing
- ✅ Startup time < 3 seconds (95% of devices)
- ✅ Memory usage < 200MB peak
- ✅ CPU usage < 10% during normal operation
- ✅ Gesture latency < 500ms

### Accessibility Testing
- ✅ Full TalkBack compatibility
- ✅ Touch targets meet 48dp minimum
- ✅ No gesture conflicts with accessibility services
- ✅ Keyboard navigation works

### Compatibility Testing
- ✅ Supports Android 8.0+ (API 26+)
- ✅ Works on 90%+ of modern Android devices
- ✅ Compatible with major OEM skins
- ✅ Functions on various screen sizes

## Issue Reporting

### Bug Report Template
```
Issue Title: [Component] Issue Description

Environment:
- Device: [Model]
- Android Version: [Version/API Level]
- Signal Version: [Version]
- Test Scenario: [Description]

Steps to Reproduce:
1. [Step 1]
2. [Step 2]
3. [Step 3]

Expected Behavior:
[Description of expected behavior]

Actual Behavior:
[Description of actual behavior]

Additional Information:
- Logs: [Attach relevant logs]
- Screenshots: [Attach screenshots]
- Performance Metrics: [Include relevant metrics]
```

### Severity Levels
- **Critical**: App crashes, data loss, security issues
- **High**: Major functionality broken, accessibility violations
- **Medium**: Minor functionality issues, performance degradation
- **Low**: Cosmetic issues, minor improvements possible

## Continuous Integration

### Automated Test Execution
```bash
# Run all automated tests
./gradlew connectedAndroidTest

# Run performance benchmarks
./testing-tools/performance-testing/performance-benchmark.sh full-suite

# Run gesture tests
./testing-tools/gesture-testing/emulator-gesture-test.sh stress-test
```

### Test Result Aggregation
```bash
# Generate test report
./testing-tools/generate-test-report.sh

# Upload results to CI system
./testing-tools/upload-results.sh --ci-system=github
```

## Maintenance

### Weekly Tasks
- [ ] Review automated test results
- [ ] Update test device inventory
- [ ] Monitor performance trends
- [ ] Address flaky tests

### Monthly Tasks
- [ ] Update Android version compatibility
- [ ] Review and update test procedures
- [ ] Analyze test coverage gaps
- [ ] Update performance baselines

### Quarterly Tasks
- [ ] Major framework updates
- [ ] New device release testing
- [ ] Accessibility standard updates
- [ ] Security testing updates

## Troubleshooting

### Common Issues

1. **ADB Connection Issues**
   ```bash
   # Restart ADB server
   adb kill-server && adb start-server

   # Check device connection
   adb devices

   # Verify device is authorized
   adb shell echo "test"
   ```

2. **Gesture Test Failures**
   - Ensure screen is unlocked
   - Check for screen overlays
   - Verify device orientation
   - Review logcat for gesture detection

3. **Performance Test Issues**
   - Clear device storage before testing
   - Close background applications
   - Ensure stable network connection
   - Monitor device temperature

4. **Accessibility Test Problems**
   - Enable TalkBack properly
   - Check accessibility permissions
   - Restart accessibility services
   - Test on physical device vs emulator

### Debug Tools
```bash
# Monitor device logs
adb logcat | grep Accessibility

# Check app memory usage
adb shell dumpsys meminfo org.thoughtcrime.securesms

# Monitor CPU usage
adb shell dumpsys cpuinfo | grep securesms

# Check accessibility status
adb shell settings get secure enabled_accessibility_services
```

## Integration with Development

### Pre-Release Testing
1. Run full test suite on primary devices
2. Execute cross-device compatibility tests
3. Perform accessibility audit with TalkBack
4. Validate performance meets requirements
5. Document any regressions or issues

### Release Validation
1. Test on final release builds
2. Validate on latest Android versions
3. Confirm accessibility compliance
4. Verify performance baselines
5. Update compatibility documentation

---

## Contact and Support

For questions about testing procedures or issues with the testing framework:

- **Testing Framework Issues**: Create an issue in the project repository
- **Test Failures**: Document in test results with detailed reproduction steps
- **Performance Regressions**: Include before/after metrics and test conditions
- **Accessibility Issues**: Reference specific WCAG guidelines and TalkBack versions
