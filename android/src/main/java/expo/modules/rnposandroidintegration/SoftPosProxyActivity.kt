package expo.modules.rnposandroidintegration

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * A harmless return address for SoftPOS, and nothing else.
 *
 * SoftPOS was handed the host's launcher Activity as `callback_activity`. Expo declares that
 * Activity `singleTask`, which makes it the root of its task, and Android answers an outside
 * app re-launching such a root by *resetting the task* rather than delivering an ordinary
 * SINGLE_TOP intent. The Activity survived and its ReactRootView stayed attached and correctly
 * sized -- so every check said the app was healthy -- but the surface stopped painting. That
 * blank, unresponsive screen is the "freeze".
 *
 * Completed transactions hid it: JS navigates away on COMPLETED, and the new screen repaints.
 * Cancelled and declined stay put, so the dead surface is all the merchant ever sees.
 *
 * This Activity is `singleTop` and never a task root, so re-launching it is uneventful. It
 * draws nothing and finishes immediately, handing the screen straight back to the host.
 *
 * Deliberately NOT in the launch path: SoftPOS is still started by the host Activity with
 * startActivityForResult, so the result still arrives at OnActivityResult and the push
 * transition into SoftPOS still looks standard. Launching from here would animate as a modal,
 * because this Activity is translucent.
 */
class SoftPosProxyActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    absorb(intent)
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    // Already alive and on top, so a second callback arrives here rather than in onCreate.
    absorb(intent)
  }

  @Suppress("DEPRECATION")
  private fun absorb(intent: Intent?) {
    // Forward only a callback that actually carries one of the two contracts' keys. An
    // intent without them says nothing about the transaction, and reporting it would let the
    // module's "first status wins" rule answer the payment with a guess -- and then discard
    // the real result, still on its way through onActivityResult, as a duplicate.
    val carriesResult = try {
      intent != null && (intent.hasExtra("result_status") || intent.hasExtra("status"))
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to inspect the SoftPOS callback intent", error)
      false
    }

    if (carriesResult) {
      Log.d("pos-app-integration", "SoftPOS callback landed on the proxy; forwarding it")
      RnPosAndroidReactActivityLifecycleListener().onNewIntent(intent)
    } else {
      Log.d("pos-app-integration", "SoftPOS returned via the proxy without a result; onActivityResult will answer")
    }

    finish()
    overridePendingTransition(0, 0)
  }
}
