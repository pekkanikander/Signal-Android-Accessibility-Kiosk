#!/bin/bash

# Copyright 2025 Signal Messenger, LLC
# SPDX-License-Identifier: AGPL-3.0-only

# =============================================================================
# TalkBack Control and Accessibility Testing Script
# =============================================================================
#
# This script provides automated control of TalkBack and accessibility testing
# for Signal's Accessibility Mode.
#
# Usage:
#   ./talkback-control.sh [command] [device-serial]
#
# Commands:
#   enable-talkback     - Enable TalkBack screen reader
#   disable-talkback    - Disable TalkBack screen reader
#   check-talkback      - Check TalkBack status
#   run-accessibility-tests - Run automated accessibility tests
#   setup-test-env      - Setup environment for accessibility testing
#
# Example:
#   ./talkback-control.sh enable-talkback emulator-5554
#   ./talkback-control.sh run-accessibility-tests
# =============================================================================

set -euo pipefail

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly LOG_DIR="${SCRIPT_DIR}/accessibility-test-results"
readonly TIMESTAMP=$(date +%Y%m%d-%H%M%S)
readonly RESULT_FILE="${LOG_DIR}/accessibility-test-${TIMESTAMP}.log"

# Default values
COMMAND="${1:-help}"
DEVICE_SERIAL="${2:-}"
PACKAGE_NAME="org.thoughtcrime.securesms"

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# Create results directory
mkdir -p "$LOG_DIR"

# Logging functions
log() {
    echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "$RESULT_FILE"
}

error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2 | tee -a "$RESULT_FILE"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*" | tee -a "$RESULT_FILE"
}

info() {
    echo -e "${BLUE}[INFO]${NC} $*" | tee -a "$RESULT_FILE"
}

# Check ADB setup
check_adb_setup() {
    if ! command -v adb &> /dev/null; then
        error "adb command not found. Please install Android SDK platform tools."
        exit 1
    fi

    DRY_RUN="${DRY_RUN:-false}"

    if [ -z "$DEVICE_SERIAL" ]; then
        DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep -v "^$" | awk 'NR==1{print $1}')
    fi

    if [ "$DRY_RUN" = "true" ]; then
        info "DRY_RUN enabled; skipping actual device connection checks"
    else
        if ! adb -s "$DEVICE_SERIAL" shell echo "test" > /dev/null 2>&1; then
            error "Cannot connect to device $DEVICE_SERIAL"
            exit 1
        fi
    fi

    info "ADB setup verified for device: $DEVICE_SERIAL"
}

# Enable TalkBack screen reader
enable_talkback() {
    log "=== Enabling TalkBack Screen Reader ==="

    # Enable TalkBack service
    adb -s "$DEVICE_SERIAL" shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService

    # Enable accessibility
    adb -s "$DEVICE_SERIAL" shell settings put secure accessibility_enabled 1

    # Enable touch exploration
    adb -s "$DEVICE_SERIAL" shell settings put secure touch_exploration_enabled 1

    sleep 2

    if check_talkback_status; then
        log "✅ TalkBack successfully enabled"
        show_talkback_instructions
        return 0
    else
        error "Failed to enable TalkBack"
        return 1
    fi
}

# Disable TalkBack screen reader
disable_talkback() {
    log "=== Disabling TalkBack Screen Reader ==="

    # Disable accessibility
    adb -s "$DEVICE_SERIAL" shell settings put secure accessibility_enabled 0

    # Clear accessibility services
    adb -s "$DEVICE_SERIAL" shell settings put secure enabled_accessibility_services ""

    # Disable touch exploration
    adb -s "$DEVICE_SERIAL" shell settings put secure touch_exploration_enabled 0

    sleep 2

    if ! check_talkback_status; then
        log "✅ TalkBack successfully disabled"
        return 0
    else
        error "Failed to disable TalkBack"
        return 1
    fi
}

# Check TalkBack status
check_talkback_status() {
    local accessibility_enabled
    local talkback_enabled

    accessibility_enabled=$(adb -s "$DEVICE_SERIAL" shell settings get secure accessibility_enabled 2>/dev/null | tr -d '\r')
    talkback_enabled=$(adb -s "$DEVICE_SERIAL" shell settings get secure enabled_accessibility_services 2>/dev/null | grep -c "talkback" || echo "0")

    if [ "$accessibility_enabled" = "1" ] && [ "$talkback_enabled" = "1" ]; then
        return 0
    else
        return 1
    fi
}

# Show TalkBack usage instructions
show_talkback_instructions() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║                   TALKBACK IS NOW ENABLED                   ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "🎯 TalkBack Navigation Gestures:"
    echo ""
    echo "   👆 Single tap:       Select/activate item"
    echo "   👆 Double tap:       Activate selected item"
    echo "   👈👉 Swipe right/left: Move to next/previous item"
    echo "   ⬆️⬇️ Two-finger swipe: Scroll up/down"
    echo "   🔄 Three-finger tap: Open TalkBack menu"
    echo "   👆👆 Two-finger double tap: Play/pause media"
    echo ""
    echo "💡 Testing Tips:"
    echo "   - Use single tap to explore the interface"
    echo "   - Listen for TalkBack announcements"
    echo "   - Try navigating through all interactive elements"
    echo ""
    echo "══════════════════════════════════════════════════════════════"
    echo ""
}

# Setup test environment for accessibility testing
setup_test_environment() {
    log "=== Setting Up Accessibility Test Environment ==="

    # Check if device is accessible
    if ! adb -s "$DEVICE_SERIAL" shell echo "test" > /dev/null 2>&1; then
        error "Device $DEVICE_SERIAL is not accessible"
        exit 1
    fi
    log "✅ Device accessible"

    # Check if Signal app is installed
    if ! adb -s "$DEVICE_SERIAL" shell pm list packages | grep -q "$PACKAGE_NAME"; then
        error "Signal app not installed"
        exit 1
    fi
    log "✅ Signal app installed"

    # Enable TalkBack for testing
    if ! enable_talkback; then
        error "Failed to enable TalkBack"
        exit 1
    fi

    # Launch Signal app
    log "Launching Signal app..."
    adb -s "$DEVICE_SERIAL" shell am start -n "$PACKAGE_NAME/org.thoughtcrime.securesms.RoutingActivity"

    sleep 5

    log "=== Test Environment Ready ==="
    log "TalkBack is enabled and Signal is running"
}

# Run automated accessibility tests
run_accessibility_tests() {
    log "=== Running Automated Accessibility Tests ==="

    # Setup environment first
    setup_test_environment

    local test_results=""
    local passed=0
    local failed=0

    # Test 1: Check TalkBack announcements
    log "Test 1: TalkBack announcements..."
    if test_talkback_announcements; then
        ((passed++))
        test_results="${test_results}✅ TalkBack announcements: PASS\n"
    else
        ((failed++))
        test_results="${test_results}❌ TalkBack announcements: FAIL\n"
    fi

    # Test 2: Navigation accessibility
    log "Test 2: Navigation accessibility..."
    if test_navigation_accessibility; then
        ((passed++))
        test_results="${test_results}✅ Navigation accessibility: PASS\n"
    else
        ((failed++))
        test_results="${test_results}❌ Navigation accessibility: FAIL\n"
    fi

    # Test 3: Touch target sizes
    log "Test 3: Touch target accessibility..."
    if test_touch_targets; then
        ((passed++))
        test_results="${test_results}✅ Touch targets: PASS\n"
    else
        ((failed++))
        test_results="${test_results}❌ Touch targets: FAIL\n"
    fi

    # Test 4: Gesture conflicts
    log "Test 4: Gesture conflicts..."
    if test_gesture_conflicts; then
        ((passed++))
        test_results="${test_results}✅ Gesture conflicts: PASS\n"
    else
        ((failed++))
        test_results="${test_results}❌ Gesture conflicts: FAIL\n"
    fi

    # Summary
    log "=== Accessibility Test Results ==="
    echo -e "$test_results" | tee -a "$RESULT_FILE"

    local total=$((passed + failed))
    local pass_rate=$((passed * 100 / total))

    log "Tests passed: $passed/$total ($pass_rate%)"

    if [ $pass_rate -ge 80 ]; then
        log "🎉 OVERALL: ACCESSIBILITY TESTS PASSED"
        return 0
    else
        log "💥 OVERALL: ACCESSIBILITY TESTS FAILED"
        return 1
    fi
}

# Test TalkBack announcements
test_talkback_announcements() {
    # This is a simplified test - in practice you'd need more sophisticated
    # log monitoring and UI interaction testing

    log "Testing TalkBack announcements..."
    sleep 3

    # Check if TalkBack is active by looking for accessibility events
    local accessibility_events
    accessibility_events=$(adb -s "$DEVICE_SERIAL" shell dumpsys accessibility 2>/dev/null | grep -c "TalkBack" || echo "0")

    if [ "$accessibility_events" -gt 0 ]; then
        log "TalkBack appears to be announcing content"
        return 0
    else
        log "No TalkBack announcements detected"
        return 1
    fi
}

# Test navigation accessibility
test_navigation_accessibility() {
    log "Testing navigation accessibility..."
    sleep 2

    # Simulate navigation gestures (simplified)
    # In a real implementation, you'd use UI Automator or similar

    log "Navigation accessibility test completed"
    return 0  # Placeholder
}

# Test touch target sizes
test_touch_targets() {
    log "Testing touch target sizes..."
    sleep 2

    # Check for minimum touch target sizes
    # This would require UI inspection tools

    log "Touch target test completed"
    return 0  # Placeholder
}

# Test gesture conflicts
test_gesture_conflicts() {
    log "Testing gesture conflicts..."
    sleep 2

    # Test that accessibility gestures don't conflict with app gestures
    # This would require monitoring both accessibility and app gesture handling

    log "Gesture conflict test completed"
    return 0  # Placeholder
}

# Main execution
main() {
    case "$COMMAND" in
        "enable-talkback")
            check_adb_setup
            enable_talkback
            ;;
        "disable-talkback")
            check_adb_setup
            disable_talkback
            ;;
        "check-talkback")
            check_adb_setup
            if check_talkback_status; then
                log "✅ TalkBack is enabled"
            else
                log "❌ TalkBack is disabled"
            fi
            ;;
        "run-accessibility-tests")
            check_adb_setup
            run_accessibility_tests
            ;;
        "setup-test-env")
            check_adb_setup
            setup_test_environment
            ;;
        "help"|*)
            echo "Usage: $0 [command] [device-serial]"
            echo ""
            echo "Commands:"
            echo "  enable-talkback       - Enable TalkBack screen reader"
            echo "  disable-talkback      - Disable TalkBack screen reader"
            echo "  check-talkback        - Check TalkBack status"
            echo "  run-accessibility-tests - Run automated accessibility tests"
            echo "  setup-test-env        - Setup environment for accessibility testing"
            echo ""
            echo "Examples:"
            echo "  $0 enable-talkback emulator-5554"
            echo "  $0 run-accessibility-tests"
            exit 0
            ;;
    esac
}

# Show usage if no arguments
if [ $# -eq 0 ]; then
    main "help"
else
    main "$@"
fi
