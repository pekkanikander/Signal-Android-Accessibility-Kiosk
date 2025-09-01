# ConversationViewModel Analysis

## 📊 Overview
- **File**: `app/src/main/java/org/thoughtcrime/securesms/conversation/v2/ConversationViewModel.kt`
- **Lines**: 632 lines
- **Git Commits**: 71 commits (moderate change frequency)
- **Base Class**: `ViewModel` (Android Architecture Components)

## 🔍 Public Interface Analysis

### **Constructor Parameters**
```kotlin
class ConversationViewModel(
  val threadId: Long,                                    // ✅ Stable - core identifier
  requestedStartingPosition: Int,                        // ✅ Stable - UI state
  initialChatColors: ChatColors,                         // ✅ Stable - theming
  private val repository: ConversationRepository,        // ✅ Stable - data access
  recipientRepository: ConversationRecipientRepository,  // ✅ Stable - contact data
  messageRequestRepository: MessageRequestRepository,    // ✅ Stable - message requests
  private val scheduledMessagesRepository: ScheduledMessagesRepository // ✅ Stable - scheduling
) : ViewModel()
```

### **Public Properties (Observable State)**

#### **Core Conversation State**
```kotlin
val recipient: Observable<Recipient>                    // ✅ STABLE - Core conversation data
val conversationThreadState: Single<ConversationThreadState> // ✅ STABLE - Thread metadata
val pagingController: ProxyPagingController<ConversationElementKey> // ✅ STABLE - Message data
```

#### **UI State**
```kotlin
val scrollButtonState: Flowable<ConversationScrollButtonState> // ✅ STABLE - Scroll UI
val showScrollButtonsSnapshot: Boolean                  // ✅ STABLE - Scroll UI
val unreadCount: Int                                    // ✅ STABLE - Unread messages
val inputReadyState: Observable<InputReadyState>        // ✅ STABLE - Input availability
```

#### **Theming & Appearance**
```kotlin
val chatColorsSnapshot: ChatColorsDrawable.ChatColorsData // ✅ STABLE - Colors
val wallpaperSnapshot: ChatWallpaper?                    // ✅ STABLE - Background
val titleViewParticipants: Observable<List<Recipient>>   // ✅ STABLE - Group participants
```

#### **Message Request State**
```kotlin
val hasMessageRequestState: Boolean                      // ✅ STABLE - Message requests
val messageRequestState: MessageRequestState             // ✅ STABLE - Request details
```

#### **Identity & Security**
```kotlin
val identityRecordsObservable: Observable<IdentityRecordsState> // ✅ STABLE - Identity verification
val identityRecordsState: IdentityRecordsState           // ✅ STABLE - Identity state
val isPushAvailable: Boolean                             // ✅ STABLE - Push availability
```

#### **Search & Navigation**
```kotlin
val searchQuery: Observable<String>                      // ✅ STABLE - Search functionality
val jumpToDateValidator: JumpToDateValidator             // ✅ STABLE - Date navigation
val backPressedState: StateFlow<BackPressedState>        // ✅ STABLE - Back navigation
```

### **Public Methods (Actions)**

#### **Message Operations**
```kotlin
fun sendMessage(...): Completable                        // ✅ STABLE - Core messaging
fun resendMessage(conversationMessage: ConversationMessage): Completable // ✅ STABLE - Resend
fun moveToMessage(messageRecord: MessageRecord): Single<Int> // ✅ STABLE - Navigation
fun moveToDate(receivedTimestamp: Long): Single<Int>     // ✅ STABLE - Date navigation
```

#### **Reactions & Interactions**
```kotlin
fun updateReaction(messageRecord: MessageRecord, emoji: String): Completable // ✅ STABLE - Reactions
fun updateCustomReaction(messageRecord: MessageRecord, hasAddedCustomEmoji: Boolean): Maybe<Unit> // ✅ STABLE - Custom reactions
```

#### **UI State Management**
```kotlin
fun setShowScrollButtonsForScrollPosition(showScrollButtons: Boolean, willScrollToBottomOnNewMessage: Boolean) // ✅ STABLE - Scroll UI
fun setLastScrolled(lastScrolledTimestamp: Long)         // ✅ STABLE - Scroll position
fun setIsReactionDelegateShowing(isReactionDelegateShowing: Boolean) // ✅ STABLE - Reaction UI
fun setIsSearchRequested(isSearchRequested: Boolean)     // ✅ STABLE - Search UI
```

#### **Identity Management**
```kotlin
fun updateIdentityRecords(): Completable                 // ✅ STABLE - Identity updates
fun resetVerifiedStatusToDefault(unverifiedIdentities: List<IdentityRecord>) // ✅ STABLE - Identity reset
```

#### **Utility Operations**
```kotlin
fun copyToClipboard(context: Context, messageParts: Set<MultiselectPart>): Maybe<CharSequence> // ✅ STABLE - Copy
fun getTemporaryViewOnceUri(mmsMessageRecord: MmsMessageRecord): Maybe<Uri> // ✅ STABLE - Media
fun canShowAsBubble(context: Context): Observable<Boolean> // ✅ STABLE - Bubble support
```

#### **Lifecycle Management**
```kotlin
override fun onCleared()                                 // ✅ STABLE - Android lifecycle
fun shouldHandleBackPressed() = isSearchRequested || isReactionDelegateShowing // ✅ STABLE - Back handling
```

## 📈 Stability Analysis

### **Change Frequency**
- **71 commits** over the file's lifetime
- **Moderate change frequency** - not extremely stable, but not constantly changing
- Most changes are **additive** (new features) rather than **breaking** (interface changes)

### **Recent Changes (Last 20 commits)**
1. **Identity key update fixes** - Internal implementation
2. **Quote preview navigation** - New feature
3. **Back pressed callback** - UI state management
4. **Avatar download blocking** - Security feature
5. **Banner system improvements** - UI enhancements
6. **Service outage handling** - Reliability improvements

### **Stability Assessment**
- **✅ High Stability**: Core messaging interface (`sendMessage`, `pagingController`)
- **✅ High Stability**: Basic state properties (`recipient`, `threadId`)
- **✅ High Stability**: UI state management (`scrollButtonState`, `inputReadyState`)
- **⚠️ Medium Stability**: Advanced features (reactions, search, identity)
- **⚠️ Medium Stability**: New features (banners, bubble support)

## 🎯 Risk Assessment for Accessibility Mode

### **Low Risk Components** (Safe to use)
```kotlin
// Core messaging - very stable
val pagingController: ProxyPagingController<ConversationElementKey>
fun sendMessage(...): Completable

// Basic state - very stable
val recipient: Observable<Recipient>
val threadId: Long
val inputReadyState: Observable<InputReadyState>

// UI state - stable
val scrollButtonState: Flowable<ConversationScrollButtonState>
val showScrollButtonsSnapshot: Boolean
val unreadCount: Int
```

### **Medium Risk Components** (Use with caution)
```kotlin
// Advanced features - moderate stability
fun updateReaction(...): Completable
val searchQuery: Observable<String>
val identityRecordsObservable: Observable<IdentityRecordsState>
```

### **High Risk Components** (Avoid for accessibility)
```kotlin
// New features - less stable
val pendingGroupJoinFlow: Flow<PendingGroupJoinRequestsBanner>
fun canShowAsBubble(context: Context): Observable<Boolean>
```

## 💡 Recommendations for Accessibility Mode

### **Option A: Minimal Integration (Lowest Risk)**
```kotlin
// Use only the most stable components
class AccessibilityModeViewModel : ViewModel() {
  private val conversationViewModel: ConversationViewModel

  // Only expose what we need
  val messages = conversationViewModel.pagingController
  val recipient = conversationViewModel.recipient
  val inputReady = conversationViewModel.inputReadyState

  fun sendMessage(text: String) = conversationViewModel.sendMessage(...)
}
```

### **Option B: Moderate Integration (Balanced Risk)**
```kotlin
// Use stable components + some UI state
class AccessibilityModeViewModel : ViewModel() {
  private val conversationViewModel: ConversationViewModel

  // Core messaging
  val messages = conversationViewModel.pagingController
  val recipient = conversationViewModel.recipient

  // UI state for accessibility
  val scrollState = conversationViewModel.scrollButtonState
  val unreadCount = conversationViewModel.unreadCount
  val inputReady = conversationViewModel.inputReadyState

  // Core actions
  fun sendMessage(text: String) = conversationViewModel.sendMessage(...)
  fun setLastScrolled(timestamp: Long) = conversationViewModel.setLastScrolled(timestamp)
}
```

### **Option C: Full Integration (Highest Risk)**
```kotlin
// Use most components (not recommended for accessibility)
class AccessibilityModeViewModel : ViewModel() {
  private val conversationViewModel: ConversationViewModel

  // Delegate most functionality
  val messages = conversationViewModel.pagingController
  val recipient = conversationViewModel.recipient
  val scrollState = conversationViewModel.scrollButtonState
  val searchQuery = conversationViewModel.searchQuery
  val identityRecords = conversationViewModel.identityRecordsObservable

  // Delegate most actions
  fun sendMessage(...) = conversationViewModel.sendMessage(...)
  fun updateReaction(...) = conversationViewModel.updateReaction(...)
  fun moveToMessage(...) = conversationViewModel.moveToMessage(...)
}
```

## 🎯 Final Recommendation

**Recommend Option B (Moderate Integration)** because:

1. **Core messaging is very stable** - `pagingController`, `sendMessage`, `recipient`
2. **UI state is stable** - `scrollButtonState`, `inputReadyState`, `unreadCount`
3. **Avoids unstable features** - reactions, search, identity management
4. **Provides good functionality** - real-time updates, proper messaging
5. **Maintains control** - we can still customize for accessibility needs

**Key Benefits:**
- ✅ Real-time message updates via `pagingController`
- ✅ Proper message sending via `sendMessage`
- ✅ UI state management via `scrollButtonState`
- ✅ Low risk of breaking changes
- ✅ Maintains accessibility customization ability

## 🚀 Practical Implementation Plan

### **Phase 1: Study ConversationViewModel Constructor**
```kotlin
// Current constructor requires these dependencies:
ConversationViewModel(
  threadId: Long,
  requestedStartingPosition: Int,
  initialChatColors: ChatColors,
  repository: ConversationRepository,
  recipientRepository: ConversationRecipientRepository,
  messageRequestRepository: MessageRequestRepository,
  scheduledMessagesRepository: ScheduledMessagesRepository
)
```

**Next Steps:**
1. **Study how these dependencies are created** in `ConversationFragment`
2. **Check if we can reuse the same dependency injection** pattern
3. **Verify if `AppDependencies` provides these repositories**

### **Phase 2: Refactor AccessibilityModeViewModel**
```kotlin
// Proposed new structure:
class AccessibilityModeViewModel(
  private val threadId: Long,
  private val conversationViewModel: ConversationViewModel
) : ViewModel() {

  // Expose only what we need
  val messages = conversationViewModel.pagingController
  val recipient = conversationViewModel.recipient
  val scrollState = conversationViewModel.scrollButtonState
  val inputReady = conversationViewModel.inputReadyState

  // Custom accessibility methods
  fun sendMessage(text: String) = conversationViewModel.sendMessage(...)
  fun setLastScrolled(timestamp: Long) = conversationViewModel.setLastScrolled(timestamp)

  // Accessibility-specific features
  fun autoScrollToBottom() { /* Custom implementation */ }
  fun simplifyMessageDisplay() { /* Custom implementation */ }
}
```

### **Phase 3: Update Fragment Integration**
```kotlin
// In AccessibilityModeFragment:
class AccessibilityModeFragment : Fragment() {
  private val viewModel: AccessibilityModeViewModel by viewModels {
    AccessibilityModeViewModelFactory(threadId)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Observe ConversationViewModel's pagingController
    viewModel.messages.observe(viewLifecycleOwner) { messages ->
      // Update RecyclerView with real-time data
    }

    // Observe recipient for UI updates
    viewModel.recipient.observe(viewLifecycleOwner) { recipient ->
      // Update conversation header
    }
  }
}
```

### **Phase 4: Benefits of This Approach**
1. **Real-time Updates**: `pagingController` provides live message updates
2. **Proper Messaging**: `sendMessage` handles all Signal protocols correctly
3. **UI State Management**: `scrollButtonState` helps with accessibility scrolling
4. **Low Maintenance**: Most complex logic is handled by Signal's stable components
5. **Future-Proof**: Core messaging interface is unlikely to change

### **Phase 5: Risk Mitigation**
1. **Test the integration** with our existing test framework
2. **Monitor for breaking changes** in ConversationViewModel
3. **Keep our custom ViewModel** as a wrapper to isolate changes
4. **Document dependencies** clearly for future maintenance

## 🎯 Immediate Next Steps

1. **Study ConversationFragment** to understand how ConversationViewModel is instantiated
2. **Check AppDependencies** to see if we can reuse the same repository instances
3. **Create a simple test** to verify we can create ConversationViewModel successfully
4. **Plan the refactoring** of AccessibilityModeViewModel to use ConversationViewModel
5. **Update the Fragment** to observe ConversationViewModel's observables

**Would you like me to start with any of these steps?**
