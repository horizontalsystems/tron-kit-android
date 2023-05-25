package io.horizontalsystems.tronkit.sync

import android.util.Log
import com.google.protobuf.ByteString
import io.horizontalsystems.tronkit.Signer
import io.horizontalsystems.tronkit.TronKit.TransactionError
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.TransferContract
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.horizontalsystems.tronkit.network.BroadcastTransactionResponse
import io.horizontalsystems.tronkit.network.CreatedTransaction
import io.horizontalsystems.tronkit.network.TronGridService
import io.horizontalsystems.tronkit.toRawHexString
import org.tron.protos.Protocol.Transaction

class TransactionSender(
    private val tronGridService: TronGridService
) {
    private fun isValidCreatedTransaction(createdTransaction: CreatedTransaction, contract: Contract): Boolean {
        val rawData = Transaction.raw.parseFrom(ByteString.fromHex(createdTransaction.raw_data_hex))
        val createdContractProto = if (rawData.contractCount == 1) rawData.getContract(0) else null
        val originalContractProto = contract.proto

        Log.e(
            "e", "isValidCreatedTransaction: "
                    + "\n contract type: ${createdContractProto?.type}"
                    + "\n contract hasParameter: ${createdContractProto?.hasParameter()}"
                    + "\n contract parameter: ${createdContractProto?.parameter?.toByteArray()?.toRawHexString()}"
                    + "\n original contract parameter: ${originalContractProto.parameter.toByteArray().toRawHexString()}"
        )
        return createdContractProto != null &&
                createdContractProto.type == originalContractProto.type &&
                createdContractProto.hasParameter() &&
                createdContractProto.parameter == originalContractProto.parameter
    }

    suspend fun createTransaction(contract: Contract, feeLimit: Long?): CreatedTransaction {
        val createdTransaction = when (contract) {
            is TransferContract -> {
                tronGridService.createTransaction(
                    fromAddress = contract.ownerAddress,
                    toAddress = contract.toAddress,
                    amount = contract.amount
                )
            }

            is TriggerSmartContract -> {
                tronGridService.triggerSmartContract(
                    ownerAddress = contract.ownerAddress,
                    contractAddress = contract.contractAddress,
                    functionSelector = contract.functionSelector ?: throw TransactionError.NoFunctionSelector(contract),
                    parameter = contract.parameter ?: throw TransactionError.NoParameter(contract),
                    feeLimit = feeLimit ?: throw TransactionError.NoFeeLimit(contract),
                    callValue = contract.callValue?.toLong() ?: 0,
                )
            }

            else -> {
                throw TransactionError.NotSupportedContract(contract)
            }
        }

        Log.e("e", "createdTransaction: $createdTransaction")

        if (isValidCreatedTransaction(createdTransaction, contract)) {
            return createdTransaction
        } else {
            throw TransactionError.InvalidCreatedTransaction(createdTransaction.raw_data_hex)
        }
    }

    suspend fun broadcastTransaction(createdTransaction: CreatedTransaction, signer: Signer): BroadcastTransactionResponse {
        val signature = signer.sign(createdTransaction)
        return tronGridService.broadcastTransaction(createdTransaction, signature)
    }

}
