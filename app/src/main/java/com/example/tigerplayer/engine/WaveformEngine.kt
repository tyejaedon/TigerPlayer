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
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class WaveformEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tigerDao: TigerDao
) {
    /**
     * Retrieves the true audio waveform.
     * Uses database caching, hardware sparse extraction for local files,
     * and deterministic pseudo-random fallback for remote/streaming files.
     */
    suspend fun getWaveform(track: AudioTrack): List<Float> = withContext(Dispatchers.IO) {

        // 1. Dynamic Bar Count: Scales with the track length.
        // Approx 1 bar every 1.5 seconds, capped to protect UI constraints
        val durationSec = (track.durationMs / 1000).toInt()
        val targetBars = (durationSec / 1.5).toInt().coerceIn(40, 120)

        // 2. Archive Check (Database Cache)
        val cached = tigerDao.getWaveformCache(track.id)
        if (cached != null && cached.amplitudes.isNotBlank()) {
            try {
                val amps = cached.amplitudes.split(",").map { it.toFloat() }
                if (amps.isNotEmpty()) return@withContext amps
            } catch (e: Exception) {
                Log.e("WaveformEngine", "Failed to parse cached waveform", e)
            }
        }

        // 3. Hardware Sparse Extraction (Local files only to save massive bandwidth)
        if (track.isLocal || track.uri.scheme == "file" || track.uri.scheme == "content") {
            val realWaveform = extractRealWaveformSparse(track, targetBars)

            if (realWaveform.isNotEmpty()) {
                tigerDao.insertWaveformCache(
                    WaveformCacheEntity(track.id, realWaveform.joinToString(","))
                )
                return@withContext realWaveform
            }
        }

        // 4. Remote/Streaming Fallback
        val fallback = generateDeterministicWaveform(track.id, targetBars, track.durationMs)
        tigerDao.insertWaveformCache(WaveformCacheEntity(track.id, fallback.joinToString(",")))
        return@withContext fallback
    }

    /**
     * 🔥 TRUE HARDWARE EXTRACTION
     * Uses MediaExtractor and MediaCodec to jump instantly through the track,
     * decoding micro-chunks to calculate precise RMS values in milliseconds.
     */
    private fun extractRealWaveformSparse(track: AudioTrack, targetBars: Int): List<Float> {
        val amplitudes = mutableListOf<Float>()
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, track.uri, null)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }

            if (audioTrackIndex == -1 || format == null) return emptyList()

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return emptyList()

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val durationUs = track.durationMs * 1000L
            val intervalUs = durationUs / targetBars

            val info = MediaCodec.BufferInfo()
            val TIMEOUT_US = 10000L

            // Jump through the file interval by interval
            for (i in 0 until targetBars) {
                val targetTimeUs = i * intervalUs
                extractor.seekTo(targetTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var decodedSamples = 0
                var maxRms = 0f
                var isEOS = false
                var decodeAttempts = 0

                // 🔥 FIX 2: Increased from 10 to 100. MediaCodec needs time to fill its pipeline
                // before it starts outputting data. 10 attempts was starving it.
                while (decodedSamples < 4096 && !isEOS && decodeAttempts < 100) {
                    decodeAttempts++

                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1

                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    var outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    while (outIndex >= 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && info.size > 0) {
                            outBuffer.position(info.offset)
                            outBuffer.limit(info.offset + info.size)

                            // 🔥 FIX 1: Set ByteOrder to NATIVE_ORDER.
                            // Without this, Java reads Little Endian audio bytes backward as Big Endian,
                            // resulting in randomized static/garbage noise instead of audio peaks.
                            outBuffer.order(ByteOrder.nativeOrder())

                            val shortBuffer = outBuffer.asShortBuffer()
                            var localTotalRms = 0f
                            var localCount = 0

                            while (shortBuffer.hasRemaining()) {
                                val sample = shortBuffer.get().toFloat() / Short.MAX_VALUE
                                localTotalRms += sample * sample
                                localCount++
                                decodedSamples++
                            }

                            if (localCount > 0) {
                                val rms = sqrt(localTotalRms / localCount)
                                maxRms = max(maxRms, rms)
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                    }
                }

                amplitudes.add(maxRms)
                codec.flush() // Reset codec internal state for the next massive seek jump
            }

        } catch (e: Exception) {
            Log.e("WaveformEngine", "Sparse Extraction failed for ${track.title}", e)
            return emptyList()
        } finally {
            try { codec?.stop(); codec?.release() } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }

        // Normalize amplitudes to fit the UI scale (15% to 100%)
        val maxAmp = amplitudes.maxOrNull() ?: 1f
        return if (maxAmp > 0f) {
            amplitudes.map { (it / maxAmp).coerceIn(0.15f, 1f) }
        } else {
            amplitudes
        }
    }

    private fun generateDeterministicWaveform(seed: String, bars: Int, duration: Long): List<Float> {
        val random = Random(seed.hashCode().toLong() + duration)
        val amplitudes = mutableListOf<Float>()
        for (i in 0 until bars) {
            val value = 0.15f + random.nextFloat() * 0.85f
            amplitudes.add(value)
        }
        return amplitudes
    }
}