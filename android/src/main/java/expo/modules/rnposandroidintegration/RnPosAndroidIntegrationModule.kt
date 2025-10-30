package expo.modules.rnposandroidintegration

import android.content.Intent
import android.util.Log
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

const val TRANSACTION_CHANGED_EVENT_NAME = "onTransactionChanged"

class RnPosAndroidIntegrationModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.

  private val context get() = requireNotNull(appContext.reactContext)
  private val appPackageName get() = context.packageName
  private val packageManager get() = context.packageManager

  private var posMode: PosMode = PosMode.SUNMI

  private fun buildLaunchIntent(mode: PosMode): Intent? {
    return context.packageManager?.getLaunchIntentForPackage(mode.packageName)
  }

  private fun resolveLaunchIntent(): Intent? {
    return buildLaunchIntent(posMode)
  }

  private var observer: (status: TransactionStatus) -> Unit = {}

  private enum class PosMode(val mode: String, val packageName: String) {
    SUNMI("sunmi-pos", "com.multisafepay.pos.sunmi"),
    SOFT_POS("soft-pos", "com.phonepos.mspsoftposapp");

    companion object {
      fun from(mode: String): PosMode? = values().firstOrNull { it.mode == mode }
    }
  }

  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('RnPosAndroidIntegration')` in JavaScript.
    Name("RnPosAndroidIntegration")

    // Defines event names that the module can send to JavaScript.
    Events(TRANSACTION_CHANGED_EVENT_NAME)

    OnCreate {
      observer = {
        val value: Map<String, String> = mapOf("status" to it.toString())
        this@RnPosAndroidIntegrationModule.sendEvent(TRANSACTION_CHANGED_EVENT_NAME, value)
        Log.d(TRANSACTION_CHANGED_EVENT_NAME, value.toString())
      }
      Notifier.registerObserver(observer)
    }

    OnDestroy {
      Notifier.deregisterObserver(observer)
    }

    AsyncFunction("canInitiatePayment") { promise: Promise ->
      promise.resolve(resolveLaunchIntent() != null)
    }

    // Defines a synchronous function that runs the native code on the different thread than the JavaScript runtime runs on.
    Function("initiatePayment") { currency: String, amount: Long, serializedItems: String, orderId: String, description: String, sessionId: String? ->
      val intent = resolveLaunchIntent()
      val packageName = posMode.packageName

      Log.d("pos-app-integration", "POS mode: ${posMode.mode}")
      Log.d("pos-app-integration", "packageName: $appPackageName")
      Log.d("pos-app-integration", "initiateManualPayment: amount=${amount} currency=$currency, serializedItems=$serializedItems, orderId=$orderId, description=$description, sessionId=$sessionId")

      if (intent != null) {
        if (posMode === PosMode.SUNMI) {
          // Sunmi POS Mode
          intent.setClassName(packageName, "com.multisafepay.pos.middleware.IntentActivity")
        } else {
          // SoftPOS Mode
          intent.setClassName(packageName, "com.phonepos.mspsoftposapp.ManualPayInputActivity")
        }

        intent.putExtra("package_name", appPackageName) // Callback package name
        intent.putExtra("currency", currency)
        intent.putExtra("items", serializedItems)
        intent.putExtra("amount", amount)
        intent.putExtra("order_id", orderId)
        intent.putExtra("order_description", description)
        if (sessionId != null) {
          intent.putExtra("session_id", sessionId)
        }

        context.startActivity(intent)
      } else {
        Log.d("pos-app-integration", "❌ Intent with package $packageName not found")
      }
    }

    Function("setPosMode") { mode: String ->
      val newMode = PosMode.from(mode)
      if (newMode != null) {
        posMode = newMode
      } else {
        Log.w("pos-app-integration", "Unknown POS mode: $mode")
      }
    }

    // Defines a JavaScript function that always returns a Promise and whose native code
    // is by default dispatched on the different thread than the JavaScript runtime runs on.
    AsyncFunction("setValueAsync") { value: String ->
      // Send an event to JavaScript.
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    // Enables the module to be used as a native view. Definition components that are accepted as part of
    // the view definition: Prop, Events.
    View(RnPosAndroidIntegrationView::class) {
      // Defines a setter for the `name` prop.
      Prop("name") { view: RnPosAndroidIntegrationView, prop: String ->
        println(prop)
      }
    }
  }
}
