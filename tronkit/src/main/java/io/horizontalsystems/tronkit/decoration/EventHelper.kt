package io.horizontalsystems.tronkit.decoration

import io.horizontalsystems.tronkit.decoration.trc20.Trc20ApproveEvent
import io.horizontalsystems.tronkit.decoration.trc20.Trc20TransferEvent
import io.horizontalsystems.tronkit.models.Trc20EventRecord

object EventHelper {

    fun eventFromRecord(record: Trc20EventRecord): Event? = when (record.type) {
        "Transfer" -> Trc20TransferEvent(record)
        "Approval" -> Trc20ApproveEvent(record)
        else -> null
    }

}
