package io.horizontalsystems.tronkit.decoration

import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.TriggerSmartContract

interface ITransactionDecorator {
    fun decoration(
        contract: TriggerSmartContract,
        internalTransactions: List<InternalTransaction>,
        events: List<Event>
    ): TransactionDecoration?
}
