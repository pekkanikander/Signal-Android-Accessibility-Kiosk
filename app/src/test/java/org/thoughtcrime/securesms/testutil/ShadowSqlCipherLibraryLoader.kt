/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.testutil

import android.content.Context
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader

/**
 * Shadow implementation of SqlCipherLibraryLoader that does nothing.
 * 
 * This prevents SQLCipher from trying to load native libraries in Robolectric tests,
 * which would cause UnsatisfiedLinkError since we're running on the JVM.
 */
@Implements(SqlCipherLibraryLoader::class)
class ShadowSqlCipherLibraryLoader {
    
    @Implementation
    fun load(@Suppress("UNUSED_PARAMETER") context: Context) {
        // No-op - don't try to load native SQLCipher libraries in tests
    }
}
