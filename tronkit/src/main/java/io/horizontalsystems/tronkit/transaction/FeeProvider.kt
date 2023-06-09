package io.horizontalsystems.tronkit.transaction

import com.google.protobuf.ByteString
import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.models.Address
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.TransferAssetContract
import io.horizontalsystems.tronkit.models.TransferContract
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.horizontalsystems.tronkit.network.TronGridService
import io.horizontalsystems.tronkit.sync.ChainParameterManager
import org.tron.protos.Protocol.Transaction
import java.math.BigInteger


sealed class Fee {

    val feeInSuns: Long
        get() = when (this) {
            is AccountActivation -> amount
            is Bandwidth -> points * price
            is Energy -> required * price
        }

    data class Bandwidth(
        val points: Long,
        val price: Long //sun
    ) : Fee()

    data class Energy(
        val required: Long,
        val price: Long //sun
    ) : Fee()

    data class AccountActivation(
        val amount: Long //sun current 1TRX
    ) : Fee()

}

// curl -X POST  https://nile.trongrid.io/wallet/getaccountresource -d '{"address" : "TNeQ7jLVzXUB9kXVurzN9ZQibLaykov5v2" }'

class FeeProvider(
    private val tronGridService: TronGridService,
    private val chainParameterManager: ChainParameterManager
) {

    private val MAX_RESULT_SIZE_IN_TX: Long = 64

    suspend fun isAccountActive(address: Address) = try {
        tronGridService.getAccountInfo(address.base58)
        true
    } catch (error: TronGridService.TronGridServiceError.NoAccountInfoData) {
        false
    }

    private fun feesAccountActivation(): List<Fee> {
        return listOf(
            Fee.Bandwidth(points = chainParameterManager.createAccountFee, price = chainParameterManager.transactionFee),
            Fee.AccountActivation(amount = chainParameterManager.createNewAccountFeeInSystemContract)
        )
    }

    private fun estimateBandwidth(contract: Contract, feeLimit: Long): Long {
        val transactionBuilder = Transaction.newBuilder()

        transactionBuilder.setRawData(
            Transaction.raw.newBuilder()
                .addContract(contract.proto)
                .setTimestamp(System.currentTimeMillis())
                .setExpiration(System.currentTimeMillis())
                .setRefBlockBytes(ByteString.copyFrom(ByteArray(2)))
                .setRefBlockHash(ByteString.copyFrom(ByteArray(8)))
                .setFeeLimit(feeLimit)
        )
            .addSignature(ByteString.copyFrom(ByteArray(65)))
            .clearRet()

        val transaction = transactionBuilder.build()

        return transaction.serializedSize + MAX_RESULT_SIZE_IN_TX
    }

    suspend fun estimateFee(contract: Contract): List<Fee> {
        val fees = mutableListOf<Fee>()
        var feeLimit: Long = 0

        when (contract) {
            is TransferContract -> {
                if (!isAccountActive(contract.toAddress)) {
                    return feesAccountActivation()
                }
            }

            is TransferAssetContract -> {
                if (!isAccountActive(contract.toAddress)) {
                    return feesAccountActivation()
                }
            }

            is TriggerSmartContract -> {
                val energyRequired = tronGridService.estimateEnergy(
                    ownerAddress = "0x${contract.ownerAddress.hex}",
                    contractAddress = "0x${contract.contractAddress.hex}",
                    value = contract.callValue ?: BigInteger.ZERO,
                    data = "0x${contract.data}"
                )
                val feeEnergy = Fee.Energy(required = energyRequired, price = chainParameterManager.energyFee)
                fees.add(feeEnergy)

                feeLimit = feeEnergy.feeInSuns
            }

            else -> {
                throw TronKit.TransactionError.NotSupportedContract(contract)
            }
        }

        val bandwidth = estimateBandwidth(contract, feeLimit)
        fees.add(Fee.Bandwidth(points = bandwidth, chainParameterManager.transactionFee))

        return fees
    }

}
