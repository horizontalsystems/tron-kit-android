package io.horizontalsystems.tronkit

data class Address(
    val raw: ByteArray
) {
    init {
        AddressHandler.validate(raw)
    }

    companion object {
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
