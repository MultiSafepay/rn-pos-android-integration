package expo.modules.rnposandroidintegration

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-screen tracing of the payment callback chain.
 *
 * The terminals run Tap to Pay with no debugger attached -- attaching one stops Tap to
 * Pay working at all -- so the only way to see what the payment apps actually hand back
 * is to put it on the screen and film it. Every hop of the callback chain reports here.
 *
 * This is a process-wide object on purpose. The chain crosses an Activity lifecycle
 * listener, a singleton notifier and the module, and the whole point is to find out
 * which of those hops the result fails to survive -- so the sink cannot live on any one
 * of them. With `enabled` false this is exactly the Logcat behaviour we had before.
 */
object Diagnostics {
  private const val TAG = "pos-app-integration"

  // Enough to cover one transaction's chain; oldest is dropped first so a runaway
  // cannot grow unbounded while the host sits in the background.
  private const val MAX_BUFFERED = 25

  @Volatile
  var enabled = false

  private var appContext: Context? = null

  @Volatile
  private var isForeground = false

  private val sequence = AtomicInteger(0)
  private val buffered = ArrayDeque<String>()
  private val main = Handler(Looper.getMainLooper())

  fun attach(context: Context) {
    appContext = context.applicationContext
  }

  fun setForeground(foreground: Boolean) {
    isForeground = foreground
    if (foreground) {
      flush()
    }
  }

  /**
   * Always logs. Also shows a toast when tracing is on.
   *
   * Messages are numbered so that a gap in the recording identifies a toast the system
   * dropped, rather than a step that never ran -- which is the distinction the whole
   * exercise depends on.
   */
  fun note(message: String) {
    Log.d(TAG, message)
    if (!enabled) {
      return
    }

    val line = "#${sequence.incrementAndGet()} $message"

    synchronized(buffered) {
      if (!isForeground) {
        // Android suppresses toasts posted while the app is in the background, which is
        // precisely when the payment app owns the screen and the interesting callbacks
        // arrive. Hold them and flush once the host is resumed.
        if (buffered.size >= MAX_BUFFERED) {
          buffered.removeFirst()
        }
        buffered.addLast(line)
        return
      }
    }

    show(line)
  }

  /**
   * Renders every extra an intent carries. This is the thing we cannot see from outside
   * the process, and the reason a declined transaction can vanish without a trace: if a
   * payment app answers on a channel or with a key we do not read, the dump says so.
   */
  @Suppress("DEPRECATION")
  fun describeIntent(label: String, intent: Intent?): String {
    if (intent == null) {
      return "$label: <null intent>"
    }

    val extras = try {
      intent.extras
    } catch (error: RuntimeException) {
      // A parcelled extra from a class we do not have blows up on unmarshalling; that is
      // itself a finding, so report it instead of letting it escape.
      return "$label: action=${intent.action} <extras unreadable: ${error.javaClass.simpleName}>"
    }

    val keys = extras?.keySet().orEmpty()
    if (keys.isEmpty()) {
      return "$label: action=${intent.action} <no extras>"
    }

    val rendered = keys.joinToString(", ") { key ->
      val value = try {
        extras?.get(key)
      } catch (error: RuntimeException) {
        "<unreadable>"
      }
      "$key=$value"
    }

    return "$label: action=${intent.action} $rendered"
  }

  private fun flush() {
    val pending = synchronized(buffered) {
      if (buffered.isEmpty()) {
        return
      }
      val copy = buffered.toList()
      buffered.clear()
      copy
    }

    pending.forEach { show(it) }
  }

  private fun show(line: String) {
    val context = appContext ?: run {
      Log.w(TAG, "Diagnostics has no context yet; toast dropped: $line")
      return
    }

    main.post { Toast.makeText(context, line, Toast.LENGTH_LONG).show() }
  }
}
