package com.jagr.fridamusic.recognition

import android.media.AudioFormat
import com.music.shazamkit.Shazam
import com.music.shazamkit.models.RecognitionResult
import kotlinx.coroutines.CancellationException
import java.nio.ByteOrder

interface MusicRecognitionProvider {
    val name: String

    suspend fun recognize(audio: DecodedAudio): MusicRecognitionProviderResult
}

sealed interface MusicRecognitionProviderResult {
    data class Success(val result: RecognitionResult) : MusicRecognitionProviderResult
    data object NoMatch : MusicRecognitionProviderResult
    data class Error(val message: String, val temporary: Boolean) : MusicRecognitionProviderResult
}

object CurrentShazamProvider : MusicRecognitionProvider {
    override val name = "Shazam"

    override suspend fun recognize(audio: DecodedAudio): MusicRecognitionProviderResult {
        val resampledAudio = AudioResampler.resample(audio, VibraSignature.REQUIRED_SAMPLE_RATE)
            .getOrElse { error ->
                return MusicRecognitionProviderResult.Error(
                    message = error.message ?: "Failed to resample audio",
                    temporary = false,
                )
            }
        require(
            resampledAudio.channelCount == 1 &&
                resampledAudio.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
                resampledAudio.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT &&
                ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN &&
                resampledAudio.data.isNotEmpty() &&
                resampledAudio.data.size % 2 == 0,
        ) { "Invalid audio format for fingerprint generation" }

        val signature = runCatching { VibraSignature.fromI16(resampledAudio.data) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                return MusicRecognitionProviderResult.Error(
                    message = error.message ?: "Failed to generate fingerprint",
                    temporary = false,
                )
            }
        val sampleDurationMs = (resampledAudio.data.size / 2) * 1_000L /
            VibraSignature.REQUIRED_SAMPLE_RATE

        return Shazam.recognize(signature, sampleDurationMs).fold(
            onSuccess = { MusicRecognitionProviderResult.Success(it) },
            onFailure = { error ->
                val message = error.message ?: "Recognition failed"
                when {
                    message.contains("No match", ignoreCase = true) || message.contains("404") ->
                        MusicRecognitionProviderResult.NoMatch
                    message.contains("429") ||
                        message.contains("temporarily", ignoreCase = true) ||
                        message.contains("timeout", ignoreCase = true) ||
                        message.contains("network", ignoreCase = true) ->
                        MusicRecognitionProviderResult.Error(message, temporary = true)
                    else -> MusicRecognitionProviderResult.Error(message, temporary = false)
                }
            },
        )
    }
}
