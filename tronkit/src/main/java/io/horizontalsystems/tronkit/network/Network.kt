package io.horizontalsystems.tronkit.network

import java.net.URL

enum class Network(
    val id: Int,
    val coinType: Int,
    val addressPrefixByte: Byte,
    val tronGridUrl: URL
) {
    Mainnet(1, 195, 0x41.toByte(), URL("https://api.trongrid.io/")),
    ShastaTestnet(2, 1, 0xa0.toByte(), URL("https://api.shasta.trongrid.io/")),
    NileTestnet(3, 195, 0x41.toByte(), URL("https://nile.trongrid.io/"))
}