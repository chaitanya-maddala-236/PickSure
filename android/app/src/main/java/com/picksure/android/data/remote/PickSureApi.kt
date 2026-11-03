package com.picksure.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PickSureApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @GET("tasks") suspend fun tasks(): List<TaskDto>
    @GET("tasks/{taskId}/items") suspend fun taskItems(@Path("taskId") taskId: String): List<TaskItemDto>
    @POST("scan/verify") suspend fun verifyScan(@Body body: ScanVerifyRequest): ScanVerifyResponse
    @POST("scan/verify-lot") suspend fun verifyLot(@Body body: LotVerifyRequest): LotVerifyResponse
    @GET("inventory/freshness") suspend fun freshness(): FreshnessSummaryDto
    @POST("sync/events") suspend fun syncEvents(@Body body: SyncBatchRequest): SyncBatchResponse
    @GET("substitutions/pending") suspend fun pendingSubstitutions(): List<SubstitutionDto>
    @POST("substitutions/{substitutionId}/approve") suspend fun decideSubstitution(@Path("substitutionId") substitutionId: String, @Body body: ApprovalRequest): SubstitutionDto
}
