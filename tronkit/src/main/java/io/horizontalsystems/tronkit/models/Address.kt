package io.horizontalsystems.tronkit.models

import io.horizontalsystems.tronkit.account.AddressHandler
import io.horizontalsystems.tronkit.hexStringToByteArray
import io.horizontalsystems.tronkit.network.Network
import io.horizontalsystems.tronkit.toRawHexString

data class Address(
    val raw: ByteArray
) {
    init {
        AddressHandler.validate(raw)
    }

    companion object {
        fun fromRawWithoutPrefix(rawWithoutPrefix: ByteArray, prefixByte: Byte = Network.Mainnet.addressPrefixByte): Address {
            return Address(byteArrayOf(prefixByte) + rawWithoutPrefix)
        }

        fun fromHex(hex: String): Address {
            return Address(hex.hexStringToByteArray())
        }

        fun fromBase58(base58: String): Address {
            return Address(AddressHandler.decode58Check(base58))
        }
    }

    val base58: String
        get() = AddressHandler.encode58Check(raw)

    val hex: String
        get() = raw.toRawHexString()

    val rawWithoutPrefix: ByteArray
        get() = raw.drop(1).toByteArray()

    override fun equals(other: Any?): Boolean {
        return this === other || other is Address && raw.contentEquals(other.raw)
    }

    override fun hashCode(): Int {
        return raw.contentHashCode()
    }

    override fun toString(): String {
        return hex
    }
}
