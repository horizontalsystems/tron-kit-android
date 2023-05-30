package io.horizontalsystems.tronkit.decoration

import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.TransactionTag
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import java.math.BigInteger

class UnknownTransactionDecoration(
    val fromAddress: Address?,
    val toAddress: Address?,
    val value: BigInteger?,
    val data: String?,
    val tokenValue: BigInteger?,
    val tokenId: Int?,
    val internalTransactions: List<InternalTransaction>,
    val events: List<Event>
) : TransactionDecoration() {

    constructor(
        contract: TriggerSmartContract?,
        internalTransactions: List<InternalTransaction>,
        events: List<Event>
    ) : this(
        fromAddress = contract?.ownerAddress,
        toAddress = contract?.contractAddress,
        value = contract?.callValue,
        data = contract?.data,
        tokenValue = contract?.callTokenValue,
        tokenId = contract?.tokenId,
        internalTransactions = internalTransactions,
        events = events
    )

    override fun tags(userAddress: Address): List<String> =
        (tagsFromInternalTransactions(userAddress) + tagsFromEvents(userAddress)).toSet().toList()

    private fun tagsFromInternalTransactions(userAddress: Address): List<String> {
        var outgoingValue = if (fromAddress == userAddress) value ?: BigInteger.ZERO else BigInteger.ZERO
        for (internalTx in internalTransactions.filter { it.from == userAddress }) {
            outgoingValue += internalTx.value
        }

        var incomingValue = if (toAddress == userAddress) value ?: BigInteger.ZERO else BigInteger.ZERO
        for (internalTx in internalTransactions.filter { it.to == userAddress }) {
            incomingValue += internalTx.value
        }

        if (incomingValue == BigInteger.ZERO && outgoingValue == BigInteger.ZERO) return listOf()

        val tags = mutableListOf(TransactionTag.TRX_COIN)

        if (incomingValue > outgoingValue) {
            tags.add(TransactionTag.TRX_COIN_INCOMING)
            tags.add(TransactionTag.INCOMING)
        }

        if (outgoingValue > incomingValue) {
            tags.add(TransactionTag.TRX_COIN_OUTGOING)
            tags.add(TransactionTag.OUTGOING)
        }

        return tags
    }

    private fun tagsFromEvents(userAddress: Address): List<String> {
        val tags: MutableList<String> = mutableListOf()

        for (event in events) {
            tags.addAll(event.tags(userAddress))
        }

        return tags
    }
}
