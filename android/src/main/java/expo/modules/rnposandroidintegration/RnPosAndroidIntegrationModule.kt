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

  // Both onNewIntent and onActivityResult are delivered *before* onResume, so a status
  // emitted straight from them reaches JS while the host Activity is still paused.
  // Any navigation JS performs then is committed against a stopped Activity, which is
  // what leaves the app on a blank, unresponsive screen. Hold the status until the
  // React host is actually resumed and emit it there.
  private var hostIsForeground = false
  private var pendingStatus: TransactionStatus? = null

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
        Diagnostics.addVerdict("fg=$hostIsForeground")
        if (hostIsForeground) {
          emit(status)
        } else {
          Diagnostics.note("5. Host backgrounded; deferring $status until foreground")
          Diagnostics.addVerdict("DEFERRED")
          pendingStatus = status
        }
      }
      Notifier.registerObserver(observer)
      Diagnostics.note("Module created; observer registered (posMode=${posMode.mode})")
    }

    OnDestroy {
      // A teardown mid-transaction takes `posMode` and `pendingStatus` with it, so say
      // so on screen: it turns a mystified freeze into an explained one.
      Diagnostics.note("Module destroyed; pendingStatus=$pendingStatus posMode=${posMode.mode}")
      Notifier.deregisterObserver(observer)
    }

    OnActivityEntersForeground {
      hostIsForeground = true
      Diagnostics.setForeground(true)

      pendingStatus?.let {
        pendingStatus = null
        Diagnostics.note("5. Host resumed; flushing deferred $it")
        Diagnostics.beginVerdict("V flush $it")
        emit(it)
        Diagnostics.showVerdict()
      }

      // A status that landed while this module was being rebuilt is held by the Notifier,
      // not by us, so resuming has to ask for it too.
      Notifier.drainParked()
    }

    OnActivityEntersBackground {
      hostIsForeground = false
      Diagnostics.setForeground(false)
    }

    // Handle activity results coming back to the host Activity.
    OnActivityResult { activity, payload ->
      // payload contains requestCode, resultCode, data
      Diagnostics.note("2. onActivityResult req=${payload.requestCode} result=${payload.resultCode} posMode=${posMode.mode}")
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

    Function("initiatePayment") { currency: String, amount: Long, serializedItems: String, orderId: String, description: String, sessionId: String?, debug: Boolean? ->
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
        intent.putExtra("callback_activity", activityClass)
        intent.putExtra("callback_package", appPackageName)

        // Drop any status still waiting to be flushed; it belongs to a previous
        // transaction and must not surface as the result of this one.
        pendingStatus = null

        activity.startActivityForResult(intent, SOFT_POS_REQUEST_CODE)
        Diagnostics.note("1c. Launched SoftPOS from ${activityClass.substringAfterLast('.')}, callback req=$SOFT_POS_REQUEST_CODE")
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
