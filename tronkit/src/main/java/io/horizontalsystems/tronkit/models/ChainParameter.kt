package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChainParameter(
    @PrimaryKey
    val key: String,
    val value: Long
)
