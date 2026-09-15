package com.twocents.mobile.ui.feed

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecDecoderException
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.concurrent.ConcurrentHashMap

/** Media3's decoder fallback covers initialization, not a codec failing after it
 * starts. Retry a different installed decoder once per failed codec, per player.
 * No transcoding, duplicate background downloads, or changes to video geometry. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VideoDecoderRecovery {
    private val failed = ConcurrentHashMap.newKeySet<String>()
    val selector = MediaCodecSelector { mime, secure, tunneling ->
        MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).filterNot { it.name in failed }
    }

    fun canRetry(error: PlaybackException): Boolean {
        if (error.errorCode != PlaybackException.ERROR_CODE_DECODING_FAILED) return false
        val format = (error as? ExoPlaybackException)?.rendererFormat ?: return false
        val mime = format.sampleMimeType ?: return false
        if (!MimeTypes.isVideo(mime)) return false
        val codec = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<MediaCodecDecoderException>().firstOrNull()?.codecInfo ?: return false
        if (failed.size >= 3 || codec.name in failed) return false
        val alternatives = runCatching {
            MediaCodecSelector.DEFAULT.getDecoderInfos(mime, codec.secure, false)
        }.getOrDefault(emptyList())
        if (alternatives.none { it.name != codec.name && it.name !in failed }) return false
        failed.add(codec.name)
        android.util.Log.i("2cVideo", "Retrying with another decoder after ${codec.name} failed ($mime)")
        return true
    }
}
