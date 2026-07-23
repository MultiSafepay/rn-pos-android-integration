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
      if (this.hasCallbackPayload(intent)) {
        this.processMSPMiddlewareResponse(intent)
      } else {
        Log.w("pos-app-integration", "SoftPOS onNewIntent callback did not include extras or data")
      }
    } else {
      Log.w("pos-app-integration", "SoftPOS onNewIntent callback intent is null")
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
    if (safeHasExtra(intent, "status")) {
      val status = safeIntExtra(intent, "status") ?: 0
      val message = safeStringExtra(intent, "message")
      Log.d("pos-app-integration", "Received SoftPOS callback via status=$status message=$message")
      this.handleMiddlewareCallback(status, message)
      return
    }

    if (safeHasExtra(intent, "result_status")) {
      val resultStatus = safeStringExtra(intent, "result_status")?.lowercase(Locale.ROOT)
      val message = safeStringExtra(intent, "message")
      val description = safeStringExtra(intent, "description")

      Log.d("pos-app-integration", "Received SoftPOS activity result result_status=$resultStatus message=$message description=$description")

      when (resultStatus) {
        "success", "completed" -> Notifier.onTransactionStatusChanged(TransactionStatus.COMPLETED)
        "cancelled" -> Notifier.onTransactionStatusChanged(TransactionStatus.CANCELLED)
        "declined" -> Notifier.onTransactionStatusChanged(TransactionStatus.DECLINED)
        "exception" -> Notifier.onTransactionStatusChanged(TransactionStatus.EXCEPTION)
        "undefined" -> Notifier.onTransactionStatusChanged(TransactionStatus.UNDEFINED)
        else -> Notifier.onTransactionStatusChanged(TransactionStatus.EXCEPTION)
      }
      return
    }

    Log.w("pos-app-integration", "SoftPOS callback intent missing expected extras")
  }
  

  private fun hasCallbackPayload(intent: Intent): Boolean {
    if (intent.dataString != null) {
      return true
    }

    return try {
      val extras = intent.extras
      extras != null && !extras.isEmpty
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to inspect SoftPOS callback extras", error)
      true
    }
  }

  private fun safeHasExtra(intent: Intent, key: String): Boolean {
    return try {
      intent.hasExtra(key)
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to check SoftPOS extra '$key'", error)
      false
    }
  }

  private fun safeIntExtra(intent: Intent, key: String): Int? {
    return try {
      intent.getIntExtra(key, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to read SoftPOS int extra '$key'", error)
      null
    }
  }

  private fun safeStringExtra(intent: Intent, key: String): String? {
    return try {
      intent.getStringExtra(key)
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to read SoftPOS string extra '$key'", error)
      null
    }
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

      0 -> {
        this.receivedCallbackIntent(TransactionStatus.UNDEFINED)
      }
    }
  }

  private fun receivedCallbackIntent(status: TransactionStatus) {
    Notifier.onTransactionStatusChanged(status)
  }
}