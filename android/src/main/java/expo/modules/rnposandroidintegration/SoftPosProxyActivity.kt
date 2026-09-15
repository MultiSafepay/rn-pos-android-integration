package expo.modules.rnposandroidintegration

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * A harmless callback target, and nothing else.
 *
 * The white screen after a cancelled or declined transaction came from what we named as
 * SoftPOS's return address. We named the host's launcher Activity, which Expo declares
 * `singleTask` and which is therefore the root of its task. When an outside app re-launches a
 * `singleTask` root, Android resets the task rather than delivering a plain SINGLE_TOP intent.
 * The Activity survived and its ReactRootView stayed attached, visible and correctly sized --
 * which is why every probe came back healthy -- but the surface painted nothing.
 *
 * So SoftPOS is given *this* Activity as the return address instead. It is `singleTop`, it is
 * never a task root, and re-launching it is an ordinary, uneventful thing for Android to do.
 * It draws nothing and finishes immediately, handing the screen straight back to the host.
 *
 * Deliberately NOT in the launch path. SoftPOS is still started by the host Activity with
 * `startActivityForResult`, exactly as before, and the result still arrives there. Launching
 * from here instead would work, but this Activity is translucent, and Android animates a
 * launch from a translucent Activity differently -- the transition into SoftPOS stops looking
 * like the standard push and starts looking like a modal. The reference integration launches
 * from an ordinary opaque screen, and that is the transition to match.
 */
class SoftPosProxyActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    reportHit("onCreate")
    dismiss()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    // Already alive and on top, so SoftPOS's callback arrives here instead of onCreate.
    reportHit("onNewIntent")
    dismiss()
  }

  /**
   * Records that SoftPOS actually used the callback address, on the one line that survives.
   *
   * A `note` is a toast among many and Android drops the tail of a burst, so its absence is
   * not evidence that this Activity was never reached. The verdict is coalesced and shown
   * late and twice, so "no proxy-hit on the verdict" does mean SoftPOS never came here --
   * which is the difference between this Activity being load-bearing and being dead weight.
   */
  private fun reportHit(via: String) {
    Diagnostics.note("3y. SoftPOS callback landed on the proxy via $via; returning to the host")
    Diagnostics.addVerdict("proxy-hit:$via")
  }

  /**
   * Leaves without an animation of its own: this Activity is plumbing and should cost the
   * flow nothing visible.
   */
  @Suppress("DEPRECATION")
  private fun dismiss() {
    finish()
    overridePendingTransition(0, 0)
  }
}
