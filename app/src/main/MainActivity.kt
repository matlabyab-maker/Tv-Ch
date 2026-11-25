package com.tvb.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.TrafficStats
import android.os.Process
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager
import android.Manifest
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var usageMeter: TextView
    private val usageHandler = Handler(Looper.getMainLooper())
    private var lastRxBytes = -1L
    private var totalRxBytes = 0L
    private var usageMonitorStarted = false
    private val usageTick = object : Runnable {
        override fun run() {
            updateNetworkUsage()
            if (usageMonitorStarted) usageHandler.postDelayed(this, 60_000L)
        }
    }
    private lateinit var webView: WebView
    private lateinit var playlistBox: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var recordButton: Button
    private lateinit var bookmarkButton: Button
    private lateinit var catalogPanel: CountryCatalogPanel
    private var recordingQuality = "Auto"
    private var activeMode = "direct"
    private var currentStreamUrl: String? = null
    private var isRecording = false
    private var isScreenRecording = false
    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenRecorderService::class.java)
                .putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, result.data)
                .putExtra(ScreenRecorderService.EXTRA_QUALITY, recordingQuality)
                .putExtra(ScreenRecorderService.EXTRA_AUDIO, checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            isRecording = true
            isScreenRecording = true
            recordButton.text = "STOP RECORDING"
            status.text = "Screen recording started. Audio uses microphone when permitted."
        } else {
            toast("Screen recording permission was not granted")
        }
    }
    private val ntvLiveUrl = "https://news.ntv.co.jp/live"

    private var pendingBackupText = ""
    private val openPlaylist = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadPlaylist(uri)
    }
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingBackupText) }
            toast("Tv Ch TXT backup saved")
        }.onFailure { toast("Backup error: ${it.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF101820.toInt())
        }
        val title = TextView(this).apply {
            text = "Tv Ch  •  گیرنده جامع تلویزیون اینترنتی"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 4, 0, 12)
        }
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("LIVE", "RADIO", "WEB TV", "CHANNELS").forEachIndexed { index, label ->
            val mode = listOf("direct", "radio", "web", "list")[index]
            modes.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { showMode(mode) }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        urlInput = EditText(this).apply {
            hint = "آدرس جریان، صفحه تلویزیون یا لینک IPTV"
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFB0BEC5.toInt())
            setSelectAllOnFocus(false)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "PLAY"
            isAllCaps = false
            setOnClickListener { resolveAndOpen() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply {
            text = "OPEN M3U"
            isAllCaps = false
            setOnClickListener { openPlaylist.launch(arrayOf("audio/x-mpegurl", "application/x-mpegURL", "text/plain", "*/*")) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val extraActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recordButton = Button(this).apply {
            text = "RECORD"
            isAllCaps = false
            setOnClickListener { toggleRecording() }
        }
        bookmarkButton = Button(this).apply {
            text = "BOOKMARKS"
            isAllCaps = false
            setOnClickListener { showBookmarks() }
        }
        extraActions.addView(recordButton, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(bookmarkButton, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply {
            text = "ADD BOOKMARK"
            isAllCaps = false
            setOnClickListener { addBookmark() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        extraActions.addView(Button(this).apply {
            text = "QUALITY: AUTO"
            isAllCaps = false
            setOnClickListener { chooseRecordingQuality(this) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val ntvActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ntvActions.addView(Button(this).apply {
            text = "NTV LIVE"
            isAllCaps = false
            setOnClickListener { openNtvLive() }
        }, LinearLayout.LayoutParams(-1, -2))
        playerView = PlayerView(this).apply {
            useController = true
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        webView = createWebView()
        playlistBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        catalogPanel = CountryCatalogPanel(this, { results, _ ->
            playlistBox.removeAllViews()
            val saved = getSharedPreferences("tvch_catalog", MODE_PRIVATE).getStringSet("entries", emptySet())?.toMutableSet() ?: mutableSetOf()
            results.forEach { channel ->
                if (channel.url.isNotBlank()) {
                    saved.add(listOf(channel.name, channel.url, channel.country, channel.type).joinToString("\t"))
                    playlistBox.addView(Button(this).apply {
                        text = "${channel.name}  •  ${channel.country}  •  ${channel.type}"
                        isAllCaps = false
                        setOnClickListener {
                            urlInput.setText(channel.url)
                            if (channel.url.contains(".m3u8", true) || channel.url.contains(".mpd", true) || channel.url.startsWith("rtsp")) {
                                showMode("direct"); playStream(channel.url)
                            } else { showMode("web"); webView.loadUrl(channel.url) }
                        }
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }
            getSharedPreferences("tvch_catalog", MODE_PRIVATE).edit().putStringSet("entries", saved).apply()
            showMode("list")
            status.text = "${results.size} catalog results loaded"
        }, { exportTextData() })
        usageMeter = TextView(this).apply {
            text = "مصرف اینترنت در هر دقیقه: در انتظار پخش"
            textSize = 11f
            setTextColor(0xFFB9D6E2.toInt())
            setPadding(8, 5, 8, 5)
            setBackgroundColor(0xFF1B2A33.toInt())
        }
        status = TextView(this).apply {
            text = "آماده دریافت شبکه"
            textSize = 14f
            setTextColor(0xFFFFC400.toInt())
            setPadding(4, 8, 4, 8)
        }
        page.addView(title)
        page.addView(modes)
        page.addView(urlInput, LinearLayout.LayoutParams(-1, -2))
        page.addView(actions)
        page.addView(extraActions)
        page.addView(ntvActions)
        page.addView(catalogPanel, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(content)
        page.addView(usageMeter)
        page.addView(status)
        setContentView(page)
        showMode("direct")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadsImagesAutomatically = true
        settings.allowContentAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                status.text = "Web page loaded. If playback does not start, tap the player button."
                // Try to start HTML5 players and common visible play controls. Cross-origin
                // iframe players may still require a user tap or their own playback permission.
                view?.evaluateJavascript("""(function(){
                    try {
                      document.querySelectorAll('video,audio').forEach(function(m){m.muted=true;var p=m.play();if(p&&p.catch)p.catch(function(){});});
                      var selectors=['button[aria-label*=Play]','button[title*=Play]','.vjs-big-play-button','.jw-icon-play','[data-testid*=play]'];
                      for(var s of selectors){var e=document.querySelector(s);if(e){e.click();break;}}
                    } catch(e) {}
                })();""", null)
            }
        }
        webChromeClient = WebChromeClient()
    }

    private fun showMode(mode: String) {
        activeMode = mode
        content.removeAllViews()
        when (mode) {
            "direct" -> {
                player?.let { exo ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                }
                content.addView(playerView, LinearLayout.LayoutParams(-1, -1))
                status.text = "Direct player: HLS, DASH, RTSP and compatible streams"
            }
            "radio" -> {
                applyRadioSavingProfile()
                val radioInfo = TextView(this).apply {
                    text = "RADIO • حالت کم‌مصرف\nفقط صدا | بدون دریافت تصویر\nبرای کاهش بیشتر مصرف اینترنت، آدرس جریان صوتی کم‌نرخ (ترجیحاً 32 تا 64 kbps) را وارد کنید."
                    textSize = 17f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(18, 24, 18, 24)
                }
                content.addView(radioInfo, LinearLayout.LayoutParams(-1, 0, 1f))
                content.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
                status.text = "Radio economy mode: audio only, video tracks disabled, audio capped at 48 kbps when available."
            }
            "web" -> {
                content.addView(webView, LinearLayout.LayoutParams(-1, -1))
                status.text = "Web TV: playback depends on the website's player and permissions."
            }
            else -> {
                val scroll = ScrollView(this)
                scroll.addView(playlistBox)
                content.addView(scroll, LinearLayout.LayoutParams(-1, -1))
                status.text = "Channels: open an M3U or M3U8 playlist."
            }
        }
    }

    private fun resolveAndOpen() {
        val raw = urlInput.text.toString().trim()
        if (raw.isBlank()) {
            toast("Enter a stream or web page URL first")
            return
        }
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        val scheme = uri?.scheme?.lowercase().orEmpty()
        if (scheme !in listOf("http", "https", "rtsp")) {
            toast("URL must start with http, https or rtsp")
            return
        }
        val path = uri?.lastPathSegment?.lowercase().orEmpty()
        val isWebPage = path.isEmpty() || (!path.contains(".") && !path.contains("m3u"))
        val isPlaylist = path.endsWith(".m3u") || path.endsWith(".m3u8") && raw.contains("get.php").not()
        if (activeMode == "web" || (isWebPage && scheme != "rtsp")) {
            showMode("web")
            webView.loadUrl(raw)
            status.text = "Opening web TV page..."
        } else if (isPlaylist && activeMode == "list") {
            webView.loadUrl(raw)
        } else {
            showMode("direct")
            playStream(raw)
        }
    }

    private fun playStream(url: String) {
        try {
            currentStreamUrl = url
            if (player == null) {
                player = ExoPlayer.Builder(this).build().also { exo ->
                    playerView.player = exo
                    exo.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            status.text = when (state) {
                                Player.STATE_BUFFERING -> "Buffering..."
                                Player.STATE_READY -> "Playing"
                                Player.STATE_ENDED -> "Playback ended"
                                else -> "Ready"
                            }
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            status.text = "Playback error: ${error.message ?: "Unsupported source or format"}"
                        }
                    })
                }
            }
            if (activeMode == "radio") applyRadioSavingProfile() else {
                player?.let { exo ->
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false).build()
                }
            }
            val item = MediaItem.Builder().setUri(url).apply {
                val lower = url.substringBefore('?').lowercase()
                when {
                    lower.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                    lower.endsWith(".mpd") -> setMimeType(MimeTypes.APPLICATION_MPD)
                    lower.endsWith(".ism") || lower.endsWith(".isml") -> setMimeType(MimeTypes.APPLICATION_SS)
                }
            }.build()
            player?.setMediaItem(item)
            player?.prepare()
            player?.play()
            startNetworkUsageMonitor()
            status.text = "Connecting to stream..."
        } catch (e: Exception) {
            status.text = "Error: ${e.message ?: "Unknown"}"
        }
    }

    // Android UID counters measure received bytes for this app (player + WebView + catalog).
    // The optimized figure is an explicitly-labelled 50% reduction scenario, not a second measurement.
    private fun startNetworkUsageMonitor() {
        usageMonitorStarted = true
        lastRxBytes = readUidRxBytes()
        totalRxBytes = 0L
        usageHandler.removeCallbacks(usageTick)
        usageMeter.text = "مصرف واقعی و بهینه: در حال اندازه‌گیری…"
        usageHandler.postDelayed(usageTick, 60_000L)
    }

    private fun readUidRxBytes(): Long = TrafficStats.getUidRxBytes(Process.myUid())

    private fun formatUsage(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(java.util.Locale.US, "%.2f MB (%.0f KB)", mb, kb)
        else String.format(java.util.Locale.US, "%.1f KB (%.3f MB)", kb, mb)
    }

    private fun updateNetworkUsage() {
        val current = readUidRxBytes()
        if (current == TrafficStats.UNSUPPORTED.toLong() || lastRxBytes < 0L) {
            usageMeter.text = "مصرف اینترنت: اندازه‌گیری در این دستگاه پشتیبانی نمی‌شود"
            lastRxBytes = current
            return
        }
        val delta = (current - lastRxBytes).coerceAtLeast(0L)
        lastRxBytes = current
        totalRxBytes += delta
        val optimizedMinute = delta / 2L
        val optimizedTotal = totalRxBytes / 2L
        usageMeter.text = buildString {
            append("واقعی در دقیقه: ${formatUsage(delta)}\n")
            append("بهینه تخمینی در دقیقه (۵۰٪ کمتر): ${formatUsage(optimizedMinute)}\n")
            append("مجموع واقعی: ${formatUsage(totalRxBytes)}\n")
            append("مجموع بهینه تخمینی: ${formatUsage(optimizedTotal)}")
        }
    }

    private fun stopNetworkUsageMonitor() {
        usageMonitorStarted = false
        usageHandler.removeCallbacks(usageTick)
    }

    private fun applyRadioSavingProfile() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .setMaxAudioBitrate(48_000)
            .setMaxAudioChannelCount(1)
            .build()
    }

    private fun loadPlaylist(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("فایل قابل خواندن نیست")
            playlistBox.removeAllViews()
            var pendingChannelName = "شبکه بدون نام"
            var count = 0
            for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    pendingChannelName = line.substringAfter(',', "شبکه بدون نام")
                        .trim()
                        .ifBlank { "شبکه بدون نام" }
                } else if (
                    line.isNotBlank() &&
                    !line.startsWith("#") &&
                    (line.startsWith("http://", ignoreCase = true) ||
                     line.startsWith("https://", ignoreCase = true) ||
                     line.startsWith("rtsp://", ignoreCase = true))
                ) {
                    val streamUrl = line
                    val channelName = pendingChannelName
                    val saved = getSharedPreferences("tvch_catalog", MODE_PRIVATE).getStringSet("entries", emptySet())?.toMutableSet() ?: mutableSetOf()
                    saved.add(listOf(channelName, streamUrl, "Unknown", "TV").joinToString("\t"))
                    getSharedPreferences("tvch_catalog", MODE_PRIVATE).edit().putStringSet("entries", saved).apply()
                    val channelButton = Button(this).apply {
                        this.text = channelName
                        isAllCaps = false
                        setOnClickListener {
                            urlInput.setText(streamUrl)
                            showMode("direct")
                            playStream(streamUrl)
                        }
                    }
                    playlistBox.addView(
                        channelButton,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                    count += 1
                    pendingChannelName = "شبکه بدون نام"
                }
            }
            showMode("list")
            status.text = "$count channels loaded."
            if (count == 0) toast("No supported channel URLs found in this file")
        } catch (e: Exception) {
            toast("Playlist error: ${e.message}")
        }
    }

    private fun openNtvLive() {
        showMode("web")
        urlInput.setText(ntvLiveUrl)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.loadUrl(ntvLiveUrl)
        status.text = "Opening NTV LIVE and trying to start its embedded player..."
    }

    private fun addBookmark() {
        val address = urlInput.text.toString().trim().ifBlank { currentStreamUrl.orEmpty() }
        if (address.isBlank()) {
            toast("Enter or play a channel URL first")
            return
        }
        val prefs = getSharedPreferences("tv_r_bookmarks", MODE_PRIVATE)
        val saved = prefs.getStringSet("items", emptySet())?.toMutableSet() ?: mutableSetOf()
        saved.add(address)
        prefs.edit().putStringSet("items", saved).apply()
        toast("Bookmark saved")
    }

    private fun showBookmarks() {
        val prefs = getSharedPreferences("tv_r_bookmarks", MODE_PRIVATE)
        val saved = prefs.getStringSet("items", emptySet())?.toList()?.sorted().orEmpty()
        if (saved.isEmpty()) {
            toast("No bookmarks saved")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("BOOKMARKS")
            .setItems(saved.toTypedArray()) { _, which ->
                val selected = saved[which]
                urlInput.setText(selected)
                val scheme = Uri.parse(selected).scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    val path = Uri.parse(selected).lastPathSegment.orEmpty().lowercase()
                    if (path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".ism")) {
                        showMode("direct"); playStream(selected)
                    } else {
                        showMode("web"); webView.loadUrl(selected)
                    }
                } else {
                    showMode("direct"); playStream(selected)
                }
            }
            .setNeutralButton("REMOVE ALL") { _, _ ->
                prefs.edit().remove("items").apply()
                toast("Bookmarks cleared")
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun chooseRecordingQuality(button: Button) {
        val qualities = arrayOf("Auto", "240p", "360p", "480p", "576p", "720p", "1080p")
        AlertDialog.Builder(this).setTitle("Recording quality")
            .setSingleChoiceItems(qualities, qualities.indexOf(recordingQuality).coerceAtLeast(0)) { dialog, which ->
                recordingQuality = qualities[which]
                button.text = "QUALITY: ${recordingQuality.uppercase()}"
                dialog.dismiss()
            }.setNegativeButton("CLOSE", null).show()
    }

    private fun exportTextData() {
        val bookmarks = getSharedPreferences("tv_r_bookmarks", MODE_PRIVATE).getStringSet("items", emptySet()).orEmpty()
        val catalog = getSharedPreferences("tvch_catalog", MODE_PRIVATE).getStringSet("entries", emptySet()).orEmpty()
        val body = buildString {
            appendLine("# Tv Ch text backup")
            appendLine("# BOOKMARKS")
            bookmarks.sorted().forEach { appendLine(it) }
            appendLine("# CATALOG: name<TAB>url<TAB>country<TAB>type")
            catalog.sorted().forEach { appendLine(it) }
        }
        pendingBackupText = body
        createBackup.launch("Tv Ch backup.txt")
    }

    private fun toggleRecording() {
        if (isRecording) {
            if (isScreenRecording) ScreenRecorderService.stop(this) else RecorderService.stop(this)
            isRecording = false
            isScreenRecording = false
            recordButton.text = "RECORD"
            status.text = "Recording stopped. Check the Recordings folder."
            return
        }
        if (activeMode == "web") {
            startWebsiteScreenRecording()
            return
        }
        if (activeMode != "direct") {
            toast("برای ضبط سایت، WEB TV و برای ضبط مستقیم، LIVE را انتخاب کنید")
            return
        }
        val stream = currentStreamUrl ?: urlInput.text.toString().trim()
        if (!stream.substringBefore('?').lowercase().endsWith(".m3u8")) {
            toast("ضبط مستقیم فقط برای جریان‌های HLS با پسوند m3u8 فعال است؛ برای سایت از WEB TV استفاده کنید")
            return
        }
        try {
            RecorderService.start(this, stream)
            isRecording = true
            recordButton.text = "STOP RECORDING"
            status.text = "Recording HLS stream..."
        } catch (e: Exception) {
            toast("Cannot start recording: ${e.message}")
        }
    }

    private fun startWebsiteScreenRecording() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val request = {
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("ضبط سایت")
                .setMessage("برای تلاش جهت ضبط صدا، اجازه میکروفون لازم است. اگر اجازه ندهید، تصویر بدون صدا ضبط می‌شود. صدای داخلی سایت در همه گوشی‌ها قابل دریافت نیست.")
                .setPositiveButton("اجازه می‌دهم") { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 702)
                }
                .setNegativeButton("فقط تصویر") { _, _ -> request() }
                .show()
        } else request()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 702) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onStop() {
        super.onStop()
        stopNetworkUsageMonitor()
        player?.pause()
    }

    override fun onDestroy() {
        stopNetworkUsageMonitor()
        playerView.player = null
        player?.release()
        player = null
        webView.destroy()
        super.onDestroy()
    }
}
