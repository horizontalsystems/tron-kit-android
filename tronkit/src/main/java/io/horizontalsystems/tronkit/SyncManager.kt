package io.horizontalsystems.tronkit

import android.util.Log

class SyncManager(
    private val network: Network,
    private val tronGridService: TronGridService
) {
    init {

    }

    suspend fun start() {
        val accountInfo = tronGridService.getAccountInfo("TNeQ7jLVzXUB9kXVurzN9ZQibLaykov5v2")

        Log.e("e", "accountInfo = $accountInfo")
    }

}
