package io.horizontalsystems.tronkit.models

import io.horizontalsystems.tronkit.decoration.TransactionDecoration

class FullTransaction(
    val transaction: Transaction,
    val decoration: TransactionDecoration
)
