package io.horizontalsystems.tronkit.decoration.trc20

import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.contracts.trc20.ApproveMethod
import io.horizontalsystems.tronkit.contracts.ContractMethodFactories
import io.horizontalsystems.tronkit.contracts.trc20.ApproveMethodFactory
import io.horizontalsystems.tronkit.contracts.trc20.TransferMethod
import io.horizontalsystems.tronkit.contracts.trc20.TransferMethodFactory
import io.horizontalsystems.tronkit.decoration.Event
import io.horizontalsystems.tronkit.decoration.ITransactionDecorator
import io.horizontalsystems.tronkit.decoration.TransactionDecoration
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.models.InternalTransaction
import io.horizontalsystems.tronkit.models.TriggerSmartContract

class Trc20TransactionDecorator(
    private val userAddress: Address
) : ITransactionDecorator {

    private val factories = ContractMethodFactories()

    init {
        factories.registerMethodFactories(listOf(TransferMethodFactory, ApproveMethodFactory))
    }

    override fun decoration(
        contract: TriggerSmartContract,
        internalTransactions: List<InternalTransaction>,
        events: List<Event>
    ): TransactionDecoration? {
        val contractMethod = factories.createMethodFromInput(contract.data.hexStringToByteArray())

        return when {
            contractMethod is TransferMethod && contract.ownerAddress == userAddress -> {
                val tokenInfo =
                    (events.firstOrNull { it is Trc20TransferEvent && it.contractAddress == contract.contractAddress } as? Trc20TransferEvent)?.tokenInfo
                OutgoingTrc20Decoration(
                    contractAddress = contract.contractAddress,
                    to = contractMethod.to,
                    value = contractMethod.value,
                    sentToSelf = contractMethod.to == userAddress,
                    tokenInfo = tokenInfo
                )
            }

            contractMethod is ApproveMethod -> {
                ApproveTrc20Decoration(
                    contractAddress = contract.contractAddress,
                    spender = contractMethod.spender,
                    value = contractMethod.value
                )
            }

            else -> null
        }
    }

}
