package expo.modules.rnposandroidintegration

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import expo.modules.core.interfaces.ReactActivityLifecycleListener
import java.util.*

class RnPosAndroidReactActivityLifecycleListener : ReactActivityLifecycleListener {

  companion object {
    private const val MAX_DEBUG_COLLECTION_ITEMS = 20
    private const val MAX_DEBUG_MESSAGE_LENGTH = 12000
    private const val MAX_DEBUG_VALUE_LENGTH = 800
    private const val DEBUG_ALERT_DELAY_MS = 250L

    private val DEBUG_EXTRA_KEYS = linkedSetOf(
      "status",
      "status_code",
      "statusCode",
      "result_status",
      "resultStatus",
      "payment_status",
      "paymentStatus",
      "transaction_status",
      "transactionStatus",
      "result_code",
      "resultCode",
      "response_code",
      "responseCode",
      "code",
      "message",
      "description",
      "order_id",
      "orderId",
      "transaction_id",
      "transactionId",
      "payment_id",
      "paymentId",
      "session_id",
      "sessionId"
    )
  }

  // https://docs.expo.dev/modules/android-lifecycle-listeners/#activity-lifecycle-listeners

  // DEBUG: keep a reference to the foreground activity so we have a context to
  // show the payload alert from. Populated by the lifecycle callbacks below.
  private var currentActivity: Activity? = null

  override fun onResume(activity: Activity) {
    this.currentActivity = activity
  }

  override fun onNewIntent(intent: Intent?): Boolean {
    // on waking up from callback process results
    // this intent is only called if target App (Pay App) is properly finalized.
    if (intent != null) {
      if (this.hasCallbackPayload(intent)) {
        this.processMSPMiddlewareResponse(intent, "onNewIntent")
      } else {
        Log.w("pos-app-integration", "SoftPOS onNewIntent callback did not include extras or data; ignoring empty debug alert")
      }
    } else {
      // DEBUG: even a null intent is worth seeing during this investigation.
      this.showIntentPayloadAlert(null, "onNewIntent (intent == null)")
    }
    return super.onNewIntent(intent)
  }

  // Not an interface override (ReactActivityLifecycleListener may not declare this); invoked via ActivityEventListener in module.
  fun handleActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
    Log.w("pos-app-integration", "SoftPOS result intent data; requestCode=$requestCode, resultCode=$resultCode activity=${activity?.javaClass?.simpleName}")

    if (activity != null) {
      this.currentActivity = activity
    }

    if (requestCode != SOFT_POS_REQUEST_CODE) {
      return
    }

    if (data != null) {
      this.processMSPMiddlewareResponse(data, "handleActivityResult resultCode=$resultCode", activity)
    } else {
      // DEBUG: surface the null-data case too — a null result Intent is itself a
      // strong signal (e.g. the Pay App called setResult without data, or
      // RESULT_CANCELED). Previously this only hit Logcat.
      Log.w("pos-app-integration", "SoftPOS result missing intent data; resultCode=$resultCode")
      this.showIntentPayloadAlert(null, "handleActivityResult resultCode=$resultCode (data == null)", activity)
    }
  }

  private fun processMSPMiddlewareResponse(intent: Intent, source: String = "unknown", activity: Activity? = null) {
    // DEBUG: surface the raw intent payload as an on-screen alert before running
    // the normal handling below. Works in release builds (no debugger needed).
    // Remove this call once the external team's payload has been verified.
    this.showIntentPayloadAlert(intent, source, activity)

    //retrieve intent extra data including message.
    if (safeHasExtra(intent, "status")) {
      val status = safeIntExtra(intent, "status") ?: 0
      val message = safeStringExtra(intent, "message")
      Log.d("pos-app-integration", "Received SoftPOS callback via status=$status message=$message")
      this.handleMiddlewareCallback(status, message)
      return
    }

    if (safeHasExtra(intent, "result_status")) {
      val resultStatus = safeStringExtra(intent, "result_status")?.lowercase(Locale.ROOT)
      val message = safeStringExtra(intent, "message")
      val description = safeStringExtra(intent, "description")

      Log.d("pos-app-integration", "Received SoftPOS activity result result_status=$resultStatus message=$message description=$description")

      when (resultStatus) {
        "success" -> Notifier.onTransactionStatusChanged(TransactionStatus.COMPLETED)
        "cancelled" -> Notifier.onTransactionStatusChanged(TransactionStatus.CANCELLED)
        "declined" -> Notifier.onTransactionStatusChanged(TransactionStatus.DECLINED)
        else -> Notifier.onTransactionStatusChanged(TransactionStatus.EXCEPTION)
      }
      return
    }

    Log.w("pos-app-integration", "SoftPOS callback intent missing expected extras")
  }
  

  private fun hasCallbackPayload(intent: Intent): Boolean {
    if (intent.dataString != null) {
      return true
    }

    return try {
      val extras = intent.extras
      extras != null && !extras.isEmpty
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to inspect SoftPOS callback extras", error)
      true
    }
  }

  private fun readTextExtra(intent: Intent, key: String): String? {
    val stringValue = safeStringExtra(intent, key)
    if (stringValue != null) {
      return stringValue
    }

    return when (val rawValue = safeRawExtra(intent, key)) {
      is CharSequence -> rawValue.toString()
      is Number -> rawValue.toString()
      is Boolean -> rawValue.toString()
      else -> null
    }
  }

  private fun safeHasExtra(intent: Intent, key: String): Boolean {
    return try {
      intent.hasExtra(key)
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to check SoftPOS extra '$key'", error)
      false
    }
  }

  private fun safeIntExtra(intent: Intent, key: String): Int? {
    return try {
      intent.getIntExtra(key, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to read SoftPOS int extra '$key'", error)
      null
    }
  }

  private fun safeStringExtra(intent: Intent, key: String): String? {
    return try {
      intent.getStringExtra(key)
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to read SoftPOS string extra '$key'", error)
      null
    }
  }

  private fun safeRawExtra(intent: Intent, key: String): Any? {
    return try {
      @Suppress("DEPRECATION")
      intent.extras?.get(key)
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to read SoftPOS raw extra '$key'", error)
      null
    }
  }


  // DEBUG: dumps every extra (plus action / data) from the incoming intent into
  // a readable AlertDialog so the payload can be inspected on-device. This does
  // not alter the normal flow — the existing handling still runs afterwards.
  private fun showIntentPayloadAlert(intent: Intent?, source: String, preferredActivity: Activity? = null) {
    val activity = preferredActivity ?: this.currentActivity
    if (activity == null || activity.isFinishing) {
      Log.w("pos-app-integration", "No activity available to show intent payload alert")
      return
    }

    val payload = StringBuilder()
    // Which entry point delivered this? Tells us whether the Pay App returned
    // via onActivityResult (setResult) or by relaunching the callback activity.
    payload.append("source = $source\n")

    if (intent == null) {
      payload.append("intent = null\n")
    } else {
      appendIntentMetadata(payload, intent)

      payload.append("--- handler parsed values ---\n")
      appendHandlerParsedValues(payload, intent)

      payload.append("--- known possible fields ---\n")
      appendKnownExtras(payload, intent)

      payload.append("--- all extras ---\n")
      appendAllExtras(payload, intent)
    }

    val fullMessage = payload.toString()
    logLongDebugMessage("Intent payload", fullMessage)
    val message = truncateDebugText(fullMessage, MAX_DEBUG_MESSAGE_LENGTH)

    activity.runOnUiThread {
      activity.window.decorView.postDelayed({
        if (activity.isFinishing || activity.isDestroyed) {
          Log.w("pos-app-integration", "Activity no longer available to show intent payload alert")
          return@postDelayed
        }

        try {
          AlertDialog.Builder(activity)
            .setTitle("SoftPOS Intent Payload (debug)")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
        } catch (error: RuntimeException) {
          Log.e("pos-app-integration", "Unable to show SoftPOS intent payload alert", error)
        }
      }, DEBUG_ALERT_DELAY_MS)
    }
  }

  private fun appendHandlerParsedValues(payload: StringBuilder, intent: Intent) {
    val statusInt = safeIntExtra(intent, "status")
    val statusString = safeStringExtra(intent, "status")
    val statusRaw = safeRawExtra(intent, "status")
    val resultStatus = safeStringExtra(intent, "result_status")
    val message = safeStringExtra(intent, "message")
    val description = safeStringExtra(intent, "description")

    payload.append("hasExtra(status)              = ${safeHasExtra(intent, "status")}\n")
    payload.append("status as Int                = ${formatDebugValue(statusInt)}\n")
    payload.append("status as String             = ${formatDebugValue(statusString)}\n")
    payload.append("status raw                   = ${formatDebugValueWithType(statusRaw)}\n")
    payload.append("hasExtra(result_status)      = ${safeHasExtra(intent, "result_status")}\n")
    payload.append("result_status as String      = ${formatDebugValue(resultStatus)}\n")
    payload.append("result_status normalized     = ${formatDebugValue(resultStatus?.lowercase(Locale.ROOT))}\n")
    payload.append("message as String            = ${formatDebugValue(message)}\n")
    payload.append("description as String        = ${formatDebugValue(description)}\n")
    payload.append("message text fallback        = ${formatDebugValue(readTextExtra(intent, "message"))}\n")
    payload.append("description text fallback    = ${formatDebugValue(readTextExtra(intent, "description"))}\n")
  }

  private fun appendIntentMetadata(payload: StringBuilder, intent: Intent) {
    payload.append("action = ${safeIntentValue("action") { intent.action }}\n")
    payload.append("dataString = ${safeIntentValue("dataString") { intent.dataString }}\n")
    payload.append("dataScheme = ${safeIntentValue("scheme") { intent.scheme }}\n")
    payload.append("type = ${safeIntentValue("type") { intent.type }}\n")
    payload.append("package = ${safeIntentValue("package") { intent.`package` }}\n")
    payload.append("component = ${safeIntentValue("component") { intent.component?.className }}\n")
    payload.append("flags = ${safeIntentValue("flags") { "0x${Integer.toHexString(intent.flags)}" }}\n")
    payload.append("categories = ${safeIntentValue("categories") { intent.categories?.joinToString() }}\n")
    payload.append("sourceBounds = ${safeIntentValue("sourceBounds") { intent.sourceBounds }}\n")
    payload.append("selector = ${safeIntentValue("selector") { describeIntentReference(intent.selector) }}\n")
    payload.append("clipData = ${safeIntentValue("clipData") { describeClipData(intent) }}\n")
    payload.append("intentUri = ${safeIntentValue("intentUri") { intent.toUri(Intent.URI_INTENT_SCHEME) }}\n")
  }

  private fun appendKnownExtras(payload: StringBuilder, intent: Intent) {
    var foundKnownExtra = false
    for (key in DEBUG_EXTRA_KEYS) {
      val hasExtra = safeHasExtra(intent, key)
      if (!hasExtra) {
        continue
      }

      foundKnownExtra = true
      val rawValue = safeRawExtra(intent, key)
      val textValue = readTextExtra(intent, key)
      payload.append("$key: hasExtra=$hasExtra value=${formatDebugValueWithType(rawValue)} text=${formatDebugValue(textValue)}\n")
    }

    if (!foundKnownExtra) {
      payload.append("(no known fields found)\n")
    }
  }

  private fun appendAllExtras(payload: StringBuilder, intent: Intent) {
    try {
      val extras = intent.extras
      if (extras != null && !extras.isEmpty) {
        for (key in extras.keySet()) {
          val value = try {
            @Suppress("DEPRECATION")
            extras.get(key)
          } catch (error: RuntimeException) {
            Log.e("pos-app-integration", "Unable to read SoftPOS extra '$key'", error)
            "<error reading value: ${error.javaClass.simpleName}>"
          }
          payload.append("$key = ${formatDebugValueWithType(value)}\n")
        }
      } else {
        payload.append("(no extras)\n")
      }
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to enumerate SoftPOS extras", error)
      payload.append("(unable to enumerate extras: ${error.javaClass.simpleName}: ${error.message})\n")
    }
  }

  private fun safeIntentValue(name: String, read: () -> Any?): String {
    return try {
      formatDebugValue(read())
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to read SoftPOS intent $name", error)
      "<error: ${error.javaClass.simpleName}: ${error.message}>"
    }
  }

  private fun describeIntentReference(intent: Intent?): String? {
    if (intent == null) {
      return null
    }

    return "Intent{action=${intent.action}, data=${intent.dataString}, component=${intent.component?.className}}"
  }

  private fun describeClipData(intent: Intent): String? {
    val clipData = intent.clipData ?: return null
    val itemCount = clipData.itemCount
    val itemLimit = itemCount.coerceAtMost(MAX_DEBUG_COLLECTION_ITEMS)
    val items = mutableListOf<String>()

    for (index in 0 until itemLimit) {
      val item = clipData.getItemAt(index)
      items.add(
        "item[$index]={text=${formatDebugValue(item.text)}, uri=${formatDebugValue(item.uri)}, html=${formatDebugValue(item.htmlText)}, intent=${formatDebugValue(describeIntentReference(item.intent))}}"
      )
    }

    val suffix = if (itemCount > itemLimit) ", ... ${itemCount - itemLimit} more" else ""
    return "ClipData{itemCount=$itemCount, items=${items.joinToString(prefix = "[", postfix = "]$suffix")}}"
  }

  private fun formatDebugValueWithType(value: Any?): String {
    return "${formatDebugValue(value)} (${value?.javaClass?.simpleName ?: "null"})"
  }

  private fun formatDebugValue(value: Any?, depth: Int = 0): String {
    val text = try {
      when (value) {
        null -> "null"
        is Bundle -> formatBundleValue(value, depth)
        is Intent -> describeIntentReference(value) ?: "null"
        is CharSequence -> value.toString()
        is Number -> value.toString()
        is Boolean -> value.toString()
        is Array<*> -> formatCollectionValue(value.asIterable(), value.size, depth)
        is IntArray -> formatPrimitiveCollectionValue(value.toList())
        is LongArray -> formatPrimitiveCollectionValue(value.toList())
        is DoubleArray -> formatPrimitiveCollectionValue(value.toList())
        is FloatArray -> formatPrimitiveCollectionValue(value.toList())
        is BooleanArray -> formatPrimitiveCollectionValue(value.toList())
        is ByteArray -> formatPrimitiveCollectionValue(value.toList())
        is ShortArray -> formatPrimitiveCollectionValue(value.toList())
        is CharArray -> formatPrimitiveCollectionValue(value.toList())
        is Iterable<*> -> formatCollectionValue(value, (value as? Collection<*>)?.size, depth)
        is Map<*, *> -> formatMapValue(value, depth)
        else -> value.toString()
      }
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to format SoftPOS debug value", error)
      "<error formatting value: ${error.javaClass.simpleName}: ${error.message}>"
    }

    return truncateDebugText(text, MAX_DEBUG_VALUE_LENGTH)
  }

  private fun formatBundleValue(bundle: Bundle, depth: Int): String {
    if (depth >= 2) {
      return "Bundle(keys=${safeBundleKeys(bundle)?.joinToString() ?: "<unreadable>"})"
    }

    val keys = safeBundleKeys(bundle) ?: return "Bundle(<unable to read keys>)"
    if (keys.isEmpty()) {
      return "Bundle{}"
    }

    val entries = keys.take(MAX_DEBUG_COLLECTION_ITEMS).map { key ->
      val value = try {
        @Suppress("DEPRECATION")
        bundle.get(key)
      } catch (error: RuntimeException) {
        Log.e("pos-app-integration", "Unable to read nested SoftPOS bundle extra '$key'", error)
        "<error reading value: ${error.javaClass.simpleName}>"
      }
      "$key=${formatDebugValue(value, depth + 1)}"
    }

    val suffix = if (keys.size > MAX_DEBUG_COLLECTION_ITEMS) ", ... ${keys.size - MAX_DEBUG_COLLECTION_ITEMS} more" else ""
    return entries.joinToString(prefix = "Bundle{", postfix = "$suffix}")
  }

  private fun safeBundleKeys(bundle: Bundle): List<String>? {
    return try {
      bundle.keySet().toList()
    } catch (error: RuntimeException) {
      Log.e("pos-app-integration", "Unable to read SoftPOS bundle keys", error)
      null
    }
  }

  private fun formatCollectionValue(values: Iterable<*>, size: Int?, depth: Int): String {
    val items = values.take(MAX_DEBUG_COLLECTION_ITEMS).map { formatDebugValue(it, depth + 1) }
    val suffix = if (size != null && size > MAX_DEBUG_COLLECTION_ITEMS) ", ... ${size - MAX_DEBUG_COLLECTION_ITEMS} more" else ""
    return items.joinToString(prefix = "[", postfix = "]$suffix")
  }

  private fun formatPrimitiveCollectionValue(values: List<Any>): String {
    val items = values.take(MAX_DEBUG_COLLECTION_ITEMS).map { it.toString() }
    val suffix = if (values.size > MAX_DEBUG_COLLECTION_ITEMS) ", ... ${values.size - MAX_DEBUG_COLLECTION_ITEMS} more" else ""
    return items.joinToString(prefix = "[", postfix = "]$suffix")
  }

  private fun formatMapValue(value: Map<*, *>, depth: Int): String {
    val entries = value.entries.take(MAX_DEBUG_COLLECTION_ITEMS).map { (key, entryValue) ->
      "${formatDebugValue(key, depth + 1)}=${formatDebugValue(entryValue, depth + 1)}"
    }
    val suffix = if (value.size > MAX_DEBUG_COLLECTION_ITEMS) ", ... ${value.size - MAX_DEBUG_COLLECTION_ITEMS} more" else ""
    return entries.joinToString(prefix = "{", postfix = "}$suffix")
  }

  private fun truncateDebugText(value: String, maxLength: Int): String {
    if (value.length <= maxLength) {
      return value
    }

    return value.take(maxLength) + "\n... <truncated ${value.length - maxLength} chars>"
  }

  private fun logLongDebugMessage(label: String, message: String) {
    val chunks = message.chunked(3500)
    for ((index, chunk) in chunks.withIndex()) {
      Log.d("pos-app-integration", "$label [${index + 1}/${chunks.size}]:\n$chunk")
    }
  }

  private fun handleMiddlewareCallback(status: Int, message: String?) {
    Log.d("pos-app-integration", "Middleware callback status=$status message=$message")
    when (status) {
      875 -> {
        this.receivedCallbackIntent(TransactionStatus.EXCEPTION)
      }

      471 -> {
        this.receivedCallbackIntent(TransactionStatus.COMPLETED)
      }

      17 -> {
        this.receivedCallbackIntent(TransactionStatus.CANCELLED)
      }

      88 -> {
        this.receivedCallbackIntent(TransactionStatus.DECLINED)
      }
    }
  }

  private fun receivedCallbackIntent(status: TransactionStatus) {
    Notifier.onTransactionStatusChanged(status)
  }
}