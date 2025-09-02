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

#### **Step 1: Technical Exit Gesture Design**
- [ ] **Define "Exit Accessibility Mode" as "Launch AppSettingsActivity"**
- [ ] Implement gesture detection in `AccessibilityModeActivity`
- [ ] Add gesture configuration options (swipe, long press, etc.)
- [ ] Ensure proper Intent stack management when launching Settings

#### **Step 2: Settings Integration Enhancement**
- [ ] Modify accessibility settings to control mode switching
- [ ] Add conversation selection when enabling Accessibility Mode
- [ ] Implement "Enable Accessibility Mode" button in settings
- [ ] Add proper state management for mode switching

#### **Step 3: Mode Transition Implementation**
- [ ] Implement Settings → Accessibility Mode transition when exiting settings
- [ ] Add proper activity lifecycle handling for mode switching
- [ ] Ensure smooth transition without app restart
- [ ] Handle conversation selection and validation

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

### **Phase 2.5: Kiosk Features** 📋 **FUTURE**

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

1. **Implement exit gesture** - "Launch AppSettingsActivity" in AccessibilityModeActivity
2. **Add gesture configuration** - Allow customizing the exit gesture
3. **Test all scenarios** - Verify Intent stack behavior across all flows
4. **Refine UX** - Hide Signal branding, improve mental model

**The Accessibility Mode Router is complete and ready for exit gesture implementation!** 🎉

## 🔧 **Technical Implementation Notes**

### **Exit Gesture Architecture**
- **Technical Definition**: "Exit Accessibility Mode" = "Launch AppSettingsActivity"
- **Gesture Detection**: Implement in `AccessibilityModeActivity`
- **Intent Stack**: Proper management when launching Settings
- **Configuration**: Allow customizing gesture type and sensitivity

### **Exit Gesture Options**
- **Long Press**: Simple, discoverable gesture
- **Swipe Gesture**: More sophisticated, configurable
- **Hidden Button**: Minimal UI impact
- **Configurable**: Allow assisting user to choose

### **Settings Integration**
- **Immediate Rebase**: When toggling Accessibility Mode in Settings
- **State Persistence**: Store selected conversation and mode state
- **Validation**: Ensure selected conversation still exists
- **Error Handling**: Graceful fallbacks for edge cases

**Ready to implement the exit gesture that completes the Accessibility Mode mental model!**
