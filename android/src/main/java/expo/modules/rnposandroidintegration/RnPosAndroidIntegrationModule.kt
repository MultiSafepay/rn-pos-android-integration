package expo.modules.rnposandroidintegration

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.Locale

const val TRANSACTION_CHANGED_EVENT_NAME = "onTransactionChanged"
internal const val SOFT_POS_REQUEST_CODE = 6017

class RnPosAndroidIntegrationModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)
  private val currentActivity get() = appContext.activityProvider?.currentActivity
  private val appPackageName get() = context.packageName

  private var posMode: PosMode = PosMode.SUNMI
  private val lifecycleListener = RnPosAndroidReactActivityLifecycleListener()

  @SuppressLint("NewApi")
  private fun buildLaunchIntent(mode: PosMode): Intent? {
    return when (mode) {
      PosMode.SUNMI -> context.packageManager?.getLaunchIntentForPackage(mode.packageName)
      PosMode.SOFT_POS -> Intent("com.phonepos.mspsoftposapp.ACTION_MANUAL_PAYMENT")
    }
  }

  private fun resolveLaunchIntent(): Intent? = buildLaunchIntent(posMode)

  private var observer: (status: TransactionStatus) -> Unit = {}

  private enum class PosMode(val mode: String, val packageName: String) {
    SUNMI("sunmi-pos", "com.multisafepay.pos.sunmi"),
    SOFT_POS("soft-pos", "com.phonepos.mspsoftposapp");

    companion object {
      fun from(mode: String): PosMode? = entries.firstOrNull { it.mode == mode }
    }
  }

  override fun definition() = ModuleDefinition {
    Name("RnPosAndroidIntegration")
    Events(TRANSACTION_CHANGED_EVENT_NAME)

    OnCreate {
      observer = {
        val value: Map<String, String> = mapOf("status" to it.toString())
        this@RnPosAndroidIntegrationModule.sendEvent(TRANSACTION_CHANGED_EVENT_NAME, value)
        Log.d(TRANSACTION_CHANGED_EVENT_NAME, value.toString())
      }
      Notifier.registerObserver(observer)
    }

    OnDestroy { Notifier.deregisterObserver(observer) }

    // Handle activity results coming back to the host Activity.
    OnActivityResult { activity, payload ->
      // payload contains requestCode, resultCode, data
      if (payload.requestCode == SOFT_POS_REQUEST_CODE && posMode == PosMode.SOFT_POS) {
        lifecycleListener.handleActivityResult(activity, payload.requestCode, payload.resultCode, payload.data)
      }
    }

    // Also handle new intents (SoftPOS may callback this way)
    OnNewIntent { intent ->
      if (posMode == PosMode.SOFT_POS) {
        lifecycleListener.onNewIntent(intent)
      }
    }

    AsyncFunction("canInitiatePayment") { promise: Promise ->
      promise.resolve(resolveLaunchIntent() != null)
    }

    Function("initiatePayment") { currency: String, amount: Long, serializedItems: String, orderId: String, description: String, sessionId: String? ->
      val intent = resolveLaunchIntent()

      Log.d("pos-app-integration", "POS mode: ${posMode.mode}")
      Log.d("pos-app-integration", "initiateManualPayment: amount=$amount currency=$currency serializedItems=$serializedItems orderId=$orderId description=$description sessionId=$sessionId")

      if (intent == null) {
        Log.d("pos-app-integration", "❌ Launch intent null for mode ${posMode.mode}")
        return@Function
      }

      intent.putExtra("package_name", appPackageName)
      intent.putExtra("order_id", orderId)
      intent.putExtra("order_description", description)
      intent.putExtra("items", serializedItems)
      intent.putExtra("currency", currency)

      if (sessionId != null){
        intent.putExtra("session_id", sessionId)
      }

      if (posMode == PosMode.SUNMI) {
        intent.setClassName("com.multisafepay.pos.sunmi", "com.multisafepay.pos.middleware.IntentActivity")
        intent.putExtra("amount", amount)
        context.startActivity(intent)
      } else {
        val activity = currentActivity
        val activityClass = activity?.componentName?.className
        if (activity == null || activityClass == null) {
          Log.w("pos-app-integration", "SoftPOS requires a foreground activity; aborting launch")
          return@Function
        }
        intent.setClassName("com.phonepos.mspsoftposapp", "com.phonepos.mspsoftposapp.ManualPayInputActivity")
        intent.putExtra("amount", String.format(Locale.US, "%.2f", amount / 100.0))
        intent.putExtra("skip_manual_input", true)
        intent.putExtra("callback_activity", activityClass)
        intent.putExtra("callback_package", appPackageName)

        activity.startActivityForResult(intent, SOFT_POS_REQUEST_CODE)
      }
    }

    Function("setPosMode") { mode: String ->
      PosMode.from(mode)?.let { posMode = it } ?: Log.w("pos-app-integration", "Unknown POS mode: $mode")
    }

    AsyncFunction("setValueAsync") { value: String ->
      sendEvent("onChange", mapOf("value" to value))
    }

    View(RnPosAndroidIntegrationView::class) {
      Prop("name") { view: RnPosAndroidIntegrationView, prop: String -> println(prop) }
    }
  }
}
