package io.horizontalsystems.tronkit.crypto

import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.jce.spec.ECParameterSpec
import java.math.BigInteger
import java.security.MessageDigest

object Utils {
    val CURVE: ECDomainParameters
    val HALF_CURVE_ORDER: BigInteger
    private val CURVE_SPEC: ECParameterSpec

    private val HASH_256_ALGORITHM_NAME: String = "ETH-KECCAK-256"

    init {
        val params = SECNamedCurves.getByName("secp256k1")
        CURVE = ECDomainParameters(params.curve, params.g, params.n, params.h)
        CURVE_SPEC = ECParameterSpec(params.curve, params.g, params.n, params.h)
        HALF_CURVE_ORDER = params.n.shiftRight(1)
    }

    fun sha3(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(HASH_256_ALGORITHM_NAME)
        digest.update(data)
        return digest.digest()
    }
}