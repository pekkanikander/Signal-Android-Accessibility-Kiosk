# Accessibility Test Files to Remove - Step 1.1

## 🎯 **OVERVIEW**

**Total Test Files in Project:** 190
**Accessibility Test Files (Ours):** 8
**Upstream Signal Tests:** 182 (PRESERVE THESE)

We will **ONLY REMOVE** the 8 test files we created for accessibility features.
All other tests are upstream Signal tests that must be preserved.

---

## 🗑️ **ACCESSIBILITY TEST FILES TO REMOVE**

### **Unit Tests (app/src/test):**
```
✅ AccessibilityModeActivityTest.kt
✅ AccessibilityModeViewModelTest.kt
✅ AccessibilityModeSettingsFragmentTest.kt
✅ AccessibilityModeSettingsStateTest.kt
✅ AccessibilityModeSettingsViewModelTest.kt
✅ ChatSelectionFlowTest.kt
✅ AccessibilityModeValuesTest.kt
```

### **Instrumentation Tests (app/src/androidTest):**
```
✅ AccessibilityModeSettingsScreenUITest.kt.temp (temporary file)
```

---

## ✅ **PRESERVE THESE UPSTREAM TESTS**

**DO NOT TOUCH:** All other test files in:
- `app/src/test/` (unit tests)
- `app/src/androidTest/` (instrumentation tests)

These are Signal's existing tests for core functionality.

---

## 📋 **REMOVAL CHECKLIST**

### **Step 1.1.1: Verify Files Exist**
- [ ] Confirm all 8 files listed above exist
- [ ] Verify they are accessibility-related (not core Signal)
- [ ] Double-check no upstream tests are in removal list

### **Step 1.1.2: Remove Files**
- [ ] Remove 7 unit test files from `app/src/test/`
- [ ] Remove 1 instrumentation test file from `app/src/androidTest/`
- [ ] Verify git status shows only these files as deleted

### **Step 1.1.3: Verify No Regressions**
- [ ] Run `./gradlew test` - should pass (upstream tests preserved)
- [ ] Run `./gradlew connectedAndroidTest` - should pass
- [ ] No build errors from removed files

### **Step 1.1.4: Commit Changes**
- [ ] Commit with message: "test: Remove accessibility test files for clean slate"
- [ ] Verify commit only removes our 8 test files
- [ ] Preserve upstream test infrastructure

---

## 🎯 **WHY THIS APPROACH**

### **✅ Surgical Removal**
- **Only our files**: No upstream Signal tests touched
- **Clean separation**: Our work vs Signal's work clearly distinguished
- **Minimal PR impact**: No disruption to existing Signal functionality

### **✅ Future-Ready**
- **Test infrastructure preserved**: Signal's testing framework intact
- **Clean slate for us**: Ready to build new semantic tests
- **Easy verification**: Clear before/after state

### **✅ Quality Assurance**
- **No regressions**: Upstream tests ensure Signal works
- **Build verification**: Gradle builds successfully
- **Integration intact**: No broken dependencies

---

## 📊 **AFTER REMOVAL STATE**

**Before:**
- Total test files: 190
- Our accessibility tests: 8
- Upstream Signal tests: 182

**After:**
- Total test files: 182 (all upstream Signal tests)
- Our accessibility tests: 0 (clean slate)
- Ready for new semantic test implementation

---

## 🚀 **NEXT STEPS**

After removal:
1. **Step 1.2**: Design new semantic test strategy
2. **Step 1.3**: Implement minimal test coverage
3. **Step 1.4**: Verify test quality

**This ensures our PR is clean, focused, and doesn't disrupt Signal's existing functionality.**
