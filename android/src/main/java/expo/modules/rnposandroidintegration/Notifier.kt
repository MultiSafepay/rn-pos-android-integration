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
            Log.w("pos-app-integration", "No observer registered for transaction status $transactionStatus")
            return
        }

        // Notify all observers. A stale observer belonging to a torn-down AppContext
        // throws when it tries to emit; swallowing that here keeps it from aborting
        // the remaining observers and from escaping into the Activity lifecycle.
        observers.forEach {
            try {
                it(transactionStatus)
            } catch (error: Throwable) {
                Log.e("pos-app-integration", "Observer failed to handle transaction status $transactionStatus", error)
            }
        }
    }
}
