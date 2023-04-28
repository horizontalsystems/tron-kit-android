package io.horizontalsystems.tronkit

import io.horizontalsystems.tronkit.network.Network

data class Address(
    val raw: ByteArray
) {
    constructor(base58: String, network: Network = Network.Mainnet)
            : this(AddressHandler.decode58Check(base58)) {
        AddressHandler.validate(raw, network)
    }

    val base58: String
        get() = AddressHandler.encode58Check(raw)

    val hex: String
        get() = raw.toHexString()

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true

        return if (other is Address)
            raw.contentEquals(other.raw)
        else false
    }

    override fun hashCode(): Int {
        return raw.contentHashCode()
    }

    override fun toString(): String {
        return hex
    }
}
