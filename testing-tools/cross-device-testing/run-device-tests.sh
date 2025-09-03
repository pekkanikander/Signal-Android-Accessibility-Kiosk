#!/bin/bash

# Copyright 2025 Signal Messenger, LLC
# SPDX-License-Identifier: AGPL-3.0-only

# =============================================================================
# Cross-Device Testing Automation Script
# =============================================================================
#
# This script automates testing of Accessibility Mode across different Android
# devices, versions, and manufacturers.
#
# Usage:
#   ./run-device-tests.sh [test-type] [device-list]
#
# Test Types:
#   compatibility      - Run basic compatibility tests
#   performance        - Run performance benchmarks
#   accessibility      - Run accessibility tests
#   full-suite         - Run all tests
#
# Device List:
#   Comma-separated list of device serials, or "all" for all connected devices
#
# Example:
#   ./run-device-tests.sh compatibility emulator-5554,pixel-device
#   ./run-device-tests.sh full-suite all
# =============================================================================

set -euo pipefail

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly RESULTS_DIR="${SCRIPT_DIR}/device-test-results"
readonly TIMESTAMP=$(date +%Y%m%d-%H%M%S)
readonly SUMMARY_FILE="${RESULTS_DIR}/cross-device-summary-${TIMESTAMP}.json"

# Default values
TEST_TYPE="${1:-compatibility}"
DEVICE_LIST="${2:-all}"
DRY_RUN="${DRY_RUN:-false}"

adb_exec() {
    if [ "$DRY_RUN" = "true" ]; then
        echo "[DRY_RUN] adb -s $1 ${@:2}"
    else
        adb -s "$1" ${@:2}
    fi
}

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

# Create results directory
mkdir -p "$RESULTS_DIR"

# Logging functions
log() {
    echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "${RESULTS_DIR}/cross-device-${TIMESTAMP}.log"
}

error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2 | tee -a "${RESULTS_DIR}/cross-device-${TIMESTAMP}.log"
}

warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*" | tee -a "${RESULTS_DIR}/cross-device-${TIMESTAMP}.log"
}

info() {
    echo -e "${BLUE}[INFO]${NC} $*" | tee -a "${RESULTS_DIR}/cross-device-${TIMESTAMP}.log"
}

# Get list of devices to test
get_device_list() {
    if [ "$DEVICE_LIST" = "all" ]; then
        # Get all connected devices
        local devices
        devices=$(adb devices | grep -v "List of devices" | grep -v "^$" | awk '{print $1}' | tr '\n' ',' | sed 's/,$//')
        echo "$devices"
    else
        echo "$DEVICE_LIST"
    fi
}

# Get device information
get_device_info() {
    local device="$1"
    local model
    local android_version
    local api_level

    model=$(adb -s "$device" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Unknown")
    android_version=$(adb -s "$device" shell getprop ro.product.version.release 2>/dev/null | tr -d '\r' || echo "Unknown")
    api_level=$(adb -s "$device" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || echo "Unknown")

    echo "{\"model\":\"$model\",\"android_version\":\"$android_version\",\"api_level\":\"$api_level\"}"
}

# Run compatibility tests on a device
run_compatibility_test() {
    local device="$1"
    local device_info="$2"

    log "=== Running Compatibility Tests on $device ==="

    local test_results="{}"
    local passed=0
    local failed=0

    # Test 1: Device connectivity
    if adb -s "$device" shell echo "test" > /dev/null 2>&1; then
        ((passed++))
        test_results=$(echo "$test_results" | jq ".connectivity = \"PASS\"")
        log "✅ Device connectivity: PASS"
    else
        ((failed++))
        test_results=$(echo "$test_results" | jq ".connectivity = \"FAIL\"")
        log "❌ Device connectivity: FAIL"
        return 1
    fi

    # Test 2: Signal app installation
    if adb -s "$device" shell pm list packages | grep -q "org.thoughtcrime.securesms"; then
        ((passed++))
        test_results=$(echo "$test_results" | jq ".app_installation = \"PASS\"")
        log "✅ Signal app installation: PASS"
    else
        ((failed++))
        test_results=$(echo "$test_results" | jq ".app_installation = \"FAIL\"")
        log "❌ Signal app installation: FAIL"
        return 1
    fi

    # Test 3: App launch capability
    if adb -s "$device" shell am start -n "org.thoughtcrime.securesms/org.thoughtcrime.securesms.RoutingActivity" > /dev/null 2>&1; then
        ((passed++))
        test_results=$(echo "$test_results" | jq ".app_launch = \"PASS\"")
        log "✅ App launch capability: PASS"
    else
        ((failed++))
        test_results=$(echo "$test_results" | jq ".app_launch = \"FAIL\"")
        log "❌ App launch capability: FAIL"
    fi

    # Test 4: Screen properties
    local screen_size
    screen_size=$(adb -s "$device" shell wm size | grep -o '[0-9]*x[0-9]*' || echo "unknown")
    if [ "$screen_size" != "unknown" ]; then
        ((passed++))
        test_results=$(echo "$test_results" | jq ".screen_size = \"$screen_size\"")
        log "✅ Screen properties: PASS ($screen_size)"
    else
        ((failed++))
        test_results=$(echo "$test_results" | jq ".screen_size = \"FAIL\"")
        log "❌ Screen properties: FAIL"
    fi

    # Summary for this device
    local total=$((passed + failed))
    local pass_rate=$((passed * 100 / total))

    test_results=$(echo "$test_results" | jq ".passed = $passed | .failed = $failed | .pass_rate = $pass_rate")

    log "Device $device: $passed/$total tests passed ($pass_rate%)"

    # Save individual device results
    echo "$test_results" > "${RESULTS_DIR}/device-${device}-${TIMESTAMP}.json"

    return 0
}

# Run performance tests on a device
run_performance_test() {
    local device="$1"
    local device_info="$2"

    log "=== Running Performance Tests on $device ==="

    # Call the performance benchmark script
    local perf_script="${SCRIPT_DIR}/../performance-testing/performance-benchmark.sh"

    if [ -f "$perf_script" ]; then
        log "Running performance benchmarks..."
        if bash "$perf_script" full-suite "$device" > "${RESULTS_DIR}/perf-${device}-${TIMESTAMP}.log" 2>&1; then
            log "✅ Performance tests completed for $device"
            return 0
        else
            log "❌ Performance tests failed for $device"
            return 1
        fi
    else
        warning "Performance benchmark script not found: $perf_script"
        return 1
    fi
}

# Run accessibility tests on a device
run_accessibility_test() {
    local device="$1"
    local device_info="$2"

    log "=== Running Accessibility Tests on $device ==="

    # Call the accessibility testing script
    local accessibility_script="${SCRIPT_DIR}/../accessibility-testing/talkback-control.sh"

    if [ -f "$accessibility_script" ]; then
        log "Running accessibility tests..."
        if bash "$accessibility_script" run-accessibility-tests "$device" > "${RESULTS_DIR}/accessibility-${device}-${TIMESTAMP}.log" 2>&1; then
            log "✅ Accessibility tests completed for $device"
            return 0
        else
            log "❌ Accessibility tests failed for $device"
            return 1
        fi
    else
        warning "Accessibility testing script not found: $accessibility_script"
        return 1
    fi
}

# Generate summary report
generate_summary_report() {
    local device_list="$1"
    local test_type="$2"

    log "=== Generating Summary Report ==="

    local summary="{}"
    local total_devices=0
    local total_passed=0
    local total_failed=0

    # Process results for each device
    IFS=',' read -ra DEVICES <<< "$device_list"
    for device in "${DEVICES[@]}"; do
        if [ -n "$device" ]; then
            ((total_devices++))

            local device_results="${RESULTS_DIR}/device-${device}-${TIMESTAMP}.json"
            if [ -f "$device_results" ]; then
                local passed
                passed=$(jq '.passed // 0' "$device_results")
                local failed
                failed=$(jq '.failed // 0' "$device_results")

                ((total_passed += passed))
                ((total_failed += failed))

                # Add device results to summary
                local device_info
                device_info=$(get_device_info "$device")
                summary=$(echo "$summary" | jq ".devices.\"$device\" = ($device_info + $(cat "$device_results"))")
            fi
        fi
    done

    # Add summary statistics
    summary=$(echo "$summary" | jq ".summary = {\"test_type\":\"$test_type\",\"total_devices\":$total_devices,\"total_passed\":$total_passed,\"total_failed\":$total_failed,\"timestamp\":\"$TIMESTAMP\"}")

    # Save summary
    echo "$summary" > "$SUMMARY_FILE"

    log "Summary report saved to: $SUMMARY_FILE"
    log "Cross-device testing completed: $total_passed passed, $total_failed failed across $total_devices devices"
}

# Main execution
main() {
    log "=== Cross-Device Testing Started ==="
    log "Test Type: $TEST_TYPE"
    log "Device List: $DEVICE_LIST"

    # Get list of devices to test
    local device_list
    device_list=$(get_device_list)

    if [ -z "$device_list" ]; then
        error "No devices found for testing"
        exit 1
    fi

    log "Testing devices: $device_list"

    # Run tests on each device
    IFS=',' read -ra DEVICES <<< "$device_list"
    for device in "${DEVICES[@]}"; do
        if [ -n "$device" ]; then
            log "Processing device: $device"

            # Get device information
            local device_info
            device_info=$(get_device_info "$device")

            # Run appropriate tests
            case "$TEST_TYPE" in
                "compatibility")
                    run_compatibility_test "$device" "$device_info"
                    ;;
                "performance")
                    run_performance_test "$device" "$device_info"
                    ;;
                "accessibility")
                    run_accessibility_test "$device" "$device_info"
                    ;;
                "full-suite")
                    run_compatibility_test "$device" "$device_info"
                    run_performance_test "$device" "$device_info"
                    run_accessibility_test "$device" "$device_info"
                    ;;
                *)
                    error "Unknown test type: $TEST_TYPE"
                    exit 1
                    ;;
            esac
        fi
    done

    # Generate summary report
    generate_summary_report "$device_list" "$TEST_TYPE"

    log "=== Cross-Device Testing Completed ==="
}

# Show usage if requested
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    echo "Usage: $0 [test-type] [device-list]"
    echo ""
    echo "Test Types:"
    echo "  compatibility      - Run basic compatibility tests"
    echo "  performance        - Run performance benchmarks"
    echo "  accessibility      - Run accessibility tests"
    echo "  full-suite         - Run all tests"
    echo ""
    echo "Device List:"
    echo "  Comma-separated list of device serials, or 'all' for all connected devices"
    echo ""
    echo "Examples:"
    echo "  $0 compatibility emulator-5554,pixel-device"
    echo "  $0 full-suite all"
    exit 0
fi

# Check if jq is available for JSON processing
if ! command -v jq &> /dev/null; then
    error "jq command not found. Please install jq for JSON processing."
    exit 1
fi

# Run main function
main "$@"
