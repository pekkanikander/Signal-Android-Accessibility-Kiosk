# Signal Accessibility Mode Implementation Plan

## 📊 **Current Status: Phase 2.1 COMPLETED** ✅

**Major Milestone Achieved**: Successfully implemented ChatGPT-5's Intent Stack Architecture with Accessibility Mode Router. Clean, surgical implementation with minimal Signal changes and robust activity stack management.

## 🎯 **Phase 2: Accessibility Mode Implementation**

### **Phase 2.1: Core Integration** ✅ **COMPLETED**

#### **✅ Step 1: ChatGPT-5 Architecture Design**
- Analyzed all Intent stack scenarios and UX issues
- Designed optimal architecture with clean separation of concerns
- Created comprehensive implementation plan with ChatGPT-5

#### **✅ Step 2: Accessibility Mode Router Implementation**
- **Created core components**:
  - `AccessibilityModeStore.kt` - Clean interface to Accessibility Mode state
  - `IntentFactory.kt` - Creates Intents with proper flags (`CLEAR_TASK`, `NO_ANIMATION`)
  - `AccessibilityModeRouter.kt` - Centralized routing decisions
  - Application initialization in `ApplicationContext.java`
- **Added activity hooks**:
  - `MainActivity.onStart()` - Routes to Accessibility Mode if enabled
  - `AccessibilityModeActivity.onStart()` - Routes to Normal Mode if disabled
- **Removed old implementation**:
  - Eliminated `careModeLaunched` flag and old routing logic
  - Cleaned up `MainActivity.onResume()`

#### **✅ Step 3: Technical Terminology Refactoring**
- **Renamed all components** to use technical "Accessibility Mode" terminology:
  - `CareModeStore` → `AccessibilityModeStore`
  - `CareModeRouter` → `AccessibilityModeRouter`
  - `careRoot()` → `accessibilityRoot()`
  - `rebaseToCare()` → `rebaseToAccessibility()`
- **Maintained UX flexibility** - User-facing terminology can be "Care Mode", "Assisted Mode", etc.
- **Clean separation** between technical implementation and user experience

#### **✅ Step 4: Architecture Benefits Achieved**
- **Minimal Signal changes**: Only 3-4 lines added to existing activities
- **Clean activity stack**: Uses `FLAG_ACTIVITY_CLEAR_TASK` for proper management
- **No animations**: Uses `FLAG_ACTIVITY_NO_ANIMATION` for smooth transitions
- **Centralized logic**: All routing decisions in one place
- **Robust error handling**: Handles all edge cases properly

### **Phase 2.2: Exit Gesture Implementation** 🔄 **NEXT**

#### **Step 1: Exit Gesture Detection System**
- [ ] **Implement `ExitGestureDetector` class** with Gesture A (opposite corners hold)
- [ ] **Add gesture configuration** to existing Accessibility Mode settings screen
- [ ] **Create `AccessibilityModeConfirmExitActivity`** for confirmation overlay
- [ ] **Integrate gesture detection** into `AccessibilityModeActivity`

#### **Step 2: Confirmation & PIN System**
- [ ] **Implement press-and-hold confirmation slider** (1.5s default)
- [ ] **Create dedicated PIN system** for Accessibility Mode (separate from Signal's PIN)
- [ ] **Add PIN entry UI** with 4-6 digit numeric keypad
- [ ] **Implement PIN validation** with salted hash storage

#### **Step 3: Settings Integration Enhancement**
- [ ] **Add gesture type selection** (Gesture A/B) to Accessibility Mode settings
- [ ] **Add PIN requirement toggle** to Accessibility Mode settings
- [ ] **Implement conversation selection** when enabling Accessibility Mode
- [ ] **Add proper state management** for mode switching and gesture configuration

#### **Step 4: Advanced Gesture Features** (Phase 2.5)
- [ ] **Implement Gesture B** (two-finger header hold) as alternative option
- [ ] **Add advanced configuration** (hold duration, corner size, drift tolerance)
- [ ] **Implement accessibility testing** (TalkBack, screen reader support)
- [ ] **Add gesture telemetry** (debug-only, no sensitive data)

### **Phase 2.3: Accessibility Mode UX Refinement** 📋 **PLANNED**

#### **Step 1: Accessibility Mode Branding**
- [ ] Hide or minimize Signal branding in Accessibility Mode
- [ ] Add "Accessibility Mode" indicators where appropriate
- [ ] Ensure clear mental model for both user types
- [ ] Test with actual users

#### **Step 2: Enhanced Accessibility Features**
- [ ] Add accessibility-specific UI customizations
- [ ] Implement custom scrolling behavior (auto-return to bottom)
- [ ] Add accessibility labels and descriptions
- [ ] Optimize for screen readers

#### **Step 3: Voice Note Button** (Deferred from Phase 2.4)
- [ ] Implement voice note recording
- [ ] Add voice note playback
- [ ] Ensure accessibility compliance

### **Phase 2.4: Advanced Accessibility Mode Features** 📋 **FUTURE**

#### **Step 1: Message Management**
- [ ] Message reactions (simplified)
- [ ] Media handling (basic)
- [ ] Message search
- [ ] Deletion/editing

#### **Step 2: Enhanced Accessibility**
- [ ] Read receipts
- [ ] Delivery status
- [ ] Custom accessibility gestures
- [ ] Advanced theming options

### **Phase 2.5: Advanced Gesture Features** 📋 **FUTURE**

#### **Step 1: Gesture B Implementation**
- [ ] **Implement two-finger header hold** as alternative to opposite corners
- [ ] **Add gesture type selection** in settings (A vs B)
- [ ] **Optimize for different device sizes** and orientations
- [ ] **Test with various accessibility tools** (TalkBack, screen readers)

#### **Step 2: Advanced Configuration**
- [ ] **Add hold duration configuration** (default A=2500ms, B=1800ms)
- [ ] **Add confirmation duration** (default 1500ms)
- [ ] **Add timeout configuration** (default 10s)
- [ ] **Add corner hit-rect size** (default 72dp, adjustable for small devices)
- [ ] **Add drift tolerance** (default 24dp)

#### **Step 3: Accessibility & Testing**
- [ ] **Implement comprehensive TalkBack support** with proper announcements
- [ ] **Add haptic feedback** during gesture detection
- [ ] **Create automated tests** for gesture detection logic
- [ ] **Add gesture telemetry** (debug-only, no sensitive data)

### **Phase 2.6: Kiosk Features** 📋 **FUTURE**

#### **Step 1: Device-Level Restrictions**
- [ ] Auto-start Signal on device boot
- [ ] Prevent access to other apps
- [ ] Implement device launcher functionality
- [ ] Add background recovery mechanisms

#### **Step 2: Advanced Device Management**
- [ ] Lock Task mode support
- [ ] Dedicated device flows
- [ ] Remote configuration options
- [ ] Device monitoring and alerts

## 🚀 **Architecture Benefits Achieved**

### **Code Quality**
- **Surgical changes**: Only 3-4 lines added to existing activities
- **Clean separation**: Technical vs user-facing terminology
- **Centralized logic**: All routing in one place
- **Robust error handling**: Handles all edge cases

### **Performance Benefits**
- **Clean activity stack**: Single root activity at all times
- **No animations**: Smooth transitions without flashes
- **Memory efficient**: No lingering activities
- **Fast routing**: Minimal overhead in activity lifecycle

### **Maintainability**
- **Minimal Signal changes**: Easy to maintain across updates
- **Clear interfaces**: Well-defined contracts between components
- **Testable architecture**: Each component can be tested independently
- **Future-proof**: Easy to extend with new features

### **User Experience**
- **Predictable behavior**: Consistent across all scenarios
- **Smooth transitions**: No jarring activity changes
- **Clear mental model**: Users understand where they are
- **Flexible UX language**: Can adapt terminology per user needs

## 🎯 **Mental Model: "Care" as Signal Mode Switch**

### **Technical Implementation**
1. **Accessibility Mode Router**: Centralized routing decisions
2. **Activity Stack Management**: Clean transitions with `CLEAR_TASK`
3. **State Management**: Persistent Accessibility Mode state
4. **Exit Gesture**: Launch `AppSettingsActivity` for configuration

### **User Experience Flow**
1. **Assisting User**: Settings → Select Conversation → Enable Accessibility Mode → Exit Settings
2. **Signal Transforms**: Normal Signal → Accessibility Mode (single conversation)
3. **Disabled User**: Sees only their selected conversation, no Signal complexity
4. **Exit Path**: Gesture → Settings → Disable Accessibility Mode → Normal Signal

### **Key Principles**
- **Mode Switch**: When enabled, accessibility Mode replaces Signal's home screen
- **No Restart**: Smooth transition without app restart
- **Clear Navigation**: Exit gesture returns to settings for configuration
- **Iterative Setup**: Easy to adjust settings and test repeatedly

## 📈 **Next Steps**

1. **Implement Gesture A detection** - Opposite corners hold in `AccessibilityModeActivity`
2. **Create confirmation overlay** - Press-and-hold slider with timeout
3. **Add PIN system** - Dedicated 4-6 digit PIN for Accessibility Mode
4. **Integrate with settings** - Add gesture configuration to Accessibility Mode settings
5. **Test all scenarios** - Verify gesture detection and confirmation flows

**Ready to implement the comprehensive exit gesture system!** 🎯

## 🔧 **Technical Implementation Notes**

### **Exit Gesture Architecture**
- **Technical Definition**: "Exit Accessibility Mode" = "Launch AppSettingsActivity"
- **Gesture Detection**: Implement in `AccessibilityModeActivity` with `ExitGestureDetector`
- **Confirmation Flow**: Trigger → Hold slider → (Optional PIN) → Settings
- **Configuration**: Gesture type (A/B), PIN requirement, timing parameters

### **Gesture Options**
- **Gesture A (Opposite corners hold)**: Very strict, hard to trigger accidentally
- **Gesture B (Two-finger header hold)**: More learnable but still intentional
- **PIN Gate**: Optional 4-6 digit PIN for additional security
- **Multi-stage Confirmation**: Multiple barriers against accidental activation

### **Settings Integration**
- **Gesture Configuration**: Add to existing Accessibility Mode settings screen
- **PIN Management**: Separate from Signal's PIN system
- **State Persistence**: Store gesture preferences and PIN hash
- **Validation**: Ensure selected conversation still exists

**Ready to implement the comprehensive exit gesture system with ChatGPT-5's design!**
