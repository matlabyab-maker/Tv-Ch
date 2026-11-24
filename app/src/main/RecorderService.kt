package com.tvb.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Basic HLS segment recorder. Supports clear MPEG-TS HLS playlists; DRM/AES encrypted streams are intentionally not bypassed. */
class RecorderService : Service() {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { running.set(false); worker?.interrupt(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        val stream = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        createChannel()
        startForeground(41, NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Tv Ch - Live Recording").setContentText("Saving the live stream").setOngoing(true).build())
        if (running.compareAndSet(false, true)) worker = Thread { record(stream) }.also { it.start() }
        return START_STICKY
    }

    private fun record(source: String) {
        try {
            val base = URL(source)
            val folder = getExternalFilesDir("Recordings") ?: File(filesDir, "Recordings").apply { mkdirs() }
            folder.mkdirs()
            val output = File(folder, "TvCh_${System.currentTimeMillis()}.ts")
            val seen = HashSet<String>()
            output.outputStream().buffered().use { out ->
                while (running.get()) {
                    val playlist = URL(source).openConnection().apply { connectTimeout = 10000; readTimeout = 15000 }.getInputStream().bufferedReader().use { it.readLines() }
                    if (playlist.any { it.startsWith("#EXT-X-KEY") && !it.contains("METHOD=NONE") }) throw IllegalStateException("این جریان رمزگذاری شده است و ضبط مستقیم آن پشتیبانی نمی‌شود")
                    val variant = playlist.firstOrNull { it.trim().endsWith(".m3u8") && !it.startsWith("#") }
                    if (variant != null) throw IllegalStateException("این نشانی فهرست چندکیفیتی است؛ نشانی فهرست کیفیت مستقیم را وارد کنید")
                    val segments = playlist.filter { it.isNotBlank() && !it.startsWith("#") }
                    var added = false
                    for (segment in segments) {
                        val resolved = URL(base, segment).toString()
                        if (seen.add(resolved)) {
                            val conn = URL(resolved).openConnection().apply { connectTimeout = 10000; readTimeout = 20000 }
                            conn.getInputStream().use { input -> input.copyTo(out) }
                            out.flush(); added = true
                        }
                    }
                    if (playlist.any { it.trim() == "#EXT-X-ENDLIST" }) break
                    if (!added) Thread.sleep(2500)
                }
            }
        } catch (_: InterruptedException) {
        } catch (e: Exception) {
            val n = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Tv Ch recording stopped").setContentText(e.message ?: "خطای ضبط").build()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(42, n)
        } finally {
            running.set(false); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "ضبط تلویزیون", NotificationManager.IMPORTANCE_LOW))
    }
    companion object {
        private const val CHANNEL = "tvch_recording"
        private const val EXTRA_URL = "url"
        private const val ACTION_STOP = "tvch.stop.recording"
        fun start(context: Context, url: String) { val i = Intent(context, RecorderService::class.java).putExtra(EXTRA_URL, url); if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i) }
        fun stop(context: Context) { context.startService(Intent(context, RecorderService::class.java).setAction(ACTION_STOP)) }
    }
}
