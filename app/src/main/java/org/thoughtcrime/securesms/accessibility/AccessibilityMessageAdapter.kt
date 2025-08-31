/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying messages in the accessibility conversation interface.
 * 
 * Features:
 * - Simple message display with text and timestamp
 * - Different styling for sent vs received messages
 * - Large, high-contrast text for accessibility
 */
class AccessibilityMessageAdapter : RecyclerView.Adapter<AccessibilityMessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<AccessibilityMessage>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun updateMessages(newMessages: List<AccessibilityMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_accessibility_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.message_text)
        private val messageTime: TextView = itemView.findViewById(R.id.message_time)

        fun bind(message: AccessibilityMessage) {
            messageText.text = message.text
            messageTime.text = dateFormat.format(Date(message.timestamp))
            
            // Set different styling for sent vs received messages
            if (message.isFromSelf) {
                // Sent message - align right, different background
                itemView.setBackgroundResource(R.drawable.bg_message_sent)
                messageText.setTextColor(itemView.context.getColor(android.R.color.white))
            } else {
                // Received message - align left, different background
                itemView.setBackgroundResource(R.drawable.bg_message_received)
                messageText.setTextColor(itemView.context.getColor(android.R.color.black))
            }
        }
    }
}
