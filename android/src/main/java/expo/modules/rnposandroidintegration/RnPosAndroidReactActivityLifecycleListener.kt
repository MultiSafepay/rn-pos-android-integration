package expo.modules.rnposandroidintegration

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.util.Log
import expo.modules.core.interfaces.ReactActivityLifecycleListener
import java.util.*

class RnPosAndroidReactActivityLifecycleListener : ReactActivityLifecycleListener {

  // https://docs.expo.dev/modules/android-lifecycle-listeners/#activity-lifecycle-listeners

  // DEBUG: keep a reference to the foreground activity so we have a context to
  // show the payload alert from. Populated by the lifecycle callbacks below.
  private var currentActivity: Activity? = null

  override fun onResume(activity: Activity) {
    this.currentActivity = activity
  }

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
    // DEBUG: surface the raw intent payload as an on-screen alert before running
    // the normal handling below. Works in release builds (no debugger needed).
    // Remove this call once the external team's payload has been verified.
    this.showIntentPayloadAlert(intent)

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
  

  // DEBUG: dumps every extra (plus action / data) from the incoming intent into
  // a readable AlertDialog so the payload can be inspected on-device. This does
  // not alter the normal flow — the existing handling still runs afterwards.
  private fun showIntentPayloadAlert(intent: Intent) {
    val activity = this.currentActivity
    if (activity == null || activity.isFinishing) {
      Log.w("pos-app-integration", "No activity available to show intent payload alert")
      return
    }

    val payload = StringBuilder()
    payload.append("action = ${intent.action}\n")
    payload.append("dataString = ${intent.dataString}\n")

    // Explicitly surface the fields the handler cares about, with their raw
    // runtime type. `extras.get(key)` returns the value as its real type, so a
    // String "status" vs an Int "status" is immediately visible here.
    val extras = intent.extras
    payload.append("--- key fields ---\n")
    payload.append("hasExtra(status)        = ${intent.hasExtra("status")}\n")
    @Suppress("DEPRECATION")
    val rawStatus = extras?.get("status")
    payload.append("status                  = $rawStatus  (${rawStatus?.javaClass?.simpleName ?: "null"})\n")
    payload.append("hasExtra(result_status) = ${intent.hasExtra("result_status")}\n")
    @Suppress("DEPRECATION")
    val rawResultStatus = extras?.get("result_status")
    payload.append("result_status           = $rawResultStatus  (${rawResultStatus?.javaClass?.simpleName ?: "null"})\n")
    payload.append("message                 = ${intent.getStringExtra("message")}\n")
    payload.append("description             = ${intent.getStringExtra("description")}\n")

    payload.append("--- all extras ---\n")
    if (extras != null && !extras.isEmpty) {
      for (key in extras.keySet()) {
        @Suppress("DEPRECATION")
        val value = extras.get(key)
        payload.append("$key = $value  (${value?.javaClass?.simpleName ?: "null"})\n")
      }
    } else {
      payload.append("(no extras)\n")
    }

    val message = payload.toString()
    Log.d("pos-app-integration", "Intent payload:\n$message")

    activity.runOnUiThread {
      AlertDialog.Builder(activity)
        .setTitle("SoftPOS Intent Payload (debug)")
        .setMessage(message)
        .setPositiveButton("OK", null)
        .setCancelable(true)
        .show()
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
    }
  }

  private fun receivedCallbackIntent(status: TransactionStatus) {
    Notifier.onTransactionStatusChanged(status)
  }
}