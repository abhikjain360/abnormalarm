package com.abhikjain360.abnormalarm.ring

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import com.abhikjain360.abnormalarm.domain.model.RingSettings

/**
 * Owns the actual noise/buzz/flash while an alarm rings (DESIGN.md §6). All three use the
 * Android-15-correct APIs:
 *  - audio on the ALARM stream (USAGE_ALARM) so it bypasses ringer/DND,
 *  - VibratorManager (the VibratorManager-era API; `Vibrator` is deprecated),
 *  - torch via CameraManager.setTorchMode (needs NO camera permission), toggled on a timer.
 */
class Ringer(private val context: Context) {

    private var player: MediaPlayer? = null
    private val rampHandler = Handler(Looper.getMainLooper())
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val torchHandler = Handler(Looper.getMainLooper())
    private var torchCameraId: String? = null
    private var torchOn = false
    private var torching = false

    fun start(settings: RingSettings) {
        startSound(settings)
        if (settings.vibrate) startVibration()
        if (settings.flashlight) startTorch()
    }

    fun stop() {
        rampHandler.removeCallbacksAndMessages(null)
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            p.release()
        }
        player = null
        runCatching { vibrator.cancel() }
        stopTorch()
    }

    private fun startSound(settings: RingSettings) {
        val uri: Uri = settings.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(context, uri)
            isLooping = true
            if (settings.volumeRampSeconds > 0) setVolume(0f, 0f)
            setOnPreparedListener { mp ->
                mp.start()
                if (settings.volumeRampSeconds > 0) rampVolume(mp, settings.volumeRampSeconds)
            }
            prepareAsync()
        }
    }

    /** Linearly ramp player volume 0→1 over [seconds] (DESIGN.md §6 gradual-volume option). */
    private fun rampVolume(mp: MediaPlayer, seconds: Int) {
        val steps = (seconds * 4).coerceAtLeast(1) // 4 updates/sec
        val intervalMs = (seconds * 1000L) / steps
        var step = 0
        rampHandler.post(object : Runnable {
            override fun run() {
                step++
                val v = (step.toFloat() / steps).coerceIn(0f, 1f)
                runCatching { mp.setVolume(v, v) }
                if (step < steps && player === mp) rampHandler.postDelayed(this, intervalMs)
            }
        })
    }

    private fun startVibration() {
        // wait 0ms, buzz 600ms, pause 600ms — repeat from index 0 until cancelled.
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0))
    }

    private fun startTorch() {
        val cm = cameraManager ?: return
        val id = runCatching {
            cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull() ?: return
        torchCameraId = id
        torching = true
        torchHandler.post(object : Runnable {
            override fun run() {
                if (!torching) return
                torchOn = !torchOn
                runCatching { cm.setTorchMode(id, torchOn) } // skip silently if the camera is busy
                torchHandler.postDelayed(this, 500)
            }
        })
    }

    private fun stopTorch() {
        torching = false
        torchHandler.removeCallbacksAndMessages(null)
        val cm = cameraManager
        val id = torchCameraId
        if (cm != null && id != null) runCatching { cm.setTorchMode(id, false) }
        torchOn = false
    }
}
