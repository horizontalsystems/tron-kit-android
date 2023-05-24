package io.horizontalsystems.tronkit.models

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.protobuf.Any
import com.google.protobuf.ByteString
import io.horizontalsystems.tronkit.Address
import io.horizontalsystems.tronkit.network.ContractRaw
import org.tron.protos.Protocol
import org.tron.protos.contract.BalanceContract
import java.math.BigInteger

sealed class Contract {

    val proto: Protocol.Transaction.Contract
        get() = when (this) {
            is TransferContract -> {
                val transferContract = BalanceContract.TransferContract.newBuilder()
                    .setToAddress(ByteString.fromHex(this.toAddress.hex))
                    .setOwnerAddress(ByteString.fromHex(this.ownerAddress.hex))
                    .setAmount(this.amount.toLong())
                    .build()

                val parameter = Any.newBuilder()
                    .setValue(transferContract.toByteString())
                    .setTypeUrl("type.googleapis.com/protocol.TransferContract")
                    .build()

                Protocol.Transaction.Contract.newBuilder()
                    .setType(Protocol.Transaction.Contract.ContractType.TransferContract)
                    .setParameter(parameter)
                    .build()
            }

            else -> throw IllegalStateException("No proto conversion for this contract: ${this.javaClass.simpleName}")
        }

    companion object {
        private val gson: Gson = Gson()

        fun from(contractsJson: String): Contract? {
            try {
                val contracts: List<ContractRaw> = gson.fromJson(contractsJson, object : TypeToken<List<ContractRaw>>() {}.type)
                val contract = contracts.firstOrNull()

                return when (contract?.type) {

                    "TransferContract" -> {
                        TransferContract(
                            amount = contract.amount!!,
                            ownerAddress = contract.ownerAddress!!,
                            toAddress = contract.toAddress!!
                        )
                    }

                    "TransferAssetContract" -> {
                        TransferAssetContract(
                            amount = contract.amount!!,
                            assetName = contract.assetName!!,
                            ownerAddress = contract.ownerAddress!!,
                            toAddress = contract.toAddress!!
                        )
                    }

                    "WithdrawBalanceContract" -> {
                        WithdrawBalanceContract(
                            amount = contract.withdrawAmount!!,
                            ownerAddress = contract.ownerAddress!!
                        )
                    }

                    "TriggerSmartContract" -> {
                        val value = contract.parameter.value
                        TriggerSmartContract(
                            data = contract.data!!,
                            ownerAddress = contract.ownerAddress!!,
                            contractAddress = contract.contractAddress!!,
                            callValue = value.call_value,
                            callTokenValue = value.call_token_value,
                            tokenId = value.token_id
                        )
                    }

                    "AssetIssueContract" -> {
                        val value = contract.parameter.value
                        AssetIssueContract(
                            totalSupply = value.total_supply!!,
                            precision = value.precision!!,
                            name = value.name!!,
                            description = value.description!!,
                            ownerAddress = contract.ownerAddress!!,
                            abbreviation = value.abbr!!,
                            url = value.url!!
                        )
                    }

                    "UnfreezeBalanceV2Contract" -> {
                        val value = contract.parameter.value
                        UnfreezeBalanceV2Contract(
                            resource = value.resource!!,
                            ownerAddress = contract.ownerAddress!!,
                            unfreezeBalance = value.unfreeze_balance!!
                        )
                    }

                    "FreezeBalanceV2Contract" -> {
                        val value = contract.parameter.value
                        FreezeBalanceV2Contract(
                            resource = value.resource!!,
                            ownerAddress = contract.ownerAddress!!,
                            frozenBalance = value.frozen_balance!!
                        )
                    }

                    "VoteWitnessContract" -> {
                        val value = contract.parameter.value
                        VoteWitnessContract(
                            ownerAddress = contract.ownerAddress!!,
                            votes = value.votes!!.map { vote ->
                                VoteWitnessContract.Vote(
                                    address = Address.fromHex(vote.vote_address),
                                    count = vote.vote_count
                                )
                            }
                        )
                    }

                    "CreateSmartContract" -> {
                        CreateSmartContract(contract.ownerAddress!!)
                    }

                    else -> {
                        Log.e("e", "unknown contract: $contractsJson")
                        Unknown(contractsJson)
                    }
                }
            } catch (error: Throwable) {
                Log.e("e", "Contract parse error", error)
                return null
            }
        }
    }

}

data class Unknown(
    val contractsRaw: String
) : Contract()

//TRX Transfer
data class TransferContract(
    val amount: BigInteger,
    val ownerAddress: Address,
    val toAddress: Address
) : Contract()

//Claim Rewards
data class WithdrawBalanceContract(
    val amount: Long,
    val ownerAddress: Address
) : Contract()

//Transfer TRC10 Token
data class TransferAssetContract(
    val amount: BigInteger,
    val assetName: String,
    val ownerAddress: Address,
    val toAddress: Address
) : Contract()

//Trigger Smart Contract
data class TriggerSmartContract(
    val data: String,
    val ownerAddress: Address,
    val contractAddress: Address,
    val callValue: BigInteger?,
    val callTokenValue: BigInteger?,
    val tokenId: Int?
) : Contract()

//Issue TRC10 token
data class AssetIssueContract(
    val totalSupply: Long,
    val precision: Int,
    val name: String,
    val description: String,
    val ownerAddress: Address,
    val abbreviation: String,
    val url: String
) : Contract()

//TRX Unstake 2.0
data class UnfreezeBalanceV2Contract(
    val resource: String,
    val ownerAddress: Address,
    val unfreezeBalance: Long
) : Contract()

//TRX Stake 2.0
data class FreezeBalanceV2Contract(
    val resource: String,
    val ownerAddress: Address,
    val frozenBalance: Long
) : Contract()

//Vote
data class VoteWitnessContract(
    val ownerAddress: Address,
    val votes: List<Vote>
) : Contract() {

    val totalVotesCount: Long
        get() = votes.sumOf { it.count }

    data class Vote(
        val address: Address,
        val count: Long
    )
}

//Create Smart Contract
data class CreateSmartContract(
    val ownerAddress: Address
) : Contract()
