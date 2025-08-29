

# NEXT STEPS TO CHECK — Accessibility Implementation (Parallel Accessibility Interface Approach)

This note captures the **verification-first** work completed and the **new parallel accessibility interface approach** selected. Goal: create a completely separate accessibility interface that reuses existing Signal components rather than intercepting existing UI.

> Reminder: upstream README moved to `README-SIGNAL.md`.

---

## 1) Architecture Decision Made ✅

**Parallel Accessibility Interface Approach Selected** over UI interception strategy.

**Why This Approach**:
- **Minimal Risk**: No complex UI interception required
- **Easy Maintenance**: Isolated from upstream changes
- **Clean Architecture**: Clear separation of concerns
- **Component Reuse**: Leverage existing, tested conversation logic
- **Accessibility Focus**: Dedicated interface for users with reduced cognitive capacity

**Implementation Strategy**:
1. **Settings Integration**: Add accessibility mode toggle in existing Signal settings
2. **Parallel Interface**: Create `AccessibilityActivity` with custom accessibility-optimized UI
3. **Component Reuse**: Reuse existing `ConversationViewModel`, `ConversationRepository`, backend services
4. **Exit Mechanism**: Hidden gesture returns to Settings (same location)

---

## 2) What Was Verified (No Longer Needed for Implementation)

The following verification work was completed but is **no longer needed** for the parallel accessibility interface approach:

- **Launcher & entry routing**: Identified MainActivity via RoutingActivity activity-alias
- **Conversation UI container**: Found ConversationActivity + ConversationFragment structure
- **Menu wiring**: Identified MenuProvider pattern and inflation points
- **Attachment & long-press**: Found AttachmentKeyboardFragment and scattered handlers
- **Back handling**: Confirmed OnBackPressedDispatcher usage
- **Flavour matrix**: Identified existing distribution/environment dimensions

**Status**: ✅ **VERIFICATION COMPLETE** - Information preserved for reference but not needed for implementation

---

## 3) New Implementation Approach: Parallel Accessibility Interface

### **What We're Building Instead**
- **AccessibilityActivity**: Completely new, dedicated accessibility interface
- **Settings Integration**: Simple toggle in existing Signal settings
- **Component Reuse**: Leverage existing ConversationViewModel, Repository, backend services
- **Zero UI Interception**: No modification of existing Signal UI

### **Implementation Plan**
1. **Settings Toggle**: Add accessibility mode switch in Signal preferences
2. **AccessibilityActivity**: Create new activity with accessibility-optimized UI
3. **Component Binding**: Connect to existing conversation logic
4. **Exit Gesture**: Hidden gesture returns to Settings

### **Files to Create**
- `AccessibilityActivity.kt` - Main accessibility interface
- `activity_accessibility.xml` - Accessibility UI layout
- `AccessibilitySettingsFragment.kt` - Accessibility settings
- `accessibility_preferences.xml` - Accessibility preferences

### **Files to Modify (Minimal)**
- `preferences.xml` - Add accessibility entry
- Settings integration code

---

## 4) Terminology Clarification

### **Accessibility Features (UI Level)**
- Large, high-contrast controls for users with reduced cognitive capacity
- Simplified conversation interface
- Reduced cognitive load design
- Easy-to-use attachment handling
- Clear visual feedback

### **Kiosk Features (System Level)**
- System-level restrictions that prevent users from escaping the app
- HOME launcher capability
- Boot auto-start functionality
- Background recovery
- Device owner provisioning

### **Implementation Strategy**
- **Accessibility Interface**: New parallel UI with component reuse
- **Kiosk Behavior**: System-level features that may be enabled independently
- **Integration**: Accessibility interface may include some kiosk features at the GUI level

---

## 5) Next Steps for Implementation

### **Ready to Proceed** ✅
- **Architecture**: Parallel accessibility interface approach selected and designed
- **Risk Assessment**: LOW risk implementation path identified
- **Component Analysis**: Existing components identified for reuse
- **Implementation Plan**: Detailed plan with phases and file modifications

### **Critical Questions to Answer**
1. **Thread Selection**: How should users select which conversation to use in accessibility mode?
2. **Voice Note Permissions**: Handle microphone permission denial gracefully?
3. **Exit Gesture**: Confirm gesture design (5 taps + 3s press in corner)?
4. **Settings Location**: Where exactly in Signal settings hierarchy?
5. **Default Behavior**: Remember last thread or always prompt for selection?

### **Implementation Readiness**
**Status**: ✅ **READY FOR IMPLEMENTATION**
**Approach**: Parallel accessibility interface with component reuse
**Risk Level**: **LOW** - isolated from existing UI
**Success Probability**: **90-95%**

**Next Action**: Answer critical questions, then proceed with "approved: proceed to code"
