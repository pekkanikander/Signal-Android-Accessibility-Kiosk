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

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly LOG_FILE="${SCRIPT_DIR}/gesture-test-$(date +%Y%m%d-%H%M%S).log"

# Default values
GESTURE_TYPE="${1:-triple-tap-debug}"
DEVICE_SERIAL="${2:-}"
EMULATOR_WAIT_TIME=2

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
    devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | wc -l)

    if [ "$devices" -eq 0 ]; then
        error "No Android devices/emulators found. Please start an emulator or connect a device."
        exit 1
    fi

    # If no specific device requested, use the first available
    if [ -z "$DEVICE_SERIAL" ]; then
        DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep -v "^$" | head -1 | cut -f1)
        info "Using device: $DEVICE_SERIAL"
    fi

    # Verify device is accessible
    if ! adb -s "$DEVICE_SERIAL" shell echo "test" &> /dev/null; then
        error "Cannot connect to device $DEVICE_SERIAL"
        exit 1
    fi

    log "ADB setup verified for device: $DEVICE_SERIAL"
}

# Get screen dimensions and calculate gesture coordinates
get_screen_info() {
    local screen_size
    screen_size=$(adb -s "$DEVICE_SERIAL" shell wm size | grep -o '[0-9]*x[0-9]*')
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

    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$start_x" "$start_y" "$end_x" "$end_y" "$duration"
    sleep 0.5
}

# Execute ADB tap command
execute_tap() {
    local x="$1" y="$2"
    log "Executing tap: ($x,$y)"

    adb -s "$DEVICE_SERIAL" shell input touchscreen tap "$x" "$y"
    sleep 0.2
}

# Triple tap debug gesture (easier for testing)
gesture_triple_tap_debug() {
    log "Testing TRIPLE TAP DEBUG gesture"

    # Three quick taps in center of screen
    for i in {1..3}; do
        execute_tap "$CENTER_X" "$CENTER_Y"
        sleep 0.1
    done

    log "Triple tap gesture completed"
}

# Opposite corners hold gesture (strict production gesture)
gesture_opposite_corners() {
    log "Testing OPPOSITE CORNERS HOLD gesture"

    # Press and hold top-left corner
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$TOP_LEFT_X" "$TOP_LEFT_Y" "$TOP_LEFT_X" "$TOP_LEFT_Y" 2000 &

    # Press and hold bottom-right corner (with slight delay)
    sleep 0.1
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$BOTTOM_RIGHT_X" "$BOTTOM_RIGHT_Y" "$BOTTOM_RIGHT_X" "$BOTTOM_RIGHT_Y" 2000 &

    # Wait for gesture to complete
    wait
    log "Opposite corners gesture completed"
}

# Two-finger header hold gesture
gesture_two_finger_header() {
    log "Testing TWO-FINGER HEADER HOLD gesture"

    # First finger on left side of header
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$HEADER_LEFT_X" "$HEADER_Y" "$HEADER_LEFT_X" "$HEADER_Y" 2000 &

    # Second finger on right side of header (with slight delay)
    sleep 0.1
    adb -s "$DEVICE_SERIAL" shell input touchscreen swipe "$HEADER_RIGHT_X" "$HEADER_Y" "$HEADER_RIGHT_X" "$HEADER_Y" 2000 &

    # Wait for gesture to complete
    wait
    log "Two-finger header gesture completed"
}

# Single-finger edge drag hold gesture
gesture_single_finger_edge() {
    log "Testing SINGLE-FINGER EDGE DRAG HOLD gesture"

    # Press on left edge and drag to right edge while holding
    execute_swipe "$EDGE_START_X" "$EDGE_START_Y" "$EDGE_END_X" "$EDGE_END_Y" 2000

    log "Single-finger edge drag gesture completed"
}

# Stress test - run all gestures repeatedly
gesture_stress_test() {
    local iterations="${1:-5}"
    log "Starting STRESS TEST with $iterations iterations"

    for i in $(seq 1 "$iterations"); do
        log "=== Iteration $i of $iterations ==="

        sleep 2
        gesture_triple_tap_debug

        sleep 3
        gesture_opposite_corners

        sleep 3
        gesture_two_finger_header

        sleep 3
        gesture_single_finger_edge

        log "Iteration $i completed"
    done

    log "Stress test completed"
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

# Main execution
main() {
    log "=== Accessibility Mode Gesture Testing Started ==="
    log "Gesture Type: $GESTURE_TYPE"
    log "Device: $DEVICE_SERIAL"

    check_adb_setup
    get_screen_info
    wait_for_app

    # Execute requested gesture
    case "$GESTURE_TYPE" in
        "triple-tap-debug")
            gesture_triple_tap_debug
            ;;
        "opposite-corners")
            gesture_opposite_corners
            ;;
        "two-finger-header")
            gesture_two_finger_header
            ;;
        "single-finger-edge")
            gesture_single_finger_edge
            ;;
        "stress-test")
            gesture_stress_test "${3:-5}"  # Default 5 iterations
            ;;
        *)
            error "Unknown gesture type: $GESTURE_TYPE"
            echo "Available types: triple-tap-debug, opposite-corners, two-finger-header, single-finger-edge, stress-test"
            exit 1
            ;;
    esac

    log "=== Gesture testing completed ==="
    log "Log saved to: $LOG_FILE"
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
