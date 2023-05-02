package io.horizontalsystems.tronkit.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

@Entity
data class Balance(
    @PrimaryKey
    val id: String,
    val balance: BigInteger
)
