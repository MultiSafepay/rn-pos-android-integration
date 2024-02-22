package expo.modules.rnposandroidintegration

enum class TransactionStatus(val value: String) {
    EXCEPTION("EXCEPTION"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED"),
    DECLINED("DECLINED")
}