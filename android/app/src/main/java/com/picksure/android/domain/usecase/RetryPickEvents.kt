package com.picksure.android.domain.usecase

import com.picksure.android.domain.repository.PickEventRepository

class RetryPickEvents(private val repository: PickEventRepository) {
    suspend operator fun invoke() = repository.retry()
}
