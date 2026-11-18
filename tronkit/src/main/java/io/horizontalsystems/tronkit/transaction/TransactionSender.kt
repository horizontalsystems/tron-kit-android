package io.horizontalsystems.tronkit.transaction

import com.google.protobuf.ByteString
import io.horizontalsystems.tronkit.TronKit.TransactionError
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.TransferContract
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.INodeApiProvider
import io.horizontalsystems.tronkit.network.NodeResponseError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.tron.protos.Protocol.Transaction

class TransactionSender(
    private val nodeApiProvider: INodeApiProvider,
    private val deliveryCheckTimeoutMs: Long = DELIVERY_CHECK_TIMEOUT_MS,
) {
    private fun isValidCreatedTransaction(createdTransaction: CreatedTransaction, contract: Contract): Boolean {
        val rawData = Transaction.raw.parseFrom(ByteString.fromHex(createdTransaction.raw_data_hex))
        val createdContractProto = if (rawData.contractCount == 1) rawData.getContract(0) else null
        val originalContractProto = contract.proto

        return createdContractProto != null &&
                createdContractProto.type == originalContractProto.type &&
                createdContractProto.hasParameter() &&
                createdContractProto.parameter == originalContractProto.parameter
    }

    suspend fun createTransaction(contract: Contract, feeLimit: Long?): CreatedTransaction {
        val createdTransaction = when (contract) {
            is TransferContract -> {
                nodeApiProvider.createTransaction(
                    ownerAddress = contract.ownerAddress.hex,
                    toAddress = contract.toAddress.hex,
                    amount = contract.amount
                )
            }

            is TriggerSmartContract -> {
                nodeApiProvider.triggerSmartContract(
                    ownerAddress = contract.ownerAddress.hex,
                    contractAddress = contract.contractAddress.hex,
                    functionSelector = contract.functionSelector ?: throw TransactionError.NoFunctionSelector(contract),
                    parameter = contract.parameter ?: throw TransactionError.NoParameter(contract),
                    callValue = contract.callValue?.toLong() ?: 0,
                    feeLimit = feeLimit ?: throw TransactionError.NoFeeLimit(contract)
                )
            }

            else -> {
                throw TransactionError.NotSupportedContract(contract)
            }
        }

        if (isValidCreatedTransaction(createdTransaction, contract)) {
            return createdTransaction
        } else {
            throw TransactionError.InvalidCreatedTransaction(createdTransaction.raw_data_hex)
        }
    }

    // Broadcasts and returns the txID on success; throws on failure.
    suspend fun broadcastTransaction(createdTransaction: CreatedTransaction, signer: Signer): String {
        val signature = signer.sign(createdTransaction)
        broadcast(createdTransaction, signature)
        return createdTransaction.txID
    }

    internal suspend fun broadcast(createdTransaction: CreatedTransaction, signature: ByteArray) {
        try {
            nodeApiProvider.broadcastTransaction(createdTransaction, signature)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // A failure here does not mean the transaction was not sent: an earlier attempt may
            // have reached a node whose answer was lost. The endpoint failover then delivers
            // it again, and the next node rejects it as a duplicate, or as expired once its
            // minute has passed. Reporting that as a failure invites sending again, which
            // would be a second transaction. The id is the hash of the transaction's data, so
            // a node that knows it has this transaction.
            val duplicate = error is NodeResponseError && error.isDuplicateTransaction
            if (!duplicate && !isOnChain(createdTransaction.txID)) throw error
        }
    }

    private suspend fun isOnChain(txID: String): Boolean =
        withTimeoutOrNull(deliveryCheckTimeoutMs) {
            try {
                nodeApiProvider.isTransactionOnChain(txID)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                false
            }
        } ?: false

    companion object {
        // Long enough for a live node to answer, short enough not to hold up a real failure
        private const val DELIVERY_CHECK_TIMEOUT_MS = 5_000L
    }
}
