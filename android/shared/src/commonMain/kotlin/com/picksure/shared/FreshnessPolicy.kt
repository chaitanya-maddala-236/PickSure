package com.picksure.shared

enum class LotDisposition { AVAILABLE, NEAR_EXPIRY, EXPIRED, EMPTY }

data class LotCandidate(
    val id: String,
    val lotCode: String,
    val expiresOnEpochDay: Long,
    val quantity: Int,
)

object FreshnessPolicy {
    fun classify(expiresOnEpochDay: Long, todayEpochDay: Long, minimumDays: Int, quantity: Int = 1): LotDisposition {
        require(minimumDays >= 0) { "Minimum shelf life cannot be negative" }
        if (quantity <= 0) return LotDisposition.EMPTY
        if (expiresOnEpochDay <= todayEpochDay) return LotDisposition.EXPIRED
        if (expiresOnEpochDay < todayEpochDay + minimumDays) return LotDisposition.NEAR_EXPIRY
        return LotDisposition.AVAILABLE
    }

    fun recommend(lots: List<LotCandidate>, todayEpochDay: Long, minimumDays: Int): LotCandidate? =
        lots.asSequence()
            .filter { classify(it.expiresOnEpochDay, todayEpochDay, minimumDays, it.quantity) == LotDisposition.AVAILABLE }
            .minWithOrNull(compareBy<LotCandidate>({ it.expiresOnEpochDay }, { it.lotCode }))
}
