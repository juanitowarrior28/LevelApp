package com.levelapp.app.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object AudioDataStream {
    private val _liveDbFlow = MutableSharedFlow<Double>(extraBufferCapacity = 1)
    val liveDbFlow: SharedFlow<Double> = _liveDbFlow

    fun emitDb(db: Double) {
        _liveDbFlow.tryEmit(db)
    }
}
