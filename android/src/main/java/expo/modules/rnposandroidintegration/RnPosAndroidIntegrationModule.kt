package expo.modules.rnposandroidintegration

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.Locale

const val TRANSACTION_CHANGED_EVENT_NAME = "onTransactionChanged"
internal const val SOFT_POS_REQUEST_CODE = 6017

// How long a status may wait for the host to resume before it is emitted regardless, and how
// often the host is checked while waiting. The cap matters more than the interval: it is the
// guarantee that no status is ever held indefinitely.
private const val DELIVERY_TIMEOUT_MS = 5_000L
private const val DELIVERY_POLL_MS = 100L

// Long enough for JS to navigate and React to commit a frame, short enough to still make the
// second copy of the verdict (which is shown 9s after delivery).
private const val SURFACE_PROBE_DELAY_MS = 1_500L

class RnPosAndroidIntegrationModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext)
  private val currentActivity get() = appContext.activityProvider?.currentActivity
  private val appPackageName get() = context.packageName

  private val mainHandler = Handler(Looper.getMainLooper())

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

  /**
   * The state of the Activity's view tree -- the thing a white screen is actually about.
   *
   * Every logical hop now reports success on a run that ends white: the status is mapped,
   * emitted, received by JS, navigated to, and the screen mounts. So the question is no
   * longer what the code computed but whether anything is on screen to draw. A React root
   * that is absent, zero-sized or not visible says the surface died; a healthy root of the
   * right size says the surface is fine and the pixels are the app's own.
   */
  private fun describeSurface(activity: Activity? = currentActivity): String {
    val content = try {
      activity?.findViewById<ViewGroup>(android.R.id.content)
    } catch (error: RuntimeException) {
      return "surface=unreadable:${error.javaClass.simpleName}"
    } ?: return "surface=none"

    val root = if (content.childCount > 0) content.getChildAt(0) else null
    val rootDesc = if (root == null) {
      "root=<none>"
    } else {
      "root=${root.javaClass.simpleName} ${root.width}x${root.height} vis=${root.visibility} " +
        "kids=${(root as? ViewGroup)?.childCount ?: -1} shown=${root.isShown}"
    }

    return "content=${content.width}x${content.height} kids=${content.childCount} $rootDesc"
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
    mainHandler.post { block() }
  }

  /**
   * Whether the host Activity is genuinely resumed, asked of the Activity itself.
   *
   * Deliberately not a flag maintained from OnActivityEntersForeground/Background: those two
   * events are not symmetric (see the note on the delivery policy above), so a cached boolean
   * can disagree with reality and stay that way. The lifecycle registry cannot.
   */
  private fun isHostResumed(): Boolean {
    val activity = currentActivity ?: return false
    if (activity.isFinishing || activity.isDestroyed) {
      return false
    }
    val owner = activity as? LifecycleOwner ?: return false
    return owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
  }

  /**
   * Emits as soon as the host is resumed, and emits anyway once [DELIVERY_TIMEOUT_MS] has passed.
   *
   * Both failure modes this has been through are covered. Emitting into a host that is not yet
   * resumed lands the navigation on a surface that is not on screen, which renders blank.
   * Waiting for an event that may never arrive never delivers at all, which hangs. Polling the
   * real state with a deadline can do neither: the common case emits on the first turn, and the
   * worst case is a late status, which is always better than no status.
   */
  private fun deliverWhenReady(status: TransactionStatus, waitedMs: Long = 0) {
    val ready = isHostResumed()

    if (!ready && waitedMs < DELIVERY_TIMEOUT_MS) {
      mainHandler.postDelayed({ deliverWhenReady(status, waitedMs + DELIVERY_POLL_MS) }, DELIVERY_POLL_MS)
      return
    }

    if (!ready) {
      Diagnostics.note("5b. Host still not resumed after ${waitedMs}ms; emitting $status anyway")
      Diagnostics.addVerdict("FORCED@${waitedMs}ms")
    } else if (waitedMs > 0) {
      Diagnostics.addVerdict("waited=${waitedMs}ms")
    }

    try {
      emit(status)
    } catch (error: Throwable) {
      // Runs long after Notifier.dispatch returned, so nothing upstream is catching and an
      // escape here would take down the main thread. Park it for the next module instance.
      Diagnostics.note("6. Delivery threw; parking $status")
      Notifier.park(status)
    }

    // Once JS has had time to navigate and draw. A root that is healthy here while the
    // screen is white moves the question off this module entirely.
    mainHandler.postDelayed({
      val surface = describeSurface()
      Diagnostics.note("7. surface after render: $surface")
      Diagnostics.addVerdict("surf:$surface")
    }, SURFACE_PROBE_DELAY_MS)

    Diagnostics.showVerdict()
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
        // Off the synchronous onActivityResult stack, then delivered on a deadline.
        postToMain { deliverWhenReady(status) }
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
      Diagnostics.note("2f. surface at result: ${describeSurface(activity)}")

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
      // Also onto the verdict line. A `late` toast is unnumbered, so Android dropping one
      // reads as a step that never ran -- the verdict is the copy that survives the burst.
      Diagnostics.addVerdict("ack:$stage")
    }

    AsyncFunction("setValueAsync") { value: String ->
      sendEvent("onChange", mapOf("value" to value))
    }

    View(RnPosAndroidIntegrationView::class) {
      Prop("name") { view: RnPosAndroidIntegrationView, prop: String -> println(prop) }
    }
  }

}
