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
  //  - SoftPOS: a String `result_status`, over *either* transport.
  //
  // The Tap to Pay documentation says SoftPOS "never calls onNewIntent" and answers
  // only through onActivityResult. That is wrong: it relaunches `callback_activity` on
  // every outcome -- completed, cancelled and declined alike -- so its result arrives
  // as a new Intent. Verified on device; the old assumption is why cancelled and
  // declined transactions were dropped and froze the host app.
  //
  // So the channel does not identify the contract -- the extras do. Dispatch on which
  // extra is present, never on which callback fired. `result_status` is checked first
  // because a declined SoftPOS result *also* carries an int `status` (the decline
  // code), and reading that first is what sent it down the middleware branch, where it
  // matched nothing and was discarded.

  override fun onNewIntent(intent: Intent?): Boolean {
    // on waking up from callback process results
    // this intent is only called if target App (Pay App) is properly finalized.
    if (intent == null) {
      Log.w(TAG, "Pay App onNewIntent callback intent is null")
      return super.onNewIntent(intent)
    }

    val resultStatus = safeStringExtra(intent, "result_status")

    if (resultStatus != null) {
      val message = safeStringExtra(intent, "message")
      val description = safeStringExtra(intent, "description")
      Log.d(TAG, "Received SoftPOS callback via onNewIntent result_status=$resultStatus message=$message description=$description")
      this.receivedCallbackIntent(softPosStatusOf(resultStatus))
    } else if (safeHasExtra(intent, "status")) {
      val status = safeIntExtra(intent, "status") ?: 0
      val message = safeStringExtra(intent, "message")
      Log.d(TAG, "Received Pay App callback via status=$status message=$message")
      this.handleMiddlewareCallback(status, message)
    } else {
      // Neither contract's key is present. Usually not a POS callback at all -- the host
      // Activity is singleTask, so onNewIntent also fires on launcher relaunches and
      // deep links -- but it could equally be a result whose shape changed. Report it
      // and let the module discard it when no payment is outstanding: a stray intent
      // costs a dropped log line, whereas a real result that reports nothing leaves the
      // pay screen waiting forever.
      //
      // UNDEFINED, not CANCELLED: we do not know the outcome, and on an intent we could
      // not parse the card may well have been charged. Never assert "not paid" here.
      Log.w(TAG, "onNewIntent carried neither 'result_status' nor 'status'; reporting UNDEFINED")
      this.receivedCallbackIntent(TransactionStatus.UNDEFINED)
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

    // SoftPOS only answers RESULT_OK when the payment completed. Cancelled, declined
    // and expired-card transactions come back as RESULT_CANCELED, still carrying the
    // real outcome in `result_status`. Gating on RESULT_OK -- as the reference
    // integration does -- therefore dropped exactly the results POSSD-2379 taught this
    // method to parse, and the pay screen, which has no timeout, waited forever.
    // The result code is now only a fallback for when there is no payload to read.
    // Deliberately reports nothing. SoftPOS relaunches `callback_activity` with the
    // real outcome on every transaction, and the order in which that Intent and this
    // result arrive is not guaranteed -- both land before onResume. Reporting CANCELLED
    // from here would, whenever this won the race, claim a completed payment was
    // cancelled and then suppress the real status as a duplicate. The onNewIntent
    // callback is the authoritative one; let it answer.
    if (data == null) {
      Log.w(TAG, "SoftPOS activity result carried no intent data; resultCode=$resultCode, awaiting the onNewIntent callback instead")
      return
    }

    val resultStatus = safeStringExtra(data, "result_status")
    val message = safeStringExtra(data, "message")
    val description = safeStringExtra(data, "description")

    Log.d(TAG, "Received SoftPOS activity result result_status=$resultStatus message=$message description=$description")

    this.receivedCallbackIntent(softPosStatusOf(resultStatus))
  }

  // The SoftPOS contract, independent of which callback carried it.
  private fun softPosStatusOf(resultStatus: String?): TransactionStatus =
    when (resultStatus?.uppercase(Locale.ROOT)) {
      "COMPLETED" -> TransactionStatus.COMPLETED
      "CANCELLED" -> TransactionStatus.CANCELLED
      "DECLINED" -> TransactionStatus.DECLINED
      else -> {
        Log.w(TAG, "Unknown SoftPOS result_status '$resultStatus'; reporting UNDEFINED")
        TransactionStatus.UNDEFINED
      }
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
