package com.example.media

import android.media.MediaPlayer
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

class AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentPlayingPath = MutableStateFlow<String?>(null)
    val currentPlayingPath: StateFlow<String?> = _currentPlayingPath.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun playOrPause(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Log.e("AudioPlayer", "File does not exist: $path")
            return
        }

        if (_currentPlayingPath.value == path) {
            // Already initialized with this file
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _isPlaying.value = false
                progressJob?.cancel()
            } else {
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressTracker()
            }
        } else {
            // Switch or new play
            stop()
            try {
                val player = MediaPlayer()
                player.setDataSource(path)
                player.prepare()
                val duration = player.duration.toLong().coerceAtLeast(0L)
                _durationMs.value = duration
                _currentPlayingPath.value = path
                _currentPositionMs.value = 0L
                _progress.value = 0f

                player.setOnCompletionListener {
                    _isPlaying.value = false
                    _progress.value = 0f
                    _currentPositionMs.value = 0L
                    progressJob?.cancel()
                }

                player.start()
                mediaPlayer = player
                _isPlaying.value = true
                startProgressTracker()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error initializing player for $path", e)
                stop()
            }
        }
    }

    fun seekTo(progressFraction: Float) {
        mediaPlayer?.let { player ->
            val target = (player.duration * progressFraction.coerceIn(0f, 1f)).toInt()
            player.seekTo(target)
            _currentPositionMs.value = target.toLong()
            _progress.value = progressFraction
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            val pos = player.currentPosition.toLong()
                            val dur = player.duration.toLong().coerceAtLeast(1L)
                            _currentPositionMs.value = pos
                            _progress.value = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        }
                    } catch (_: Exception) {}
                }
                delay(100)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingPath.value = null
        _currentPositionMs.value = 0L
        _progress.value = 0f
    }

    fun release() {
        stop()
    }
}
