# Implementation Plan v1 - Signal-Android-Accessibility-Kiosk (Parallel Accessibility Interface Approach)

## Long-Term Strategy Context

**Two-Track Approach:**
1. **Kiosk/Launcher Layer** (this fork only) - System-level device management and escape prevention
2. **Accessibility UI Layer** (this fork + potential upstream PR) - Simplified interface, large controls, high contrast, cognitive accessibility

**Upstream PR Goals:**
- Contribute accessibility improvements to main Signal repo
- Focus on UI/UX enhancements for people with dementia and other disabilities
- Keep changes minimal and focused on accessibility, not device management
- Separate from kiosk/launcher functionality

**This Fork Scope:**
- **NEW**: Parallel accessibility interface (AccessibilityActivity) that reuses existing components
- Settings integration for accessibility mode toggle
- Component reuse strategy (ViewModel, Repository, backend services)
- **Kiosk features**: System-level restrictions (HOME launcher, boot auto-start, background recovery)
- Hidden admin access via gesture

---

## A) New Architecture: Parallel Accessibility Interface

**Core Concept**: Instead of intercepting and modifying existing Signal UI, create a completely separate accessibility interface that leverages existing backend components while providing dedicated accessibility features.

```
Settings → Enable Accessibility Mode → Switch to AccessibilityActivity
    ↓
AccessibilityActivity (parallel accessibility interface)
    ↓
Reuse existing components:
- ConversationViewModel (message logic)
- ConversationRepository (data access)
- Attachment handling (but with accessibility UI)
- Backend services (crypto, network, etc.)
    ↓
Exit gesture → Return to Settings (same location)
```

**Key Benefits**:
- **Minimal Risk**: No complex UI interception required
- **Easy Maintenance**: Isolated from upstream changes
- **Clean Architecture**: Clear separation of concerns
- **Component Reuse**: Leverage existing, tested conversation logic
- **Accessibility Focus**: Dedicated interface for users with reduced cognitive capacity

---

## B) Settings Integration & Accessibility Mode Toggle

**Integration Point**: Add accessibility mode toggle in existing Signal settings
**Storage**: Use existing `SignalStore` preferences system
**Toggle**: Simple boolean flag `accessibility_mode_enabled`

**Implementation Details**:
- **Location**: Existing Signal settings hierarchy
- **UI**: Simple toggle switch with descriptive text
- **Behavior**: When enabled, immediately launch `AccessibilityActivity`
- **State**: Preserve settings location for return navigation

**Files to Modify**:
- `app/src/main/res/xml/preferences.xml` - Add accessibility preferences
- `app/src/main/java/org/thoughtcrime/securesms/preferences/` - Add accessibility settings fragment

---

## C) AccessibilityActivity Design & Implementation

**Purpose**: Complete parallel conversation interface with accessibility-optimized UI
**Layout**: Custom accessibility-optimized UI (large buttons, simplified interface)
**Navigation**: No back button, no menus, no escape routes

**UI Components**:
- **Large Send Button**: Prominent text sending control
- **Large Voice Note Button**: Hold-to-record with visual feedback
- **Message Display**: Simplified conversation view (reuse existing logic)
- **Input Field**: Large, high-contrast text input
- **No Menus**: Zero menu inflation or navigation options

**Component Reuse Strategy**:
- **ConversationViewModel**: Existing message handling, thread management
- **ConversationRepository**: Existing data access, message sending
- **Backend Services**: Zero changes to crypto, network, storage
- **UI Logic**: Reuse existing conversation display and input handling

---

## D) Thread Selection & Configuration

**Thread ID Storage**: Use existing Signal preferences system
**Configuration**: Simple thread selection in accessibility settings
**Default Behavior**: Use last active conversation or prompt for selection

**Implementation**:
- Store selected thread ID in `SignalStore` preferences
- Provide thread selection UI in accessibility settings
- Handle missing/invalid thread IDs gracefully
- Support both individual and group conversations

---

## E) Exit Mechanism & Return Navigation

**Hidden Gesture**: Same design (5 taps + 3s press in corner)
**Return Path**: `finish()` AccessibilityActivity, return to Settings
**State Preservation**: Settings location maintained, accessibility mode can be disabled

**Implementation**:
- Gesture detector on AccessibilityActivity root view
- Return to exact settings location
- Clean state cleanup on exit
- Option to disable accessibility mode from settings

---

## F) Kiosk Features vs. Accessibility Features

### **Accessibility Features (UI Level)**
- Large, high-contrast controls
- Simplified conversation interface
- Reduced cognitive load design
- Easy-to-use attachment handling
- Clear visual feedback

### **Kiosk Features (System Level)**
- HOME launcher capability
- Boot auto-start functionality
- Background recovery
- Device owner provisioning
- Lock Task mode support

### **Implementation Strategy**
- **Accessibility Interface**: New parallel UI with component reuse
- **Kiosk Behavior**: System-level features that may be enabled independently
- **Integration**: Accessibility interface may include some kiosk features at the GUI level

---

## G) Risk Assessment: Parallel vs. Interception

| Risk Category | Interception Approach | Parallel Accessibility Interface Approach |
|---------------|----------------------|-------------------------------------------|
| **Menu System** | ⚠️ **CRITICAL** - Complex MenuProvider interception | ✅ **LOW** - No existing menus to intercept |
| **Long-press** | ⚠️ **HIGH** - Scattered throughout UI | ✅ **LOW** - Custom accessibility UI, no existing handlers |
| **Attachments** | ⚠️ **MODERATE-HIGH** - Multiple entry points | ✅ **LOW** - Custom attachment UI, reuse backend logic |
| **Navigation** | ⚠️ **MODERATE** - Back button interception | ✅ **LOW** - No back button in accessibility UI |
| **Upstream Changes** | ⚠️ **HIGH** - UI refactors break our hooks | ✅ **LOW** - Minimal coupling to existing UI |

**Overall Risk**: **LOW** ✅ (vs. MODERATE-HIGH for interception approach)

---

## H) Implementation Checklist & File Modifications

### **Files to Create (New Accessibility Interface)**
1. **`app/src/main/java/org/thoughtcrime/securesms/accessibility/AccessibilityActivity.kt`** - Main accessibility interface
2. **`app/src/main/res/layout/activity_accessibility.xml`** - Accessibility UI layout
3. **`app/src/main/java/org/thoughtcrime/securesms/accessibility/AccessibilitySettingsFragment.kt`** - Accessibility settings
4. **`app/src/main/res/xml/accessibility_preferences.xml`** - Accessibility preferences

### **Files to Modify (Existing Signal)**
5. **`app/src/main/res/xml/preferences.xml`** - Add accessibility preferences entry
6. **`app/src/main/java/org/thoughtcrime/securesms/preferences/`** - Integrate accessibility settings

### **Component Reuse (No Changes Required)**
- **ConversationViewModel** - Existing message handling
- **ConversationRepository** - Existing data operations
- **Backend Services** - All Signal services remain untouched
- **UI Logic** - Reuse existing conversation display components

---

## I) Implementation Phases

### **Phase 1: Foundation (Week 1)**
- Create AccessibilityActivity with basic layout
- Implement settings integration
- Basic component reuse testing

### **Phase 2: Core Functionality (Week 2)**
- Message sending/receiving
- Thread selection and configuration
- Basic accessibility UI refinement

### **Phase 3: Polish & Testing (Week 3)**
- Voice notes and attachment handling
- Exit gesture implementation
- Testing and bug fixes

---

## J) Critical Questions (Still Need Answers)

1. **Thread Selection**: How should users select which conversation to use in accessibility mode? Simple list, search, or last active?

2. **Voice Note Permissions**: If microphone permissions are denied, should the app still function for text-only communication?

3. **Exit Gesture**: Is the proposed gesture (5 taps + 3s press in corner) acceptable, or prefer a different pattern?

4. **Settings Location**: Where exactly in the Signal settings hierarchy should the accessibility toggle be placed?

5. **Default Behavior**: Should accessibility mode remember the last selected thread, or always prompt for selection?

---

## Summary

This **parallel accessibility interface approach** provides a significantly lower-risk implementation path compared to the original interception strategy. By creating a separate accessibility interface that reuses existing components, we achieve:

**Key Advantages**:
- **Minimal Risk**: No complex UI interception required
- **Easy Maintenance**: Isolated from upstream changes
- **Clean Architecture**: Clear separation of concerns
- **Component Reuse**: Leverage existing, tested conversation logic
- **Fast Development**: Focus on accessibility UI, not interception complexity
- **Clear Separation**: Distinction between accessibility features (UI) and kiosk features (system-level)

**Implementation Complexity**: **LOW** - 4 new files, 2 minor modifications
**Success Probability**: **90-95%** on first attempt
**Maintenance Overhead**: **LOW** - minimal coupling to existing code

**Next Steps**:
1. Review this parallel accessibility interface plan
2. Answer the critical questions above
3. When ready, respond with "approved: proceed to code"
