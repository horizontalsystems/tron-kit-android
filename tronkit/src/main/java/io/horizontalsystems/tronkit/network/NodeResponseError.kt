package io.horizontalsystems.tronkit.network

/**
 * The node answered and refused the request. [code] is the node's result code where it gives
 * one, e.g. `CONTRACT_VALIDATE_ERROR` or `DUP_TRANSACTION_ERROR` for a broadcast.
 */
class NodeResponseError(message: String, val code: String? = null) : IllegalStateException(message) {

    /** The node already holds this exact transaction, so it has been delivered. */
    val isDuplicateTransaction: Boolean
        get() = code == DUP_TRANSACTION

    /**
     * The refusal says something about this node (overloaded, cut off from its peers, behind),
     * not about the request, so another node may accept it.
     */
    val isNodeState: Boolean
        get() = code in nodeStateCodes

    companion object {
        private const val DUP_TRANSACTION = "DUP_TRANSACTION_ERROR"

        private val nodeStateCodes = setOf(
            "SERVER_BUSY",
            "NO_CONNECTION",
            "NOT_ENOUGH_EFFECTIVE_CONNECTION",
            "BLOCK_UNSOLIDIFIED",
        )
    }
}
