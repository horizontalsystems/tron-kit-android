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
        val incomingTxs = internalTransactions.filter { it.to == userAddress }
        val outgoingTxs = internalTransactions.filter { it.from == userAddress }

        var incomingValue = incomingTxs.sumOf { it.value }
        var outgoingValue = outgoingTxs.sumOf { it.value }

        value?.let {
            when (userAddress) {
                toAddress -> incomingValue += value
                fromAddress -> outgoingValue += value
            }
        }

        return buildList {
            when {
                incomingValue > outgoingValue -> {
                    add(TransactionTag.TRX_COIN_INCOMING)
                    add(TransactionTag.INCOMING)
                }
                incomingValue < outgoingValue -> {
                    add(TransactionTag.TRX_COIN_OUTGOING)
                    add(TransactionTag.OUTGOING)
                }
            }

            internalTransactions.forEach { internalTransaction ->
                if (internalTransaction.from != userAddress) {
                    add(TransactionTag.fromAddress(internalTransaction.from.hex))
                }

                if (internalTransaction.to != userAddress) {
                    add(TransactionTag.toAddress(internalTransaction.to.hex))
                }
            }
        }
    }

    private fun tagsFromEvents(userAddress: Address): List<String> {
        val tags: MutableList<String> = mutableListOf()

        for (event in events) {
            tags.addAll(event.tags(userAddress))
        }

        return tags
    }
}
