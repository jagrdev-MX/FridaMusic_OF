package com.jagr.fridamusic.data

import com.jagr.fridamusic.BuildConfig
import com.jagr.fridamusic.api.FlowNeuroengineApi
import com.jagr.fridamusic.api.NeuroengineRequest
import com.jagr.fridamusic.api.NeuroengineResponse
import com.jagr.fridamusic.db.daos.EchoBrainDao
import com.jagr.fridamusic.db.entities.BrainActivityLogEntity
import com.jagr.fridamusic.db.entities.PlayEventEntity
import com.jagr.fridamusic.db.entities.TasteProfileEntity
import kotlinx.coroutines.flow.Flow
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EchoBrainRepository @Inject constructor(
    private val echoBrainDao: EchoBrainDao
) {
    private val api: FlowNeuroengineApi by lazy {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer ${BuildConfig.FLOW_NEURO_API_KEY}")
                .build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.FLOW_NEURO_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FlowNeuroengineApi::class.java)
    }

    suspend fun analyzeEvent(trackId: String, durationMs: Long, skipped: Boolean): NeuroengineResponse? {
        return try {
            val response = api.analyzeEvent(
                NeuroengineRequest(trackId, durationMs, skipped)
            )
            response.tasteProfileUpdate?.let {
                echoBrainDao.insertOrUpdateTasteProfile(
                    TasteProfileEntity(
                        id = 1,
                        genres = it.genres,
                        confidence = it.confidence,
                        patternsFound = it.patternsFound,
                        modelVersion = it.modelVersion,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            response
        } catch (e: Exception) {
            null
        }
    }

    suspend fun logActivity(action: String, reason: String) {
        echoBrainDao.insertActivityLog(
            BrainActivityLogEntity(action = action, reason = reason, timestamp = System.currentTimeMillis())
        )
    }

    suspend fun logPlayEvent(trackId: String, startTime: Long, durationMs: Long, skipped: Boolean, engaged: Boolean) {
        echoBrainDao.insertPlayEvent(
            PlayEventEntity(
                trackId = trackId,
                startTime = startTime,
                durationMs = durationMs,
                skipped = skipped,
                engaged = engaged
            )
        )
    }

    fun getRecentActivityLogs(limit: Int): Flow<List<BrainActivityLogEntity>> =
        echoBrainDao.getRecentActivityLogs(limit)

    fun getTasteProfile(): Flow<TasteProfileEntity?> =
        echoBrainDao.getTasteProfile()

    fun getPlayEventCount(): Flow<Int> = echoBrainDao.getPlayEventCount()
    
    fun getTotalListeningTime(): Flow<Long?> = echoBrainDao.getTotalListeningTime()
}
