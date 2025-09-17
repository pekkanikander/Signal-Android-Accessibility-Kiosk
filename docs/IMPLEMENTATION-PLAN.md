# Signal Accessibility Mode - Simplified Implementation Plan

## 🎯 **Mission: Reduce 8,000 lines to 2,000-4,000 lines**

**Previous implementation**: 7,946 lines across 58 files
**Goal**: Clean, maintainable implementation with essential functionality

## 🚀 **PHASE 3: CORE ARCHITECTURE (2-3 Weeks)**

### **Step 3.1: Simplified Gesture System**
- [X] **Production Gesture**: One reliable gesture for real device use
- [X] **Debug Gesture**: One simple gesture for emulator testing

### **Step 3.2: Clean Settings UI**
- [X] **Replace 10 current files** with 2-3 focused files
- [X] **Essential Settings Only**:
  - Enable/Disable toggle
  - Gesture type selection (2 options)
  - Conversation selection (reuse Signal's picker)
- [X] **Data Model**: Simplify `AccessibilityModeValues.kt`

### **Step 3.3: Preserve Working Core**
- [X] **Keep**: `AccessibilityModeActivity.kt`, `AccessibilityModeFragment.kt`
- [X] **Keep**: `AccessibilityModeRouter.kt`, `IntentFactory.kt`
- [X] **Keep**: Core conversation integration with Signal components
- [X] Verify Signal component compatibility

- **Hardening**:
  - [X] Ensure router is idempotent and safe on repeated `onStart()` calls.  (implemented; see `AccessibilityModeRouter`)

### **Step 3.4: Routing & Entry-point normalization**
- [X] Funnel deep links and conversation entry points through router (`DeepLinkEntryActivity`, `ConversationActivity`).
- [X] Verify `ApplicationContext` initializes the router store and `MainActivity.onStart()` invokes routing.
- [X] Funnel notifications through a single decision path that respects Accessibility Mode.
- [X] Keep tests largely as-is; add minimal coverage only if behavior changes.

---

## 🏗️ **PHASE 4: CLEAN IMPLEMENTATION (3-4 Weeks)**

### **Step 4.1: New Gesture Implementation**
- [X] Add a clear header to AccessibilityBode page, giving clear indication of whom the chat is with
- [X] Change the selected chat into a ConversationPreview or similar, so that it looks similar to the one that the user selects in the ChatSelectionFragment
- [X] Another polisihing round of the state machine implementation, adding first class gesture abstraction, for multiple gestures
- [ ] Implement a reasonable number of Advanced options now in the KV model but not yet in the UI
- [ ] Reasonably thorough testing of the gestures, both manual testing and automated testing
  - especially the two finger hold usability needs more manual tuning
- [ ] Another polishing round on the state machine implementation: Unify the Outer and Inner state machine syntax & semantics
- [ ] Implement another production gesture: Corner-based, reliable on real devices
- [ ] Polish the final code: remove all smells and all too complex arithmetics

### **Step 4.2: New Settings Implementation**
- [ ] Proper error handling and user feedback
- [ ] Consolidate and deprecate legacy settings/chat selection files to the minimal set

### **Step 4.3: Integration Verification**
- [ ] Verify Signal component compatibility
- [ ] Accessibility audit with TalkBack
- [ ] Validate rebasing flows, deep links, and notification taps respect the active mode

---

## 🎯 **PHASE 5: VALIDATION & POLISH (1-2 Weeks)**

### **Step 5.1: Quality Assurance**
- [ ] Speed up Gradle Kotlin compile for :app:compilePlayProdInstrumentationKotlin
- [ ] Add a Gradle aggregate `testAccessibility*` tasks to root build (if still needed)
- [ ] All tests pass consistently
- [ ] Manual testing on real devices
- [ ] Accessibility compliance verification

### **Step 5.2: Documentation Finalization**
- [ ] Update PR-ready documentation
- [ ] Create integration guide for Signal engineers
- [ ] Document maintenance procedures

### **Step 5.3: Final Review**
- [ ] Code review against Signal standards
- [ ] Performance impact assessment
- [ ] Security review for accessibility features

---

## 📈 **SUCCESS METRICS**

### **Code Quality:**
- [ ] **2,000-4,000 total lines** (vs 7,946 current)
- [ ] **10-15 files maximum** (vs 58 current)
- [ ] **Clean architecture** following Signal patterns
- [ ] **Comprehensive test coverage** with semantic tests

### **Functionality:**
- [ ] **Enter accessibility mode** from Signal settings
- [ ] **Simplified conversation view** with large controls
- [ ] **Reliable exit gesture** for real device use
- [ ] **Debug gesture** for emulator development
- [ ] **Accessibility compliance** with TalkBack

### **Maintainability:**
- [ ] **Clear documentation** for Signal engineers
- [ ] **Modular design** for future enhancements
- [ ] **Integration tests** prevent regressions
- [ ] **Following Signal conventions**

---

## ⚠️ **RISK MITIGATION**

### **Technical Risks:**
- **Signal API Changes**: Regular integration testing
- **Android Version Compatibility**: Test on multiple versions
- **Performance Impact**: Monitor memory and battery usage

### **Quality Risks:**
- **Accessibility Compliance**: Regular audit with TalkBack
- **Gesture Reliability**: Extensive real-device testing
- **Settings Complexity**: Keep UI minimal and intuitive

---

## 🎯 **DELIVERABLES**

1. **Clean Implementation**: 2,000-4,000 lines of maintainable code
2. **PR-Ready Documentation**: Clear technical documentation for Signal
3. **Comprehensive Tests**: Semantic test coverage with integration tests
4. **Accessibility Audit**: Verified TalkBack compatibility
5. **Integration Guide**: Instructions for Signal engineers

---

## 📅 **TIMELINE**

- **Phase 0 (Foundation)**: This week
- **Phase 1 (Testing)**: Weeks 2-3
- **Phase 2 (Architecture)**: Weeks 4-6
- **Phase 3 (Implementation)**: Weeks 7-10
- **Phase 4 (Validation)**: Weeks 11-12

**Total: 12 weeks for production-ready, maintainable implementation**

---

*This plan focuses on quality over quantity, preserving essential functionality while eliminating unnecessary complexity from the previous 8,000-line implementation.*
