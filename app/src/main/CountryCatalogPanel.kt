package com.tvb.player

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/** Online catalog based on public IPTV-org playlists and Radio-Browser directory. */
class CountryCatalogPanel(
    context: Context,
    private val onResults: (List<CatalogChannel>, String) -> Unit,
    private val onExport: () -> Unit
) : LinearLayout(context) {
    private val countries = listOf(
        "China" to "cn", "USA" to "us", "Iran" to "ir", "United Kingdom" to "gb",
        "France" to "fr", "Canada" to "ca", "Germany" to "de", "Japan" to "jp",
        "India" to "in", "Turkey" to "tr", "Russia" to "ru", "Italy" to "it",
        "Spain" to "es", "Brazil" to "br", "Australia" to "au", "South Korea" to "kr",
        "Netherlands" to "nl", "Ukraine" to "ua", "Switzerland" to "ch", "Sweden" to "se",
        "Norway" to "no", "Mexico" to "mx", "Egypt" to "eg", "South Africa" to "za"
    )
    private val country = Spinner(context)
    private val topic = Spinner(context)
    private val query = EditText(context)
    private val status = TextView(context)
    private val searchGeneration = AtomicInteger(0)
    private val searchExecutor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "TvCh-CatalogSearch").apply { isDaemon = true }
    }
    @Volatile private var currentSearch: Future<*>? = null
    @Volatile private var activeConnection: HttpURLConnection? = null

    init {
        orientation = VERTICAL
        setPadding(8, 6, 8, 6)
        setBackgroundColor(Color.rgb(18, 43, 82))
        fun label(text: String) = TextView(context).apply {
            this.text = text; textSize = 14f; setTextColor(Color.WHITE); setPadding(4, 4, 4, 2)
        }
        addView(label("COUNTRY CATALOG / جستجوی کشور و موضوع"))
        country.adapter = yellowSpinnerAdapter(countries.map { it.first })
        country.setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(18, 43, 82)))
        addView(country, LayoutParams(-1, -2))
        topic.adapter = yellowSpinnerAdapter(listOf("All channels", "NEWS", "SPORTS", "DOCUMENTARY"))
        topic.setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(18, 43, 82)))
        addView(topic, LayoutParams(-1, -2))
        query.hint = "Search TV or radio channel name"
        query.setSingleLine(true)
        addView(query, LayoutParams(-1, -2))
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        row.addView(Button(context).apply {
            text = "SEARCH COUNTRY"; isAllCaps = false
            setOnClickListener { searchCountry() }
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(context).apply {
            text = "SEARCH NAME"; isAllCaps = false
            setOnClickListener { searchName() }
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(context).apply {
            text = "EXPORT TXT"; isAllCaps = false
            setOnClickListener { onExport() }
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(row)
        status.text = "Public catalog search is ready"
        status.setTextColor(0xFFFFD54F.toInt())
        addView(status)
    }

    /** Yellow text for both the selected Spinner value and its expanded dropdown rows. */
    private fun yellowSpinnerAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View =
                styleSpinnerText(super.getView(position, convertView, parent))

            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View =
                styleSpinnerText(super.getDropDownView(position, convertView, parent), true)

            private fun styleSpinnerText(view: android.view.View, dropdown: Boolean = false): android.view.View {
                (view as? TextView)?.apply {
                    setTextColor(0xFFFFD54F.toInt())
                    textSize = 15f
                    setPadding(12, if (dropdown) 12 else 8, 12, if (dropdown) 12 else 8)
                    if (dropdown) setBackgroundColor(Color.rgb(18, 43, 82))
                }
                return view
            }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun searchCountry() {
        val selected = countries[country.selectedItemPosition]
        val topicName = when (topic.selectedItemPosition) {
            1 -> "news"; 2 -> "sports"; 3 -> "documentary"; else -> ""
        }
        runSearch("Searching ${selected.first} ${topicName.ifBlank { "TV" }}…") {
            val countryItems = parseM3u(get("https://iptv-org.github.io/iptv/countries/${selected.second}.m3u"), selected.first)
            val filtered = if (topicName.isBlank()) countryItems else {
                val topicItems = parseM3u(get("https://iptv-org.github.io/iptv/categories/$topicName.m3u"), selected.first)
                val urls = topicItems.map { it.url }.toHashSet()
                countryItems.filter { it.url in urls }
            }
            val radio = runCatching { fetchRadio(selected.first) }.getOrDefault(emptyList())
            if (topicName.isBlank()) (filtered + radio).distinctBy { it.url }.take(250)
            else (filtered.take(15) + radio.take(3)).distinctBy { it.url }
        }
    }

    private fun searchName() {
        val term = query.text.toString().trim()
        if (term.isBlank()) { Toast.makeText(context, "Enter a channel name", Toast.LENGTH_SHORT).show(); return }
        runSearch("Searching public channel catalogs for $term…") {
            val channels = JSONArray(get("https://iptv-org.github.io/api/channels.json"))
            val matched = HashMap<String, CatalogChannel>()
            for (i in 0 until channels.length()) {
                val item = channels.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (name.contains(term, true)) {
                    val code = item.optString("country")
                    val countryName = countries.firstOrNull { it.second.equals(code, true) }?.first ?: code
                    matched[item.optString("id")] = CatalogChannel(name, "", countryName, "TV")
                }
            }
            val streams = JSONArray(get("https://iptv-org.github.io/api/streams.json"))
            val found = ArrayList<CatalogChannel>()
            for (i in 0 until streams.length()) {
                val item = streams.optJSONObject(i) ?: continue
                val channelId = item.optString("channel")
                val base = matched[channelId] ?: continue
                val address = item.optString("url")
                if (address.startsWith("http") || address.startsWith("rtsp"))
                    found.add(base.copy(url = address))
                if (found.size >= 50) break
            }
            found
        }
    }

    private fun fetchRadio(countryName: String): List<CatalogChannel> {
        val encoded = java.net.URLEncoder.encode(countryName, "UTF-8")
        val array = JSONArray(get("https://de1.api.radio-browser.info/json/stations/bycountryexact/$encoded"))
        val result = ArrayList<CatalogChannel>()
        for (i in 0 until minOf(array.length(), 3)) {
            val item = array.optJSONObject(i) ?: continue
            val stream = item.optString("url_resolved").ifBlank { item.optString("url") }
            if (stream.startsWith("http")) result.add(CatalogChannel(item.optString("name", "Radio"), stream, countryName, "RADIO"))
        }
        return result
    }

    private fun runSearch(message: String, task: () -> List<CatalogChannel>) {
        // هر جستجوی جدید، نتیجه و کار قبلی را منسوخ می‌کند؛ فقط آخرین درخواست اجازه
        // تغییر UI یا تحویل نتیجه به MainActivity را دارد.
        val generation = searchGeneration.incrementAndGet()
        currentSearch?.cancel(true)
        try { activeConnection?.disconnect() } catch (_: Exception) { }
        status.text = message

        currentSearch = searchExecutor.submit {
            try {
                val result = task()
                if (Thread.currentThread().isInterrupted ||
                    generation != searchGeneration.get()
                ) return@submit

                post {
                    if (generation != searchGeneration.get()) return@post
                    status.text =
                        "${result.size} result(s). Some directory entries may need a playable stream URL."
                    onResults(result, message)
                }
            } catch (e: Exception) {
                if (generation == searchGeneration.get() &&
                    !Thread.currentThread().isInterrupted
                ) {
                    post {
                        if (generation == searchGeneration.get()) {
                            status.text = "Search failed: ${e.message ?: "network error"}"
                        }
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        searchGeneration.incrementAndGet()
        currentSearch?.cancel(true)
        try { activeConnection?.disconnect() } catch (_: Exception) { }
        super.onDetachedFromWindow()
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("User-Agent", "TvCh-Android/1.0")
        activeConnection = connection
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }

    private fun parseM3u(text: String, countryName: String): List<CatalogChannel> {
        val result = ArrayList<CatalogChannel>()
        var name = "Unnamed channel"
        for (lineRaw in text.lineSequence()) {
            val line = lineRaw.trim()
            if (line.startsWith("#EXTINF", true)) name = line.substringAfterLast(',').trim().ifBlank { "Unnamed channel" }
            else if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtsp://")) {
                result.add(CatalogChannel(name, line, countryName, "TV")); name = "Unnamed channel"
            }
        }
        return result
    }
}

data class CatalogChannel(val name: String, val url: String, val country: String, val type: String)
