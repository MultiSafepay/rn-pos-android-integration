package expo.modules.rnposandroidintegration

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

object Notifier {
    // Observers are registered/deregistered from the module lifecycle while callbacks
    // are dispatched from the Activity lifecycle, so the list has to tolerate
    // concurrent mutation while it is being iterated.
    private val observers = CopyOnWriteArrayList<(status: TransactionStatus) -> Unit>()

    fun registerObserver(observer: (status: TransactionStatus) -> Unit) {
        observers.addIfAbsent(observer)
    }

    fun deregisterObserver(observer: (status: TransactionStatus) -> Unit) {
        observers.remove(observer)
    }

    fun onTransactionStatusChanged(transactionStatus: TransactionStatus) {
        if (observers.isEmpty()) {
            // No observer means the module was torn down and never rebuilt: the status is
            // real but has nowhere to go, and the host waits forever.
            Diagnostics.note("5. NO OBSERVER registered -- $transactionStatus discarded")
            return
        }
        Diagnostics.note("5. Dispatching $transactionStatus to ${observers.size} observer(s)")

        // Notify all observers. A stale observer belonging to a torn-down AppContext
        // throws when it tries to emit; swallowing that here keeps it from aborting
        // the remaining observers and from escaping into the Activity lifecycle.
        observers.forEach {
            try {
                it(transactionStatus)
            } catch (error: Throwable) {
                Diagnostics.note("5b. Observer THREW on $transactionStatus: ${error.javaClass.simpleName}")
                Log.e("pos-app-integration", "Observer failed to handle transaction status $transactionStatus", error)
            }
        }
    }
}
