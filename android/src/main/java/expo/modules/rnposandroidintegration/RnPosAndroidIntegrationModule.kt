package expo.modules.rnposandroidintegration

import android.util.Log
import androidx.core.os.bundleOf
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
  private val intent get() = context.packageManager?.getLaunchIntentForPackage("com.multisafepay.pos.sunmi")

  private var observer: (status: TransactionStatus) -> Unit = {}

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
      if (intent != null) {
        promise.resolve(true)
      } else {
        promise.resolve(false)
      }
    }

    // Defines a synchronous function that runs the native code on the different thread than the JavaScript runtime runs on.
    // Function("initiatePayment") { currency: String, amount: Int, orderId: String, description: String, sessionId: String? ->
    Function("initiatePayment") { currency: String, serializedItems: String, orderId: String, description: String, sessionId: String? ->
      val intent = intent
      val packageName = intent?.`package`

      Log.d("pos-app-integration", "packageName: $appPackageName")
      //Log.d("pos-app-integration", "initiateManualPayment: currency=$currency, amount=$amount, orderId=$orderId, description=$description, sessionId=$sessionId")
      Log.d("pos-app-integration", "initiateManualPayment: currency=$currency, serializedItems=$serializedItems, orderId=$orderId, description=$description, sessionId=$sessionId")

      if (packageName != null) {
        intent.setClassName(packageName, "com.multisafepay.pos.middleware.IntentActivity")

        intent.putExtra("package_name", appPackageName) // Callback package name
        intent.putExtra("currency", currency)
        intent.putExtra("items", serializedItems)
        //intent.putExtra("amount", amount)
        intent.putExtra("order_id", orderId)
        intent.putExtra("order_description", description)
        if (sessionId != null) {
          intent.putExtra("session_id", sessionId)
        }

        context.startActivity(intent)
      } else {
        Log.d("pos-app-integration", "❌ Intent with package not found")
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
