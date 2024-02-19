package expo.modules.rnposandroidintegration

import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise

class RnPosAndroidIntegrationModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.

  private val context get() = requireNotNull(appContext.reactContext)
  private val appPackageName get() = context.packageName
  private val intent get() = context.packageManager?.getLaunchIntentForPackage("com.multisafepay.pos.sunmi")

  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('RnPosAndroidIntegration')` in JavaScript.
    Name("RnPosAndroidIntegration")

    // Sets constant properties on the module. Can take a dictionary or a closure that returns a dictionary.
    Constants(
      "PI" to Math.PI
    )

    // Defines event names that the module can send to JavaScript.
    Events("onChange")

    // Defines a JavaScript synchronous function that runs the native code on the JavaScript thread.
    Function("hello") {
      "Hello world! 👋"
    }

    AsyncFunction("canInitiatePayment") { promise: Promise ->
      if (intent != null) {
        promise.resolve(true)
      } else {
        promise.resolve(false)
      }
    }

    // Defines a synchronous function that runs the native code on the different thread than the JavaScript runtime runs on.
    Function("initiatePayment") { currency: String, amount: Int, orderId: String, description: String, sessionId: String? ->
      val intent = intent
      val packageName = intent?.`package`

      Log.d("pos-app-integration", "packageName: $appPackageName")
      Log.d("pos-app-integration", "initiateManualPayment: currency=$currency, amount=$amount, orderId=$orderId, description=$description, sessionId=$sessionId")

      if (packageName != null) {
        intent.setClassName(packageName, "com.multisafepay.pos.middleware.IntentActivity")

        intent.putExtra("package_name", appPackageName) // Callback package name
        intent.putExtra("currency", currency)
        //intent.putExtra("items", serializedItems)
        intent.putExtra("amount", amount)
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
    
    // // Defines a synchronous function that runs the native code on the different thread than the JavaScript runtime runs on.
    // Function("initiateManualPayment") { orderId: String, description: String, amount: Int  ->
    //   val intent = intent
    //   val packageName = intent?.`package`

    //   Log.d("pos-app-integration", "packageName: $appPackageName")
    //   Log.d("pos-app-integration", "initiateManualPayment")

    //   if (packageName != null) {
    //     intent.setClassName(packageName, "com.multisafepay.pos.middleware.IntentActivity")

    //     intent.putExtra("package_name", appPackageName) // Callback package name
    //     intent.putExtra("currency", "EUR")
    //     //intent.putExtra("items", serializedItems)
    //     intent.putExtra("amount", amount)
    //     intent.putExtra("order_id", orderId)
    //     intent.putExtra("order_description", description)
    //     intent.putExtra("session_id", )

    //     context.startActivity(intent)
    //   } else {
    //     Log.d("pos-app-integration", "❌ Intent with package not found")
    //   }
    // }

    // // Defines a synchronous function that runs the native code on the different thread than the JavaScript runtime runs on.
    // Function("initiateRemotePayment") { sessionId: String ->
    //   val intent = intent
    //   val packageName = intent?.`package`

    //   Log.d("pos-app-integration", "packageName: $appPackageName")
    //   Log.d("pos-app-integration", "initiateRemotePayment")

    //   if (packageName != null) {
    //     intent.setClassName(packageName, "com.multisafepay.pos.middleware.IntentActivity")

    //     intent.putExtra("package_name", packageName) // Callback package name
    //     intent.putExtra("session_id", sessionId)

    //     context.startActivity(intent)
    //   } else {
    //     Log.d("pos-app-integration", "❌ Intent with package not found")
    //   }
    // }

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
