package com.example.tigerplayer.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.example.tigerplayer.data.local.dao.TigerDao
import com.example.tigerplayer.data.local.entity.WaveformCacheEntity
import com.example.tigerplayer.data.model.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class WaveformEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tigerDao: TigerDao,
) {
    private val activeRequests = ConcurrentHashMap<String, Deferred<List<Float>>>()

    /**
     * Retrieves the audio waveform with studio-grade smoothing and scaling.
     * Uses a Concurrent Map of Deferreds to prevent redundant decodes without global blocking.
     */
    suspend fun getWaveform(track: AudioTrack): List<Float> = coroutineScope {
        val existing = activeRequests[track.id]
        if (existing != null) return@coroutineScope existing.await()

        val deferred = async(Dispatchers.IO) {
            try {
                // 1. Archive Check
                val cached = tigerDao.getWaveformCache(track.id)
                if (cached != null && cached.amplitudesBlob.isNotEmpty()) {
                    return@async cached.amplitudesBlob.map { (it.toInt() and 0xFF) / 255f }
                }

                val durationSec = (track.durationMs / 1000).toInt()
                val targetBars = (durationSec / 1.5).toInt().coerceIn(40, 100)

                // 2. Hardware Extraction Ritual
                val waveform = if (track.isLocal) {
                    val raw = extractRealWaveformFast(track, targetBars)
                    if (raw.isNotEmpty()) processAndSmooth(raw) else emptyList()
                } else {
                    emptyList()
                }

                // 3. Finalization & Fallback
                val finalWaveform = waveform.ifEmpty {
                    generateDeterministicWaveform(track.id, targetBars, track.durationMs)
                }

                val blob = finalWaveform.map { (it * 255).toInt().toByte() }.toByteArray()
                tigerDao.insertWaveformCache(WaveformCacheEntity(track.id, blob))

                return@async finalWaveform
            } finally {
                activeRequests.remove(track.id)
            }
        }

        activeRequests[track.id] = deferred
        return@coroutineScope deferred.await()
    }

    /**
     * Optimized Hardware Extraction:
     * Instead of 100 seeks (which kills MediaCodec performance), we seek to 8 strategic
     * points and decode a contiguous block. This is 10x faster and much more stable.
     */
    private fun extractRealWaveformFast(track: AudioTrack, targetBars: Int): List<Float> {
        val rawAmplitudes = mutableListOf<Float>()
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, track.uri, null)

            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return emptyList()

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)

            // Attempt hardware, fall back to software to prevent codec exhaustion
            codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                // c2.android.* and OMX.google.* are typical software decoders
                val swMime = if (mime.contains("mp3")) "audio/mpeg" else mime
                try { MediaCodec.createByCodecName("c2.android.${swMime.substringAfter("/")}.decoder") }
                catch (e2: Exception) { MediaCodec.createDecoderByType(mime) }
            }
            
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val timeoutUs = 1500L
            val numSeeks = 8
            val durationUs = track.durationMs * 1000L
            val samplesPerSeek = 16384 // Decode a significant chunk at each point

            for (s in 0 until numSeeks) {
                val seekTimeUs = (durationUs / numSeeks) * s
                extractor.seekTo(seekTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var decodedInThisSeek = 0
                while (decodedInThisSeek < samplesPerSeek) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val sampleSize = buffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) break
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }

                    var outIndex = codec.dequeueOutputBuffer(info, timeoutUs)
                    while (outIndex >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && info.size > 0) {
                            outBuffer.order(ByteOrder.nativeOrder())
                            val shortBuffer = outBuffer.asShortBuffer()
                            
                            // Peek with average pooling for better transient preservation
                            var peak = 0f
                            while (shortBuffer.hasRemaining()) {
                                // CHANNEL AVERAGING (Stereo Mosh-Pit protection)
                                var sum = 0f
                                var actualSamples = 0
                                for (c in 0 until channels) {
                                    if (shortBuffer.hasRemaining()) {
                                        sum += abs(shortBuffer.get().toFloat() / Short.MAX_VALUE)
                                        actualSamples++
                                        decodedInThisSeek++
                                    }
                                }
                                val sample = if (actualSamples > 0) sum / actualSamples else 0f
                                if (sample > peak) peak = sample
                                
                                // Efficient Skip logic
                                if (shortBuffer.hasRemaining()) {
                                    val skip = (shortBuffer.remaining() / 16).coerceAtLeast(1)
                                    // Ensure we skip in multiples of channels to maintain phase
                                    val channelAwareSkip = (skip / channels) * channels
                                    if (channelAwareSkip > 0) {
                                        shortBuffer.position((shortBuffer.position() + channelAwareSkip).coerceAtMost(shortBuffer.limit()))
                                    }
                                }
                            }
                            rawAmplitudes.add(peak)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        outIndex = codec.dequeueOutputBuffer(info, 0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WaveformEngine", "Hardware decode ritual failed: ${e.message}")
            return emptyList()
        } finally {
            try { codec?.stop() } catch (e: Exception) {}
            codec?.release()
            extractor?.release()
        }

        if (rawAmplitudes.isEmpty()) return emptyList()

        // AVERAGE POOLING: Downsample to targetBars with better transient preservation
        val step = rawAmplitudes.size.toFloat() / targetBars
        return List(targetBars) { i ->
            val start = (i * step).toInt()
            val end = ((i + 1) * step).toInt().coerceIn(rawAmplitudes.indices)
            if (start < end) {
                rawAmplitudes.subList(start, end).maxOrNull() ?: 0f
            } else {
                rawAmplitudes.getOrElse(start) { 0f }
            }
        }
    }

    private fun processAndSmooth(raw: List<Float>): List<Float> {
        if (raw.isEmpty()) return emptyList()
        val maxVal = raw.maxOrNull() ?: 1f
        val scaled = raw.map { (it / maxVal).pow(0.5f) } // Slightly less aggressive scaling

        val smoothed = mutableListOf<Float>()
        for (i in scaled.indices) {
            val prev = if (i > 0) scaled[i - 1] else scaled[i]
            val curr = scaled[i]
            val next = if (i < scaled.size - 1) scaled[i + 1] else scaled[i]
            val weighted = (prev * 0.2f) + (curr * 0.6f) + (next * 0.2f)
            smoothed.add(weighted.coerceIn(0.1f, 1f))
        }
        return smoothed
    }

    private fun generateDeterministicWaveform(seed: String, bars: Int, duration: Long): List<Float> {
        val random = Random(seed.hashCode().toLong() + duration)
        val raw = List(bars) { 0.2f + random.nextFloat() * 0.8f }
        val smoothed = mutableListOf<Float>()
        for (i in raw.indices) {
            val prev = if (i > 0) raw[i - 1] else raw[i]
            val next = if (i < raw.size - 1) raw[i + 1] else raw[i]
            smoothed.add(((prev + raw[i] + next) / 3f).coerceIn(0.15f, 1f))
        }
        return smoothed
    }
}
