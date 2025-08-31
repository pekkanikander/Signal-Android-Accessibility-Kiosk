/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.thoughtcrime.securesms.R

/**
 * Main accessibility interface for Signal conversations.
 *
 * Features:
 * - No back button or navigation options
 * - Large, high-contrast controls
 * - Simplified conversation interface
 * - Accessibility-optimized UI
 */
class AccessibilityModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accessibility_mode)
        
        // Hide action bar to remove back button
        supportActionBar?.hide()
        
        // Add the accessibility fragment if this is the first creation
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AccessibilityModeFragment())
                .commit()
        }
    }
}
