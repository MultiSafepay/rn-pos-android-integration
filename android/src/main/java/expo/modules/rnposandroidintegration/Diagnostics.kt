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
 * The terminals run Tap to Pay with no debugger and no adb, so the screen is the only
 * instrument available and every diagnosis costs a full build-and-install cycle. That
 * shapes the design here:
 *
 *  - `note` is the play-by-play, numbered so a gap identifies a toast the system dropped
 *    rather than a step that never ran.
 *  - `verdict` is the one message that must not be lost. Android throttles a burst of
 *    toasts and silently drops the tail -- which is exactly what happened to the end of
 *    the chain last time -- so the verdict is coalesced into a single line, held back
 *    until the burst has drained, and shown twice.
 *
 * Process-wide on purpose: the chain crosses an Activity lifecycle listener, a singleton
 * notifier and the module, and the question is which of those hops the result dies at.
 */
object Diagnostics {
  private const val TAG = "pos-app-integration"
  private const val MAX_BUFFERED = 25

  @Volatile
  var enabled = false

  private var appContext: Context? = null

  @Volatile
  private var isForeground = false

  private val sequence = AtomicInteger(0)
  private val buffered = ArrayDeque<String>()
  private val verdictParts = mutableListOf<String>()
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

  /** Play-by-play. Always logs; toasts when tracing is on. */
  fun note(message: String) {
    Log.d(TAG, message)
    if (!enabled) {
      return
    }

    val line = "#${sequence.incrementAndGet()} $message"

    synchronized(buffered) {
      if (!isForeground) {
        // Toasts posted from the background are suppressed, and the background is exactly
        // when the payment app owns the screen. Hold them until the host is resumed.
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
   * Shown late and twice, so it survives the toast burst that precedes it. For facts that
   * arrive after the chain has run -- a JS acknowledgement, say.
   */
  fun late(message: String) {
    Log.d(TAG, message)
    if (!enabled) {
      return
    }
    main.postDelayed({ show("* $message") }, 1200)
    main.postDelayed({ show("* $message") }, 6000)
  }

  fun beginVerdict(label: String) {
    synchronized(verdictParts) {
      verdictParts.clear()
      verdictParts.add(label)
    }
  }

  fun addVerdict(part: String) {
    synchronized(verdictParts) { verdictParts.add(part) }
  }

  /** Emits the accumulated verdict as one line, after the burst and twice over. */
  fun showVerdict() {
    val text = synchronized(verdictParts) { verdictParts.joinToString(" | ") }
    Log.d(TAG, "VERDICT $text")
    if (!enabled) {
      return
    }
    main.postDelayed({ show("VERDICT $text") }, 3000)
    main.postDelayed({ show("VERDICT $text") }, 9000)
  }

  /**
   * Renders every extra an intent carries -- the thing that cannot be seen from outside
   * the process.
   */
  @Suppress("DEPRECATION")
  fun describeIntent(label: String, intent: Intent?): String {
    if (intent == null) {
      return "$label: <null intent>"
    }

    val extras = try {
      intent.extras
    } catch (error: RuntimeException) {
      // An extra parcelled from a class we do not have blows up on unmarshalling; that is
      // itself a finding, so report it rather than letting it escape.
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
