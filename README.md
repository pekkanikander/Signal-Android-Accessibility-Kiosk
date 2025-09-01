# Signal Care Mode

A Signal feature that provides a **simplified, single-conversation experience**
for users with reduced cognitive capacity (elderly, dementia, etc.)
while maintaining full Signal security and functionality.

> The original upstream README is preserved as **`README-SIGNAL.md`**.

---

## For Caregivers & Family Members

### What is Signal Care Mode?

Signal Care Mode transforms Signal into a **simple messaging app** for your loved one.
Instead of seeing all their conversations, they see only **one selected conversation** -
typically with their primary caregiver or family member, or a group chat with a few people.

### How It Works

1. **Setup**: You enable Care Mode in Signal Settings and select which conversation to show
2. **Simplified Interface**: Your loved one sees only their conversation - no complex menus, popups, or multiple chats
3. **Easy Communication**: Large, clear buttons for sending messages and voice notes
4. **No Confusion**: No back buttons, settings, popups, or other apps to accidentally tap

### Key Benefits

- **Reduces Cognitive Load**: No complex navigation or multiple conversations
- **Maintains Privacy**: Uses Signal's secure messaging protocol
- **Familiar Technology**: Works with existing Signal contacts and groups
- **Easy Setup**: Simple toggle in Signal settings

### Getting Started

1. Open Signal on your loved one's device
2. Go to **Settings** → **Accessibility Mode**
3. Select the conversation you want them to see
4. Select **Enable Care Mode**
5. Exit Settings - Signal will switch to Care Mode
6. Your loved one can now use the simplified interface

### Customization

You can adjust, using the default settings:
- **Text size** for better readability
- **Theme** for dark, light, or dynamic
New settings, to be implemented:
- **Contrast** for visual clarity
- **Touch sensitivity** for easier interaction
- **Voice note settings** for audio communication

### Exiting Care Mode

To return to normal Signal:
- Use the exit gesture to return to Settings
- Go to **Accessibility Mode** → **Disable Care Mode**

---

## For Engineers

### Technical Overview

This implementation adds a **parallel accessibility interface** to Signal without modifying existing functionality.
The approach maintains Signal's user experience and security model while providing a simplified user experience.

### Architecture

**Parallel Interface Design:**
- New `AccessibilityModeActivity` and `AccessibilityModeFragment`
- Reuses existing `ConversationViewModel`, `ConversationRepository`, and backend services
- Zero changes to existing Signal functionality
- Minimal patchset that rebases cleanly onto upstream

**Component Reuse Strategy:**
- **Existing**: Message handling, crypto, network, storage, conversation logic
- **New**: Accessibility UI, simplified attachment handling, accessibility-specific navigation
- **Minimal changes** to existing Signal code

### Implementation Status

**✅ Completed:**
- Accessibility interface design and implementation
- AccessibilityMode settings integration
- Conversation display with Signal's proven components
- Message sending/receiving functionality
- Auto-scrolling to newest messages
- Proper read status management (MarkReadHelper integration)
- Notification suppression during conversation
- RxJava subscription management

**🔄 In Progress:**
- Mode switching behavior (Settings → Care Mode transition)
- Exit gesture implementation
- Additional accessibility features

### Key Technical Decisions

1. **Parallel Interface**: Instead of modifying existing UI, created separate accessibility components
2. **Component Reuse**: Direct integration of `ConversationViewModel` and `ConversationAdapterV2`
3. **Settings Integration**: Leverages existing Signal settings infrastructure
4. **Lifecycle Management**: Proper RxJava subscription cleanup and fragment lifecycle handling

### Build & Development

- **JDK:** 17 (Temurin recommended)
- **Compile/Target SDK:** 35 (Android 15)
- **Min SDK:** as per upstream
- **Build:** Gradle wrapper; Android SDK command-line tools
- **IDE:** Cursor/VS Code or Android Studio; builds are CLI-driven

Example CLI:
```bash
# From repo root
./gradlew assembleDebug
./gradlew installDebug
adb shell monkey -p org.thoughtcrime.securesms -c android.intent.category.LAUNCHER 1
```

### Development Workflow

- Fork → clone your fork → `upstream` remote tracking → small feature branches → frequent rebases
- Keep every change as isolated commits (settings integration, accessibility interface, component reuse)
- This minimizes merge friction with upstream Signal

### Security & Privacy

- **No protocol changes**: This fork does not alter Signal's protocol or servers
- **Pure UI/UX enhancement**: Only modifies the user interface layer
- **Same security model**: Maintains all Signal's encryption and privacy features
- **Production use**: Governed by Signal's own terms

### Terminology

- **Care Mode**: User-facing term for the simplified interface
- **Accessibility Mode**: Technical implementation term
- **Kiosk Features**: System-level restrictions (future implementation)

---

## Goals

- **One conversation only**: App opens directly into a preselected conversation
- **Simplified interface**: Large, high-contrast controls; no complex navigation
- **Essential actions only**: Send/receive text; record/send voice notes
- **Low cognitive load**: Removes surprises and interaction traps
- **Maintains Signal security**: No changes to protocol, registration, or cryptography

## Non-Goals

- No changes to the Signal protocol, registration, servers, or cryptography
- No alternative networks or bridges
- No theming beyond what's required for clarity and accessibility

## License

This fork remains open-source under the same license as the upstream project. See upstream license files for details.

## Acknowledgements

Thanks to the Signal team for the upstream codebase and to the maintainers of related forks whose build and packaging practices informed this approach.

---

For the upstream documentation and build notes, see **`README-SIGNAL.md`**.
