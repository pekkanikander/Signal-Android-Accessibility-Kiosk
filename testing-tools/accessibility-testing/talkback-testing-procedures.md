# TalkBack Accessibility Testing Procedures

## Overview
This document provides comprehensive testing procedures for verifying that Accessibility Mode works correctly with Android's TalkBack screen reader and other accessibility services.

## Prerequisites

### 1. Enable TalkBack on Test Device
```bash
# Enable TalkBack via ADB
adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure accessibility_enabled 1
```

### 2. Enable Accessibility Mode
1. Launch Signal app
2. Navigate to Settings → Accessibility → Accessibility Mode
3. Select a conversation for accessibility mode
4. Enable accessibility mode
5. Exit to settings using gesture (triple tap for testing)

### 3. Test Environment Setup
- **Device**: Physical Android device (emulator TalkBack is unreliable)
- **Android Version**: API 24+ (Android 7.0+)
- **TalkBack Version**: Latest available
- **Signal Version**: Current development build

---

## Test Procedures

## 1. Navigation and Focus Management

### 1.1 Initial Focus
**Test**: Verify initial focus when entering accessibility mode
- **Steps**:
  1. Enable accessibility mode
  2. Launch accessibility mode
  3. Listen for TalkBack announcement
- **Expected**:
  - TalkBack announces "Accessibility Mode" or similar
  - Focus moves to conversation content
  - Screen reader describes current context

### 1.2 Message Navigation
**Test**: Navigate through messages using TalkBack
- **Steps**:
  1. Enter accessibility mode with conversation containing messages
  2. Use swipe gestures to navigate messages
  3. Listen for TalkBack announcements
- **Expected**:
  - Each message is announced with sender and content
  - Navigation is logical (chronological order)
  - Message timestamps are announced

### 1.3 UI Element Navigation
**Test**: Navigate through all interactive elements
- **Steps**:
  1. Use TalkBack navigation gestures
  2. Verify all buttons, inputs, and controls are accessible
- **Expected**:
  - All elements have proper content descriptions
  - Focus order is logical
  - No elements are missed or duplicated

## 2. Gesture Conflicts

### 2.1 TalkBack Gesture Override
**Test**: Verify accessibility mode gestures don't interfere with TalkBack
- **Steps**:
  1. Enable TalkBack
  2. Enable accessibility mode
  3. Attempt to trigger exit gesture
- **Expected**:
  - TalkBack gestures take precedence
  - Accessibility mode gestures are ignored when TalkBack is active
  - No gesture conflicts occur

### 2.2 Accessibility Service Detection
**Test**: Verify accessibility mode detects TalkBack correctly
- **Steps**:
  1. Enable/disable TalkBack while in accessibility mode
  2. Monitor logcat for detection messages
- **Expected**:
  - Accessibility mode detects TalkBack state changes
  - Gesture detector disables when TalkBack is active
  - Proper logging of accessibility service state

## 3. Content Announcements

### 3.1 Message Content
**Test**: Verify message content is properly announced
- **Steps**:
  1. Receive new message while in accessibility mode
  2. Listen for TalkBack announcement
- **Expected**:
  - Sender name is announced
  - Message content is read
  - Message type (text, media, etc.) is indicated

### 3.2 Status Updates
**Test**: Verify status changes are announced
- **Steps**:
  1. Change message read status
  2. Send/receive messages
  3. Monitor TalkBack announcements
- **Expected**:
  - Read/unread status changes are announced
  - New message notifications are read
  - Connection status changes are announced

## 4. Touch Target Accessibility

### 4.1 Minimum Touch Targets
**Test**: Verify all interactive elements meet accessibility guidelines
- **Steps**:
  1. Use TalkBack to identify all interactive elements
  2. Verify touch target sizes
- **Expected**:
  - All elements are at least 48x48dp
  - Elements are properly spaced
  - No overlapping touch targets

### 4.2 Custom Gesture Areas
**Test**: Verify gesture detection areas are accessible
- **Steps**:
  1. Examine gesture detection zones
  2. Verify they don't interfere with TalkBack navigation
- **Expected**:
  - Gesture areas don't block TalkBack gestures
  - Custom gestures are disabled when TalkBack is active

---

## Automated Testing Tools

### 1. Accessibility Scanner Integration
```bash
# Run accessibility scanner
adb shell am start -n com.google.android.apps.accessibility.auditor/com.google.android.apps.accessibility.auditor.ScanActivity
```

### 2. UI Automator Tests
```kotlin
// Example test for TalkBack compatibility
@Test
fun testTalkbackMessageNavigation() {
    // Enable TalkBack programmatically
    enableTalkBack()

    // Navigate to accessibility mode
    onView(withId(R.id.accessibility_mode_button)).perform(click())

    // Use TalkBack gestures
    performTalkBackGesture(TalkBackGesture.SWIPE_RIGHT)

    // Verify announcements
    assertTalkBackAnnouncement("Message from John: Hello")
}
```

### 3. Accessibility Event Monitoring
```bash
# Monitor accessibility events
adb shell dumpsys accessibility
```

---

## Performance Testing

### 1. Memory Usage with TalkBack
**Test**: Monitor memory usage when TalkBack is enabled
- **Steps**:
  1. Enable TalkBack
  2. Use accessibility mode extensively
  3. Monitor memory usage
- **Expected**:
  - No excessive memory growth
  - App remains responsive
  - No memory leaks

### 2. CPU Usage
**Test**: Monitor CPU usage during accessibility operations
- **Steps**:
  1. Enable CPU profiling
  2. Perform accessibility operations
  3. Analyze CPU usage patterns
- **Expected**:
  - CPU usage remains within acceptable limits
  - No performance degradation

---

## Test Results Template

### Test Session Summary
- **Date**: YYYY-MM-DD
- **Tester**: [Name]
- **Device**: [Device Model/API Level]
- **TalkBack Version**: [Version]
- **Signal Version**: [Version]

### Results Matrix

| Test Case | Status | Notes |
|-----------|--------|-------|
| 1.1 Initial Focus | [PASS/FAIL] | |
| 1.2 Message Navigation | [PASS/FAIL] | |
| 1.3 UI Element Navigation | [PASS/FAIL] | |
| 2.1 TalkBack Gesture Override | [PASS/FAIL] | |
| 2.2 Accessibility Service Detection | [PASS/FAIL] | |
| 3.1 Message Content | [PASS/FAIL] | |
| 3.2 Status Updates | [PASS/FAIL] | |
| 4.1 Minimum Touch Targets | [PASS/FAIL] | |
| 4.2 Custom Gesture Areas | [PASS/FAIL] | |

### Issues Found
1. **Issue Description**
   - **Severity**: [Critical/High/Medium/Low]
   - **Steps to Reproduce**:
   - **Expected Behavior**:
   - **Actual Behavior**:
   - **Workaround**:

### Recommendations
- [ ] Accessibility improvements needed
- [ ] Performance optimizations required
- [ ] Additional testing scenarios identified

---

## Troubleshooting

### Common Issues

1. **TalkBack Not Announcing**
   - Check TalkBack volume settings
   - Verify accessibility permissions
   - Restart TalkBack service

2. **Gesture Conflicts**
   - Disable custom gestures when TalkBack is active
   - Use TalkBack's built-in gesture settings
   - Test on physical device vs emulator

3. **Focus Issues**
   - Verify content descriptions are set
   - Check focus order in layout files
   - Test with different screen sizes

### Debug Commands
```bash
# Check accessibility service status
adb shell settings get secure enabled_accessibility_services

# View accessibility events
adb shell dumpsys accessibility

# Check TalkBack logs
adb logcat | grep -i talkback
```
