/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.accessibility

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.testutil.ShadowSqlCipherLibraryLoader

/**
 * Basic test cases for AccessibilityModeActivity.
 * 
 * Following ChatGPT-5's recommendation:
 * - Use ShadowSqlCipherLibraryLoader to prevent native library loading
 * - Test activity structure without triggering full application initialization
 * - Keep database-dependent tests in instrumentation tests
 * - Focus on compilation and basic structure tests
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    shadows = [ShadowSqlCipherLibraryLoader::class]
)
class AccessibilityModeActivityTest {

    @Test
    fun `test AccessibilityModeActivity class exists and extends AppCompatActivity`() {
        // Given - Activity class exists
        
        // When & Then
        // Verify it extends the correct base class
        val activityClass = AccessibilityModeActivity::class.java
        assert(androidx.appcompat.app.AppCompatActivity::class.java.isAssignableFrom(activityClass))
        assert(activityClass.name == "org.thoughtcrime.securesms.accessibility.AccessibilityModeActivity")
    }

    @Test
    fun `test AccessibilityModeActivity has correct package structure`() {
        // Given - Activity class exists
        
        // When & Then
        // Verify it's in the correct package
        val activityClass = AccessibilityModeActivity::class.java
        assert(activityClass.`package`?.name == "org.thoughtcrime.securesms.accessibility")
    }
}
