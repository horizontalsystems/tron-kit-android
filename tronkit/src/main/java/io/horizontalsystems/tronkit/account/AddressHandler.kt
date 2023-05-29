package io.horizontalsystems.tronkit.account

import io.horizontalsystems.hdwalletkit.Base58
import io.horizontalsystems.hdwalletkit.Utils
import io.horizontalsystems.tronkit.network.Network


object AddressHandler {

    private const val addressSize = 21

    @Throws(AddressValidationException::class)
    fun validate(address: ByteArray, network: Network = Network.Mainnet) {
        if (address.size != addressSize) {
            throw AddressValidationException.InvalidAddressLength(addressSize, address.size)

        }
        val prefixByte = address[0]
        if (prefixByte != network.addressPrefixByte) {
            throw AddressValidationException.InvalidAddressPrefix(network.addressPrefixByte, prefixByte)
        }
    }

    fun encode58Check(input: ByteArray): String {
        val hash: ByteArray = Utils.doubleDigest(input)
        val inputCheck = ByteArray(input.size + 4)
        System.arraycopy(input, 0, inputCheck, 0, input.size)
        System.arraycopy(hash, 0, inputCheck, input.size, 4)
        return Base58.encode(inputCheck)
    }

    fun decode58Check(input: String): ByteArray {
        val decodeCheck = Base58.decode(input)
        if (decodeCheck.size <= 4) {
            throw AddressValidationException.InvalidChecksum
        }
        val decodeData = ByteArray(decodeCheck.size - 4)
        System.arraycopy(decodeCheck, 0, decodeData, 0, decodeData.size)
        val hash0: ByteArray = Utils.sha256(decodeData)
        val hash1: ByteArray = Utils.sha256(hash0)
        return if (
            hash1[0] == decodeCheck[decodeData.size] &&
            hash1[1] == decodeCheck[decodeData.size + 1] &&
            hash1[2] == decodeCheck[decodeData.size + 2] &&
            hash1[3] == decodeCheck[decodeData.size + 3]
        ) {
            decodeData
        } else
            throw AddressValidationException.InvalidChecksum
    }

    sealed class AddressValidationException : Throwable() {
        class InvalidAddressLength(val expected: Int, val actual: Int) : AddressValidationException()
        class InvalidAddressPrefix(val expected: Byte, val actual: Byte) : AddressValidationException()
        object InvalidChecksum : AddressValidationException()
    }
}
