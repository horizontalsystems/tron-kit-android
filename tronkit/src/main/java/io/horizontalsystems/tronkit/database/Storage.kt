package io.horizontalsystems.tronkit.database

import io.horizontalsystems.tronkit.models.LastBlockHeight

class Storage(
    private val database: MainDatabase
) {
    fun getLastBlockHeight(): Long? {
        return database.lastBlockHeightDao().getLastBlockHeight()?.height
    }

    fun saveLastBlockHeight(lastBlockHeight: Long) {
        database.lastBlockHeightDao().insert(LastBlockHeight(lastBlockHeight))
    }
}
