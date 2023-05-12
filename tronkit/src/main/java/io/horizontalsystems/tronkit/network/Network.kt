package io.horizontalsystems.tronkit.network

enum class Network(
    val id: Int,
    val coinType: Int,
    val addressPrefixByte: Byte
) {
    Mainnet(1, 195, 0x41.toByte()),
    ShastaTestnet(2, 1, 0xa0.toByte()),
    NileTestnet(3, 195, 0x41.toByte())
}