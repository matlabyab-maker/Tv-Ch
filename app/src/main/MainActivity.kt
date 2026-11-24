package com.tvb.player

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var webView: WebView
    private lateinit var playlistBox: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var recordButton: Button
    private lateinit var bookmarkButton: Button
    private var activeMode = "direct"
    private var currentStreamUrl: String? = null
    private var isRecording = false
    private val ntvLiveUrl = "https://news.ntv.co.jp/live"

    private val openPlaylist = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadPlaylist(uri)
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
        listOf("LIVE", "WEB TV", "CHANNELS").forEachIndexed { index, label ->
            val mode = listOf("direct", "web", "list")[index]
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
        page.addView(content)
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
                content.addView(playerView, LinearLayout.LayoutParams(-1, -1))
                status.text = "Direct player: HLS, DASH, RTSP and compatible streams"
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
            status.text = "Connecting to stream..."
        } catch (e: Exception) {
            status.text = "Error: ${e.message ?: "Unknown"}"
        }
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

    private fun toggleRecording() {
        if (isRecording) {
            RecorderService.stop(this)
            isRecording = false
            recordButton.text = "RECORD"
            status.text = "Recording stopped."
            return
        }
        if (activeMode != "direct") {
            toast("Switch to LIVE mode and play a direct HLS stream before recording")
            return
        }
        val stream = currentStreamUrl ?: urlInput.text.toString().trim()
        if (!stream.substringBefore('?').lowercase().endsWith(".m3u8")) {
            toast("Recording currently supports clear HLS (.m3u8) streams only")
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

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        webView.destroy()
        super.onDestroy()
    }
}
