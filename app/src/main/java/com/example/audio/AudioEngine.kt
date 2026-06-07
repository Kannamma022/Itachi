package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioEngine(private val context: Context) {
    private val TAG = "AudioEngine"

    // Engine parameters
    private var sampleRate = 16000 // default highly recommended for voice (Opus)
    private var isLoopbackEnabled = false
    private var isMuted = false
    private var isEcoCanvasMode = false
    private var isGameBoosterEnabled = false
    private var isNoiseSuppressionEnabled = true
    private var currentNoiseProfile = "STANDARD" // "OFF", "STANDARD", "FAN", "AC", "WASHING", "MIXER"

    // State flows for UI update
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _activeSampleRate = MutableStateFlow(16000)
    val activeSampleRate: StateFlow<Int> = _activeSampleRate

    private val _bufferUsageUs = MutableStateFlow(0L)
    val bufferUsageUs: StateFlow<Long> = _bufferUsageUs

    private val _nativeBufferInfo = MutableStateFlow("Uninitialized")
    val nativeBufferInfo: StateFlow<String> = _nativeBufferInfo

    private val _isNoiseSuppressionActive = MutableStateFlow(true)
    val isNoiseSuppressionActive: StateFlow<Boolean> = _isNoiseSuppressionActive

    private val _activeNoiseProfile = MutableStateFlow("STANDARD")
    val activeNoiseProfile: StateFlow<String> = _activeNoiseProfile

    private val _voiceActivationThreshold = MutableStateFlow(0.12f) // default sensitivity setting
    val voiceActivationThreshold: StateFlow<Float> = _voiceActivationThreshold

    private var currentVoiceEffect = "NONE" // "NONE", "NARUTO", "OBITO", "ITACHI"

    private val _activeVoiceEffect = MutableStateFlow("NONE")
    val activeVoiceEffect: StateFlow<String> = _activeVoiceEffect

    fun setVoiceEffect(effect: String) {
        if (this.currentVoiceEffect != effect) {
            this.currentVoiceEffect = effect
            _activeVoiceEffect.value = effect
            Log.d(TAG, "Voice effect changed to: $effect")
        }
    }

    // Voice modulation DSP state variables
    private var obitoDelayIndex = 0
    private val obitoDelayBuffer = ShortArray(120) { 0.toShort() }
    private var itachiDelayPtr = 0
    private var itachiDelayBuffer: FloatArray? = null

    /**
     * Updates the sensitivity threshold for voice activation gating (0.0f - 1.0f range).
     */
    fun setVoiceActivationThreshold(value: Float) {
        _voiceActivationThreshold.value = value.coerceIn(0f, 1f)
        Log.d(TAG, "Voice activation threshold set to: $value")
    }

    private var isAgcEnabled = true
    private val _isAgcEnabledFlow = MutableStateFlow(true)
    val isAgcEnabledFlow: StateFlow<Boolean> = _isAgcEnabledFlow

    fun setAgcEnabled(enabled: Boolean) {
        this.isAgcEnabled = enabled
        _isAgcEnabledFlow.value = enabled
        Log.d(TAG, "AGC toggled: $enabled")
    }

    // Active scopes & threads
    private var audioJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default)
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null

    /**
     * Set the sample rate dynamically and restarts if recording.
     */
    fun configure(rate: Int, ecoEnabled: Boolean, boosterEnabled: Boolean = false) {
        if (this.sampleRate != rate || this.isEcoCanvasMode != ecoEnabled || this.isGameBoosterEnabled != boosterEnabled) {
            this.sampleRate = rate
            this.isEcoCanvasMode = ecoEnabled
            this.isGameBoosterEnabled = boosterEnabled
            _activeSampleRate.value = rate
            this.mediaStreamProcessor = MediaStreamTrackProcessor(rate)
            if (_isRecording.value) {
                stop()
                start()
            }
        }
    }

    /**
     * Toggles whether audio is routed back to Speaker/Earpiece in real time.
     */
    fun setLoopback(enabled: Boolean) {
        this.isLoopbackEnabled = enabled
        Log.d(TAG, "Audio loopback toggled: $enabled")
    }

    /**
     * Mute the microphone recording stream.
     */
    fun setMute(muted: Boolean) {
        this.isMuted = muted
        Log.d(TAG, "Mute toggled: $muted")
    }

    /**
     * Toggles the low-overhead gaming optimizer mode.
     */
    fun setGameBooster(enabled: Boolean) {
        if (this.isGameBoosterEnabled != enabled) {
            this.isGameBoosterEnabled = enabled
            Log.d(TAG, "Game Booster Mode toggled: $enabled")
            if (_isRecording.value) {
                stop()
                start()
            }
        }
    }

    /**
     * Toggles the noise suppression feature.
     */
    fun setNoiseSuppression(enabled: Boolean) {
        if (this.isNoiseSuppressionEnabled != enabled) {
            this.isNoiseSuppressionEnabled = enabled
            _isNoiseSuppressionActive.value = enabled
            Log.d(TAG, "Noise Suppression toggled: $enabled")
            
            // Apply dynamically if hardware suppressor is initialized
            try {
                noiseSuppressor?.enabled = enabled
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dynamically toggle hardware NoiseSuppressor: ${e.message}")
            }
        }
    }

    /**
     * Toggles the dynamic appliance noise cancellation profile.
     */
    fun setNoiseProfile(profile: String) {
        if (this.currentNoiseProfile != profile) {
            this.currentNoiseProfile = profile
            _activeNoiseProfile.value = profile
            Log.d(TAG, "Noise profile changed to: $profile")
        }
    }

    // DSP filtering states reusable across blocks
    private val svFilter = SVFilter()
    private var mediaStreamProcessor = MediaStreamTrackProcessor(sampleRate)
    private var noiseFloorEstimate = 400.0
    private var continuousHumCounter = 0

    // Auto-Gain Control (AGC) state variables
    private var agcGain = 1.0f
    private var rmsEnvelope = 1000f
    private val targetRms = 4500f // Target average amplitude level in 16-bit PCM space
    private val maxAgcGain = 4.5f
    private val minAgcGain = 0.35f

    /**
     * Real-time Automatic Gain Control (AGC).
     * Normalizes quiet and loud talking levels to a consistent reference target level (targetRms),
     * using asymmetric time constants for quick attack and slower natural release.
     */
    private fun processAGC(buffer: ShortArray, size: Int) {
        if (!isAgcEnabled || size <= 0) return

        // 1. Compute current block's energy (RMS)
        var sumSq = 0.0
        for (i in 0 until size) {
            val s = buffer[i].toDouble()
            sumSq += s * s
        }
        val blockRms = sqrt(sumSq / size).toFloat()

        // 2. Ignore whispering below voice gate activation standard to prevent hushing/hissing surge
        if (blockRms < 150f) {
            // Decay back to neutral gain slowly
            agcGain = agcGain * 0.98f + 1.0f * 0.02f
            return
        }

        // 3. Track the RMS envelope of the speech spectrum with asymmetric integration:
        // Fast attack (limit loud peaks/yells), slow recovery release (preserve natural syllables)
        val attackAlpha = 0.20f
        val releaseAlpha = 0.03f
        val alpha = if (blockRms > rmsEnvelope) attackAlpha else releaseAlpha
        rmsEnvelope = rmsEnvelope * (1f - alpha) + blockRms * alpha

        // 4. Determine targeted gain to drive signal to reference level
        val targetGain = (targetRms / rmsEnvelope).coerceIn(minAgcGain, maxAgcGain)

        // Smoothly update gain value to eliminate audio packet click transients
        val smoothingFactor = 0.08f
        agcGain = agcGain * (1f - smoothingFactor) + targetGain * smoothingFactor

        // 5. Apply computed gain factor in-place with hard protection clip limiter
        for (i in 0 until size) {
            val sampleVal = buffer[i] * agcGain
            buffer[i] = sampleVal.coerceIn(-32768f, 32767f).toInt().toShort()
        }
    }

    /**
     * Real-time software Noise Suppression and Keyboard Click Filter.
     * Processes 16-bit PCM buffer (ShortArray) in-place.
     */
    private fun processNoiseSuppression(buffer: ShortArray, size: Int) {
        if (!isNoiseSuppressionEnabled || currentNoiseProfile == "OFF") return

        // 1. Calculate Block Energy (RMS) to track live noise envelopes
        var sumSq = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sumSq += sample * sample
        }
        val rms = sqrt(sumSq / size)

        // Slow running average tracker of background silent noise Floor
        if (rms < 900.0) {
            noiseFloorEstimate = noiseFloorEstimate * 0.95 + rms * 0.05
        }

        // Apply adjustable dynamic voice activation threshold gate globally
        val normalizedRms = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
        if (normalizedRms < _voiceActivationThreshold.value) {
            val gateReduction = 0.02f // 98% damp attenuation to block out background room noise completely
            for (i in 0 until size) {
                buffer[i] = (buffer[i] * gateReduction).toInt().toShort()
            }
            return // Gated out early
        }

        when (currentNoiseProfile) {
            "STANDARD" -> {
                // Gate threshold. Slightly adjusted depending on game booster mode state
                val gateThreshold = if (isGameBoosterEnabled) 380.0 else 480.0
                val reductionFactor = 0.04f // 96% noise volume reduction

                if (rms < gateThreshold) {
                    for (i in 0 until size) {
                        buffer[i] = (buffer[i] * reductionFactor).toInt().toShort()
                    }
                } else {
                    // Keyboard Peak Squelch Filter
                    var absoluteSum = 0.0
                    for (i in 0 until size) {
                        absoluteSum += Math.abs(buffer[i].toDouble())
                    }
                    val averageAbs = absoluteSum / size
                    val clickFactor = 5.0
                    for (i in 0 until size) {
                        val absoluteVal = Math.abs(buffer[i].toInt())
                        if (absoluteVal > averageAbs * clickFactor && absoluteVal > 1200) {
                            val sign = if (buffer[i] < 0) -1 else 1
                            buffer[i] = (averageAbs * clickFactor * sign).toInt().toShort()
                        }
                    }
                }
            }
            "FAN" -> {
                // Fan noise consists of low frequency heavy vibrations (50Hz hum up to 250Hz frequency).
                // 1. Apply active High-Pass Filter with steep 24dB/oct cutoff at 320Hz to eliminate mechanical hum
                svFilter.setupHighPass(320f, sampleRate.toFloat(), 0.707f)
                for (i in 0 until size) {
                    val filtered = svFilter.processHighPass(buffer[i].toFloat())
                    buffer[i] = filtered.coerceIn(-32768f, 32767f).toInt().toShort()
                }

                // 2. Track steady continuous signals (hums) and compress the absolute amplitude
                val closeness = Math.abs(rms - noiseFloorEstimate)
                if (closeness < 220.0 && rms < 1600.0) {
                    continuousHumCounter++
                    if (continuousHumCounter > 4) {
                        // Suppress quiet stationary fan drone completely
                        val dampFactor = 0.08f
                        for (i in 0 until size) {
                            buffer[i] = (buffer[i] * dampFactor).toInt().toShort()
                        }
                    }
                } else {
                    continuousHumCounter = 0
                }
            }
            "AC" -> {
                // Air Conditioners create low rumble combined with a continuous rushing wind hiss (broadband noise).
                // 1. Constrain frequency bandwidth strictly to human voice vocal formants (300Hz to 3300Hz)
                svFilter.setupBandPass(300f, 3300f, sampleRate.toFloat())
                for (i in 0 until size) {
                    val filtered = svFilter.processBandPass(buffer[i].toFloat())
                    buffer[i] = filtered.coerceIn(-32768f, 32767f).toInt().toShort()
                }

                // 2. Continuous Spectral Subtraction gate
                val threshold = noiseFloorEstimate * 2.3 + 300.0
                if (rms < threshold) {
                    // Entirely drown remaining wind breeze hiss during silence
                    val dampFactor = (rms / threshold).toFloat().coerceIn(0.01f, 0.4f)
                    for (i in 0 until size) {
                        buffer[i] = (buffer[i] * dampFactor).toInt().toShort()
                    }
                } else {
                    // Suppress air floor noise during speech segments by scaling dynamic range
                    val scaleFactor = 0.82f
                    for (i in 0 until size) {
                        buffer[i] = (buffer[i] * scaleFactor).toInt().toShort()
                    }
                }
            }
            "WASHING" -> {
                // Washing machines combine periodic high-energy thuds (40Hz-100Hz gravity splash) and high-frequency spin spray clicks.
                // 1. Restrict bandwidth tightly to major vocal structures (350Hz - 2400Hz)
                svFilter.setupBandPass(350f, 2400f, sampleRate.toFloat())
                for (i in 0 until size) {
                    val filtered = svFilter.processBandPass(buffer[i].toFloat())
                    buffer[i] = filtered.coerceIn(-32768f, 32767f).toInt().toShort()
                }

                // 2. Dynamic Transient Limiter for drum clatter and spinning sloshes
                var absoluteSum = 0.0
                for (i in 0 until size) {
                    absoluteSum += Math.abs(buffer[i].toDouble())
                }
                val avgAbs = absoluteSum / size
                val crestLimit = (avgAbs * 3.4).coerceAtLeast(800.0)

                for (i in 0 until size) {
                    val sampleVal = buffer[i].toInt()
                    if (Math.abs(sampleVal) > crestLimit) {
                        val sign = if (sampleVal < 0) -1 else 1
                        buffer[i] = (crestLimit * sign).toInt().toShort()
                    }
                }

                // 3. Noise expander for idle periods
                if (rms < 850.0) {
                    for (i in 0 until size) {
                        buffer[i] = (buffer[i] * 0.05f).toInt().toShort()
                    }
                }
            }
            "MIXER" -> {
                // Kitchen blender jars generate screeching mechanical grinds and whining motor gears (1kHz - 8kHz up to 90dB).
                // 1. Extreme bandpass heavily attenuating everything above 1200Hz to preserve raw vowels and filter ear-splitting screeches
                svFilter.setupBandPass(250f, 1200f, sampleRate.toFloat())
                for (i in 0 until size) {
                    val filtered = svFilter.processBandPass(buffer[i].toFloat())
                    buffer[i] = filtered.coerceIn(-32768f, 32767f).toInt().toShort()
                }

                // 2. High Frequency derivative Slew-Rate Limiter (Dynamic de-esser & motor grinder dissolved)
                var prevSample = 0
                val lowPassBlend = 0.35f
                for (i in 0 until size) {
                    val currentSample = buffer[i].toInt()
                    val smoothed = (currentSample * lowPassBlend + prevSample * (1f - lowPassBlend)).toInt().toShort()
                    buffer[i] = smoothed
                    prevSample = smoothed.toInt()
                }

                // 3. Aggressive Downward Squelch Gate
                val speechCheckThreshold = 2200.0
                if (rms < speechCheckThreshold) {
                    // Suppress screaming motors completely when not active talking
                    for (i in 0 until size) {
                        buffer[i] = (buffer[i] * 0.02f).toInt().toShort()
                    }
                } else {
                    // Compress voice signals to hold the volume clear over remaining background whine
                    for (i in 0 until size) {
                        buffer[i] = (buffer[i] * 0.45f).toInt().toShort()
                    }
                }
            }
        }
    }

    /**
     * Real-time character themed voice modulation effect.
     * Alters raw PCM 16-bit ShortArray in-place.
     */
    private fun processVoiceModulation(buffer: ShortArray, size: Int) {
        if (currentVoiceEffect == "NONE") return

        when (currentVoiceEffect) {
            "NARUTO" -> {
                // Naruto: High-energy resampled pitch shift UP + volume gain
                val temp = ShortArray(size) { 0.toShort() }
                val pitchFactor = 1.25f // pitch shift up
                var outIdx = 0
                var inIdx = 0f
                while (outIdx < size) {
                    val idx1 = inIdx.toInt()
                    val idx2 = (idx1 + 1).coerceAtMost(size - 1)
                    val frac = inIdx - idx1
                    val sample = if (idx1 < size) {
                        val s1 = buffer[idx1].toFloat()
                        val s2 = buffer[idx2].toFloat()
                        s1 + frac * (s2 - s1)
                    } else 0f
                    val excited = (sample * 1.3f).coerceIn(-32768f, 32767f)
                    temp[outIdx] = excited.toInt().toShort()
                    
                    outIdx++
                    inIdx += pitchFactor
                    if (inIdx >= size) {
                        inIdx = 0f
                    }
                }
                temp.copyInto(buffer, 0, 0, size)
            }
            "OBITO" -> {
                // Obito: Heavy pitch shift DOWN (deep menacing voice) + hollow digital echo resonance
                val temp = ShortArray(size) { 0.toShort() }
                val pitchFactor = 0.65f // deep voice pitch shift down
                var outIdx = 0
                var inIdx = 0f
                
                while (outIdx < size) {
                    val idx1 = inIdx.toInt()
                    val idx2 = (idx1 + 1).coerceAtMost(size - 1)
                    val frac = inIdx - idx1
                    var sample = if (idx1 < size) {
                        val s1 = buffer[idx1].toFloat()
                        val s2 = buffer[idx2].toFloat()
                        s1 + frac * (s2 - s1)
                    } else 0f
                    
                    sample = (sample * 1.5f).coerceIn(-32768f, 32767f)
                    temp[outIdx] = sample.toInt().toShort()
                    outIdx++
                    inIdx += pitchFactor
                }
                
                // Add metallic background reverb
                for (i in 0 until size) {
                    val original = temp[i].toInt()
                    val delayedSample = obitoDelayBuffer[obitoDelayIndex].toInt()
                    obitoDelayBuffer[obitoDelayIndex] = (original * 0.45f + delayedSample * 0.35f).toInt().toShort()
                    obitoDelayIndex = (obitoDelayIndex + 1) % obitoDelayBuffer.size
                    
                    buffer[i] = (original * 0.7f + delayedSample * 0.5f).coerceIn(-32768f, 32767f).toInt().toShort()
                }
            }
            "ITACHI" -> {
                // Itachi: Calm pitch shift DOWN + slow multi-tap echo (Tsukuyomi illusion)
                if (itachiDelayBuffer == null) {
                    itachiDelayBuffer = FloatArray((sampleRate * 0.18).toInt()) { 0f }
                }
                val delayBufferSize = itachiDelayBuffer!!.size
                val itachiDelay = itachiDelayBuffer!!
                
                val temp = ShortArray(size) { 0.toShort() }
                val pitchFactor = 0.88f // slight pitch reduction
                var outIdx = 0
                var inIdx = 0f
                while (outIdx < size) {
                    val idx1 = inIdx.toInt()
                    val idx2 = (idx1 + 1).coerceAtMost(size - 1)
                    val frac = inIdx - idx1
                    val sample = if (idx1 < size) {
                        val s1 = buffer[idx1].toFloat()
                        val s2 = buffer[idx2].toFloat()
                        s1 + frac * (s2 - s1)
                    } else 0f
                    
                    temp[outIdx] = sample.toInt().toShort()
                    outIdx++
                    inIdx += pitchFactor
                }
                
                for (i in 0 until size) {
                    val inputSample = temp[i].toFloat()
                    
                    val tap1 = itachiDelay[(itachiDelayPtr - 1000 + delayBufferSize) % delayBufferSize]
                    val tap2 = itachiDelay[(itachiDelayPtr - 2500 + delayBufferSize) % delayBufferSize]
                    val tap3 = itachiDelay[(itachiDelayPtr - delayBufferSize + 1) % delayBufferSize]
                    
                    val echoMix = tap1 * 0.3f + tap2 * 0.2f + tap3 * 0.15f
                    
                    itachiDelay[itachiDelayPtr] = inputSample + echoMix * 0.4f
                    itachiDelayPtr = (itachiDelayPtr + 1) % delayBufferSize
                    
                    val outVal = inputSample * 0.75f + echoMix * 0.55f
                    buffer[i] = outVal.coerceIn(-32768f, 32767f).toInt().toShort()
                }
            }
        }
    }

    /**
     * Chamberlin State-Variable-Filter (SVF) Cascade.
     * High-performance, fast, sample-rate independent dual-stage filter.
     */
    private class SVFilter {
        private var low1 = 0f
        private var band1 = 0f
        private var low2 = 0f
        private var band2 = 0f

        private var fHigh = 0.1f
        private var fLow = 0.1f
        private var q = 0.707f

        fun setupHighPass(cutoffHz: Float, sampleRate: Float, qFactor: Float = 0.707f) {
            val angle = Math.PI * cutoffHz.toDouble() / sampleRate.toDouble()
            fLow = (2.0 * Math.sin(angle)).toFloat().coerceIn(0f, 1f)
            q = (1.0 / qFactor.toDouble()).toFloat()
        }

        fun setupBandPass(lowCutoffHz: Float, highCutoffHz: Float, sampleRate: Float, qFactor: Float = 0.707f) {
            val angleLow = Math.PI * lowCutoffHz.toDouble() / sampleRate.toDouble()
            fLow = (2.0 * Math.sin(angleLow)).toFloat().coerceIn(0f, 1f)

            val angleHigh = Math.PI * highCutoffHz.toDouble() / sampleRate.toDouble()
            fHigh = (2.0 * Math.sin(angleHigh)).toFloat().coerceIn(0f, 1f)
            q = (1.0 / qFactor.toDouble()).toFloat()
        }

        fun configure(sampleRate: Int) {
            // Keep state helper
        }

        fun processHighPass(input: Float): Float {
            // Stage 1
            val h1 = input - low1 - q * band1
            band1 = fLow * h1 + band1
            low1 = fLow * band1 + low1
            
            // Stage 2
            val h2 = h1 - low2 - q * band2
            band2 = fLow * h2 + band2
            low2 = fLow * band2 + low2

            return h2
        }

        fun processBandPass(input: Float): Float {
            // Highpass stage
            val h1 = input - low1 - q * band1
            band1 = fLow * h1 + band1
            low1 = fLow * band1 + low1
            val hpSignal = h1

            // Lowpass stage
            val h2 = hpSignal - low2 - q * band2
            band2 = fHigh * h2 + band2
            low2 = fHigh * band2 + low2
            val lpSignal = low2

            return lpSignal
        }
    }

    /**
     * MediaStreamTrack processor inspired class to perform basic real-time dynamic spectral gating
     * and sub-audible mechanical filter clean up on raw microphone audio buffers.
     */
    private class MediaStreamTrackProcessor(private val sampleRate: Int) {
        private var noiseFloor = 350f
        private val alphaFast = 0.1f
        private val alphaSlow = 0.01f
        private var lastOutput = 0f
        private var lastInput = 0f
        private var gain = 1.0f

        fun process(buffer: ShortArray, size: Int): ShortArray {
            if (size <= 0) return buffer
            
            // 1. Calculate short-term average amplitude (RMS)
            var sumSq = 0f
            for (i in 0 until size) {
                val s = buffer[i].toFloat()
                sumSq += s * s
            }
            val rms = Math.sqrt((sumSq / size).toDouble()).toFloat()
            
            // 2. Track noise floor dynamically using asymmetrical fast/slow filter
            if (rms < noiseFloor) {
                noiseFloor = noiseFloor * (1f - alphaFast) + rms * alphaFast
            } else {
                noiseFloor = noiseFloor * (1f - alphaSlow) + rms * alphaSlow
            }
            
            // Prevent division by zero, establish minimum threshold
            val noiseFloorLocal = noiseFloor.coerceAtLeast(10f)
            
            // Calculate Signal-to-Noise Ratio (SNR) for the packet
            val snr = rms / noiseFloorLocal
            
            // 3. Apply soft-kneeling dynamic gain reduction based on SNR
            val targetGain = if (snr < 1.5f) {
                // Signal matches background noise floor -> apply heavy suppression gate
                0.08f
            } else if (snr < 3.0f) {
                // Transition zone -> linear interpolation of gain
                0.08f + (snr - 1.5f) * (0.92f / 1.5f)
            } else {
                // Clean speech -> clear pass
                1.0f
            }
            
            // Smooth gain transition to avoid click transients
            gain = gain * 0.7f + targetGain * 0.3f
            
            // 4. Smooth sample process with basic dynamic low-pass smoothing
            val hpfAlpha = 0.85f // high pass filter to remove sub-audible mechanical/rumble noise (e.g. dc offset)
            
            for (i in 0 until size) {
                val input = buffer[i].toFloat()
                
                // Sub-audible low frequency AC coupling HPF (cutoff ~ 80Hz)
                val hpfOutput = hpfAlpha * (lastOutput + input - lastInput)
                lastInput = input
                lastOutput = hpfOutput
                
                // Scaled processed audio
                val processed = hpfOutput * gain
                
                buffer[i] = processed.coerceIn(-32768f, 32767f).toInt().toShort()
            }
            
            return buffer
        }
    }

    /**
     * Starts the audio connection processing thread.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (audioJob != null) return

        _isRecording.value = true
        audioJob = engineScope.launch {
            try {
                // Elevate thread priority to urgent real-time audio.
                // Ensures Android schedules our packet loop instantly, even if heavy 3D games are executing!
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                Log.d(TAG, "Successfully promoted background audio thread to THREAD_PRIORITY_URGENT_AUDIO")
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting audio thread priority: ${e.message}")
            }

            var audioRecord: AudioRecord? = null
            var audioTrack: AudioTrack? = null

            // Determine optimal buffer size (the smaller the buffer, the lower the latency)
            val minInputBufSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val minOutputBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            _nativeBufferInfo.value = "InBuf: ${minInputBufSize}B, OutBuf: ${minOutputBufSize}B"

            // Adjust buffer size. Half buffers are used when Game Booster is enabled to achieve sub-5ms packet turnaround.
            val inputBufSize = if (isGameBoosterEnabled) minInputBufSize.coerceAtLeast(512) else minInputBufSize.coerceAtLeast(1024)
            val outputBufSize = if (isGameBoosterEnabled) minOutputBufSize.coerceAtLeast(512) else minOutputBufSize.coerceAtLeast(1024)

            try {
                // Check if microphone permission is granted first to avoid EX_SECURITY logging
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    throw SecurityException("Manifest.permission.RECORD_AUDIO permission is not granted.")
                }

                // Initialize raw hardware record listener
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    inputBufSize
                )

                // Initialize raw hardware speaker target (using lowest latency STREAM_VOICE_CALL)
                audioTrack = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    outputBufSize,
                    AudioTrack.MODE_STREAM
                )

                // Validate if audio hardware initialized properly
                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("Microphone hardware initialization failed. Entering Simulation Mode.")
                }

                // If supported and enabled, setup hardware-level suppression block
                if (isNoiseSuppressionEnabled && android.media.audiofx.NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(audioRecord.audioSessionId)
                        noiseSuppressor?.enabled = true
                        Log.d(TAG, "Hardware NoiseSuppressor initialized on session ${audioRecord.audioSessionId}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Hardware NoiseSuppressor creation failed: ${e.message}")
                    }
                }

                audioRecord.startRecording()
                audioTrack.play()

                val audioBuffer = ShortArray(inputBufSize / 2) // short is 16-bit
                Log.d(TAG, "Hardware audio recording started. Mode sampleRate: $sampleRate")

                while (isActive) {
                    val processStartTime = System.nanoTime()

                    // Real-time block polling
                    val readResult = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                    if (readResult > 0) {
                        // Apply Noise suppression node (Hardware + Software Gate / Click Killer)
                        processNoiseSuppression(audioBuffer, readResult)

                        // Clean microphone audio using basic MediaStreamTrack processor inspired filter before transmission or voice loopback
                        mediaStreamProcessor.process(audioBuffer, readResult)

                        // Apply dynamic Automatic Gain Control (AGC) for level normalization
                        processAGC(audioBuffer, readResult)

                        // Apply Voice modulation (Naruto, Obito, Itachi effect presets)
                        processVoiceModulation(audioBuffer, readResult)

                        // Calculate Real amplitude
                        var sum = 0.0
                        for (i in 0 until readResult) {
                            val value = audioBuffer[i].toDouble()
                            sum += value * value
                        }
                        val rms = sqrt(sum / readResult)
                        
                        // Scale and expose to UI Flow
                        val finalAmp = if (isMuted) 0f else (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        _amplitude.value = finalAmp

                        // Directly write to playback if Loopback is turned ON
                        if (isLoopbackEnabled && !isMuted) {
                            audioTrack.write(audioBuffer, 0, readResult)
                        }
                    }

                    val processEndTime = System.nanoTime()
                    _bufferUsageUs.value = (processEndTime - processStartTime) / 1000
                }

            } catch (e: Exception) {
                Log.e(TAG, "Audio Engine error: ${e.message}. Launching High-Fidelity Simulation Stream.", e)
                // Fallback: Simulation loop (useful for emulators without MIC input or missing permission)
                runSimulationLoop()
            } finally {
                // Safe closure of hardware components
                try {
                    noiseSuppressor?.release()
                    noiseSuppressor = null
                } catch (ex: Exception) { /* ignore */ }

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (ex: Exception) { /* ignore */ }

                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (ex: Exception) { /* ignore */ }
            }
        }
    }

    /**
     * High-fidelity, low-overhead simulation loop.
     * Prevents empty UI on test rigs/emulators.
     */
    private suspend fun CoroutineScope.runSimulationLoop() {
        var degree = 0f
        while (isActive) {
            val startTime = System.nanoTime()
            
            // Generate a natural-breathing voice amplitude simulation
            if (!isMuted) {
                degree += 0.08f
                // Add minor random noise to make visualizer live and interesting
                val noise = (Math.random() * 0.15).toFloat()
                val signal = (Math.sin(degree.toDouble()).toFloat() * 0.45f + 0.5f).coerceIn(0f, 1f)
                val rawValue = (signal * 0.7f + noise * 0.3f).coerceIn(0f, 1f)
                
                // If it falls below voice activation sensitivity threshold, gate it out completely
                if (rawValue < _voiceActivationThreshold.value) {
                    _amplitude.value = rawValue * 0.02f // gated out dampening
                } else {
                    _amplitude.value = rawValue
                }
            } else {
                _amplitude.value = 0f
            }

            // Simulate small thread process (very low CPU usage)
            kotlinx.coroutines.delay(20)
            val endTime = System.nanoTime()
            _bufferUsageUs.value = (endTime - startTime) / 1000 - 20000 // normalize delay
        }
    }

    /**
     * Stop the audio connection thread.
     */
    fun stop() {
        _isRecording.value = false
        audioJob?.cancel()
        audioJob = null
        _amplitude.value = 0f
        Log.d(TAG, "Audio recording and loopback stopped.")
    }
}
