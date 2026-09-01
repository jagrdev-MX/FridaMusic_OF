

package com.jagr.fridamusic.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.music.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream


object MusicRecognitionService {
    
    
    private const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    
    
    private const val RECORDING_DURATION_MS = 6500L
    private const val PRIMARY_SAMPLE_DURATION_MS = 4200L
    private const val RETRY_SAMPLE_DURATION_MS = 5200L
    
    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()
    private val recognitionMutex = Mutex()
    private val providers: List<MusicRecognitionProvider> = listOf(CurrentShazamProvider)
    
    fun hasRecordPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    
    @SuppressLint("MissingPermission")
    suspend fun recognize(context: Context): RecognitionStatus = recognitionMutex.withLock {
        withContext(Dispatchers.IO) {
        if (!hasRecordPermission(context)) {
            return@withContext RecognitionStatus.Error("Microphone permission not granted")
        }
        
        _recognitionStatus.value = RecognitionStatus.Listening
        
        try {
            
            val audioData = recordAudio()
            
            _recognitionStatus.value = RecognitionStatus.Processing
            
            
            val decodedAudio = DecodedAudio(
                data = audioData,
                channelCount = 1,
                sampleRate = RECORDING_SAMPLE_RATE,
                pcmEncoding = AUDIO_FORMAT
            )
            
            val primaryAudio = decodedAudio.sliceForRecognition(
                startMs = 0L,
                durationMs = PRIMARY_SAMPLE_DURATION_MS,
            )
            val primaryResult = recognizeWithProviders(primaryAudio)
            val providerResult = if (primaryResult is MusicRecognitionProviderResult.NoMatch) {
                decodedAudio.sliceForRecognition(
                    startMs = (RECORDING_DURATION_MS - RETRY_SAMPLE_DURATION_MS).coerceAtLeast(0L),
                    durationMs = RETRY_SAMPLE_DURATION_MS,
                ).let { recognizeWithProviders(it) }
            } else {
                primaryResult
            }

            _recognitionStatus.value = when (providerResult) {
                is MusicRecognitionProviderResult.Success -> RecognitionStatus.Success(providerResult.result)
                MusicRecognitionProviderResult.NoMatch ->
                    RecognitionStatus.NoMatch("No matches found. Try again with clearer audio.")
                is MusicRecognitionProviderResult.Error -> RecognitionStatus.Error(providerResult.message)
            }
            
            _recognitionStatus.value
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _recognitionStatus.value = RecognitionStatus.Error(e.message ?: "Recognition failed")
            _recognitionStatus.value
        }
        }
    }

    private suspend fun recognizeWithProviders(audio: DecodedAudio): MusicRecognitionProviderResult {
        var lastError: MusicRecognitionProviderResult.Error? = null
        var sawNoMatch = false
        providers.forEach { provider ->
            when (val result = provider.recognize(audio)) {
                is MusicRecognitionProviderResult.Success -> return result
                MusicRecognitionProviderResult.NoMatch -> sawNoMatch = true
                is MusicRecognitionProviderResult.Error -> {
                    lastError = result
                    if (!result.temporary) return result
                }
            }
        }
        return if (sawNoMatch) MusicRecognitionProviderResult.NoMatch
        else lastError ?: MusicRecognitionProviderResult.Error("Recognition unavailable", temporary = true)
    }

    private fun DecodedAudio.sliceForRecognition(startMs: Long, durationMs: Long): DecodedAudio {
        val bytesPerFrame = channelCount * 2
        val startFrame = (startMs * sampleRate / 1_000L).toInt()
        val requestedFrames = (durationMs * sampleRate / 1_000L).toInt()
        val startByte = (startFrame * bytesPerFrame).coerceIn(0, data.size)
        val endByte = (startByte + requestedFrames * bytesPerFrame).coerceAtMost(data.size)
        return copy(data = data.copyOfRange(startByte, endByte))
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun recordAudio(): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            RECORDING_SAMPLE_RATE, 
            CHANNEL_CONFIG, 
            AUDIO_FORMAT
        )
        require(bufferSize > 0) { "Microphone configuration is not supported" }
        
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDING_SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )
        
        require(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            "Microphone could not be initialized"
        }

        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        val startTime = System.currentTimeMillis()
        var started = false
        
        try {
            audioRecord.startRecording()
            started = audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING
            require(started) { "Microphone did not start recording" }
            
            while (System.currentTimeMillis() - startTime < RECORDING_DURATION_MS && isActive) {
                val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        } finally {
            if (started) runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
        }

        outputStream.toByteArray().also {
            require(it.isNotEmpty()) { "No microphone audio was captured" }
        }
    }
    
    fun reset() {
        _recognitionStatus.value = RecognitionStatus.Ready
    }
}
