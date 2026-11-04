package com.picksure.android.domain.usecase

import com.picksure.android.domain.model.PickEvent
import com.picksure.android.domain.repository.PickEventRepository
import kotlinx.coroutines.flow.Flow

class ObservePickEvents(private val repository: PickEventRepository) {
    operator fun invoke(): Flow<List<PickEvent>> = repository.observeEvents()
}
