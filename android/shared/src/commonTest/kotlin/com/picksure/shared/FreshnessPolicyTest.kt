package com.picksure.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class FreshnessPolicyTest {
    @Test
    fun exactShelfLifeBoundaryIsEligible() {
        assertEquals(LotDisposition.AVAILABLE, FreshnessPolicy.classify(13, 10, minimumDays = 3))
    }

    @Test
    fun lotInsideShelfLifeWindowIsHeldBack() {
        assertEquals(LotDisposition.NEAR_EXPIRY, FreshnessPolicy.classify(12, 10, minimumDays = 3))
    }

    @Test
    fun expiredAndEmptyLotsAreNeverAvailable() {
        assertEquals(LotDisposition.EXPIRED, FreshnessPolicy.classify(10, 10, minimumDays = 0))
        assertEquals(LotDisposition.EMPTY, FreshnessPolicy.classify(20, 10, minimumDays = 3, quantity = 0))
    }

    @Test
    fun recommendationChoosesEarliestEligibleLotWithStableTieBreak() {
        val lots = listOf(
            LotCandidate("held", "A", expiresOnEpochDay = 12, quantity = 4),
            LotCandidate("later", "C", expiresOnEpochDay = 20, quantity = 5),
            LotCandidate("earliest", "B", expiresOnEpochDay = 15, quantity = 5),
        )
        assertEquals("earliest", FreshnessPolicy.recommend(lots, todayEpochDay = 10, minimumDays = 3)?.id)
    }
}
