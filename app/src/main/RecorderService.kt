package com.tvb.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Records non-DRM HLS streams into the shared Tv Ch Recorded/Recordings folder. */
class RecorderService : Service() {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running.set(false)
            worker?.interrupt()
            return START_NOT_STICKY
        }
        val stream = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        createChannel()
        startForeground(
            41,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle("Tv Ch - Live Recording")
                .setContentText("Saving the live stream")
                .setOngoing(true)
                .build()
        )
        if (running.compareAndSet(false, true)) {
            worker = Thread({ record(stream) }, "TvCh-HlsRecorder").apply { start() }
        }
        return START_NOT_STICKY
    }

    private fun record(source: String) {
        var target: PublicRecordingTarget? = null
        var bytesWritten = 0L
        try {
            var playlistUrl = URL(source)
            var playlist = loadPlaylist(playlistUrl)

            // اگر آدرس، Master Playlist باشد، بالاترین کیفیت اعلام‌شده انتخاب می‌شود.
            val variants = playlist.mapNotNull { line ->
                val value = line.trim()
                if (value.isNotEmpty() && !value.startsWith("#") &&
                    value.substringBefore('?').endsWith(".m3u8", true)
                ) URL(playlistUrl, value) else null
            }
            if (variants.isNotEmpty()) {
                playlistUrl = variants.last()
                playlist = loadPlaylist(playlistUrl)
            }

            if (playlist.any { it.startsWith("#EXT-X-KEY") && !it.contains("METHOD=NONE") }) {
                throw IllegalStateException("این جریان رمزگذاری شده است و ضبط مستقیم آن پشتیبانی نمی‌شود")
            }

            val fragmentedMp4 = playlist.any { it.startsWith("#EXT-X-MAP:") }
            val recordingTarget = PublicRecordingTarget.create(
                this,
                "TvCh_${System.currentTimeMillis()}.${if (fragmentedMp4) "mp4" else "ts"}",
                if (fragmentedMp4) "video/mp4" else "video/mp2t"
            )
            target = recordingTarget

            val output = if (recordingTarget.descriptor != null) {
                FileOutputStream(recordingTarget.descriptor.fileDescriptor)
            } else {
                FileOutputStream(recordingTarget.file!!)
            }

            output.buffered().use { out ->
                val seen = HashSet<String>()
                var initMapWritten = false
                while (running.get()) {
                    val current = loadPlaylist(playlistUrl)
                    val mapLine = current.firstOrNull { it.startsWith("#EXT-X-MAP:") }
                    if (!initMapWritten && mapLine != null) {
                        val mapUri = Regex("URI=\\\"([^\\\"]+)\\\"").find(mapLine)?.groupValues?.get(1)
                        if (mapUri != null) {
                            val initUrl = URL(playlistUrl, mapUri).toString()
                            URL(initUrl).openStream().use { input -> bytesWritten += input.copyTo(out) }
                            out.flush()
                        }
                        initMapWritten = true
                    }

                    var added = false
                    for (line in current) {
                        val item = line.trim()
                        if (item.isEmpty() || item.startsWith("#")) continue
                        val resolved = URL(playlistUrl, item).toString()
                        if (seen.add(resolved)) {
                            val connection = URL(resolved).openConnection().apply {
                                connectTimeout = 12000
                                readTimeout = 30000
                                setRequestProperty("User-Agent", "TvCh-Android/1.0")
                            }
                            connection.getInputStream().use { input ->
                                bytesWritten += input.copyTo(out)
                            }
                            out.flush()
                            added = true
                        }
                    }
                    if (current.any { it.trim() == "#EXT-X-ENDLIST" }) break
                    if (!added) Thread.sleep(1800)
                }
            }

            if (bytesWritten > 0L) recordingTarget.publish(this)
            else recordingTarget.delete(this)
        } catch (_: InterruptedException) {
            if (bytesWritten > 0L) runCatching { target?.publish(this) }
            else target?.delete(this)
        } catch (e: Exception) {
            target?.delete(this)
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Tv Ch recording stopped")
                .setContentText(e.message ?: "خطای ضبط")
                .build()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(42, n)
        } finally {
            try { target?.descriptor?.close() } catch (_: Exception) { }
            running.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun loadPlaylist(url: URL): List<String> {
        val connection = url.openConnection().apply {
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("User-Agent", "TvCh-Android/1.0")
        }
        return connection.getInputStream().bufferedReader().use { it.readLines() }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL, "ضبط تلویزیون", NotificationManager.IMPORTANCE_LOW)
                )
        }
    }

    companion object {
        private const val CHANNEL = "tvch_recording"
        private const val EXTRA_URL = "url"
        private const val ACTION_STOP = "tvch.stop.recording"
        fun start(context: Context, url: String) {
            val intent = Intent(context, RecorderService::class.java).putExtra(EXTRA_URL, url)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }
        fun stop(context: Context) {
            context.startService(Intent(context, RecorderService::class.java).setAction(ACTION_STOP))
        }
    }
}
