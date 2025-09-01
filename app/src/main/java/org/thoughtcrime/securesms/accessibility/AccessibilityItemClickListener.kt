/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.net.Uri
import android.view.View
import androidx.lifecycle.Observer
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.stickers.StickerLocator

/**
 * Minimal ItemClickListener implementation for accessibility mode.
 * 
 * This implementation disables most complex features and provides only
 * basic message interaction for accessibility users.
 */
class AccessibilityItemClickListener : ConversationAdapter.ItemClickListener {

    // Basic click handling - only essential for accessibility
    override fun onItemClick(item: MultiselectPart) {
        // Simple message click - no reactions, no media
        // For accessibility, we just acknowledge the click
    }

    override fun onItemLongClick(itemView: View, item: MultiselectPart) {
        // Disable long press for accessibility
    }

    // Disable all complex features for accessibility
    override fun onQuoteClicked(messageRecord: MmsMessageRecord) {
        // Disabled for accessibility
    }

    override fun onLinkPreviewClicked(linkPreview: LinkPreview) {
        // Disabled for accessibility
    }

    override fun onQuotedIndicatorClicked(messageRecord: MessageRecord) {
        // Disabled for accessibility
    }

    override fun onMoreTextClicked(conversationRecipientId: RecipientId, messageId: Long, isMms: Boolean) {
        // Disabled for accessibility
    }

    override fun onStickerClicked(stickerLocator: StickerLocator) {
        // Disabled for accessibility
    }

    override fun onViewOnceMessageClicked(messageRecord: MmsMessageRecord) {
        // Disabled for accessibility
    }

    override fun onSharedContactDetailsClicked(contact: Contact, avatarTransitionView: View) {
        // Disabled for accessibility
    }

    override fun onAddToContactsClicked(contact: Contact) {
        // Disabled for accessibility
    }

    override fun onMessageSharedContactClicked(choices: List<Recipient>) {
        // Disabled for accessibility
    }

    override fun onInviteSharedContactClicked(choices: List<Recipient>) {
        // Disabled for accessibility
    }

    override fun onReactionClicked(multiselectPart: MultiselectPart, messageId: Long, isMms: Boolean) {
        // Disabled for accessibility
    }

    override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) {
        // Disabled for accessibility
    }

    override fun onMessageWithErrorClicked(messageRecord: MessageRecord) {
        // Disabled for accessibility
    }

    override fun onMessageWithRecaptchaNeededClicked(messageRecord: MessageRecord) {
        // Disabled for accessibility
    }

    override fun onIncomingIdentityMismatchClicked(recipientId: RecipientId) {
        // Disabled for accessibility
    }

    override fun onRegisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) {
        // Disabled for accessibility
    }

    override fun onUnregisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) {
        // Disabled for accessibility
    }

    override fun onVoiceNotePause(uri: Uri) {
        // Disabled for accessibility
    }

    override fun onVoiceNotePlay(uri: Uri, messageId: Long, position: Double) {
        // Disabled for accessibility
    }

    override fun onSingleVoiceNotePlay(uri: Uri, messageId: Long, position: Double) {
        // Disabled for accessibility
    }

    override fun onVoiceNoteSeekTo(uri: Uri, position: Double) {
        // Disabled for accessibility
    }

    override fun onVoiceNotePlaybackSpeedChanged(uri: Uri, speed: Float) {
        // Disabled for accessibility
    }

    override fun onGroupMigrationLearnMoreClicked(membershipChange: GroupMigrationMembershipChange) {
        // Disabled for accessibility
    }

    override fun onChatSessionRefreshLearnMoreClicked() {
        // Disabled for accessibility
    }

    override fun onBadDecryptLearnMoreClicked(author: RecipientId) {
        // Disabled for accessibility
    }

    override fun onSafetyNumberLearnMoreClicked(recipient: Recipient) {
        // Disabled for accessibility
    }

    override fun onJoinGroupCallClicked() {
        // Disabled for accessibility
    }

    override fun onInviteFriendsToGroupClicked(groupId: GroupId.V2) {
        // Disabled for accessibility
    }

    override fun onEnableCallNotificationsClicked() {
        // Disabled for accessibility
    }

    override fun onPlayInlineContent(conversationMessage: ConversationMessage?) {
        // Disabled for accessibility
    }

    override fun onInMemoryMessageClicked(messageRecord: InMemoryMessageRecord) {
        // Disabled for accessibility
    }

    override fun onViewGroupDescriptionChange(groupId: GroupId?, description: String, isMessageRequestAccepted: Boolean) {
        // Disabled for accessibility
    }

    override fun onChangeNumberUpdateContact(recipient: Recipient) {
        // Disabled for accessibility
    }

    override fun onChangeProfileNameUpdateContact(recipient: Recipient) {
        // Disabled for accessibility
    }

    override fun onCallToAction(action: String) {
        // Disabled for accessibility
    }

    override fun onDonateClicked() {
        // Disabled for accessibility
    }

    override fun onBlockJoinRequest(recipient: Recipient) {
        // Disabled for accessibility
    }

    override fun onRecipientNameClicked(target: RecipientId) {
        // Disabled for accessibility
    }

    override fun onInviteToSignalClicked() {
        // Disabled for accessibility
    }

    override fun onActivatePaymentsClicked() {
        // Disabled for accessibility
    }

    override fun onSendPaymentClicked(recipientId: RecipientId) {
        // Disabled for accessibility
    }

    override fun onScheduledIndicatorClicked(view: View, conversationMessage: ConversationMessage) {
        // Disabled for accessibility
    }

    override fun onUrlClicked(url: String): Boolean {
        // Disabled for accessibility
        return false
    }

    override fun onViewGiftBadgeClicked(messageRecord: MessageRecord) {
        // Disabled for accessibility
    }

    override fun onGiftBadgeRevealed(messageRecord: MessageRecord) {
        // Disabled for accessibility
    }

    override fun goToMediaPreview(parent: ConversationItem, sharedElement: View, args: MediaIntentFactory.MediaPreviewArgs) {
        // Disabled for accessibility
    }

    override fun onEditedIndicatorClicked(conversationMessage: ConversationMessage) {
        // Disabled for accessibility
    }

    override fun onShowGroupDescriptionClicked(groupName: String, description: String, shouldLinkifyWebLinks: Boolean) {
        // Disabled for accessibility
    }

    override fun onJoinCallLink(callLinkRootKey: org.signal.ringrtc.CallLinkRootKey, callLinkEpoch: org.signal.ringrtc.CallLinkEpoch?) {
        // Disabled for accessibility
    }

    override fun onShowSafetyTips(forGroup: Boolean) {
        // Disabled for accessibility
    }

    override fun onReportSpamLearnMoreClicked() {
        // Disabled for accessibility
    }

    override fun onMessageRequestAcceptOptionsClicked() {
        // Disabled for accessibility
    }

    override fun onItemDoubleClick(multiselectPart: MultiselectPart) {
        // Disabled for accessibility
    }

    override fun onPaymentTombstoneClicked() {
        // Disabled for accessibility
    }

    override fun onDisplayMediaNoLongerAvailableSheet() {
        // Disabled for accessibility
    }

    override fun onShowUnverifiedProfileSheet(forGroup: Boolean) {
        // Disabled for accessibility
    }

    override fun onUpdateSignalClicked() {
        // Disabled for accessibility
    }
}
