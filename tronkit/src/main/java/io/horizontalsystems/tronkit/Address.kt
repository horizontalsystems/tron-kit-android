package io.horizontalsystems.tronkit

import io.horizontalsystems.tronkit.network.Network

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
