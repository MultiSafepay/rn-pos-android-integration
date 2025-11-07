package expo.modules.rnposandroidintegration

import android.app.Activity
import android.content.Intent
import android.util.Log
import expo.modules.core.interfaces.ReactActivityLifecycleListener
import java.util.*

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

  // Not an interface override (ReactActivityLifecycleListener may not declare this); invoked via ActivityEventListener in module.
  fun handleActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
    Log.w("pos-app-integration", "SoftPOS result intent data; requestCode=$requestCode, resultCode=$resultCode activity=${activity?.javaClass?.simpleName}")

    if (requestCode != SOFT_POS_REQUEST_CODE) {
      return
    }

    if (data != null) {
      this.processMSPMiddlewareResponse(data)
    } else {
      Log.w("pos-app-integration", "SoftPOS result missing intent data; resultCode=$resultCode")
    }
  }

  private fun processMSPMiddlewareResponse(intent: Intent) {
    //retrieve intent extra data including message.
    if (intent.hasExtra("status")) {
      val status = intent.getIntExtra("status", 0)
      val message = intent.getStringExtra("message")
      Log.d("pos-app-integration", "Received SoftPOS callback via status=$status message=$message")
      this.handleMiddlewareCallback(status, message)
      return
    }

    if (intent.hasExtra("result_status")) {
      val resultStatus = intent.getStringExtra("result_status")?.lowercase(Locale.ROOT)
      val message = intent.getStringExtra("message")
      val description = intent.getStringExtra("description")

      Log.d("pos-app-integration", "Received SoftPOS activity result result_status=$resultStatus message=$message description=$description")

      when (resultStatus) {
        "success" -> Notifier.onTransactionStatusChanged(TransactionStatus.COMPLETED)
        "cancelled" -> Notifier.onTransactionStatusChanged(TransactionStatus.CANCELLED)
        "declined" -> Notifier.onTransactionStatusChanged(TransactionStatus.DECLINED)
        else -> Notifier.onTransactionStatusChanged(TransactionStatus.EXCEPTION)
      }
      return
    }

    Log.w("pos-app-integration", "SoftPOS callback intent missing expected extras")
  }
  

  private fun handleMiddlewareCallback(status: Int, message: String?) {
    Log.d("pos-app-integration", "Middleware callback status=$status message=$message")
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
    Notifier.onTransactionStatusChanged(status)
  }
}