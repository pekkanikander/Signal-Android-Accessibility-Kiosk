/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.databinding.V2ConversationItemTextOnlyIncomingBinding
import org.thoughtcrime.securesms.databinding.V2ConversationItemTextOnlyOutgoingBinding
import org.thoughtcrime.securesms.util.ThemeUtil
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Simple adapter for displaying messages in the accessibility conversation interface.
 *
 * Uses Signal's existing message layouts for consistency:
 * - v2_conversation_item_text_only_incoming.xml for received messages
 * - v2_conversation_item_text_only_outgoing.xml for sent messages
 *
 * Features:
 * - Reuses Signal's visual components and styling
 * - Simple data model for accessibility needs
 * - Large, high-contrast text for accessibility
 */
class AccessibilityMessageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<AccessibilityMessage>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val colorizer = Colorizer()

    companion object {
        private const val VIEW_TYPE_INCOMING = 0
        private const val VIEW_TYPE_OUTGOING = 1
    }

    fun updateMessages(newMessages: List<AccessibilityMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromSelf) VIEW_TYPE_OUTGOING else VIEW_TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_INCOMING -> {
                val binding = V2ConversationItemTextOnlyIncomingBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                IncomingMessageViewHolder(binding)
            }
            VIEW_TYPE_OUTGOING -> {
                val binding = V2ConversationItemTextOnlyOutgoingBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                OutgoingMessageViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is IncomingMessageViewHolder -> holder.bind(message)
            is OutgoingMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class IncomingMessageViewHolder(
        private val binding: V2ConversationItemTextOnlyIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

                        fun bind(message: AccessibilityMessage) {
            // Set message text
            binding.conversationItemBody.text = message.text

            // Set timestamp
            binding.conversationItemFooterDate.text = dateFormat.format(Date(message.timestamp))

            // Hide avatar for simplicity in accessibility mode
            binding.contactPhoto.visibility = View.GONE

            // Hide group sender name for simplicity
            binding.groupMessageSender.visibility = View.GONE

            // Hide reply indicator for simplicity
            binding.conversationItemReply.visibility = View.GONE

                        // Set proper background color for incoming messages using Signal's color system
            val backgroundColor = ContextCompat.getColor(binding.root.context, R.color.conversation_item_recv_bubble_color_normal)

            // Create rounded bubble background for incoming messages
            val bubbleBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * binding.root.context.resources.displayMetrics.density
                setColor(backgroundColor)
            }
            binding.conversationItemBodyWrapper.background = bubbleBackground

            // Set text color using Signal's colorizer
            binding.conversationItemBody.setTextColor(colorizer.getIncomingBodyTextColor(binding.root.context, false))

            // Set timestamp color using Signal's colorizer
            binding.conversationItemFooterDate.setTextColor(colorizer.getIncomingFooterTextColor(binding.root.context, false))
        }
    }

    inner class OutgoingMessageViewHolder(
        private val binding: V2ConversationItemTextOnlyOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

                        fun bind(message: AccessibilityMessage) {
            // Set message text
            binding.conversationItemBody.text = message.text

            // Set timestamp
            binding.conversationItemFooterDate.text = dateFormat.format(Date(message.timestamp))

            // Hide reply indicator for simplicity
            binding.conversationItemReply.visibility = View.GONE

            // Hide delivery status for simplicity in accessibility mode
            binding.conversationItemDeliveryStatus.visibility = View.GONE

                        // Set proper background color for outgoing messages using Signal's color system
            // Outgoing messages use the ultramarine color from Signal's settings
            val backgroundColor = ContextCompat.getColor(binding.root.context, R.color.conversation_ultramarine)

            // Create rounded bubble background for outgoing messages
            val bubbleBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * binding.root.context.resources.displayMetrics.density
                setColor(backgroundColor)
            }
            binding.conversationItemBodyWrapper.background = bubbleBackground

            // Set text color using Signal's colorizer
            binding.conversationItemBody.setTextColor(colorizer.getOutgoingBodyTextColor(binding.root.context))

            // Set timestamp color using Signal's colorizer
            binding.conversationItemFooterDate.setTextColor(colorizer.getOutgoingFooterTextColor(binding.root.context))
        }
    }
}

/**
 * Simple message representation for accessibility interface.
 */
data class AccessibilityMessage(
    val id: Long,
    val text: String,
    val isFromSelf: Boolean,
    val timestamp: Long
)
