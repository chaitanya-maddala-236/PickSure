package com.picksure.android.data.remote

import android.content.Context
import com.picksure.android.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiFactory {
    fun create(context: Context): PickSureApi {
        val appContext = context.applicationContext
        val auth = Interceptor { chain ->
            val token = appContext.getSharedPreferences("picksure", Context.MODE_PRIVATE).getString("token", null)
            val request = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            }.build()
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder().addInterceptor(auth).build()
        return Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(PickSureApi::class.java)
    }
}
