package expo.modules.rnposandroidintegration

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

object Notifier {
    // Observers are registered/deregistered from the module lifecycle while callbacks
    // are dispatched from the Activity lifecycle, so the list has to tolerate
    // concurrent mutation while it is being iterated.
    private val observers = CopyOnWriteArrayList<(status: TransactionStatus) -> Unit>()

    // A status that arrives while nothing is listening used to be dropped, and the host
    // then waited on a transaction that had already resolved. The module is rebuilt on
    // reload and can be torn down while the payment app owns the screen, so that window
    // is real. Park it here -- process-wide, outliving any single module instance -- and
    // hand it over as soon as an observer registers.
    private var parked: TransactionStatus? = null

    fun registerObserver(observer: (status: TransactionStatus) -> Unit) {
        observers.addIfAbsent(observer)
        drainParked()
    }

    fun deregisterObserver(observer: (status: TransactionStatus) -> Unit) {
        observers.remove(observer)
    }

    /** Hands whatever is parked to the current observers, if there are any. */
    fun drainParked() {
        if (observers.isEmpty()) {
            return
        }

        val pending = synchronized(this) {
            val value = parked
            parked = null
            value
        } ?: return

        Diagnostics.note("5. Draining parked $pending")
        Diagnostics.addVerdict("drained=$pending")
        dispatch(pending)
    }

    fun onTransactionStatusChanged(transactionStatus: TransactionStatus) {
        Diagnostics.addVerdict("obs=${observers.size}")

        if (observers.isEmpty()) {
            synchronized(this) { parked = transactionStatus }
            Diagnostics.note("5. No observer; parking $transactionStatus until one registers")
            Diagnostics.addVerdict("PARKED")
            Diagnostics.showVerdict()
            return
        }

        Diagnostics.note("5. Dispatching $transactionStatus to ${observers.size} observer(s)")
        dispatch(transactionStatus)
        Diagnostics.showVerdict()
    }

    private fun dispatch(transactionStatus: TransactionStatus) {
        // Notify all observers. A stale observer belonging to a torn-down AppContext
        // throws when it tries to emit; swallowing that here keeps it from aborting
        // the remaining observers and from escaping into the Activity lifecycle.
        observers.forEach {
            try {
                it(transactionStatus)
            } catch (error: Throwable) {
                Diagnostics.addVerdict("observerTHREW:${error.javaClass.simpleName}")
                Diagnostics.note("5b. Observer THREW on $transactionStatus: ${error.javaClass.simpleName}")
                Log.e("pos-app-integration", "Observer failed to handle transaction status $transactionStatus", error)
            }
        }
    }
}
