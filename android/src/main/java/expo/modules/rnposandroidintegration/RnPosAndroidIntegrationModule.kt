package expo.modules.rnposandroidintegration

import android.app.Activity
import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition


class RnPosAndroidIntegrationModule : Module() {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
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
    
    // Defines a synchronous function that runs the native code on the different thread than the JavaScript runtime runs on.
    Function("initiatePayment") { orderId: String, description: String, serializedItems: String ->
      //val packageManager = appContext.activityProvider?.currentActivity?.packageManager
      val reactContext = appContext.reactContext
      val packageManager = reactContext?.packageManager
      val intent = packageManager?.getLaunchIntentForPackage("com.multisafepay.pos.sunmi")
      val packageName = intent?.`package`

      if (packageName != null) {
        intent.setClassName(packageName, "com.multisafepay.pos.middleware.IntentActivity")

        intent.putExtra("package_name", packageName) // Callback package name
        intent.putExtra("currency", "EUR")
        intent.putExtra("items", serializedItems)
        intent.putExtra("order_id", orderId)
        intent.putExtra("order_description", description)

        reactContext.startActivity(intent)
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
