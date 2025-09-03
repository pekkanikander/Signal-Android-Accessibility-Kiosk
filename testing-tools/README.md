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
│   ├── talkback-testing-procedures.md  # Comprehensive TalkBack test procedures
│   └── accessibility-test-results/    # Accessibility test results
├── performance-testing/       # Performance benchmarking tools
│   ├── performance-benchmark.sh      # Automated performance benchmarks
│   └── benchmark-results/            # Performance test results
├── cross-device-testing/      # Cross-device compatibility testing
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

### 2. Basic Gesture Testing
```bash
# Test triple tap gesture on default emulator
./testing-tools/gesture-testing/emulator-gesture-test.sh triple-tap-debug

# Test all gestures with stress testing
./testing-tools/gesture-testing/emulator-gesture-test.sh stress-test emulator-5554 10
```

### 3. Performance Benchmarking
```bash
# Run full performance benchmark suite
./testing-tools/performance-testing/performance-benchmark.sh full-suite emulator-5554

# Test just startup time
./testing-tools/performance-testing/performance-benchmark.sh startup-time emulator-5554
```

### 4. Accessibility Testing
Follow the procedures in `testing-tools/accessibility-testing/talkback-testing-procedures.md`

### 5. Cross-Device Testing
Follow the matrix in `testing-tools/cross-device-testing/cross-device-test-matrix.md`

## Test Categories

### 1. Gesture Testing
**Purpose**: Validate gesture detection works reliably
- **Tools**: `emulator-gesture-test.sh`
- **Coverage**: All 4 gesture types (triple tap, opposite corners, two-finger header, single-finger edge)
- **Results**: Logged to `gesture-test-results/`

### 2. Accessibility Testing
**Purpose**: Ensure TalkBack and screen reader compatibility
- **Procedures**: `talkback-testing-procedures.md`
- **Coverage**: Navigation, announcements, touch targets, gesture conflicts
- **Results**: Documented in `accessibility-test-results/`

### 3. Performance Testing
**Purpose**: Validate performance meets requirements
- **Tools**: `performance-benchmark.sh`
- **Metrics**: Startup time, memory usage, CPU usage, gesture latency
- **Results**: JSON format in `benchmark-results/`

### 4. Cross-Device Testing
**Purpose**: Ensure compatibility across Android ecosystem
- **Matrix**: `cross-device-test-matrix.md`
- **Coverage**: Android 8.0-14, various device types, OEM skins
- **Results**: Documented in `device-test-results/`

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
