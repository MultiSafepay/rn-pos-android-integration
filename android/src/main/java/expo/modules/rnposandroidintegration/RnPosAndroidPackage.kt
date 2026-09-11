package expo.modules.rnposandroidintegration

import android.content.Context
import expo.modules.core.interfaces.Package
import expo.modules.core.interfaces.ReactActivityLifecycleListener

// NOTE: this Package is deliberately *not* declared in `expo-module.config.json`
// (`android.packages`). RnPosAndroidIntegrationModule owns a listener instance and
// forwards OnNewIntent/OnActivityResult to it; autolinking this Package as well would
// put a second listener on the same intents, and each one keeps its own duplicate
// bookkeeping — so every SoftPOS callback would be reported to JS twice.
class RnPosAndroidPackage : Package {
  override fun createReactActivityLifecycleListeners(activityContext: Context): List<ReactActivityLifecycleListener> {
    return listOf(RnPosAndroidReactActivityLifecycleListener())
  }
}