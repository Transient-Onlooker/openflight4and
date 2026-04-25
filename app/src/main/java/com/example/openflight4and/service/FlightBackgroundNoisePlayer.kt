package com.example.openflight4and.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.openflight4and.R
import com.example.openflight4and.model.FlightBackgroundSound
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FlightBackgroundNoisePlayer(
    context: Context
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracks = listOf(
        R.raw.flight_noise_1,
        R.raw.flight_noise_2,
        R.raw.flight_noise_3,
        R.raw.flight_noise_4,
        R.raw.flight_noise_5
    )
    private var currentPlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var lastTrack: Int? = null
    private var selectedSound: String = FlightBackgroundSound.AIRPLANE_WHITE_NOISE
    private var customSoundUri: String? = null
    private var isActive = false
    private var isPaused = false
    private var crossfadeStarted = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateCrossfade()
            if (isActive && !isPaused) {
                mainHandler.postDelayed(this, PROGRESS_TICK_MS)
            }
        }
    }

    fun setSelectedSound(sound: String) {
        if (selectedSound == sound) {
            return
        }
        selectedSound = sound
        if (isActive && !isPaused) {
            startFreshTrack()
        }
    }

    fun setCustomSoundUri(uri: String?) {
        if (customSoundUri == uri) {
            return
        }
        customSoundUri = uri
        if (isActive && !isPaused) {
            startFreshTrack()
        }
    }

    fun start() {
        if (isActive && !isPaused && currentPlayer != null) {
            return
        }
        isActive = true
        isPaused = false
        startFreshTrack()
    }

    fun pause() {
        isPaused = true
        mainHandler.removeCallbacks(progressRunnable)
        currentPlayer?.takeIf { it.isPlaying }?.pause()
        nextPlayer?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        if (!isActive) {
            start()
            return
        }
        isPaused = false
        val player = currentPlayer
        if (player == null) {
            startFreshTrack()
            return
        }
        runCatching {
            if (!player.isPlaying) {
                player.start()
            }
            nextPlayer?.takeIf { !it.isPlaying }?.start()
        }.onFailure { error ->
            Log.w(TAG, "Failed to resume flight background noise", error)
        }
        scheduleProgressUpdates()
    }

    fun stop() {
        isActive = false
        isPaused = false
        mainHandler.removeCallbacks(progressRunnable)
        releasePlayer(currentPlayer)
        releasePlayer(nextPlayer)
        currentPlayer = null
        nextPlayer = null
        crossfadeStarted = false
    }

    private fun startFreshTrack() {
        mainHandler.removeCallbacks(progressRunnable)
        releasePlayer(currentPlayer)
        releasePlayer(nextPlayer)
        currentPlayer = null
        nextPlayer = null
        crossfadeStarted = false

        if (!canPlaySelectedSound()) {
            return
        }

        currentPlayer = createSelectedPlayer(volume = 1f)?.also { player ->
            runCatching { player.start() }
                .onFailure { error -> Log.w(TAG, "Failed to start flight background noise", error) }
        }
        scheduleProgressUpdates()
    }

    private fun updateCrossfade() {
        val current = currentPlayer ?: return
        if (!canPlaySelectedSound()) {
            stop()
            return
        }
        if (selectedSound == FlightBackgroundSound.CUSTOM_MP3) {
            return
        }

        val duration = current.duration.takeIf { it > 0 } ?: return
        val remainingMs = duration - current.currentPosition
        if (!crossfadeStarted && remainingMs <= CROSSFADE_DURATION_MS) {
            startCrossfade()
        }

        val next = nextPlayer
        if (crossfadeStarted && next != null) {
            val progress = (CROSSFADE_DURATION_MS - remainingMs)
                .coerceIn(0, CROSSFADE_DURATION_MS)
                .toFloat() / CROSSFADE_DURATION_MS.toFloat()
            val smoothProgress = progress * progress * (3f - 2f * progress)
            current.setSafeVolume(cos(smoothProgress * PI.toFloat() / 2f))
            next.setSafeVolume(sin(smoothProgress * PI.toFloat() / 2f))
        }
    }

    private fun startCrossfade() {
        val current = currentPlayer ?: return
        if (selectedSound == FlightBackgroundSound.CUSTOM_MP3) {
            return
        }
        if (nextPlayer != null) {
            return
        }
        val nextTrack = pickNextTrack()
        lastTrack = nextTrack
        nextPlayer = createPreparedPlayer(nextTrack, volume = 0f)?.also { player ->
            player.setOnCompletionListener {
                promoteNextPlayer()
            }
            runCatching { player.start() }
                .onFailure { error -> Log.w(TAG, "Failed to start crossfade background noise", error) }
        }
        current.setOnCompletionListener {
            promoteNextPlayer()
        }
        crossfadeStarted = nextPlayer != null
    }

    private fun promoteNextPlayer() {
        val oldCurrent = currentPlayer
        val next = nextPlayer
        currentPlayer = next
        nextPlayer = null
        crossfadeStarted = false
        releasePlayer(oldCurrent)
        currentPlayer?.setSafeVolume(1f)
        if (isActive && !isPaused && currentPlayer != null) {
            scheduleProgressUpdates()
        } else if (isActive && !isPaused) {
            startFreshTrack()
        }
    }

    private fun createSelectedPlayer(volume: Float): MediaPlayer? {
        return when (selectedSound) {
            FlightBackgroundSound.CUSTOM_MP3 -> createPreparedCustomPlayer(volume)
            else -> {
                val track = pickNextTrack()
                lastTrack = track
                createPreparedRawPlayer(track, volume)
            }
        }
    }

    private fun createPreparedPlayer(track: Int, volume: Float): MediaPlayer? {
        return createPreparedRawPlayer(track, volume)
    }

    private fun createPreparedRawPlayer(track: Int, volume: Float): MediaPlayer? {
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                appContext.resources.openRawResourceFd(track).use { descriptor ->
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Flight background noise error: what=$what extra=$extra")
                    startFreshTrack()
                    true
                }
                prepare()
                setSafeVolume(volume)
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to prepare flight background noise", error)
            null
        }
    }

    private fun createPreparedCustomPlayer(volume: Float): MediaPlayer? {
        val uri = customSoundUri?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(appContext, Uri.parse(uri))
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Custom flight background sound error: what=$what extra=$extra")
                    startFreshTrack()
                    true
                }
                prepare()
                setSafeVolume(volume)
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to prepare custom flight background sound", error)
            null
        }
    }

    private fun canPlaySelectedSound(): Boolean {
        return isActive &&
            !isPaused &&
            when (selectedSound) {
                FlightBackgroundSound.AIRPLANE_WHITE_NOISE -> tracks.isNotEmpty()
                FlightBackgroundSound.CUSTOM_MP3 -> !customSoundUri.isNullOrBlank()
                else -> false
            }
    }

    private fun pickNextTrack(): Int {
        if (tracks.size == 1) {
            return tracks.first()
        }
        val candidates = tracks.filter { it != lastTrack }
        return candidates[Random.nextInt(candidates.size)]
    }

    private fun scheduleProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
        if (isActive && !isPaused) {
            mainHandler.postDelayed(progressRunnable, PROGRESS_TICK_MS)
        }
    }

    private fun releasePlayer(player: MediaPlayer?) {
        player?.runCatching {
            setOnCompletionListener(null)
            setOnErrorListener(null)
            stop()
            release()
        }
    }

    private fun MediaPlayer.setSafeVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        runCatching {
            setVolume(safeVolume, safeVolume)
        }
    }

    private companion object {
        const val TAG = "FlightNoisePlayer"
        const val CROSSFADE_DURATION_MS = 10_000
        const val PROGRESS_TICK_MS = 250L
    }
}
