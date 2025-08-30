# Signal — Android Accessibility Kiosk

A thin, maintainable fork of the official Signal Android client that provides a **single‑conversation, zero‑navigation** user experience for a cognitively impaired and/or deaf user. The aim is to remove surprises and interaction traps while keeping Signal's protocol and backend untouched.

> The original upstream README is preserved as **`README-SIGNAL.md`**.

---

## Goals

- **One chat only**: App always opens directly into a preselected group/thread; a conversation list is never shown.
- **No exits**: Back/home/recent are neutralised inside the app; menus, settings, media galleries and contact cards are unreachable.
- **Essential actions only**: Send/receive text; record/send voice notes. Large, high‑contrast affordances.
- **Kiosk behaviour**: Can function as the device **Launcher** and supports Lock Task (dedicated device) flows; auto‑start on boot; auto‑recover if backgrounded.
- **Low delta**: Minimal patchset that rebases cleanly onto upstream Signal.

## Non‑Goals

- No changes to the Signal protocol, registration, servers, or cryptography.
- No alternative networks or bridges.
- No theming beyond what's required for clarity and affordance.

## Status

**Architecture Decision Made - Parallel Accessibility Interface Selected.**
- ✅ Repository set up; upstream README moved to `README-SIGNAL.md`.
- ✅ Local build on macOS (CLI, Gradle wrapper) verified.
- ✅ Architecture analysis complete; parallel accessibility interface approach selected over UI interception.
- ⏳ **Phase 1**: Accessibility interface design and implementation (WIP)
- ⏳ **Phase 2**: AccessibilityMode settings implementation.
- ✅ **Phase 2.1 Complete**: AccessibilityModeValues class implemented with comprehensive test coverage.
- ⏳ **Phase 2.2**: SignalStore integration and accessibility settings UI development.
- ⏳ **Phase 2**: AccessibilityModeActivity implementation with component reuse.

Initial target device: **Samsung Galaxy A21s (Android 12)**. Development also validated on a matching AVD profile.

## Approach

**NEW: Parallel Accessibility Interface Architecture** - Instead of modifying existing Signal UI, we create a completely separate accessibility interface that reuses existing backend components while providing a dedicated, simplified user experience.

Keep the networking/crypto stack exactly as upstream; create a **parallel accessibility interface** that leverages existing conversation logic:

1. **Settings Integration**
   Add accessibility mode toggle in existing Signal settings. When enabled, switch to dedicated AccessibilityModeActivity.

2. **Parallel Accessibility Mode Interface**
   - Create `AccessibilityModeActivity` with custom, accessibility-optimized UI
   - Reuse existing `ConversationViewModel`, `ConversationRepository`, and backend services
   - Large, high-contrast controls for send/voice note actions
   - No menus, no navigation, no escape routes

**Current Progress**: ✅ **Phase 2.1 Complete** - AccessibilityModeValues class implemented with full test coverage. Ready for SignalStore integration and settings UI development.

3. **Component Reuse Strategy**
   - **Existing**: Message handling, crypto, network, storage, conversation logic
   - **New**: Accessibility UI, simplified attachment handling, accessibility-specific navigation
   - **Zero changes** to existing Signal functionality

4. **Exit Mechanism**
   Hidden gesture returns user to Settings (same location) where accessibility mode can be disabled.

### **Terminology Clarification**

- **Accessibility Features**: UI/UX enhancements for users with reduced cognitive capacity (large buttons, high contrast, simplified interface)
- **Kiosk Features**: System-level restrictions that prevent users from escaping the app (HOME launcher, boot auto-start, background recovery)
- **Accessibility Mode**: The new GUI interface that may include some kiosk features at the GUI level
- **Kiosk Behavior**: System-level device management features beyond the accessibility interface

### Build & Tooling

- **JDK:** 17 (Temurin recommended).
- **Compile/Target SDK:** 35 (Android 15).
- **Min SDK:** as per upstream.
- **Build:** Gradle wrapper; Android SDK command‑line tools.
- **IDE:** Cursor/VS Code or Android Studio; builds are CLI‑driven.

Example CLI:

```bash
# From repo root
./gradlew assembleDebug
./gradlew installDebug
adb shell monkey -p org.thoughtcrime.securesms -c android.intent.category.LAUNCHER 1
```

### Flavours / Variants (planned)

- **Standard variants**: Upstream Signal functionality unchanged.
- **Accessibility mode**: Toggle in settings to enable dedicated accessibility interface.

## Development Workflow

- Fork → clone your fork → `upstream` remote tracking → small feature branches → frequent rebases.
- Keep every change as isolated commits (settings integration, accessibility interface, component reuse). This minimises merge friction with upstream Signal.

## Security & Privacy Notes

- This fork **does not** alter Signal's protocol or servers. It is a pure UI/UX enhancement layer.
- Production use of Signal's service is governed by Signal's own terms; review them if distributing beyond personal/family use.

## Licence

- This fork remains open‑source under the same licence as the upstream project. See upstream licence files for details.

## Acknowledgements

- Thanks to the Signal team for the upstream codebase and to the maintainers of related forks whose build and packaging practices informed this approach.

---

For the upstream documentation and build notes, see **`README-SIGNAL.md`**.
