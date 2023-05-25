package io.horizontalsystems.tronkit

import android.util.Log
import com.google.protobuf.ByteString
import io.horizontalsystems.tronkit.models.Contract
import io.horizontalsystems.tronkit.models.TransferAssetContract
import io.horizontalsystems.tronkit.models.TransferContract
import io.horizontalsystems.tronkit.models.TriggerSmartContract
import io.horizontalsystems.tronkit.network.TronGridService
import org.tron.protos.Protocol.Transaction


sealed class Fee {

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

class ChainParameterManager {
    //curl -X POST  https://nile.trongrid.io/wallet/getchainparameters

    val transactionFee: Long // price of 1 bandwidth point
        get() {
            /*{
              "key": "getTransactionFee",
              "value": 1000
             }*/
            return 1000
        }

    val energyFee: Long // price of 1 energy unit
        get() {
            /*{
                "key": "getEnergyFee",
                "value": 420
              }*/
            return 420
        }

    val createAccountFee: Long // bandwidth points for creating account
        get() {
            /*{
              "key": "getCreateAccountFee",
              "value": 100000
            }*/
            return 100_000 / transactionFee
        }

    val createNewAccountFeeInSystemContract: Long
        get() {
            /*{
              "key": "getCreateNewAccountFeeInSystemContract",
              "value": 1000000
            }*/
            return 1_000_000
        }

}

class FeeProvider(
    private val tronGridService: TronGridService,
    private val chainParameterManager: ChainParameterManager
) {

    private val MAX_RESULT_SIZE_IN_TX: Long = 64

    private suspend fun isAccountActive(address: Address) = try {
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

    private fun extractMethod(data: String): Pair<String, ByteArray> {
        return Pair("", byteArrayOf())
    }

    private fun estimateBandwidth(contract: Contract): Long {
        val transactionBuilder = Transaction.newBuilder()

        transactionBuilder.setRawData(
            Transaction.raw.newBuilder()
                .addContract(contract.proto)
                .setTimestamp(System.currentTimeMillis())
                .setExpiration(System.currentTimeMillis())
                .setRefBlockBytes(ByteString.copyFrom(ByteArray(2)))
                .setRefBlockHash(ByteString.copyFrom(ByteArray(8)))
        )
            .addSignature(ByteString.copyFrom(ByteArray(65)))
            .clearRet()

        val transaction = transactionBuilder.build()
        val bandwidth = transaction.serializedSize + MAX_RESULT_SIZE_IN_TX
        Log.e("e", "bandwidth: $bandwidth, transaction: ${transaction.toByteArray().toRawHexString()}")

        return bandwidth
    }


    suspend fun estimateFee(contract: Contract): List<Fee> {
        val fees = mutableListOf<Fee>()

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
                val (methodSignature, parameters) = extractMethod(contract.data)
                val energyRequired = tronGridService.estimateEnergy(
                    ownerAddress = contract.ownerAddress.hex,
                    contractAddress = contract.contractAddress.hex,
                    functionSelector = methodSignature,
                    parameter = parameters
                )
                fees.add(Fee.Energy(required = energyRequired, price = chainParameterManager.energyFee))
            }

            else -> {
                throw TronKit.TransactionError.NotSupportedContract(contract)
            }
        }

        val bandwidth = estimateBandwidth(contract)
        fees.add(Fee.Bandwidth(points = bandwidth, chainParameterManager.transactionFee))

        return fees
    }

}
