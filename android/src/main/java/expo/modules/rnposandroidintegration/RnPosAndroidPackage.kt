package expo.modules.rnposandroidintegration

import android.content.Context
import expo.modules.core.interfaces.Package
import expo.modules.core.interfaces.ReactActivityLifecycleListener

class RnPosAndroidPackage : Package {
  override fun createReactActivityLifecycleListeners(activityContext: Context): List<ReactActivityLifecycleListener> {
    return listOf(RnPosAndroidReactActivityLifecycleListener())
  }
}