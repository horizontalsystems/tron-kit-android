package io.horizontalsystems.tronkit.sync

import io.horizontalsystems.tronkit.TronKit
import io.horizontalsystems.tronkit.network.ConnectionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class SyncTimer(
    private val syncInterval: Long,
    private val connectionManager: ConnectionManager
) {

    interface Listener {
        fun onUpdateSyncTimerState(state: State)
        fun sync()
    }

    private var scope: CoroutineScope? = null
    private var isStarted = false
    private var timerJob: Job? = null
    private var listener: Listener? = null

    init {
        connectionManager.listener = object : ConnectionManager.Listener {
            override fun onConnectionChange() {
                handleConnectionChange()
            }
        }
    }

    var state: State = State.NotReady(TronKit.SyncError.NotStarted())
        private set(value) {
            if (value != field) {
                field = value
                listener?.onUpdateSyncTimerState(value)
            }
        }

    fun start(listener: Listener, scope: CoroutineScope) {
        isStarted = true

        this.listener = listener
        this.scope = scope

        handleConnectionChange()
    }

    fun stop() {
        isStarted = false

        connectionManager.stop()
        state = State.NotReady(TronKit.SyncError.NotStarted())
        scope = null
        stopTimer()
    }

    private fun handleConnectionChange() {
        if (!isStarted) return

        if (connectionManager.isConnected) {
            state = State.Ready
            startTimer()
        } else {
            state = State.NotReady(TronKit.SyncError.NoNetworkConnection())
            stopTimer()
        }
    }

    private fun startTimer() {
        timerJob = scope?.launch {
            flow {
                while (isActive) {
                    emit(Unit)
                    delay(syncInterval.toDuration(DurationUnit.SECONDS))
                }
            }.collect {
                listener?.sync()
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
    }

    sealed class State {
        object Ready : State()
        class NotReady(val error: Throwable) : State()
    }
}
