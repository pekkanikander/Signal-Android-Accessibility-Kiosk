/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.thoughtcrime.securesms.testutil.MockAppDependenciesRule

/**
 * Basic test cases for AccessibilityModeActivity
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityModeActivityTest {

    @Test
    fun `test AccessibilityModeActivity launches successfully`() {
        // Given
        val intent = Intent(ApplicationProvider.getApplicationContext(), AccessibilityModeActivity::class.java)
        
        // When & Then
        ActivityScenario.launch<AccessibilityModeActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // Verify activity is created and displayed
                assert(activity.isFinishing.not())
            }
        }
    }

    @Test
    fun `test AccessibilityModeActivity has no back button`() {
        // Given
        val intent = Intent(ApplicationProvider.getApplicationContext(), AccessibilityModeActivity::class.java)
        
        // When & Then
        ActivityScenario.launch<AccessibilityModeActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                // Verify no back button is shown
                assert(activity.supportActionBar?.isShowing != true)
            }
        }
    }
}
