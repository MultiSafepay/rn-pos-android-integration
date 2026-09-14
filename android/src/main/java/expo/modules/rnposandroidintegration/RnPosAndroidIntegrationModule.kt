package expo.modules.rnposandroidintegration

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
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

  // There is deliberately no "wait until the host is resumed" gate here any more.
  //
  // AppContext.onHostResume() returns early when currentActivity is null and throws when the
  // Activity is not an AppCompatActivity -- either way ACTIVITY_ENTERS_FOREGROUND is never
  // posted. onHostPause() has no such guard and always posts ACTIVITY_ENTERS_BACKGROUND. So
  // a flag driven by those two events is a latch that can stick shut, and a status held
  // behind it is never delivered: the host waits on a transaction that already resolved.
  // That is a permanent freeze, which is strictly worse than the momentary blank screen the
  // gate was guessing at. The reference integration acts on the result synchronously inside
  // onActivityResult and has never needed one.

  // Which Activity instance launched the payment, so the result can say whether it came
  // back to the same one. A different instance means the React surface JS is rendering
  // into is not the one on screen.
  private var launchActivityKey: String? = null
  private var launchConfigKey: String? = null

  /**
   * The Activity's configuration and display, captured either side of the payment app.
   *
   * Expo's MainActivity declares a broad `android:configChanges`, so a configuration change
   * is absorbed rather than recreating the Activity -- the instance stays identical, which is
   * why comparing identity alone said nothing. If the configuration differs between launch
   * and result, the host was reconfigured underneath a React surface that never recovered,
   * and that is a different failure from anything in the payment path.
   */
  @Suppress("DEPRECATION")
  private fun describeConfig(activity: Activity? = currentActivity): String {
    val configuration = activity?.resources?.configuration ?: return "cfg=none"
    val displayId = try {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        activity.display?.displayId ?: -1
      } else {
        activity.windowManager?.defaultDisplay?.displayId ?: -1
      }
    } catch (error: RuntimeException) {
      -2
    }
    return "o=${configuration.orientation} w=${configuration.screenWidthDp} h=${configuration.screenHeightDp} " +
      "sw=${configuration.smallestScreenWidthDp} dens=${configuration.densityDpi} ui=${configuration.uiMode} disp=$displayId"
  }

  private fun describeActivity(activity: Activity? = currentActivity): String {
    if (activity == null) {
      return "act=none"
    }
    return "act=${Integer.toHexString(System.identityHashCode(activity))} task=${activity.taskId} fin=${activity.isFinishing}"
  }

  /**
   * Hands work to the next main-loop turn.
   *
   * This is the one part of the old deferral worth keeping: it gets the emit off the
   * synchronous onActivityResult stack, so JS never re-enters native from inside an Activity
   * callback. Unlike a post on the decor view it does not depend on the view being attached,
   * and unlike a lifecycle flag it cannot be withheld -- the runnable always executes.
   */
  private fun postToMain(block: () -> Unit) {
    Handler(Looper.getMainLooper()).post { block() }
  }

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

  private fun emit(status: TransactionStatus) {
    val value: Map<String, String> = mapOf("status" to status.toString())
    try {
      sendEvent(TRANSACTION_CHANGED_EVENT_NAME, value)
      Diagnostics.note("6. Emitted $status to JS")
      Diagnostics.addVerdict("SENT")
    } catch (error: Throwable) {
      Diagnostics.addVerdict("sendFAIL:${error.javaClass.simpleName}")
      // Notifier swallows this so one dead observer cannot abort the others. Report it
      // here too: a status that fails to reach JS otherwise looks exactly like one that
      // was never produced, and those need different fixes.
      Diagnostics.note("6. FAILED to emit $status: ${error.javaClass.simpleName} ${error.message}")
      throw error
    }
  }

  override fun definition() = ModuleDefinition {
    Name("RnPosAndroidIntegration")
    Events(TRANSACTION_CHANGED_EVENT_NAME)

    OnCreate {
      appContext.reactContext?.let { Diagnostics.attach(it) }

      observer = { status ->
        // Unconditional. Whatever the host's lifecycle is doing, the status goes to JS on the
        // next main-loop turn; there is no state in which it can be held back.
        postToMain {
          try {
            emit(status)
          } catch (error: Throwable) {
            // This runs a loop turn after Notifier.dispatch returned, so nothing upstream is
            // catching any more and an escape here would take down the main thread. sendEvent
            // fails when the AppContext went away in between; park the status so the next
            // module instance delivers it instead of losing it.
            Notifier.park(status)
          }
          Diagnostics.showVerdict()
        }
      }
      Notifier.registerObserver(observer)
      Diagnostics.note("Module created; observer registered (posMode=${posMode.mode})")
    }

    OnDestroy {
      // A teardown mid-transaction takes `posMode` with it, so say so on screen: it turns
      // a mystified freeze into an explained one.
      Diagnostics.note("Module destroyed; posMode=${posMode.mode}")
      Notifier.deregisterObserver(observer)
    }

    OnActivityEntersForeground {
      Diagnostics.setForeground(true)

      // A status that landed while this module was being rebuilt is held by the Notifier,
      // not by us, so resuming has to ask for it too. This is a catch-up for a module that
      // did not exist when the result arrived -- not a gate on normal delivery.
      Notifier.drainParked()
    }

    OnActivityEntersBackground {
      Diagnostics.setForeground(false)
    }

    // Handle activity results coming back to the host Activity.
    OnActivityResult { activity, payload ->
      // payload contains requestCode, resultCode, data
      Diagnostics.note("2. onActivityResult req=${payload.requestCode} result=${payload.resultCode} posMode=${posMode.mode}")
      Diagnostics.note("2a. result on ${describeActivity(activity)} | launched from ${launchActivityKey ?: "unknown"}")
      val resultConfig = describeConfig(activity)
      if (launchConfigKey != null && resultConfig != launchConfigKey) {
        Diagnostics.note("2d. CONFIG CHANGED! was ${launchConfigKey ?: "?"}")
        Diagnostics.note("2e. CONFIG CHANGED! now $resultConfig")
        Diagnostics.addVerdict("CFG-CHANGED")
      } else {
        Diagnostics.note("2d. cfg unchanged $resultConfig")
        Diagnostics.addVerdict("cfgSame")
      }
      Diagnostics.note(Diagnostics.describeIntent("2b. result intent", payload.data))

      if (payload.requestCode != SOFT_POS_REQUEST_CODE) {
        Diagnostics.note("2c. IGNORED: requestCode ${payload.requestCode} is not $SOFT_POS_REQUEST_CODE")
        return@OnActivityResult
      }

      // `posMode` is per-module-instance state. If the AppContext was rebuilt while the
      // payment app held the screen it is back to its default, and this guard throws the
      // result away without a word -- indistinguishable from a result that never came.
      if (posMode != PosMode.SOFT_POS) {
        Diagnostics.note("2c. IGNORED: posMode is ${posMode.mode}, not soft-pos -- result discarded")
        return@OnActivityResult
      }

      lifecycleListener.handleActivityResult(activity, payload.requestCode, payload.resultCode, payload.data)
    }

    // The MSP Pay App (Sunmi) reports app-to-app through a new Intent. This was gated
    // on SOFT_POS, which is the one mode that never calls onNewIntent, so the
    // middleware callback was discarded. The listener ignores intents without a status.
    OnNewIntent { intent ->
      Diagnostics.note(Diagnostics.describeIntent("2. onNewIntent", intent))
      lifecycleListener.onNewIntent(intent)
    }

    AsyncFunction("canInitiatePayment") { promise: Promise ->
      promise.resolve(resolveLaunchIntent() != null)
    }

    Function("initiatePayment") { currency: String, amount: Long, serializedItems: String, orderId: String, description: String, sessionId: String?, debug: Boolean?, doNotReturn: Boolean? ->
      // Tracing is armed here rather than by its own call so it is impossible to launch a
      // traced payment and miss the callbacks: everything we need to see happens after
      // this point.
      Diagnostics.enabled = debug == true
      Diagnostics.setForeground(true)

      val intent = resolveLaunchIntent()

      Diagnostics.note("1. initiatePayment mode=${posMode.mode} amount=$amount order=$orderId session=${sessionId ?: "<none>"}")
      Log.d("pos-app-integration", "initiateManualPayment: amount=$amount currency=$currency serializedItems=$serializedItems orderId=$orderId description=$description sessionId=$sessionId")

      if (intent == null) {
        Diagnostics.note("1b. ABORT: no launch intent for mode ${posMode.mode}")
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
        Diagnostics.note("1c. Launched Sunmi Pay App")
      } else {
        val activity = currentActivity
        val activityClass = activity?.componentName?.className
        if (activity == null || activityClass == null) {
          Diagnostics.note("1b. ABORT: SoftPOS needs a foreground activity, none available")
          return@Function
        }
        intent.setClassName("com.phonepos.mspsoftposapp", "com.phonepos.mspsoftposapp.ManualPayInputActivity")
        intent.putExtra("amount", String.format(Locale.US, "%.2f", amount / 100.0))
        intent.putExtra("skip_manual_input", true)

        // Debug only. The callback extras are what make SoftPOS bring the host app back the
        // instant a transaction resolves, which makes it impossible to control how long the
        // host sits in the background. Omitting them leaves the operator to return manually,
        // so a long background can be tested against a short one with everything else equal.
        // The result still arrives over startActivityForResult either way.
        if (doNotReturn == true) {
          Diagnostics.note("1b. doNotReturn: omitting callback extras, SoftPOS will not return automatically")
        } else {
          intent.putExtra("callback_activity", activityClass)
          intent.putExtra("callback_package", appPackageName)
        }

        launchActivityKey = describeActivity(activity)
        launchConfigKey = describeConfig(activity)
        Diagnostics.note("1d. launch cfg ${launchConfigKey ?: "?"}")
        activity.startActivityForResult(intent, SOFT_POS_REQUEST_CODE)
        Diagnostics.note("1c. Launched SoftPOS from ${describeActivity(activity)}, callback req=$SOFT_POS_REQUEST_CODE")
      }
    }

    Function("setPosMode") { mode: String ->
      PosMode.from(mode)?.let {
        posMode = it
        Diagnostics.note("0. posMode set to ${it.mode}")
      } ?: Log.w("pos-app-integration", "Unknown POS mode: $mode")
    }

    // Called from JS when an event is received. Native cannot otherwise distinguish a
    // status that never reached JS from one that reached it and was dropped on the way to
    // the screen, and those need opposite fixes.
    Function("ackDiagnostics") { stage: String ->
      Diagnostics.late("JS ACK $stage")
    }

    AsyncFunction("setValueAsync") { value: String ->
      sendEvent("onChange", mapOf("value" to value))
    }

    View(RnPosAndroidIntegrationView::class) {
      Prop("name") { view: RnPosAndroidIntegrationView, prop: String -> println(prop) }
    }
  }

}
