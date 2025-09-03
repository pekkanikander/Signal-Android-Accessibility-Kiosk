#!/bin/bash

# Copyright 2025 Signal Messenger, LLC
# SPDX-License-Identifier: AGPL-3.0-only

# =============================================================================
# Accessibility Mode Gesture Testing Script for Android Emulator
# =============================================================================
#
# This script provides reliable gesture testing for Accessibility Mode
# in Android Studio Emulator. It handles multi-touch gestures that are
# difficult to reproduce manually.
#
# Usage:
#   ./emulator-gesture-test.sh [gesture-type] [device-serial]
#
# Gesture Types:
#   triple-tap-debug    - Triple tap for debug gesture testing
#   opposite-corners    - Opposite corners hold gesture
#   two-finger-header   - Two-finger header hold gesture
#   single-finger-edge  - Single-finger edge drag hold gesture
#   stress-test         - Run all gestures repeatedly
#
# Example:
#   ./emulator-gesture-test.sh triple-tap-debug emulator-5554
#   ./emulator-gesture-test.sh stress-test
# =============================================================================

set -euo pipefail

# Script directory and logs (define early so DRY_RUN and result paths work)
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly LOG_FILE="${SCRIPT_DIR}/gesture-test-$(date +%Y%m%d-%H%M%S).log"

# ----- Safety / run flags -----
# If DRY_RUN is set to "true" the script will only print intended adb commands
DRY_RUN="${DRY_RUN:-false}"
# Optional package override (3rd CLI arg or env var)
PACKAGE_NAME="${3:-${PACKAGE_NAME:-}}"
# JSON results output
RESULT_JSON="${SCRIPT_DIR}/gesture-test-result-$(date +%Y%m%d-%H%M%S).json"

# Small wrapper for adb that respects DRY_RUN and device selection
adb_exec() {
    if [ "$DRY_RUN" = "true" ]; then
        echo "[DRY_RUN] adb -s $DEVICE_SERIAL $*" | tee -a "$LOG_FILE"
    else
        adb -s "$DEVICE_SERIAL" $*
    fi
}

# Helper to write a minimal JSON result
write_result_json() {
    local overall="$1"
    cat > "$RESULT_JSON" <<-JSON
{
  "timestamp": "$(date --iso-8601=seconds 2>/dev/null || date)",
  "device": "$DEVICE_SERIAL",
  "gesture": "$GESTURE_TYPE",
  "result": "$overall",
  "log": "${LOG_FILE}"
}
JSON
    log "Result JSON written to: $RESULT_JSON"
}

# Default values
# GESTURE_TYPE, DEVICE_SERIAL, PACKAGE_NAME, optional SET_GESTURE
GESTURE_TYPE="${1:-triple-tap-debug}"
DEVICE_SERIAL="${2:-}"
EMULATOR_WAIT_TIME=2
# Default to Production package unless overridden
PACKAGE_NAME="${3:-org.thoughtcrime.securesms}"
# Optional: set accessibility exit gesture (best-effort; may fail on prod builds)
SET_GESTURE="${4:-}"
# Ensure-only mode: prod or staging (defaults to prod)
ENSURE_ONLY="${ENSURE_ONLY:-prod}"
# If true, uninstall other Signal variants (use with care)
UNINSTALL_OTHER="${UNINSTALL_OTHER:-false}"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Logging functions
log() {
    echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2 | tee -a "$LOG_FILE"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*" | tee -a "$LOG_FILE"
}

info() {
    echo -e "${BLUE}[INFO]${NC} $*" | tee -a "$LOG_FILE"
}

# Check if adb is available and device is connected
check_adb_setup() {
    if ! command -v adb &> /dev/null; then
        error "adb command not found. Please install Android SDK platform tools."
        exit 1
    fi

    # Find connected devices
    local devices
    devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | awk '{print $1" "$2}' | grep -v "^$" | wc -l)

    if [ "$devices" -eq 0 ]; then
        error "No Android devices/emulators found. Please start an emulator or connect a device."
        exit 1
    fi

    # If no specific device requested, use the first available
    if [ -z "$DEVICE_SERIAL" ]; then
        DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep -v "^$" | awk 'NR==1{print $1}')
        info "Using device: $DEVICE_SERIAL"
    fi

    # Verify device is accessible
    if ! adb_exec shell echo "test" &> /dev/null; then
        error "Cannot connect to device $DEVICE_SERIAL"
        exit 1
    fi

    log "ADB setup verified for device: $DEVICE_SERIAL"
}

# Get screen dimensions and calculate gesture coordinates
get_screen_info() {
    local screen_size
    screen_size=$(adb_exec shell wm size | grep -o '[0-9]*x[0-9]*')
    readonly SCREEN_WIDTH=$(echo "$screen_size" | cut -dx -f1)
    readonly SCREEN_HEIGHT=$(echo "$screen_size" | cut -dx -f2)

    info "Screen size: ${SCREEN_WIDTH}x${SCREEN_HEIGHT}"

    # Calculate gesture coordinates based on screen size
    readonly CENTER_X=$((SCREEN_WIDTH / 2))
    readonly CENTER_Y=$((SCREEN_HEIGHT / 2))
    readonly CORNER_OFFSET=100
    readonly EDGE_OFFSET=50

    # Corner positions for opposite corners gesture
    readonly TOP_LEFT_X=$CORNER_OFFSET
    readonly TOP_LEFT_Y=$CORNER_OFFSET
    readonly BOTTOM_RIGHT_X=$((SCREEN_WIDTH - CORNER_OFFSET))
    readonly BOTTOM_RIGHT_Y=$((SCREEN_HEIGHT - CORNER_OFFSET))

    # Header area for two-finger header gesture
    readonly HEADER_Y=150
    readonly HEADER_LEFT_X=$((SCREEN_WIDTH / 4))
    readonly HEADER_RIGHT_X=$((SCREEN_WIDTH * 3 / 4))

    # Edge positions for single-finger edge drag
    readonly EDGE_START_X=$EDGE_OFFSET
    readonly EDGE_START_Y=$CENTER_Y
    readonly EDGE_END_X=$((SCREEN_WIDTH - EDGE_OFFSET))
    readonly EDGE_END_Y=$CENTER_Y
}

# Execute ADB swipe command with timing
execute_swipe() {
    local start_x="$1" start_y="$2" end_x="$3" end_y="$4" duration="${5:-300}"
    log "Executing swipe: ($start_x,$start_y) -> ($end_x,$end_y) [${duration}ms]"

    adb_exec shell input touchscreen swipe "$start_x" "$start_y" "$end_x" "$end_y" "$duration"
    sleep 0.5
}

# Execute ADB tap command
execute_tap() {
    local x="$1" y="$2"
    log "Executing tap: ($x,$y)"

    adb_exec shell input touchscreen tap "$x" "$y"
    sleep 0.2
}

# Monitor logcat for gesture detection
verify_gesture_detection() {
    local gesture_name="$1"
    local timeout="${2:-5}"  # Default 5 second timeout
    local log_pattern="${3:-"Gesture.*detected\|Exit.*gesture\|AccessibilityModeExitToSettingsGestureDetector"}"

    log "Verifying $gesture_name gesture detection..."

    # Start logcat monitoring in background
    local logcat_pid=""
    local temp_log="${SCRIPT_DIR}/temp_logcat_$$.log"
    if [ "$DRY_RUN" = "true" ]; then
        echo "[DRY_RUN] start logcat to $temp_log"
    else
        adb -s "$DEVICE_SERIAL" logcat -v time -T "$(date '+%m-%d %H:%M:%S.000')" > "$temp_log" 2>/dev/null &
        logcat_pid=$!
    fi
    logcat_pid=$!

    # Wait for gesture detection or timeout
    local count=0
    local detected=false

    while [ $count -lt $timeout ]; do
        if grep -q "$log_pattern" "$temp_log" 2>/dev/null; then
            detected=true
            break
        fi
        sleep 0.5
        ((count++))
    done

    # Clean up logcat process
    if [ -n "$logcat_pid" ]; then
        kill "$logcat_pid" 2>/dev/null || true
        wait "$logcat_pid" 2>/dev/null || true
    fi

    # Extract relevant log lines
    local gesture_logs=""
    if [ -f "$temp_log" ]; then
        gesture_logs=$(grep -E "$log_pattern" "$temp_log" 2>/dev/null || echo "")
        rm -f "$temp_log"
    fi

    if [ "$detected" = true ]; then
        log "✅ $gesture_name gesture DETECTED"
        if [ -n "$gesture_logs" ]; then
            log "Detection logs: $gesture_logs"
        fi
        return 0
    else
        log "❌ $gesture_name gesture NOT DETECTED within ${timeout}s"
        if [ -n "$gesture_logs" ]; then
            log "Related logs: $gesture_logs"
        fi
        return 1
    fi
}

# Check if app state changed (e.g., exited accessibility mode)
verify_app_state_change() {
    local expected_change="$1"
    local timeout="${2:-3}"

    log "Checking for app state change: $expected_change"

    local initial_state=""
    local final_state=""

    # Get initial state
    case "$expected_change" in
        "exit_accessibility_mode")
            initial_state=$(adb -s "$DEVICE_SERIAL" shell dumpsys activity activities | grep -c "AccessibilityModeActivity" || echo "0")
            ;;
        "enter_accessibility_mode")
            initial_state=$(adb -s "$DEVICE_SERIAL" shell dumpsys activity activities | grep -c "MainActivity" || echo "0")
            ;;
    esac

    sleep "$timeout"

    # Get final state
    case "$expected_change" in
        "exit_accessibility_mode")
            final_state=$(adb -s "$DEVICE_SERIAL" shell dumpsys activity activities | grep -c "AccessibilityModeActivity" || echo "0")
            ;;
        "enter_accessibility_mode")
            final_state=$(adb -s "$DEVICE_SERIAL" shell dumpsys activity activities | grep -c "MainActivity" || echo "0")
            ;;
    esac

    if [ "$expected_change" = "exit_accessibility_mode" ] && [ "$final_state" -lt "$initial_state" ]; then
        log "✅ App state changed: Accessibility Mode exited"
        return 0
    elif [ "$expected_change" = "enter_accessibility_mode" ] && [ "$final_state" -lt "$initial_state" ]; then
        log "✅ App state changed: Accessibility Mode entered"
        return 0
    else
        log "❌ App state did not change as expected"
        log "Initial state: $initial_state, Final state: $final_state"
        return 1
    fi
}

# Triple tap debug gesture (easier for testing)
gesture_triple_tap_debug() {
    log "Testing TRIPLE TAP DEBUG gesture"

    # Start logcat monitoring before gesture (longer timeout to handle slow systems)
    verify_gesture_detection "Triple Tap Debug" 20 "Triple.*tap.*completed\|Gesture.*detected" &
    local verify_pid=$!

    # Three quick taps in center of screen
    for i in {1..3}; do
        execute_tap "$CENTER_X" "$CENTER_Y"
        sleep 0.1
    done

    # Give system a short moment to process taps
    sleep 1

    # Wait for verification to complete
    wait "$verify_pid"
    local verify_result=$?

    if [ $verify_result -eq 0 ]; then
        log "✅ Triple tap gesture VERIFIED"
        return 0
    else
        log "❌ Triple tap gesture FAILED verification"
        return 1
    fi
}

# Opposite corners hold gesture (strict production gesture)
gesture_opposite_corners() {
    log "Testing OPPOSITE CORNERS HOLD gesture"

    # Start verification before gesture
    verify_gesture_detection "Opposite Corners" 8 "Opposite.*corners.*hold\|Corner.*gesture.*detected" &
    local verify_pid=$!

    # Press and hold top-left corner
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$TOP_LEFT_X" "$TOP_LEFT_Y" "$TOP_LEFT_X" "$TOP_LEFT_Y" 2000 &

    # Press and hold bottom-right corner (with slight delay)
    sleep 0.1
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$BOTTOM_RIGHT_X" "$BOTTOM_RIGHT_Y" "$BOTTOM_RIGHT_X" "$BOTTOM_RIGHT_Y" 2000 &

    # Wait for gesture to complete
    wait

    # Wait for verification to complete
    wait "$verify_pid"
    local verify_result=$?

    if [ $verify_result -eq 0 ]; then
        log "✅ Opposite corners gesture VERIFIED"
        return 0
    else
        log "❌ Opposite corners gesture FAILED verification"
        return 1
    fi
}

# Two-finger header hold gesture
gesture_two_finger_header() {
    log "Testing TWO-FINGER HEADER HOLD gesture"

    # Start verification
    verify_gesture_detection "Two-Finger Header" 8 "Two.*finger.*header\|Header.*gesture.*detected" &
    local verify_pid=$!

    # First finger on left side of header
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$HEADER_LEFT_X" "$HEADER_Y" "$HEADER_LEFT_X" "$HEADER_Y" 2000 &

    # Second finger on right side of header (with slight delay)
    sleep 0.1
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$HEADER_RIGHT_X" "$HEADER_Y" "$HEADER_RIGHT_X" "$HEADER_Y" 2000 &

    # Wait for gesture to complete
    wait

    # Wait for verification
    wait "$verify_pid"
    local verify_result=$?

    if [ $verify_result -eq 0 ]; then
        log "✅ Two-finger header gesture VERIFIED"
        return 0
    else
        log "❌ Two-finger header gesture FAILED verification"
        return 1
    fi
}

# Single-finger edge drag hold gesture
gesture_single_finger_edge() {
    log "Testing SINGLE-FINGER EDGE DRAG HOLD gesture"

    # Start verification
    verify_gesture_detection "Single-Finger Edge" 8 "Single.*finger.*edge\|Edge.*gesture.*detected" &
    local verify_pid=$!

    # Press on left edge and drag to right edge while holding
    execute_swipe "$EDGE_START_X" "$EDGE_START_Y" "$EDGE_END_X" "$EDGE_END_Y" 2000

    # Wait for verification
    wait "$verify_pid"
    local verify_result=$?

    if [ $verify_result -eq 0 ]; then
        log "✅ Single-finger edge gesture VERIFIED"
        return 0
    else
        log "❌ Single-finger edge gesture FAILED verification"
        return 1
    fi
}

# Stress test - run all gestures repeatedly
gesture_stress_test() {
    local iterations="${1:-5}"
    local success_count=0
    local total_tests=0

    log "Starting STRESS TEST with $iterations iterations per gesture"

    for i in $(seq 1 "$iterations"); do
        log "=== Iteration $i of $iterations ==="

        # Test triple tap
        ((total_tests++))
        if gesture_triple_tap_debug; then
            ((success_count++))
        fi

        sleep 3

        # Test opposite corners
        ((total_tests++))
        if gesture_opposite_corners; then
            ((success_count++))
        fi

        sleep 3

        # Test two-finger header
        ((total_tests++))
        if gesture_two_finger_header; then
            ((success_count++))
        fi

        sleep 3

        # Test single-finger edge
        ((total_tests++))
        if gesture_single_finger_edge; then
            ((success_count++))
        fi

        sleep 3

        local current_rate=$((success_count * 100 / total_tests))
        log "Iteration $i completed - Success rate: $current_rate% ($success_count/$total_tests)"
    done

    local final_rate=$((success_count * 100 / total_tests))
    log "=== Stress test completed ==="
    log "Final Results: $success_count/$total_tests successful ($final_rate% success rate)"

    if [ $final_rate -ge 90 ]; then
        log "✅ STRESS TEST PASSED (≥90% success rate)"
        return 0
    else
        log "❌ STRESS TEST FAILED (<90% success rate)"
        return 1
    fi
}

# Wait for app to be ready
wait_for_app() {
    local package_name="${1:-org.thoughtcrime.securesms}"
    log "Waiting for app $package_name to be ready..."

    # Wait for app process to start
    local timeout=30
    local count=0

    while [ $count -lt $timeout ]; do
        if adb -s "$DEVICE_SERIAL" shell pidof "$package_name" > /dev/null 2>&1; then
            log "App $package_name is ready"
            return 0
        fi
        sleep 1
        ((count++))
    done

    warning "App $package_name not detected within $timeout seconds. Continuing anyway."
}

# Verify device and app state before testing
verify_test_environment() {
    log "=== Verifying Test Environment ==="

    # Check if device is accessible
    if ! adb -s "$DEVICE_SERIAL" shell echo "test" > /dev/null 2>&1; then
        error "Device $DEVICE_SERIAL is not accessible"
        echo ""
        echo "🔧 TROUBLESHOOTING STEPS:"
        echo "1. Ensure device/emulator is running:"
        echo "   $ adb devices"
        echo ""
        echo "2. For physical device:"
        echo "   - Enable Developer Options"
        echo "   - Enable USB Debugging"
        echo "   - Authorize computer for debugging"
        echo ""
        echo "3. For emulator:"
        echo "   - Start Android Studio emulator"
        echo "   - Wait for emulator to fully boot"
        echo ""
        show_device_status
        exit 1
    fi
    log "✅ Device $DEVICE_SERIAL is accessible"

    # Preflight: ensure only the selected package is installed if requested
    preflight_package_check

    # Check if Signal app is installed
    if ! adb_exec shell pm list packages | grep -q "$PACKAGE_NAME"; then
        error "Signal app is not installed on device"
        echo ""
        echo "📦 INSTALLATION REQUIRED:"
        echo ""
        echo "1. Build the Signal app:"
        echo "   $ ./gradlew :Signal-Android:assemblePlayProdDebug"
        echo ""
        echo "2. Install to device:"
        echo "   $ ./gradlew :Signal-Android:installPlayProdDebug"
        echo ""
        echo "3. Or install APK manually:"
        echo "   $ adb -s $DEVICE_SERIAL install app/build/outputs/apk/playProd/debug/app-play-prod-debug.apk"
        echo ""
        echo "4. Then re-run this test"
        echo ""
        exit 1
    fi
    log "✅ Signal app is installed"

    # Check if Accessibility Mode is enabled in settings
    if ! is_accessibility_mode_enabled; then
        log "⚠️  Accessibility Mode is not enabled"
        if ! setup_accessibility_mode; then
            error "Failed to setup Accessibility Mode"
            echo "Please manually enable Accessibility Mode in Signal settings:"
            echo "1. Open Signal app"
            echo "2. Go to Settings → Accessibility → Accessibility Mode"
            echo "3. Select a conversation"
            echo "4. Enable Accessibility Mode"
            exit 1
        fi
    fi
    log "✅ Accessibility Mode is enabled"

    # Ensure we're in Accessibility Mode Activity
    if ! is_in_accessibility_mode_activity; then
        log "⚠️  Not currently in Accessibility Mode"
        if ! enter_accessibility_mode; then
            error "Failed to enter Accessibility Mode"
            echo "Please manually navigate to Accessibility Mode"
            exit 1
        fi
    fi
    log "✅ Currently in Accessibility Mode Activity"

    log "=== Test Environment Ready ==="
}

# Look for multiple Signal variants and optionally uninstall others
preflight_package_check() {
    log "Checking for multiple Signal variants on device..."
    local pkgs
    pkgs=$(adb_exec shell pm list packages | grep "org.thoughtcrime.securesms" || true)
    local count
    count=$(echo "$pkgs" | wc -l | tr -d ' ')

    if [ "$count" -gt 1 ]; then
        warning "Multiple Signal packages detected:\n$pkgs"
        if [ "$UNINSTALL_OTHER" = "true" ]; then
            log "Uninstalling other variants except $PACKAGE_NAME"
            while read -r line; do
                pkg=$(echo "$line" | cut -d: -f2)
                if [ "$pkg" != "$PACKAGE_NAME" ]; then
                    if [ "$DRY_RUN" = "true" ]; then
                        echo "[DRY_RUN] adb -s $DEVICE_SERIAL uninstall $pkg" | tee -a "$LOG_FILE"
                    else
                        adb -s "$DEVICE_SERIAL" uninstall "$pkg" || warning "Failed to uninstall $pkg"
                    fi
                fi
            done <<< "$pkgs"
        else
            warning "Set UNINSTALL_OTHER=true to remove non-selected variants automatically (use with care)."
        fi
    else
        log "No conflicting Signal variants detected"
    fi
}

# Best-effort: set Accessibility Mode exit gesture via broadcast or run-as
# Note: This will only work if the app honors the debug broadcast or the build is debuggable
set_accessibility_exit_gesture() {
    local gesture_value="$1"
    log "Attempting to set Accessibility Mode exit gesture to: $gesture_value"

    # 1) Try a debug broadcast the app may listen for
    local bc_cmd="am broadcast -a org.thoughtcrime.securesms.DEBUG_SET_EXIT_GESTURE --es gesture '$gesture_value'"
    if [ "$DRY_RUN" = "true" ]; then
        echo "[DRY_RUN] adb -s $DEVICE_SERIAL shell $bc_cmd" | tee -a "$LOG_FILE"
    else
        log "Sending debug broadcast to app (best-effort)"
        adb -s "$DEVICE_SERIAL" shell $bc_cmd 2>&1 | tee -a "$LOG_FILE" || true
    fi

    # 2) If build is debuggable, try run-as to write to shared_prefs (best-effort placeholder)
    if [ "$DRY_RUN" = "true" ]; then
        echo "[DRY_RUN] run-as $PACKAGE_NAME echo 'gesture=$gesture_value' > /data/data/$PACKAGE_NAME/files/accessibility_gesture_debug" | tee -a "$LOG_FILE"
    else
        log "Attempting run-as fallback (only works on debuggable builds)"
        adb -s "$DEVICE_SERIAL" shell run-as "$PACKAGE_NAME" sh -c "echo 'gesture=$gesture_value' > /data/data/$PACKAGE_NAME/files/accessibility_gesture_debug" 2>/dev/null && log "run-as write succeeded" || warning "run-as write failed (non-debug build or permission denied)"
    fi

    log "Set gesture attempt finished (may require app restart to take effect)"
}

# Show setup instructions for enabling Accessibility Mode
show_setup_instructions() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║              ACCESSIBILITY MODE SETUP REQUIRED              ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "📱 Please follow these steps in the Signal app:"
    echo ""
    echo "   1️⃣  Open Signal app (just launched)"
    echo "   2️⃣  Tap the menu button (☰) in top-left"
    echo "   3️⃣  Tap 'Settings'"
    echo "   4️⃣  Scroll down and tap 'Accessibility'"
    echo "   5️⃣  Tap 'Accessibility Mode'"
    echo "   6️⃣  Select a conversation from the list"
    echo "   7️⃣  Enable the 'Accessibility Mode' toggle"
    echo "   8️⃣  Tap 'Start Accessibility Mode' button"
    echo ""
    echo "⏳ The test will automatically detect when Accessibility Mode is enabled"
    echo "💡 Make sure you have at least one conversation in Signal"
    echo ""
    echo "══════════════════════════════════════════════════════════════"
    echo ""
}

# Show current device status for debugging
show_device_status() {
    echo ""
    echo "📊 CURRENT DEVICE STATUS:"
    echo ""

    # Show connected devices
    echo "Connected devices:"
    adb devices | while read -r line; do
        echo "  $line"
    done
    echo ""

    # Show device properties if accessible
    if adb -s "$DEVICE_SERIAL" shell echo "test" > /dev/null 2>&1; then
        echo "Device $DEVICE_SERIAL info:"
        echo "  Model: $(adb -s "$DEVICE_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
        echo "  Android: $(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
        echo "  API: $(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
        echo "  Signal installed: $(adb -s "$DEVICE_SERIAL" shell pm list packages 2>/dev/null | grep -c "org.thoughtcrime.securesms")"
    else
        echo "  Device not accessible"
    fi
    echo ""
}

# Check if Accessibility Mode is enabled
is_accessibility_mode_enabled() {
    log "Checking if Accessibility Mode is enabled..."

    # Try to read SignalStore settings via content provider or broadcast
    # This is a simplified approach - in a real implementation you'd need to:
    # 1. Use a custom content provider to read SignalStore
    # 2. Or implement a broadcast receiver in the app
    # 3. Or use UI automation to check the settings screen

    # For now, we'll check if AccessibilityModeActivity exists in recent tasks
    local recent_tasks
    recent_tasks=$(adb -s "$DEVICE_SERIAL" shell dumpsys activity recents 2>/dev/null | grep -c "AccessibilityModeActivity" || echo "0")

    if [ "$recent_tasks" -gt 0 ]; then
        log "Found AccessibilityModeActivity in recent tasks - likely enabled"
        return 0
    fi

    # Alternative: Check if the accessibility mode settings exist
    # This requires the app to be running and accessible
    local settings_check
    settings_check=$(adb -s "$DEVICE_SERIAL" shell am broadcast -a "org.thoughtcrime.securesms.DEBUG_CHECK_ACCESSIBILITY" --ez "check_enabled" true 2>/dev/null | grep -c "result=0" || echo "1")

    if [ "$settings_check" = "0" ]; then
        log "Accessibility Mode appears to be enabled"
        return 0
    else
        log "Accessibility Mode does not appear to be enabled"
        return 1
    fi
}

# Setup Accessibility Mode if not enabled
setup_accessibility_mode() {
    log "Attempting to setup Accessibility Mode..."

    # Show clear setup instructions
    show_setup_instructions

    # Try basic setup - launch the app
    log "Launching Signal app..."
    adb -s "$DEVICE_SERIAL" shell am start -n "org.thoughtcrime.securesms/org.thoughtcrime.securesms.RoutingActivity"

    sleep 3

    # Wait for user to complete setup
    log "Please follow the setup instructions above in the app"
    log "Press Enter when you've enabled Accessibility Mode..."

    # Wait for user confirmation or timeout
    local timeout=60
    local count=0

    while [ $count -lt $timeout ]; do
        echo -n "."
        sleep 1
        ((count++))

        # Check periodically if accessibility mode is now enabled
        if is_accessibility_mode_enabled; then
            log ""
            log "✅ Accessibility Mode detected!"
            return 0
        fi
    done

    log ""
    error "Timeout waiting for Accessibility Mode setup"
    return 1
}

# Check if we're currently in Accessibility Mode Activity
is_in_accessibility_mode_activity() {
    local activities
    activities=$(adb -s "$DEVICE_SERIAL" shell dumpsys activity activities 2>/dev/null | grep -E "(mResumedActivity|mFocusedActivity)" | head -1)

    if echo "$activities" | grep -q "AccessibilityModeActivity"; then
        return 0
    else
        return 1
    fi
}

# Enter Accessibility Mode
enter_accessibility_mode() {
    log "Attempting to enter Accessibility Mode..."

    # Show instructions for entering accessibility mode
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║           ENTER ACCESSIBILITY MODE REQUIRED                ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "📱 Accessibility Mode is enabled but not active. Please:"
    echo ""
    echo "   1️⃣  If you're in Signal's main screen, tap the"
    echo "       'Start Accessibility Mode' button (if visible)"
    echo ""
    echo "   2️⃣  Or navigate to: Settings → Accessibility → Accessibility Mode"
    echo "       and tap 'Start Accessibility Mode'"
    echo ""
    echo "   3️⃣  The screen should change to show the simplified"
    echo "       conversation interface"
    echo ""
    echo "⏳ Waiting for you to enter Accessibility Mode..."
    echo ""

    # Check again after a delay
    sleep 3
    if is_in_accessibility_mode_activity; then
        log "✅ Successfully entered Accessibility Mode"
        return 0
    else
        warning "Still not in Accessibility Mode"
        warning "Please follow the instructions above"
        sleep 2
        return 1
    fi
}

# Main execution
main() {
    log "=== Accessibility Mode Gesture Testing Started ==="
    log "Gesture Type: $GESTURE_TYPE"
    log "Device: $DEVICE_SERIAL"

    # Initial setup
    check_adb_setup
    get_screen_info

    # Verify test environment and guide user if needed
    verify_test_environment

    # If requested, attempt to set the accessibility exit gesture (best-effort)
    if [ -n "$SET_GESTURE" ]; then
        set_accessibility_exit_gesture "$SET_GESTURE"
        # Small pause to let app process change
        sleep 1
    fi

    # Wait for app to be fully ready after environment setup
    wait_for_app

    local test_result=0

    # Execute requested gesture
    case "$GESTURE_TYPE" in
        "triple-tap-debug")
            if gesture_triple_tap_debug; then
                log "✅ Triple tap debug test PASSED"
            else
                log "❌ Triple tap debug test FAILED"
                test_result=1
            fi
            ;;
        "opposite-corners")
            if gesture_opposite_corners; then
                log "✅ Opposite corners test PASSED"
            else
                log "❌ Opposite corners test FAILED"
                test_result=1
            fi
            ;;
        "two-finger-header")
            if gesture_two_finger_header; then
                log "✅ Two-finger header test PASSED"
            else
                log "❌ Two-finger header test FAILED"
                test_result=1
            fi
            ;;
        "single-finger-edge")
            if gesture_single_finger_edge; then
                log "✅ Single-finger edge test PASSED"
            else
                log "❌ Single-finger edge test FAILED"
                test_result=1
            fi
            ;;
        "stress-test")
            if gesture_stress_test "${3:-5}"; then
                log "✅ Stress test PASSED"
            else
                log "❌ Stress test FAILED"
                test_result=1
            fi
            ;;
        *)
            error "Unknown gesture type: $GESTURE_TYPE"
            echo "Available types: triple-tap-debug, opposite-corners, two-finger-header, single-finger-edge, stress-test"
            exit 1
            ;;
    esac

    log "=== Gesture testing completed ==="
    log "Log saved to: $LOG_FILE"

    if [ $test_result -eq 0 ]; then
        log "🎉 OVERALL RESULT: SUCCESS"
    else
        log "💥 OVERALL RESULT: FAILURE"
    fi

    return $test_result
}

# Show usage if requested
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    echo "Usage: $0 [gesture-type] [device-serial] [iterations]"
    echo ""
    echo "Gesture Types:"
    echo "  triple-tap-debug    - Triple tap for debug gesture testing"
    echo "  opposite-corners    - Opposite corners hold gesture"
    echo "  two-finger-header   - Two-finger header hold gesture"
    echo "  single-finger-edge  - Single-finger edge drag hold gesture"
    echo "  stress-test         - Run all gestures repeatedly"
    echo ""
    echo "Examples:"
    echo "  $0 triple-tap-debug"
    echo "  $0 opposite-corners emulator-5554"
    echo "  $0 stress-test emulator-5554 10"
    exit 0
fi

# Run main function
main "$@"
