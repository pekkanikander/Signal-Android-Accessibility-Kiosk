package org.thoughtcrime.securesms.deeplinks;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.accessibility.AccessibilityModeRouter;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;

public class DeepLinkEntryActivity extends PassphraseRequiredActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    // Route first so that deep links respect Accessibility Mode
    AccessibilityModeRouter.INSTANCE.routeIfNeeded(this);

    Intent intent = MainActivity.clearTop(this);
    Uri    data   = getIntent().getData();
    intent.setData(data);
    startActivity(intent);
  }
}
