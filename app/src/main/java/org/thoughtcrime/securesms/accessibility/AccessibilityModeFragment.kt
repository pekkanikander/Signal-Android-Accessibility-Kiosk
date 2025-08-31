/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.accessibility.AccessibilityMessageAdapter

/**
 * Fragment for the accessibility conversation interface.
 *
 * This fragment will host the conversation UI components:
 * - Message display area
 * - Input field
 * - Send button
 * - Accessibility-optimized controls
 */
class AccessibilityModeFragment : Fragment() {

    private val viewModel: AccessibilityModeViewModel by viewModels()
    private lateinit var messageList: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var messageAdapter: AccessibilityMessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_accessibility_mode, container, false)
    }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        messageList = view.findViewById(R.id.message_list)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)

        // Setup RecyclerView with Signal's existing message layouts
        messageList.layoutManager = LinearLayoutManager(context)
        messageAdapter = AccessibilityMessageAdapter()
        messageList.adapter = messageAdapter

        // Setup send button
        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                viewModel.sendMessage(messageText)
                messageInput.text.clear()
            }
        }

        // Get the selected thread ID from arguments and set it in the ViewModel
        arguments?.let { args ->
            val selectedThreadId = args.getLong("selected_thread_id", -1L)
            if (selectedThreadId != -1L) {
                android.util.Log.d("AccessibilityFragment", "Setting thread ID: $selectedThreadId")
                viewModel.setThreadId(selectedThreadId)
            }
        }

        // Observe ViewModel state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                // Update UI based on state
                messageAdapter.updateMessages(state.messages)
                android.util.Log.d("AccessibilityFragment", "State updated: $state")
            }
        }
    }
}
