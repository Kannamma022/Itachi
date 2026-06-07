package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WaterDropSoundManager {
    private const val TAG = "WaterDropSoundManager"
    private const val SAMPLE_RATE = 44100

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Generates a single water droplet frequency sweep.
     * Sweeps frequency exponentially/quadratically with an organic decay envelope.
     */
    private fun generateDroplet(
        fStart: Double,
        fEnd: Double,
        durationMs: Int,
        volume: Double = 0.65
    ): ShortArray {
        val duration = durationMs.toDouble() / 1000.0
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(totalSamples)
        var phase = 0.0
        
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / totalSamples
            // Dynamic envelope: snappy attack followed by soft natural organic decay
            val envelope = if (t < 0.12) {
                t / 0.12
            } else {
                Math.exp(-5.5 * (t - 0.12))
            }
            
            // Ascending or descending frequency sweep curve (quadratic)
            val currentFreq = fStart + (fEnd - fStart) * Math.pow(t, 2.0)
            phase += 2.0 * Math.PI * currentFreq / SAMPLE_RATE
            
            val sampleVal = Math.sin(phase) * envelope * 32767.0 * volume
            buffer[i] = sampleVal.toInt().coerceIn(-32768, 32767).toShort()
        }
        return buffer
    }

    /**
     * Synthesizes and joins multiple PCM buffers with optional silence gaps.
     */
    private fun concatBuffers(vararg buffers: ShortArray, gapSamples: Int = 0): ShortArray {
        val totalLength = buffers.sumOf { it.size } + (buffers.size - 1) * gapSamples
        val result = ShortArray(totalLength)
        var position = 0
        for (i in buffers.indices) {
            System.arraycopy(buffers[i], 0, result, position, buffers[i].size)
            position += buffers[i].size
            if (i < buffers.size - 1) {
                position += gapSamples // Leaves zeroes (silence) as structural spacing
            }
        }
        return result
    }

    // Precompiled organic sound buffers
    private val joinSound: ShortArray by lazy {
        // Bloop-bloop double bubble/water-drops! 
        val drop1 = generateDroplet(310.0, 820.0, 110, volume = 0.55)
        val drop2 = generateDroplet(420.0, 1050.0, 100, volume = 0.65)
        concatBuffers(drop1, drop2, gapSamples = (SAMPLE_RATE * 0.045).toInt()) // 45ms gap between ploops
    }

    private val leaveSound: ShortArray by lazy {
        // Descending bubble burst plop sound
        generateDroplet(780.0, 190.0, 195, volume = 0.50)
    }

    private val muteSound: ShortArray by lazy {
        // Quick organic snap bubble plop (descending lock)
        generateDroplet(580.0, 160.0, 75, volume = 0.45)
    }

    private val unmuteSound: ShortArray by lazy {
        // Low-to-high clean bubble popping sound (ascending release)
        generateDroplet(220.0, 800.0, 80, volume = 0.45)
    }

    /**
     * Plays the specified sound buffer asynchronously in a safe background task.
     */
    private fun playPcmAsync(pcmData: ShortArray) {
        scope.launch {
            var track: AudioTrack? = null
            try {
                val bufferSizeInBytes = pcmData.size * 2
                
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                // Push all samples into static pipeline
                track.write(pcmData, 0, pcmData.size)
                
                // Immediately stream out with lowest audio framework latency
                track.play()
                
                // Coroutine-friendly delay keeping track alive until playing is finished
                val durationMs = (pcmData.size.toDouble() / SAMPLE_RATE * 1000).toLong()
                kotlinx.coroutines.delay(durationMs + 60)
            } catch (e: Exception) {
                Log.e(TAG, "Failed playing synthesized sound: ${e.message}", e)
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun playJoin() {
        Log.d(TAG, "Triggering join sound")
        playPcmAsync(joinSound)
    }

    fun playLeave() {
        Log.d(TAG, "Triggering leave sound")
        playPcmAsync(leaveSound)
    }

    fun playMute() {
        Log.d(TAG, "Triggering mute sound")
        playPcmAsync(muteSound)
    }

    fun playUnmute() {
        Log.d(TAG, "Triggering unmute sound")
        playPcmAsync(unmuteSound)
    }

    fun playAnimeAura(fStart: Double, fEnd: Double) {
        val pcm = generateDroplet(fStart, fEnd, 170, volume = 0.70)
        playPcmAsync(pcm)
    }
}
