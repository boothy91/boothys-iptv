package com.my.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import android.content.SharedPreferences
import android.util.Xml
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream

data class Account(val name: String, val xtream: Boolean, val server: String, val user: String, val pass: String, val epg: String = "")
data class Cat(val id: String, val name: String, val type: String)
data class Item(val id: String, val name: String, val cat: String, val type: String, val url: String, val epgId: String = "", val icon: String = "")
data class Prog(val s: Long, val e: Long, val t: String)
data class Upd(val tag: String, val url: String)
data class CW(val url: String, val name: String, val icon: String, val type: String, val pos: Long, val dur: Long)

object Repo {
    lateinit var dir: File
    lateinit var appCtx: Context
    lateinit var sp: SharedPreferences
    var account: Account? = null
    var tick by mutableIntStateOf(0)
    var status by mutableStateOf("")
    var onMenu: (() -> Unit)? = null
    var organise by mutableStateOf(true)
    var onZap: ((Int) -> Boolean)? = null
    var onMedia: ((Int) -> Boolean)? = null
    var favKeys by mutableStateOf<List<String>>(emptyList())
    private fun fkey(i: Item) = "${i.type}|${i.id}"
    fun isFav(i: Item) = fkey(i) in favKeys
    fun toggleFav(i: Item) {
        val k = fkey(i)
        favKeys = if (k in favKeys) favKeys - k else listOf(k) + favKeys
        sp.edit().putString("fav", JSONArray(favKeys).toString()).apply()
    }
    fun favItems(type: String): List<Item> = favKeys.mapNotNull { byKey[it] }.filter { it.type == type }

    // ---------- in-app updates (GitHub releases) ----------
    private const val REPO = "boothy91/boothys-iptv"
    var update by mutableStateOf<Upd?>(null)
    var updProgress by mutableIntStateOf(-1)
    var updMsg by mutableStateOf("")
    var updCheck by mutableStateOf("")
    private var checked = false

    fun ver(): String = runCatching { appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName }.getOrNull() ?: "0"

    private fun newer(a: String, b: String): Boolean {
        val x = Regex("\\d+").findAll(a).map { it.value.toInt() }.toList()
        val y = Regex("\\d+").findAll(b).map { it.value.toInt() }.toList()
        for (i in 0 until maxOf(x.size, y.size)) { val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }; if (p != q) return p > q }
        return false
    }

    fun checkUpdate(manual: Boolean = false) {
        if (!manual && checked) return
        checked = true
        if (manual) updCheck = "Checking…"
        Thread {
            runCatching {
                val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "BoothysIPTV")
                c.connectTimeout = 8000; c.readTimeout = 8000
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = o.getString("tag_name"); val assets = o.getJSONArray("assets")
                var url = ""
                for (k in 0 until assets.length()) { val a = assets.getJSONObject(k); if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break } }
                if (url.isNotEmpty() && newer(tag, ver())) { update = Upd(tag, url); updCheck = "Update available: $tag" }
                else updCheck = "You're up to date (v${ver()})"
            }.onFailure { if (manual) updCheck = "Couldn't check: ${it.message}" }
        }.start()
    }

    fun downloadUpdate() {
        val u = update ?: return
        if (updProgress >= 0) return
        if (Build.VERSION.SDK_INT >= 26 && !appCtx.packageManager.canRequestPackageInstalls()) {
            updMsg = "Allow 'Install unknown apps' for this app, then tap this banner again"
            runCatching {
                appCtx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appCtx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        updProgress = 0; updMsg = ""
        Thread {
            runCatching {
                val dir = File(appCtx.externalCacheDir ?: appCtx.cacheDir, "update").apply { mkdirs() }
                val f = File(dir, "BoothysIPTV.apk")
                val c = URL(u.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
                val total = c.contentLengthLong
                c.inputStream.use { i ->
                    f.outputStream().use { o ->
                        val b = ByteArray(65536); var n = 0L
                        while (true) { val r = i.read(b); if (r < 0) break; o.write(b, 0, r); n += r; if (total > 0) updProgress = (n * 100 / total).toInt() }
                    }
                }
                updProgress = 100
                val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(appCtx, "${appCtx.packageName}.fileprovider", f) else Uri.fromFile(f)
                appCtx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { updMsg = "Update failed: ${it.message}" }
            updProgress = -1
        }.start()
    }

    var fastLive by mutableStateOf(true)
    fun toggleFast() { fastLive = !fastLive; sp.edit().putBoolean("fast", fastLive).apply() }
    // Xtream live: raw .ts starts much faster than HLS (.m3u8)
    fun playUrl(u: String): String =
        if (fastLive && account?.xtream == true && "/live/" in u && u.endsWith(".m3u8")) u.removeSuffix(".m3u8") + ".ts" else u

    @Volatile var lastInput = System.currentTimeMillis()
    var sleepHours by mutableIntStateOf(4)

    // ---------- continue watching ----------
    fun cwList(): List<CW> {
        val a = JSONArray(sp.getString("cw", "[]"))
        return (0 until a.length()).map { val r = a.getJSONArray(it); CW(r.getString(0), r.getString(1), r.getString(2), r.getString(3), r.getLong(4), r.getLong(5)) }
    }

    private fun putCW(l: List<CW>) {
        sp.edit().putString("cw", JSONArray(l.map { JSONArray(listOf(it.url, it.name, it.icon, it.type, it.pos, it.dur)) }).toString()).apply(); tick++
    }

    fun removeCW(url: String) { putCW(cwList().filter { it.url != url }); sp.edit().remove("pos|$url").apply() }

    fun saveCW(url: String, name: String, icon: String, type: String, pos: Long, dur: Long) {
        val rest = cwList().filter { it.url != url }
        if (pos > dur * 0.95 || pos < 10000) { putCW(rest); sp.edit().remove("pos|$url").apply() }
        else { putCW((listOf(CW(url, name, icon, type, pos, dur)) + rest).take(20)); sp.edit().putLong("pos|$url", pos).apply() }
    }

    fun cycleSleep() {
        val o = listOf(0, 1, 2, 3, 4, 6)
        sleepHours = o[(o.indexOf(sleepHours) + 1) % o.size]
        sp.edit().putInt("sleep", sleepHours).apply()
    }

    private val tagRe = Regex("\\b(UHD|FHD|HD|SD|4K|8K|HEVC|H265|H264|RAW|60FPS|50FPS|FPS)\\b", RegexOption.IGNORE_CASE)
    private val uhdRe = Regex("\\b(UHD|4K|8K)\\b", RegexOption.IGNORE_CASE)
    private val fhdRe = Regex("\\bFHD\\b", RegexOption.IGNORE_CASE)
    private val hdRe = Regex("\\bHD\\b", RegexOption.IGNORE_CASE)
    private val junkRe = Regex("[^\\p{L}\\p{N}]+")

    private val cc = "UK|GB|US|USA|CA|AU|NZ|IE|DE|FR|ES|IT|PT|NL|BE|PL|TR|IN|PK|AE|SA|MX|BR|AR|SE|NO|DK|FI|RU|GR|AL|RO|CH|AT|IL|ZA|NG|EG|AF|IR|KR|JP|CN|LATAM|ARAB|EX-YU"
    private val prefixRe = Regex("^[\\s|\\[(]*(?:$cc)[\\s\\])]*[|:\\-–»>/]+\\s*")

    // "UK| Twitch HD" / "UK | Twitch HD" / "US: Twitch HD" -> "Twitch HD"
    fun stripPrefix(n: String): String = prefixRe.replace(n, "").ifBlank { n }
    fun label(i: Item): String = if (organise && i.type == "live") stripPrefix(i.name) else i.name

    private fun baseName(n: String) = junkRe.replace(tagRe.replace(n, " "), " ").trim().lowercase()
    private fun rank(n: String) = when {
        uhdRe.containsMatchIn(n) -> 3
        fhdRe.containsMatchIn(n) -> 2
        hdRe.containsMatchIn(n) -> 1
        else -> 0
    }

    fun toggleOrganise() { organise = !organise; sp.edit().putBoolean("org", organise).apply() }

    // Drops "##" placeholder channels and groups SD/HD/FHD/UHD versions of a channel together
    fun organised(l: List<Item>): List<Item> {
        if (!organise) return l
        val g = LinkedHashMap<String, MutableList<Item>>()
        l.forEach { if ("##" !in it.name) g.getOrPut(baseName(stripPrefix(it.name)).ifBlank { it.name }) { ArrayList() }.add(it) }
        return g.values.flatMap { v -> if (v.size > 1) v.sortedBy { rank(it.name) } else v }
    }
    var cats: List<Cat> = emptyList()
    var items: List<Item> = emptyList()
    var byCat: Map<String, List<Item>> = emptyMap()
    var byKey: Map<String, Item> = emptyMap()
    @Volatile var epg: Map<String, List<Prog>> = emptyMap()
    @Volatile var epgNames: Map<String, String> = emptyMap()
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun init(c: Context) {
        appCtx = c.applicationContext
        runCatching { File(c.cacheDir, "update").deleteRecursively(); File(c.externalCacheDir, "update").deleteRecursively() }
        dir = c.filesDir; sp = c.getSharedPreferences("p", 0); organise = sp.getBoolean("org", true); sleepHours = sp.getInt("sleep", 4); fastLive = sp.getBoolean("fast", true); favKeys = list("fav")
        sp.getString("acc", null)?.let {
            val o = JSONObject(it)
            account = Account(o.getString("n"), o.getBoolean("x"), o.getString("s"), o.getString("u"), o.getString("p"), o.optString("e"))
        }
        account?.let { load(it) }
    }

    private fun wipe() {
        File(dir, "data3.txt").delete(); File(dir, "epg.txt").delete()
        cats = emptyList(); items = emptyList(); byCat = emptyMap(); byKey = emptyMap(); epg = emptyMap(); epgNames = emptyMap(); status = ""; tick++
    }

    fun login(a: Account) {
        wipe(); account = a
        sp.edit().putString("acc", JSONObject().put("n", a.name).put("x", a.xtream).put("s", a.server).put("u", a.user).put("p", a.pass).put("e", a.epg).toString()).apply()
        load(a)
    }

    fun logout() { account = null; sp.edit().remove("acc").apply(); wipe() }

    private fun setData(c: List<Cat>, i: List<Item>) {
        cats = c; items = i
        byCat = i.groupBy { "${it.type}|${it.cat}" }
        byKey = i.associateBy { "${it.type}|${it.id}" }
        tick++
    }

    private fun load(a: Account, force: Boolean = false) {
        Thread {
            val f = File(dir, "data3.txt")
            if (f.exists()) runCatching { readCache(f) }
            if (force || !f.exists() || System.currentTimeMillis() - f.lastModified() > 6 * 3600_000L) {
                status = "Loading playlist…"
                runCatching { fetchAll(a); writeCache(f) }
                    .onSuccess { status = if (warn.isEmpty()) "" else "Problem: $warn" }
                    .onFailure { status = "Load failed: ${it.message}" }
            }
            loadEpg(a, force)
        }.start()
    }

    private fun readCache(f: File) {
        val c = ArrayList<Cat>(); val i = ArrayList<Item>()
        f.forEachLine { ln ->
            val p = ln.split('\t')
            if (p[0] == "C" && p.size >= 4) c.add(Cat(p[1], p[2], p[3]))
            else if (p[0] == "I" && p.size >= 8) i.add(Item(p[1], p[2], p[3], p[4], p[5], p[6], p[7]))
        }
        setData(c, i)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun writeCache(f: File) {
        val tmp = File(dir, "data.tmp")
        tmp.bufferedWriter().use { w ->
            cats.forEach { w.write("C\t${clean(it.id)}\t${clean(it.name)}\t${it.type}\n") }
            items.forEach { w.write("I\t${clean(it.id)}\t${clean(it.name)}\t${clean(it.cat)}\t${it.type}\t${clean(it.url)}\t${clean(it.epgId)}\t${clean(it.icon)}\n") }
        }
        tmp.renameTo(f)
    }

    fun open(u: String): InputStream {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 120000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        val b = BufferedInputStream(c.inputStream, 65536)
        b.mark(2); val x = b.read(); val y = b.read(); b.reset()
        return if (x == 0x1f && y == 0x8b) GZIPInputStream(b, 65536) else b
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun base(a: Account) = a.server.trimEnd('/')
    private fun api(a: Account) = "${base(a)}/player_api.php?username=${enc(a.user)}&password=${enc(a.pass)}"
    private fun json(a: Account, act: String) = JSONArray(open("${api(a)}&action=$act").bufferedReader().use { it.readText() })

    private val want = setOf("category_id", "category_name", "stream_id", "series_id", "name", "container_extension", "epg_channel_id", "stream_icon", "cover")

    private fun stream(a: Account, act: String, f: (Map<String, String>) -> Unit) {
        android.util.JsonReader(InputStreamReader(open("${api(a)}&action=$act"), "UTF-8").buffered(65536)).use { r ->
            r.beginArray()
            while (r.hasNext()) {
                val m = HashMap<String, String>()
                r.beginObject()
                while (r.hasNext()) {
                    val k = r.nextName()
                    val t = r.peek()
                    if (k in want && (t == android.util.JsonToken.STRING || t == android.util.JsonToken.NUMBER)) m[k] = r.nextString() else r.skipValue()
                }
                r.endObject()
                f(m)
            }
            r.endArray()
        }
    }

    private fun fetchAll(a: Account) {
        val cl = ArrayList<Cat>(); val il = ArrayList<Item>()
        if (a.xtream) {
            val b = base(a)
            val errs = ArrayList<String>()
            for ((t, ca, sa) in listOf(Triple("live", "get_live_categories", "get_live_streams"), Triple("movie", "get_vod_categories", "get_vod_streams"), Triple("series", "get_series_categories", "get_series"))) {
                runCatching {
                    stream(a, ca) { o -> cl.add(Cat(o["category_id"] ?: "", o["category_name"] ?: "", t)) }
                    stream(a, sa) { o ->
                        val id = o[if (t == "series") "series_id" else "stream_id"] ?: ""
                        val url = when (t) {
                            "live" -> "$b/live/${a.user}/${a.pass}/$id.m3u8"
                            "movie" -> "$b/movie/${a.user}/${a.pass}/$id.${(o["container_extension"] ?: "").ifBlank { "mp4" }}"
                            else -> ""
                        }
                        il.add(Item(id, o["name"] ?: "", o["category_id"] ?: "", t, url, o["epg_channel_id"] ?: "", o[if (t == "series") "cover" else "stream_icon"] ?: ""))
                    }
                }.onFailure { errs.add("$t: ${it.message}") }
            }
            warn = errs.joinToString("; ")
            if (il.isEmpty()) error("No streams returned (check server/login)")
            setData(cl, il)
        } else {
                        val re = Regex("([\\w-]+)=\"([^\"]*)\"")
            val cs = LinkedHashSet<Cat>()
            var meta: Map<String, String>? = null; var nm = ""
            open(a.server).bufferedReader().lineSequence().forEach { ln ->
                val l = ln.trim()
                if (l.startsWith("#EXTINF")) {
                    meta = re.findAll(l).associate { it.groupValues[1] to it.groupValues[2] }
                    nm = l.substringAfterLast(',').trim()
                } else if (l.isNotEmpty() && !l.startsWith("#") && meta != null) {
                    val m = meta!!
                    val t = when { "/movie/" in l -> "movie"; "/series/" in l -> "series"; else -> "live" }
                    val g = m["group-title"]?.ifBlank { null } ?: "Other"
                    cs.add(Cat(g, g, t))
                    il.add(Item(il.size.toString(), nm.ifBlank { m["tvg-name"] ?: "?" }, g, t, l, m["tvg-id"] ?: "", m["tvg-logo"] ?: ""))
                    meta = null
                }
            }
            if (il.isEmpty()) error("No channels found in playlist")
            setData(cs.toList(), il)
        }
    }

    fun episodes(s: Item): List<Item> {
        val a = account!!
        val o = JSONObject(open("${api(a)}&action=get_series_info&series_id=${s.id}").bufferedReader().use { it.readText() }).getJSONObject("episodes")
        val r = ArrayList<Item>()
        for (sn in o.keys().asSequence().sortedBy { it.toIntOrNull() ?: 0 }) {
            val ar = o.getJSONArray(sn)
            for (k in 0 until ar.length()) {
                val e = ar.getJSONObject(k); val id = e.optString("id")
                r.add(Item(id, "S$sn E${e.optString("episode_num")}  ${e.optString("title")}", s.cat, "episode",
                    "${base(a)}/series/${a.user}/${a.pass}/$id.${e.s("container_extension").ifBlank { "mp4" }}"))
            }
        }
        return r
    }

    // ---------- EPG (streamed to disk, cached 12h) ----------
    private fun loadEpg(a: Account, force: Boolean = false) {
        val f = File(dir, "epg.txt")
        val u = if (a.xtream) "${base(a)}/xmltv.php?username=${enc(a.user)}&password=${enc(a.pass)}" else a.epg
        if (f.exists()) runCatching { readEpg(f); tick++ }
        if (u.isBlank() || (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600_000L)) return
        runCatching {
            val tmp = File(dir, "epg.tmp")
            fetchEpg(u, tmp)
            tmp.renameTo(f)
            readEpg(f); tick++
        }
    }

    private fun fetchEpg(url: String, out: File) {
        val now = System.currentTimeMillis(); val lim = now + 12 * 3600_000L
        val f = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val ids = items.map { it.epgId }.filter { it.isNotBlank() }.toHashSet()
        val names = items.map { norm(it.name) }.toHashSet()
        val keep = HashSet<String>()
        val p = Xml.newPullParser(); p.setInput(open(url), null)
        out.bufferedWriter().use { w ->
            var ch = ""; var s = 0L; var e = 0L; var ok = false; var cur = ""
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    if (p.name == "channel") {
                        cur = p.getAttributeValue(null, "id") ?: ""
                    } else if (p.name == "display-name" && cur.isNotEmpty()) {
                        val dn = p.nextText().replace('\t', ' ').replace('\n', ' ')
                        if (cur in ids || norm(dn) in names) { keep.add(cur); w.write("#\t$cur\t$dn\n") }
                    } else if (p.name == "programme") {
                        cur = ""
                        ch = p.getAttributeValue(null, "channel") ?: ""
                        ok = runCatching { s = f.parse(p.getAttributeValue(null, "start"))!!.time; e = f.parse(p.getAttributeValue(null, "stop"))!!.time }.isSuccess
                    } else if (p.name == "title" && ok) {
                        val t = p.nextText()
                        if (e > now && s < lim && (ch in ids || ch in keep)) w.write("$ch\t$s\t$e\t${t.replace('\t', ' ').replace('\n', ' ')}\n")
                        ok = false
                    }
                }
                ev = p.next()
            }
        }
    }

    private fun readEpg(f: File) {
        val m = HashMap<String, MutableList<Prog>>(); val n = HashMap<String, String>()
        f.forEachLine {
            val p = it.split('\t')
            if (p.size >= 3 && p[0] == "#") n[norm(p[2])] = p[1]
            else if (p.size >= 4) m.getOrPut(p[0]) { ArrayList() }.add(Prog(p[1].toLong(), p[2].toLong(), p[3]))
        }
        m.values.forEach { l -> l.sortBy { it.s } }
        epg = m; epgNames = n
    }

    private fun progs(i: Item): List<Prog>? =
        epg[i.epgId] ?: epgNames[norm(i.name)]?.let { epg[it] }

    fun guideProgs(i: Item): List<Prog> = progs(i) ?: emptyList()

    fun nowProg(i: Item): Prog? {
        val l = progs(i) ?: return null
        val t = System.currentTimeMillis()
        return l.firstOrNull { it.s <= t && t < it.e }
    }

    fun nextProg(i: Item): Prog? {
        val l = progs(i) ?: return null
        val t = System.currentTimeMillis()
        return l.firstOrNull { it.s >= t }
    }

    private var warn = ""

    fun refresh() { account?.let { infoCache.clear(); load(it, true) } }

    private val infoCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun plot(i: Item): String {
        val k = "${i.type}|${i.id}"
        infoCache[k]?.let { return it }
        val a = account ?: return ""
        if (!a.xtream) return ""
        val act = if (i.type == "series") "get_series_info&series_id=" else "get_vod_info&vod_id="
        val o = JSONObject(open("${api(a)}&action=$act${i.id}").bufferedReader().use { it.readText() }).optJSONObject("info") ?: return ""
        val txt = o.s("plot").ifBlank { o.s("description") }
        val meta = listOf(o.s("releasedate").ifBlank { o.s("releaseDate") }, o.s("genre"), o.s("rating").let { if (it.isBlank()) "" else "★ $it" })
            .filter { it.isNotBlank() }.joinToString("  •  ")
        val r = (meta + "\n" + txt).trim()
        infoCache[k] = r
        return r
    }

    fun clearHidden() {
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("hc_") || it.startsWith("hi_") }.forEach { e.remove(it) }
        e.apply(); tick++
    }

    // ---------- pins & recents ----------
    fun list(k: String): List<String> { val a = JSONArray(sp.getString(k, "[]")); return (0 until a.length()).map { a.getString(it) } }
    private fun put(k: String, l: List<String>) { sp.edit().putString(k, JSONArray(l).toString()).apply(); tick++ }
    fun toggle(k: String, v: String) { val l = list(k).toMutableList(); if (!l.remove(v)) l.add(0, v); put(k, l) }
    fun addRecent(i: Item) {
        val k = "r_${i.type}"; val key = "${i.type}|${i.id}"
        val l = list(k).toMutableList(); l.remove(key); l.add(0, key); put(k, l.take(20))
    }
    fun <T> pinFirst(l: List<T>, pins: List<String>, key: (T) -> String): List<T> {
        if (pins.isEmpty()) return l
        val m = l.associateBy(key); val top = pins.mapNotNull { m[it] }; val s = top.map(key).toSet()
        return top + l.filter { key(it) !in s }
    }
}

private fun JSONObject.s(k: String): String = optString(k, "").let { if (it == "null") "" else it }
