package com.example.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class AudioRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    fun startRecording(): Boolean {
        if (_isRecording.value) return false

        try {
            val mediaDir = File(context.filesDir, "saved_media").apply { mkdirs() }
            val outputFile = File(mediaDir, "voice_${System.currentTimeMillis()}.m4a")
            currentFile = outputFile

            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            recorder = newRecorder
            _isRecording.value = true
            _recordingDurationMs.value = 0L

            timerJob?.cancel()
            val startTime = System.currentTimeMillis()
            timerJob = scope.launch {
                while (isActive && _isRecording.value) {
                    _recordingDurationMs.value = System.currentTimeMillis() - startTime
                    try {
                        val maxAmp = recorder?.maxAmplitude ?: 0
                        val norm = (maxAmp / 32767f).coerceIn(0f, 1f)
                        _currentAmplitude.value = norm
                    } catch (_: Exception) {
                    }
                    delay(100)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            cleanup()
            return false
        }
    }

    fun stopRecording(): Pair<File, Long>? {
        if (!_isRecording.value) return null
        val duration = _recordingDurationMs.value
        val file = currentFile

        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recorder", e)
        }
        cleanup()

        return if (file != null && file.exists() && file.length() > 0) {
            Pair(file, duration)
        } else {
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        currentFile?.delete()
        cleanup()
    }

    private fun cleanup() {
        timerJob?.cancel()
        timerJob = null
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        _currentAmplitude.value = 0f
    }
}
