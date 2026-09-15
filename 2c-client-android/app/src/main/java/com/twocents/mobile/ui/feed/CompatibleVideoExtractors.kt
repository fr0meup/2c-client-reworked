package com.twocents.mobile.ui.feed

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.TrackOutput
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/** Profile 8 Dolby Vision has an HEVC-compatible base layer. Preserve its pixels/HLG
 * color information, but don't configure an HEVC decoder with a Dolby Vision profile. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class CompatibleVideoExtractors(private val context: android.content.Context) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = DefaultExtractorsFactory().createExtractors().map { extractor ->
        object : Extractor by extractor {
            override fun init(output: ExtractorOutput) {
                extractor.init(object : ExtractorOutput by output {
                    override fun track(id: Int, type: Int): TrackOutput {
                        val track = output.track(id, type)
                        return object : TrackOutput by track {
                            override fun format(format: Format) {
                                val baseLayer = format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION &&
                                    format.codecs?.let { it.startsWith("dvhe.08.") || it.startsWith("dvh1.08.") } == true &&
                                    runCatching { MediaCodecUtil.getDecoderInfos(MimeTypes.VIDEO_DOLBY_VISION, false, false)
                                        .none { it.isFormatSupported(context, format) } }.getOrDefault(false)
                                if (baseLayer) android.util.Log.i("2cVideo", "Using HEVC base layer for ${format.codecs}")
                                track.format(if (baseLayer) format.buildUpon()
                                    .setSampleMimeType(MimeTypes.VIDEO_H265).setCodecs(null).build() else format)
                            }
                        }
                    }
                })
            }
        }
    }.toTypedArray()
}
