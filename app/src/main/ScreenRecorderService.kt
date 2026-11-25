package com.tvb.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/** Records the visible Android screen (including WebView playback) with the user's explicit MediaProjection consent. */
class ScreenRecorderService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var outputTarget: PublicRecordingTarget? = null
    private var recordingStarted = false
    private var projectionCallbackRegistered = false
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            projectionCallbackRegistered = false
            stopCapture(stopProjection = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startIntent = intent ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (startIntent.action == ACTION_STOP) {
            stopCapture()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val withMic = startIntent.getBooleanExtra(EXTRA_AUDIO, false)

        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Tv Ch • ضبط صفحه")
            .setContentText("در حال ضبط صفحه")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            var foregroundType =
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION

            if (Build.VERSION.SDK_INT >= 30 && withMic) {
                foregroundType = foregroundType or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            val resultCode = startIntent.getIntExtra(
                EXTRA_RESULT_CODE,
                Activity.RESULT_CANCELED
            )

            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= 33) {
                startIntent.getParcelableExtra(
                    EXTRA_RESULT_DATA,
                    Intent::class.java
                )
            } else {
                startIntent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode != Activity.RESULT_OK || data == null) {
                throw IllegalStateException("اجازه ضبط صفحه دریافت نشد")
            }

            val manager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            projectionCallbackRegistered = true
            startCapture(
                startIntent.getStringExtra(EXTRA_QUALITY) ?: "Auto",
                withMic
            )

        } catch (e: Exception) {
            stopCapture()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(
                    NOTIFICATION_ID + 1,
                    NotificationCompat.Builder(this, CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_notify_error)
                        .setContentTitle("Tv Ch • خطای ضبط")
                        .setContentText(e.message ?: "ضبط شروع نشد")
                        .build()
                )
        }

        return START_NOT_STICKY
    }

    private fun startCapture(quality: String, withMic: Boolean) {
        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)

        val width = (metrics.widthPixels / 2) * 2
        val height = (metrics.heightPixels / 2) * 2
        val dpi = metrics.densityDpi

        val target = PublicRecordingTarget.create(
            this,
            "TvCh_Web_${System.currentTimeMillis()}.mp4",
            "video/mp4"
        )
        outputTarget = target

        val targetHeight = quality.removeSuffix("p").toIntOrNull() ?: 720
        val scale = minOf(1f, targetHeight.toFloat() / height.toFloat())
        val outW = ((width * scale).toInt() / 2) * 2
        val outH = ((height * scale).toInt() / 2) * 2

        val recorderInstance = createRecorder()
        recorder = recorderInstance

        recorderInstance.apply {
            if (withMic) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }

            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (target.descriptor != null) {
                setOutputFile(target.descriptor.fileDescriptor)
            } else {
                setOutputFile(target.file!!.absolutePath)
            }

            setVideoSize(
                outW.coerceAtLeast(2),
                outH.coerceAtLeast(2)
            )

            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(4_000_000)
            setVideoFrameRate(30)

            if (withMic) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
            }

            prepare()
        }

        val surface = recorderInstance.surface

        virtualDisplay = projection?.createVirtualDisplay(
            "TvChScreenCapture",
            outW.coerceAtLeast(2),
            outH.coerceAtLeast(2),
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        ) ?: throw IllegalStateException("اتصال ضبط صفحه برقرار نشد")

        recorderInstance.start()
        recordingStarted = true
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

    private fun stopCapture(stopProjection: Boolean = true) {
        var finalized = false
        try {
            recorder?.stop()
            finalized = recordingStarted
        } catch (_: Exception) {
            finalized = false
        }

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {
        }

        if (projectionCallbackRegistered) {
            try { projection?.unregisterCallback(projectionCallback) } catch (_: Exception) { }
            projectionCallbackRegistered = false
        }
        if (stopProjection) {
            try { projection?.stop() } catch (_: Exception) { }
        }

        virtualDisplay = null
        recorder = null
        projection = null
        recordingStarted = false

        val target = outputTarget
        outputTarget = null
        try { target?.descriptor?.close() } catch (_: Exception) { }
        if (finalized) {
            try { target?.publish(this) } catch (_: Exception) { target?.delete(this) }
        } else {
            target?.delete(this)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "ضبط صفحه Tv Ch",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_QUALITY = "recording_quality"
        const val EXTRA_AUDIO = "record_audio"

        private const val CHANNEL = "tvch_screen_recording"
        private const val NOTIFICATION_ID = 71
        private const val ACTION_STOP = "tvch.stop.screen.recording"

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenRecorderService::class.java)
                    .setAction(ACTION_STOP)
            )
        }
    }
}
