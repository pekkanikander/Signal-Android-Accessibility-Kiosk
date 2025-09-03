#!/bin/bash

# Copyright 2025 Signal Messenger, LLC
# SPDX-License-Identifier: AGPL-3.0-only

# =============================================================================
# Accessibility Mode Performance Benchmarking Script
# =============================================================================
#
# This script measures the performance characteristics of Accessibility Mode
# including startup time, memory usage, CPU usage, and gesture response times.
#
# Usage:
#   ./performance-benchmark.sh [benchmark-type] [device-serial]
#
# Benchmark Types:
#   startup-time      - Measure time to launch accessibility mode
#   memory-usage      - Monitor memory consumption patterns
#   cpu-usage         - Monitor CPU usage during operations
#   gesture-latency   - Measure gesture detection response time
#   full-suite        - Run all benchmarks
#
# Example:
#   ./performance-benchmark.sh startup-time emulator-5554
#   ./performance-benchmark.sh full-suite
# =============================================================================

set -euo pipefail

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly LOG_DIR="${SCRIPT_DIR}/benchmark-results"
readonly TIMESTAMP=$(date +%Y%m%d-%H%M%S)
readonly RESULT_FILE="${LOG_DIR}/benchmark-${TIMESTAMP}.json"

# Default values
DRY_RUN="${DRY_RUN:-false}"
BENCHMARK_TYPE="${1:-startup-time}"
DEVICE_SERIAL="${2:-${DEVICE_SERIAL:-}}"
PACKAGE_NAME="${3:-org.thoughtcrime.securesms}"
ITERATIONS="${4:-5}"

# Simple adb wrapper respecting DRY_RUN
adb_exec() {
    if [ "$DRY_RUN" = "true" ]; then
        echo "[DRY_RUN] adb -s $DEVICE_SERIAL $*" | tee -a "${LOG_DIR}/benchmark-${TIMESTAMP}.log"
    else
        adb -s "$DEVICE_SERIAL" $*
    fi
}

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
    echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "${LOG_DIR}/benchmark-${TIMESTAMP}.log"
}

error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

info() {
    echo -e "${BLUE}[INFO]${NC} $*" | tee -a "${LOG_DIR}/benchmark-${TIMESTAMP}.log"
}

# Initialize JSON results file
init_results() {
    cat > "$RESULT_FILE" << EOF
{
  "timestamp": "$TIMESTAMP",
  "device": "$DEVICE_SERIAL",
  "package": "$PACKAGE_NAME",
  "benchmarks": {}
}
EOF
}

# Update JSON results
update_results() {
    local benchmark_name="$1"
    local result_data="$2"

    # Use jq if available, otherwise use sed for simple updates
    if command -v jq &> /dev/null; then
        jq ".benchmarks.\"$benchmark_name\" = $result_data" "$RESULT_FILE" > "${RESULT_FILE}.tmp"
        mv "${RESULT_FILE}.tmp" "$RESULT_FILE"
    else
        # Fallback: just log the results
        info "Benchmark $benchmark_name results: $result_data"
    fi
}

# Check ADB setup
check_adb_setup() {
    if ! command -v adb &> /dev/null; then
        error "adb command not found. Please install Android SDK platform tools."
        exit 1
    fi

    if [ -z "$DEVICE_SERIAL" ]; then
        DEVICE_SERIAL=$(adb devices | grep -v "List of devices" | grep -v "^$" | awk 'NR==1{print $1}')
    fi

    if ! adb_exec shell echo "test" &> /dev/null; then
        error "Cannot connect to device $DEVICE_SERIAL"
        exit 1
    fi

    info "ADB setup verified for device: $DEVICE_SERIAL"
}

# Get device info
get_device_info() {
    local device_model
    device_model=$(adb -s "$DEVICE_SERIAL" shell getprop ro.product.model | tr -d '\r')
    local android_version
    android_version=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.release | tr -d '\r')
    local api_level
    api_level=$(adb -s "$DEVICE_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')

    info "Device: $device_model"
    info "Android Version: $android_version (API $api_level)"
}

# Benchmark startup time
benchmark_startup_time() {
    log "=== Benchmarking Startup Time ==="

    local total_time=0
    local results=()

    for i in $(seq 1 "$ITERATIONS"); do
        info "Iteration $i of $ITERATIONS"

        # Kill app if running
        adb -s "$DEVICE_SERIAL" shell am force-stop "$PACKAGE_NAME"

        # Clear app data for clean start
        adb -s "$DEVICE_SERIAL" shell pm clear "$PACKAGE_NAME"

        # Start timing
        local start_time
        start_time=$(date +%s%3N)  # milliseconds

        # Launch app
        adb -s "$DEVICE_SERIAL" shell am start -n "$PACKAGE_NAME/org.thoughtcrime.securesms.RoutingActivity"

        # Wait for app to be fully loaded (adjust timing as needed)
        sleep 3

        # Check if accessibility mode activity is ready
        local activity_ready=false
        local timeout=30
        local count=0

        while [ $count -lt $timeout ] && [ "$activity_ready" = false ]; do
            if adb -s "$DEVICE_SERIAL" shell dumpsys activity activities | grep -q "AccessibilityModeActivity"; then
                activity_ready=true
            fi
            sleep 1
            ((count++))
        done

        local end_time
        end_time=$(date +%s%3N)
        local duration=$((end_time - start_time))

        results+=("$duration")
        total_time=$((total_time + duration))

        info "Iteration $i: ${duration}ms"

        # Clean up
        adb -s "$DEVICE_SERIAL" shell am force-stop "$PACKAGE_NAME"
    done

    # Calculate statistics
    local avg_time=$((total_time / ITERATIONS))
    local min_time=$(printf '%s\n' "${results[@]}" | sort -n | head -1)
    local max_time=$(printf '%s\n' "${results[@]}" | sort -n | tail -1)

    local result_data
    result_data=$(cat << EOF
{
  "iterations": $ITERATIONS,
  "average_ms": $avg_time,
  "min_ms": $min_time,
  "max_ms": $max_time,
  "results": [$(IFS=,; echo "${results[*]}")]
}
EOF
)

    update_results "startup_time" "$result_data"
    log "Startup Time Results: Avg=${avg_time}ms, Min=${min_time}ms, Max=${max_time}ms"
}

# Benchmark memory usage
benchmark_memory_usage() {
    log "=== Benchmarking Memory Usage ==="

    local results=()
    local baseline_memory=0

    # Get baseline memory usage
    baseline_memory=$(get_memory_usage)
    info "Baseline memory: ${baseline_memory}KB"

    # Enable accessibility mode and monitor memory
    for i in $(seq 1 "$ITERATIONS"); do
        info "Memory measurement $i of $ITERATIONS"

        # Trigger accessibility mode (this would need to be customized based on your app)
        # For now, we'll just monitor the running app
        sleep 2

        local current_memory
        current_memory=$(get_memory_usage)
        local delta=$((current_memory - baseline_memory))

        results+=("$current_memory")
        info "Memory usage: ${current_memory}KB (Δ${delta}KB)"

        sleep 1
    done

    local avg_memory=$(calculate_average "${results[@]}")
    local max_memory=$(printf '%s\n' "${results[@]}" | sort -n | tail -1)

    local result_data
    result_data=$(cat << EOF
{
  "baseline_kb": $baseline_memory,
  "average_kb": $avg_memory,
  "max_kb": $max_memory,
  "measurements": [$(IFS=,; echo "${results[*]}")]
}
EOF
)

    update_results "memory_usage" "$result_data"
    log "Memory Usage Results: Avg=${avg_memory}KB, Max=${max_memory}KB"
}

# Get current memory usage
get_memory_usage() {
    adb -s "$DEVICE_SERIAL" shell dumpsys meminfo "$PACKAGE_NAME" | grep "TOTAL" | awk '{print $2}' | tr -d '\r'
}

# Benchmark CPU usage
benchmark_cpu_usage() {
    log "=== Benchmarking CPU Usage ==="

    local results=()
    local baseline_cpu=0

    # Get baseline CPU usage
    baseline_cpu=$(get_cpu_usage)
    info "Baseline CPU: ${baseline_cpu}%"

    # Monitor CPU during operations
    for i in $(seq 1 "$ITERATIONS"); do
        info "CPU measurement $i of $ITERATIONS"

        # Perform some accessibility operations here
        sleep 2

        local current_cpu
        current_cpu=$(get_cpu_usage)
        local delta=$((current_cpu - baseline_cpu))

        results+=("$current_cpu")
        info "CPU usage: ${current_cpu}% (Δ${delta}%)"

        sleep 1
    done

    local avg_cpu=$(calculate_average "${results[@]}")
    local max_cpu=$(printf '%s\n' "${results[@]}" | sort -n | tail -1)

    local result_data
    result_data=$(cat << EOF
{
  "baseline_percent": $baseline_cpu,
  "average_percent": $avg_cpu,
  "max_percent": $max_cpu,
  "measurements": [$(IFS=,; echo "${results[*]}")]
}
EOF
)

    update_results "cpu_usage" "$result_data"
    log "CPU Usage Results: Avg=${avg_cpu}%, Max=${max_cpu}%"
}

# Get current CPU usage
get_cpu_usage() {
    adb -s "$DEVICE_SERIAL" shell dumpsys cpuinfo | grep "$PACKAGE_NAME" | awk '{print $1}' | sed 's/%//' | tr -d '\r' || echo "0"
}

# Benchmark gesture latency
benchmark_gesture_latency() {
    log "=== Benchmarking Gesture Latency ==="

    local results=()
    local gesture_script="${SCRIPT_DIR}/../gesture-testing/emulator-gesture-test.sh"

    if [ ! -f "$gesture_script" ]; then
        error "Gesture test script not found: $gesture_script"
        return 1
    fi

    for i in $(seq 1 "$ITERATIONS"); do
        info "Gesture latency test $i of $ITERATIONS"

        # Start monitoring logcat for gesture detection
        local start_time
        start_time=$(date +%s%3N)

        # Execute gesture via script
        bash "$gesture_script" triple-tap-debug "$DEVICE_SERIAL" > /dev/null 2>&1

        # Wait a bit for gesture processing
        sleep 1

        # Check logcat for gesture detection (adjust grep pattern as needed)
        local gesture_detected=false
        if adb -s "$DEVICE_SERIAL" logcat -d | grep -q "Gesture.*detected\|Exit.*gesture\|AccessibilityModeExitToSettingsGestureDetector"; then
            gesture_detected=true
        fi

        local end_time
        end_time=$(date +%s%3N)
        local latency=$((end_time - start_time))

        results+=("$latency")
        info "Gesture latency: ${latency}ms (Detected: $gesture_detected)"
    done

    local avg_latency=$(calculate_average "${results[@]}")
    local min_latency=$(printf '%s\n' "${results[@]}" | sort -n | head -1)
    local max_latency=$(printf '%s\n' "${results[@]}" | sort -n | tail -1)

    local result_data
    result_data=$(cat << EOF
{
  "iterations": $ITERATIONS,
  "average_ms": $avg_latency,
  "min_ms": $min_latency,
  "max_ms": $max_latency,
  "results": [$(IFS=,; echo "${results[*]}")]
}
EOF
)

    update_results "gesture_latency" "$result_data"
    log "Gesture Latency Results: Avg=${avg_latency}ms, Min=${min_latency}ms, Max=${max_latency}ms"
}

# Calculate average of array
calculate_average() {
    local sum=0
    local count=0

    for value in "$@"; do
        sum=$((sum + value))
        ((count++))
    done

    if [ $count -eq 0 ]; then
        echo 0
    else
        echo $((sum / count))
    fi
}

# Run full benchmark suite
benchmark_full_suite() {
    log "=== Running Full Benchmark Suite ==="

    benchmark_startup_time
    sleep 2

    benchmark_memory_usage
    sleep 2

    benchmark_cpu_usage
    sleep 2

    benchmark_gesture_latency

    log "=== Full Benchmark Suite Completed ==="
}

# Verify device and app state before benchmarking
verify_benchmark_environment() {
    log "=== Verifying Benchmark Environment ==="

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

    # Check if Signal app is installed
    if ! adb -s "$DEVICE_SERIAL" shell pm list packages | grep -q "$PACKAGE_NAME"; then
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
        echo "4. Then re-run this benchmark"
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

    log "=== Benchmark Environment Ready ==="
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
    echo "⏳ The benchmark will automatically detect when Accessibility Mode is enabled"
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
        echo "  Signal installed: $(adb -s "$DEVICE_SERIAL" shell pm list packages 2>/dev/null | grep -c "$PACKAGE_NAME")"
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
    adb -s "$DEVICE_SERIAL" shell am start -n "$PACKAGE_NAME/org.thoughtcrime.securesms.RoutingActivity"

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
    log "=== Accessibility Mode Performance Benchmark Started ==="
    log "Benchmark Type: $BENCHMARK_TYPE"
    log "Device: $DEVICE_SERIAL"
    log "Iterations: $ITERATIONS"

    # Initial setup
    init_results
    check_adb_setup
    get_device_info

    # Verify benchmark environment and guide user if needed
    verify_benchmark_environment

    case "$BENCHMARK_TYPE" in
        "startup-time")
            benchmark_startup_time
            ;;
        "memory-usage")
            benchmark_memory_usage
            ;;
        "cpu-usage")
            benchmark_cpu_usage
            ;;
        "gesture-latency")
            benchmark_gesture_latency
            ;;
        "full-suite")
            benchmark_full_suite
            ;;
        *)
            error "Unknown benchmark type: $BENCHMARK_TYPE"
            echo "Available types: startup-time, memory-usage, cpu-usage, gesture-latency, full-suite"
            exit 1
            ;;
    esac

    log "=== Benchmark completed ==="
    log "Results saved to: $RESULT_FILE"
    log "Log saved to: ${LOG_DIR}/benchmark-${TIMESTAMP}.log"
}

# Show usage if requested
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    echo "Usage: $0 [benchmark-type] [device-serial] [iterations]"
    echo ""
    echo "Benchmark Types:"
    echo "  startup-time      - Measure time to launch accessibility mode"
    echo "  memory-usage      - Monitor memory consumption patterns"
    echo "  cpu-usage         - Monitor CPU usage during operations"
    echo "  gesture-latency   - Measure gesture detection response time"
    echo "  full-suite        - Run all benchmarks"
    echo ""
    echo "Examples:"
    echo "  $0 startup-time emulator-5554"
    echo "  $0 full-suite emulator-5554 10"
    exit 0
fi

# Run main function
main "$@"
