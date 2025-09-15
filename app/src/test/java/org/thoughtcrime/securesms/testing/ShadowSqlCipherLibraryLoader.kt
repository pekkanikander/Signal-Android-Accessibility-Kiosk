package org.thoughtcrime.securesms.testing

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.thoughtcrime.securesms.database.SqlCipherLibraryLoader

@Implements(SqlCipherLibraryLoader::class)
class ShadowSqlCipherLibraryLoader {
  @Implementation
  fun load() {
    // no-op: prevent loading native sqlcipher during Robolectric unit tests
  }
}
