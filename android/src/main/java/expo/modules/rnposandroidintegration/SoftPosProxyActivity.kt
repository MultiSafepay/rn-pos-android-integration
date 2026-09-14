package expo.modules.rnposandroidintegration

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Owns the SoftPOS round trip so the host's launcher Activity never has to.
 *
 * The reference integration launches SoftPOS from `PaymentActivity`, which is declared
 * `singleTop` and is not the root of its task; its `MainActivity` is the `singleTask`
 * launcher root and stays out of the payment path entirely. Cancelled and declined
 * transactions work there.
 *
 * This module had no activity of its own, so it launched SoftPOS from whatever
 * `currentActivity` happened to be -- in every Expo app that is `MainActivity`, which the
 * template declares `singleTask` and LAUNCHER, making it the task root. Handing that
 * Activity to SoftPOS as `callback_activity` means SoftPOS re-launches a `singleTask` root,
 * and Android answers by resetting the task rather than delivering a plain SINGLE_TOP
 * intent. The Activity instance survives and its ReactRootView stays attached, visible and
 * correctly sized -- which is why every probe came back healthy -- but the surface paints
 * nothing, and the host is left on a white screen.
 *
 * Standing between the two removes the host's launch mode from the equation: this Activity
 * is `singleTop`, never a task root, and is the only thing SoftPOS is ever told to call back.
 */
class SoftPosProxyActivity : Activity() {

  private val lifecycleListener = RnPosAndroidReactActivityLifecycleListener()

  companion object {
    const val EXTRA_PAYMENT_INTENT = "msp_payment_intent"
    const val EXTRA_DO_NOT_RETURN = "msp_do_not_return"
  }

  @Suppress("DEPRECATION")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Already launched before a configuration change or process restart; the result is still
    // coming to onActivityResult and launching a second payment would be far worse than
    // waiting for the first.
    if (savedInstanceState != null) {
      Diagnostics.note("1e. proxy recreated; waiting for the in-flight result")
      return
    }

    val payment = try {
      intent?.getParcelableExtra<Intent>(EXTRA_PAYMENT_INTENT)
    } catch (error: Throwable) {
      Diagnostics.note("1e. proxy could not read the payment intent: ${error.javaClass.simpleName}")
      null
    }

    if (payment == null) {
      Diagnostics.note("1e. ABORT: proxy started without a payment intent")
      finish()
      return
    }

    if (intent?.getBooleanExtra(EXTRA_DO_NOT_RETURN, false) == true) {
      Diagnostics.note("1e. doNotReturn: omitting callback extras, SoftPOS will not return automatically")
    } else {
      // The callback target is this Activity, never the host's. That is the whole point.
      payment.putExtra("callback_activity", this.javaClass.name)
      payment.putExtra("callback_package", packageName)
    }

    try {
      startActivityForResult(payment, SOFT_POS_REQUEST_CODE)
      Diagnostics.note("1c. Launched SoftPOS from proxy task=$taskId, callback req=$SOFT_POS_REQUEST_CODE")
    } catch (error: Throwable) {
      // A missing or unresolvable SoftPOS would otherwise strand the caller on a screen that
      // only moves when a status arrives.
      Log.e("pos-app-integration", "Unable to launch SoftPOS from the proxy", error)
      Diagnostics.note("1e. ABORT: could not launch SoftPOS (${error.javaClass.simpleName})")
      Notifier.onTransactionStatusChanged(TransactionStatus.EXCEPTION)
      finish()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode != SOFT_POS_REQUEST_CODE) {
      return
    }

    // Same parsing as before -- only the Activity it runs on has changed.
    lifecycleListener.handleActivityResult(this, requestCode, resultCode, data)

    // Finishing hands the screen back to the host. The module holds the status until the host
    // is resumed again, so the order here does not race the delivery.
    finish()
  }
}
