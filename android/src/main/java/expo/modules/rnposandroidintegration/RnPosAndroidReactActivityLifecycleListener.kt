package expo.modules.rnposandroidintegration

import android.app.Activity
import android.content.Intent
import android.util.Log
import expo.modules.core.interfaces.ReactActivityLifecycleListener
import java.util.Locale

private const val TAG = "pos-app-integration"

class RnPosAndroidReactActivityLifecycleListener : ReactActivityLifecycleListener {

  // https://docs.expo.dev/modules/android-lifecycle-listeners/#activity-lifecycle-listeners

  // The two POS apps answer over two separate, unrelated contracts and each one is
  // handled on its own channel here. Merging them is what broke declined SoftPOS
  // transactions: a result carrying both keys was parsed as a middleware callback.
  //
  //  - MSP Pay App (Sunmi): app-to-app, a new Intent carrying an int `status`.
  //  - SoftPOS: launched with startActivityForResult, answers only through
  //    onActivityResult with a String `result_status`. It never calls onNewIntent.
  //
  // See https://github.com/MultiSafepay/pos-android-integration#softpos-callback

  override fun onNewIntent(intent: Intent?): Boolean {
    // on waking up from callback process results
    // this intent is only called if target App (Pay App) is properly finalized.
    if (intent == null) {
      Log.w(TAG, "Pay App onNewIntent callback intent is null")
      return super.onNewIntent(intent)
    }

    if (safeHasExtra(intent, "status")) {
      val status = safeIntExtra(intent, "status") ?: 0
      val message = safeStringExtra(intent, "message")
      Log.d(TAG, "Received Pay App callback via status=$status message=$message")
      this.handleMiddlewareCallback(status, message)
    } else {
      Log.w(TAG, "Pay App onNewIntent callback did not include a 'status' extra")
    }

    return super.onNewIntent(intent)
  }

  // Not an interface override (ReactActivityLifecycleListener may not declare this); invoked via ActivityEventListener in module.
  // Mirrors PaymentActivity#onActivityResult in the reference integration.
  fun handleActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
    Log.d(TAG, "SoftPOS result intent data; requestCode=$requestCode, resultCode=$resultCode activity=${activity?.javaClass?.simpleName}")

    if (requestCode != SOFT_POS_REQUEST_CODE) {
      return
    }

    val resultStatus = data?.let { safeStringExtra(it, "result_status") }
    val message = data?.let { safeStringExtra(it, "message") }
    val description = data?.let { safeStringExtra(it, "description") }

    Log.d(TAG, "Received SoftPOS activity result resultCode=$resultCode result_status=$resultStatus message=$message description=$description")

    // Every path out of here reports a status. SoftPOS finishes a declined or expired
    // card with RESULT_CANCELED and puts the outcome in `result_status`, so gating the
    // read on RESULT_OK dropped exactly the results this file exists to deliver, and
    // the host's pay screen -- which only moves when an event arrives -- waited forever.
    if (resultStatus == null) {
      Log.w(TAG, "SoftPOS returned no result_status; resultCode=$resultCode, reporting CANCELLED")
      this.receivedCallbackIntent(TransactionStatus.CANCELLED)
      return
    }

    // Only `result_status` is read here. A declined transaction also carries the
    // decline code in `status`, and consulting that first sent the result down the
    // middleware branch, where the code matched nothing and the callback was dropped.
    val status = when (resultStatus.uppercase(Locale.ROOT)) {
      // A success is trusted only when the result code agrees. COMPLETED on a non-OK
      // result is self-contradictory, and marking an unpaid order paid is the
      // expensive way to be wrong.
      "COMPLETED" ->
        if (resultCode == Activity.RESULT_OK) {
          TransactionStatus.COMPLETED
        } else {
          Log.w(TAG, "SoftPOS reported COMPLETED with resultCode=$resultCode; reporting UNDEFINED")
          TransactionStatus.UNDEFINED
        }
      "CANCELLED" -> TransactionStatus.CANCELLED
      "DECLINED" -> TransactionStatus.DECLINED
      else -> {
        Log.w(TAG, "Unknown SoftPOS result_status '$resultStatus'; reporting UNDEFINED")
        TransactionStatus.UNDEFINED
      }
    }

    this.receivedCallbackIntent(status)
  }

  private fun safeHasExtra(intent: Intent, key: String): Boolean {
    return try {
      intent.hasExtra(key)
    } catch (error: RuntimeException) {
      Log.e(TAG, "Unable to check extra '$key'", error)
      false
    }
  }

  private fun safeIntExtra(intent: Intent, key: String): Int? {
    return try {
      intent.getIntExtra(key, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
    } catch (error: RuntimeException) {
      Log.e(TAG, "Unable to read int extra '$key'", error)
      null
    }
  }

  private fun safeStringExtra(intent: Intent, key: String): String? {
    return try {
      intent.getStringExtra(key)
    } catch (error: RuntimeException) {
      Log.e(TAG, "Unable to read string extra '$key'", error)
      null
    }
  }

  private fun handleMiddlewareCallback(status: Int, message: String?) {
    Log.d(TAG, "Middleware callback status=$status message=$message")
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

      // A status that maps to nothing used to be dropped silently, leaving the app
      // waiting on a transaction that never resolves.
      else -> {
        Log.w(TAG, "Unknown middleware status code $status; reporting UNDEFINED")
        this.receivedCallbackIntent(TransactionStatus.UNDEFINED)
      }
    }
  }

  private fun receivedCallbackIntent(status: TransactionStatus) {
    Log.d(TAG, "Reporting transaction status=$status")
    Notifier.onTransactionStatusChanged(status)
  }
}
