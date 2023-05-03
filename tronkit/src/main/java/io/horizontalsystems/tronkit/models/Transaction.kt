package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Transaction(
    @PrimaryKey
    val txID: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val type: String?
)