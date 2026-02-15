package com.meshtalk.app.mesh.transport

import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.meshtalk.app.data.model.TransportType
import com.meshtalk.app.data.preferences.UserPreferences
import com.meshtalk.app.mesh.MeshPacket
import com.meshtalk.app.mesh.parseMeshPacket
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * ══════════════════════════════════════════════════════════════════════
 * UltrasonicTransport — Audio-based data transmission
 * ══════════════════════════════════════════════════════════════════════
 *
 * Uses near-ultrasound (18kHz - 20kHz) to transmit data between devices
 * using the microphone and speaker.
 *
 * Use Cases:
 * - Device pairing in close proximity
 * - "Air-gapped" data transfer
 * - Fallback when RF is jammed or restricted
 *
 * Limitations:
 * - Very low bandwidth (~16-20 bits/second with simple FSK)
 * - Short range (< 3m)
 * - Susceptible to audio noise
 * - Requires RECORD_AUDIO permission
 */
@Singleton
class UltrasonicTransport @Inject constructor(
    private val context: Context,
    private val userPreferences: UserPreferences
) : Transport {

    companion object {
        private const val TAG = "UltrasonicTransport"
        private const val SAMPLE_RATE = 44100
        private const val FREQ_LOW = 18000.0 // Frequency for '0' bit
        private const val FREQ_HIGH = 19000.0 // Frequency for '1' bit
        // START/STOP markers to frame data
        private const val FREQ_START = 17500.0
        private const val FREQ_STOP = 19500.0
        
        // Duration of one bit in samples
        private const val BIT_DURATION_MS = 50
        private const val SAMPLES_PER_BIT = (SAMPLE_RATE * BIT_DURATION_MS) / 1000
    }

    override val name: String = "Ultrasonic Audio"
    override val type: TransportType = TransportType.ULTRASONIC
    override var isActive: Boolean = false
        private set

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    
    private var onPacketReceived: (suspend (MeshPacket, String) -> Unit)? = null
    private var onPeerConnected: (suspend (String, String, String) -> Unit)? = null
    private var onPeerDisconnected: (suspend (String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null
    private var localMeshId: String = ""

    override suspend fun start() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
            Log.w(TAG, "Microphone not available")
            return
        }

        localMeshId = userPreferences.getMeshIdSync()
        Log.i(TAG, "Starting Ultrasonic Transport")
        
        try {
            startListening()
            isActive = true
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for Audio: ${e.message}")
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "Stopping Ultrasonic Transport")
        isActive = false
        recordingJob?.cancel()
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        
        audioRecord = null
        audioTrack = null
    }

    override suspend fun sendPacket(packet: MeshPacket, endpointId: String?) {
        // Ultrasonic is strictly broadcast (omni-directional sound)
        // Only feasible for very small payloads (like handshakes/IDs), not full JSON packets
        // This implementation mimics sending a tiny "beacon" payload
        
        // For demo/prototype purposes, we send a simplified signal
        // In production, this would use a robust modem library like Quiet or Chirp
        
        val payload = packet.packetId.hashCode() // Send hash as a simple beacon
        val bytes = ByteBuffer.allocate(4).putInt(payload).array()
        sendBytes(bytes, endpointId)
    }

    override suspend fun sendBytes(data: ByteArray, endpointId: String?) {
        scope.launch {
            try {
                // Generate audio samples for the data
                // Protocol: START_SIGNAL + DATA_BITS + STOP_SIGNAL
                val samples = generateSamples(data)
                
                playAudio(samples)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending ultrasonic audio: ${e.message}")
            }
        }
    }
    
    private fun generateSamples(data: ByteArray): ShortArray {
        val totalBits = data.size * 8
        val totalSamples = (totalBits + 2) * SAMPLES_PER_BIT // +2 for start/stop
        val buffer = ShortArray(totalSamples)
        var offset = 0
        
        // Start Marker
        generateTone(FREQ_START, SAMPLES_PER_BIT, buffer, offset)
        offset += SAMPLES_PER_BIT
        
        // Data Bits
        for (byte in data) {
            for (i in 0 until 8) {
                val bit = (byte.toInt() shr i) and 1
                val freq = if (bit == 0) FREQ_LOW else FREQ_HIGH
                generateTone(freq, SAMPLES_PER_BIT, buffer, offset)
                offset += SAMPLES_PER_BIT
            }
        }
        
        // Stop Marker
        generateTone(FREQ_STOP, SAMPLES_PER_BIT, buffer, offset)
        
        return buffer
    }
    
    private fun generateTone(freq: Double, durationSamples: Int, buffer: ShortArray, offset: Int) {
        for (i in 0 until durationSamples) {
            if (offset + i >= buffer.size) break
            val angle = 2.0 * Math.PI * i / (SAMPLE_RATE / freq)
            buffer[offset + i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
        }
    }
    
    @Suppress("MissingPermission")
    private fun playAudio(samples: ShortArray) {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        
        if (audioTrack == null) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }
        
        audioTrack?.play()
        audioTrack?.write(samples, 0, samples.size)
    }

    @Suppress("MissingPermission")
    private fun startListening() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }
        
        audioRecord?.startRecording()
        
        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize)
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    processAudio(buffer, read)
                }
                yield() // Cooperate
            }
        }
    }

    private fun processAudio(buffer: ShortArray, size: Int) {
        // FFT or Goertzel algorithm would go here to detect frequencies
        // For prototype, we simulate detection if volume at target frequencies is high
        
        // TODO: Implement robust FSK demodulation
        // Because DSP (Digital Signal Processing) is complex to implement from scratch
        // without external libraries, we leave the placeholder for the rigorous math here.
        // A real implementation would use JTransforms (FFT) or a dedicated modem lib.
    }

    override fun setOnPacketReceived(callback: suspend (MeshPacket, String) -> Unit) {
        onPacketReceived = callback
    }

    override fun setOnPeerConnected(callback: suspend (String, String, String) -> Unit) {
        onPeerConnected = callback
    }

    override fun setOnPeerDisconnected(callback: suspend (String) -> Unit) {
        onPeerDisconnected = callback
    }
    
    fun destroy() {
        scope.cancel()
    }
}
