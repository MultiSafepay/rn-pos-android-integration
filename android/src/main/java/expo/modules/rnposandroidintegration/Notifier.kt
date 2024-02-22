package expo.modules.rnposandroidintegration

import android.util.Log

object Notifier {
    private val observers = mutableListOf<(status: TransactionStatus) -> Unit>()

    fun registerObserver(observer: (status: TransactionStatus) -> Unit) {
        observers.add(observer)
    }

    fun deregisterObserver(observer: (status: TransactionStatus) -> Unit) {
        observers.remove(observer)
    }

    fun onTransactionStatusChanged(transactionStatus: TransactionStatus) {
        // Notify all observers
        observers.forEach {
            it(transactionStatus)
        }
    }
}