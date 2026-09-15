package expo.modules.rnposandroidintegration

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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

  // Set when a payment is launched, cleared by the first status that answers it.
  //
  // It does two jobs. SoftPOS answers over *both* channels for one transaction -- it
  // relaunches `callback_activity`, arriving as onNewIntent, and it also returns
  // through onActivityResult -- so the first status wins and the rest are dropped.
  // And because the host Activity is singleTask, onNewIntent also fires on launcher
  // relaunches and deep links, at any time; this is what keeps those from surfacing
  // as a transaction result when no payment is outstanding.
  private var transactionInFlight = false

  @SuppressLint("NewApi")
  private fun buildLaunchIntent(mode: PosMode): Intent? {
    return when (mode) {
      // A Sunmi terminal may carry the SmartPOS Pay App under either package, so both
      // are tried in order. The middleware component is set here rather than at launch
      // so it always points at the package that actually resolved.
      PosMode.SUNMI -> listOfNotNull(mode.packageName, mode.fallbackPackageName)
        .firstNotNullOfOrNull { pkg ->
          context.packageManager?.getLaunchIntentForPackage(pkg)
            ?.setClassName(pkg, "com.multisafepay.pos.middleware.IntentActivity")
        }
      // An action-only Intent is never null, so canInitiatePayment reported true in
      // soft-pos mode even when SoftPOS was not installed -- and the launch then threw
      // ActivityNotFoundException with no status ever reaching JS, leaving the pay
      // screen waiting. Resolve the component up front, as the reference integration
      // does, so an uninstalled SoftPOS is reported as "cannot pay" instead.
      PosMode.SOFT_POS -> Intent("com.phonepos.mspsoftposapp.ACTION_MANUAL_PAYMENT")
        .setClassName(mode.packageName, "com.phonepos.mspsoftposapp.ManualPayInputActivity")
        .takeIf { context.packageManager?.resolveActivity(it, 0) != null }
    }
  }

  private fun resolveLaunchIntent(): Intent? = buildLaunchIntent(posMode)

  private var observer: (status: TransactionStatus) -> Unit = {}

  private enum class PosMode(val mode: String, val packageName: String, val fallbackPackageName: String? = null) {
    SUNMI("sunmi-pos", "com.multisafepay.pos.sunmi", "com.multisafepay.pos.nokernels"),
    SOFT_POS("soft-pos", "com.phonepos.mspsoftposapp");

    companion object {
      fun from(mode: String): PosMode? = entries.firstOrNull { it.mode == mode }
    }
  }

  private fun report(status: TransactionStatus) {
    if (!transactionInFlight) {
      Log.d("pos-app-integration", "Ignoring $status; no payment is outstanding (duplicate, or an intent unrelated to a transaction)")
      return
    }
    transactionInFlight = false

    if (hostIsForeground) {
      emit(status)
    } else {
      Log.d("pos-app-integration", "Host is not resumed yet; deferring $status until foreground")
      pendingStatus = status
    }
  }

  private fun emit(status: TransactionStatus) {
    val value: Map<String, String> = mapOf("status" to status.toString())
    sendEvent(TRANSACTION_CHANGED_EVENT_NAME, value)
    Log.d(TRANSACTION_CHANGED_EVENT_NAME, value.toString())
  }

  override fun definition() = ModuleDefinition {
    Name("RnPosAndroidIntegration")
    Events(TRANSACTION_CHANGED_EVENT_NAME)

    OnCreate {
      observer = { status -> report(status) }
      Notifier.registerObserver(observer)
    }

    OnDestroy { Notifier.deregisterObserver(observer) }

    OnActivityEntersForeground {
      hostIsForeground = true

      pendingStatus?.let {
        pendingStatus = null
        emit(it)
      }
    }

    OnActivityEntersBackground { hostIsForeground = false }

    // Handle activity results coming back to the host Activity.
    OnActivityResult { activity, payload ->
      // payload contains requestCode, resultCode, data
      if (payload.requestCode == SOFT_POS_REQUEST_CODE && posMode == PosMode.SOFT_POS) {
        lifecycleListener.handleActivityResult(activity, payload.requestCode, payload.resultCode, payload.data)
      }
    }

    // The MSP Pay App (Sunmi) reports app-to-app through a new Intent. This was gated
    // on SOFT_POS, which is the one mode that never calls onNewIntent, so the
    // middleware callback was discarded. The listener ignores intents without a status.
    OnNewIntent { intent ->
      lifecycleListener.onNewIntent(intent)
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

      // Arm for this transaction: the next status from either channel is its result.
      transactionInFlight = true

      intent.putExtra("package_name", appPackageName)
      intent.putExtra("order_id", orderId)
      intent.putExtra("order_description", description)
      intent.putExtra("items", serializedItems)
      intent.putExtra("currency", currency)

      if (sessionId != null){
        intent.putExtra("session_id", sessionId)
      }

      if (posMode == PosMode.SUNMI) {
        intent.putExtra("amount", amount)
        context.startActivity(intent)
      } else {
        val activity = currentActivity
        if (activity == null) {
          Log.w("pos-app-integration", "SoftPOS requires a foreground activity; aborting launch")
          return@Function
        }
        intent.putExtra("amount", String.format(Locale.US, "%.2f", amount / 100.0))
        intent.putExtra("skip_manual_input", true)
        // The return address is SoftPosProxyActivity, never the host Activity.
        //
        // Naming the host is what blanked the screen on cancelled and declined: Expo declares
        // it `singleTask`, making it the root of its task, and Android answers an outside app
        // re-launching such a root by resetting the task. The surface stayed attached and
        // stopped painting. The proxy is `singleTop` and never a root, so the re-launch is
        // uneventful -- see SoftPosProxyActivity.
        //
        // Both extras are sent, so the target is unambiguous. SoftPOS may answer twice --
        // once because startActivityForResult resumes its caller and again because these
        // extras ask it to start the proxy -- which is harmless: the proxy is built to
        // absorb that relaunch, and `transactionInFlight` reports only the first status.
        // Omitting `callback_package` would risk the opposite and much worse failure: if
        // SoftPOS cannot resolve `callback_activity` without it, the likely fallback is the
        // package's launcher Activity -- the singleTask task root this proxy exists to keep
        // SoftPOS away from.
        intent.putExtra("callback_package", appPackageName)
        intent.putExtra("callback_activity", SoftPosProxyActivity::class.java.name)

        // Drop any status still waiting to be flushed; it belongs to a previous
        // transaction and must not surface as the result of this one.
        pendingStatus = null

        try {
          activity.startActivityForResult(intent, SOFT_POS_REQUEST_CODE)
        } catch (error: ActivityNotFoundException) {
          Log.e("pos-app-integration", "SoftPOS resolved but could not be launched", error)
        }
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
