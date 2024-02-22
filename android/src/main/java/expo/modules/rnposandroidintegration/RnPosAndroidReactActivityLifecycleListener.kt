package expo.modules.rnposandroidintegration

import android.content.Intent
import android.util.Log
import expo.modules.core.interfaces.ReactActivityLifecycleListener

class RnPosAndroidReactActivityLifecycleListener : ReactActivityLifecycleListener {

  // https://docs.expo.dev/modules/android-lifecycle-listeners/#activity-lifecycle-listeners

  override fun onNewIntent(intent: Intent?): Boolean {
    // on waking up from callback process results
    // this intent is only called if target App (Pay App) is properly finalized.
    if (intent != null) {
      this.processMSPMiddlewareResponse(intent)
    }
    return super.onNewIntent(intent)
  }

  private fun processMSPMiddlewareResponse(intent: Intent) {
    //retrieve intent extra data including message.
    if (intent.hasExtra("status")) {
      val status = intent.getIntExtra("status", 0)
      val message = intent.getStringExtra("message")
      this.handleMiddlewareCallback(status, message)
    }
  }
  

  private fun handleMiddlewareCallback(status: Int, message: String?) {
    when (status) {
      875 -> {
        this.receivedCallbackIntent(TransactionStatus.EXCEPTION)
      }

      471 -> {
        this.receivedCallbackIntent(TransactionStatus.COMPLETED)
      }

      17 -> {
        this.receivedCallbackIntent(TransactionStatus.CANCELLED)
      }

      88 -> {
        this.receivedCallbackIntent(TransactionStatus.DECLINED)
      }
    }
  }

  private fun receivedCallbackIntent(status: TransactionStatus) {
    val integrationModule = RnPosAndroidIntegrationModule()
    integrationModule.notifyAppUpdate(status)
  }
}