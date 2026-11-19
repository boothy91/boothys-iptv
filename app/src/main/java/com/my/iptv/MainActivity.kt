@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.my.iptv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent as AKE
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent { App() }
    }

    private var zapHandled = false

    override fun onUserInteraction() { super.onUserInteraction(); Repo.lastInput = System.currentTimeMillis() }

    override fun dispatchKeyEvent(e: AKE): Boolean {
        Repo.lastInput = System.currentTimeMillis()
        val m = Repo.onMenu
        if (m != null && e.keyCode == AKE.KEYCODE_MENU) { if (e.action == AKE.ACTION_UP) m(); return true }
        val md = Repo.onMedia
        if (md != null) {
            val k = e.keyCode
            val mk = k == AKE.KEYCODE_MEDIA_PLAY_PAUSE || k == AKE.KEYCODE_MEDIA_PLAY || k == AKE.KEYCODE_MEDIA_PAUSE ||
                k == AKE.KEYCODE_MEDIA_STOP || k == AKE.KEYCODE_MEDIA_FAST_FORWARD || k == AKE.KEYCODE_MEDIA_REWIND || k == AKE.KEYCODE_HEADSETHOOK
            if (mk) { if (e.action == AKE.ACTION_DOWN && e.repeatCount == 0) md(k); return true }
        }
        val z = Repo.onZap
        if (z != null) {
            val dir = when (e.keyCode) {
                AKE.KEYCODE_DPAD_UP, AKE.KEYCODE_CHANNEL_UP, AKE.KEYCODE_PAGE_UP -> -1
                AKE.KEYCODE_DPAD_DOWN, AKE.KEYCODE_CHANNEL_DOWN, AKE.KEYCODE_PAGE_DOWN -> 1
                else -> 0
            }
            if (dir != 0) {
                if (e.action == AKE.ACTION_DOWN) {
                    if (e.repeatCount == 0) zapHandled = z(dir)
                    if (zapHandled) return true
                } else if (e.action == AKE.ACTION_UP && zapHandled) { zapHandled = false; return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }
}

// Vexora brand colours (sampled from the logo)
val VPurple = Color(0xFF8512FB)
val VBlue = Color(0xFF3823F8)
val VCyan = Color(0xFF00C8FD)
val VBrush = Brush.horizontalGradient(listOf(VPurple, VBlue))

val DarkC = darkColorScheme(
    primary = Color(0xFF5B4BFF), onPrimary = Color.White, background = Color(0xFF07041A), surface = Color(0xFF07041A),
    onSurface = Color(0xFFF3F1FF), surfaceVariant = Color(0xFF151235), onSurfaceVariant = Color(0xFFA9A3D6),
    secondaryContainer = Color(0xFF2A1F66), onSecondaryContainer = Color.White
)
val LightC = lightColorScheme(
    primary = Color(0xFF3823F8), onPrimary = Color.White, background = Color(0xFFF4F2FF), surface = Color(0xFFF4F2FF),
    onSurface = Color(0xFF150F33), surfaceVariant = Color(0xFFE6E1FA), onSurfaceVariant = Color(0xFF5B5680),
    secondaryContainer = Color(0xFFD8CFFF), onSecondaryContainer = Color(0xFF1B0F66)
)

class Nav {
    var tab by mutableStateOf("live")
    var cat by mutableStateOf<Cat?>(null)
    var ser by mutableStateOf<Item?>(null)
    var searching by mutableStateOf(false)
    var guide by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var gcat by mutableStateOf<Cat?>(null)
    var favs by mutableStateOf(false)
    var home by mutableStateOf(true)
    var lastId: String? = null
}

data class PlayReq(val url: String, val list: List<Item> = emptyList(), val idx: Int = 0, val name: String = "", val icon: String = "", val type: String = "", val ch: Item? = null)

data class Dlg(val t: String, val opts: List<Pair<String, () -> Unit>>)

fun hm(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

@Composable fun App() {
    var dark by remember { mutableStateOf(Repo.sp.getBoolean("dark", true)) }
    var acc by remember { mutableStateOf(Repo.account) }
    var play by remember { mutableStateOf<PlayReq?>(null) }
    var splash by remember { mutableStateOf(true) }
    val nav = remember { Nav() }
    LaunchedEffect(Unit) { delay(2500); splash = false }
    MaterialTheme(colorScheme = if (dark) DarkC else LightC) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (splash) Splash()
            else if (play != null) Player(play!!) { play = null }
            else if (acc == null) Login { Repo.login(it); acc = it }
            else Home(nav, dark, { dark = !dark; Repo.sp.edit().putBoolean("dark", dark).apply() }, { acc = null }) { play = it }
        }
    }
}

@Composable fun Splash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF050814))) {
        Image(painterResource(R.drawable.vexora_splash), contentDescription = "Vexora", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        CircularProgressIndicator(
            color = Color.White.copy(alpha = 0.8f), strokeWidth = 3.dp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).size(26.dp)
        )
    }
}

@Composable fun Modifier.longKeys(onLong: (() -> Unit)?): Modifier {
    val lp = remember { booleanArrayOf(false) }
    if (onLong == null) return this
    return onPreviewKeyEvent { k ->
        val c = k.nativeKeyEvent.keyCode
        if (c == AKE.KEYCODE_DPAD_CENTER || c == AKE.KEYCODE_ENTER || c == AKE.KEYCODE_NUMPAD_ENTER) {
            if (k.type == KeyEventType.KeyDown && k.nativeKeyEvent.isLongPress) { lp[0] = true; onLong(); true }
            else if (k.type == KeyEventType.KeyUp && lp[0]) { lp[0] = false; true }
            else false
        } else if (c == AKE.KEYCODE_MENU && k.type == KeyEventType.KeyUp) { onLong(); true }
        else false
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Tile(text: String, sub: String = "", sel: Boolean = false, onLong: (() -> Unit)? = null, mod: Modifier = Modifier, pill: Boolean = false, lines: Int = 1, onFocus: (() -> Unit)? = null, fr: FocusRequester? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val sc by animateFloatAsState(if (f) 1.04f else 1f, label = "s")
    val shape = RoundedCornerShape(if (pill) 26.dp else 16.dp)
    Column(
        mod.scale(sc)
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { f = it.hasFocus; if (it.hasFocus) onFocus?.invoke() }
            .longKeys(onLong)
            .then(if (f) Modifier.background(VBrush, shape).border(2.dp, VCyan, shape) else Modifier.background(if (sel) cs.secondaryContainer else cs.surfaceVariant, shape))
            .clip(shape)
            .combinedClickable(onLongClick = onLong, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text, maxLines = lines, color = if (f) cs.onPrimary else if (sel) cs.onSecondaryContainer else cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (sub.isNotEmpty()) Text(sub, maxLines = 1, color = if (f) cs.onPrimary.copy(alpha = 0.85f) else cs.onSurfaceVariant, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Poster(i: Item, pinned: Boolean, onFocus: (Item) -> Unit, onLong: () -> Unit, mod: Modifier = Modifier, fr: FocusRequester? = null, progress: Float = 0f, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val sc by animateFloatAsState(if (f) 1.05f else 1f, label = "p")
    val shape = RoundedCornerShape(18.dp)
    Column(
        mod.scale(sc)
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { f = it.hasFocus; if (it.hasFocus) onFocus(i) }
            .longKeys(onLong)
            .then(if (f) Modifier.background(VBrush, shape).border(2.dp, VCyan, shape) else Modifier.background(cs.surfaceVariant, shape))
            .clip(shape)
            .combinedClickable(onLongClick = onLong, onClick = onClick)
            .padding(6.dp)
    ) {
        AsyncImage(
            model = i.icon.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(13.dp)).background(cs.background)
        )
        if (progress > 0f) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(cs.background)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(if (f) Color.White else VCyan))
        }
        Text((if (Repo.isFav(i)) "♥ " else "") + (if (pinned) "★ " else "") + i.name, maxLines = 1, color = if (f) cs.onPrimary else cs.onSurface, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
    }
}

@Composable fun Info(i: Item?) {
    val cs = MaterialTheme.colorScheme
    var txt by remember { mutableStateOf("") }
    LaunchedEffect(i) {
        txt = ""
        if (i != null) { delay(350); txt = withContext(Dispatchers.IO) { runCatching { Repo.plot(i) }.getOrDefault("") } }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 96.dp).padding(top = 8.dp)) {
        if (i != null) {
            Text(i.name, color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(txt, color = cs.onSurfaceVariant, fontSize = 13.sp, maxLines = 3)
        }
    }
}

@Composable fun Login(onDone: (Account) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var x by remember { mutableStateOf(true) }
    var n by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var e by remember { mutableStateOf("") }
    val nx = KeyboardOptions(imeAction = ImeAction.Next)
    val sh = RoundedCornerShape(16.dp)
    var phone by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    DisposableEffect(phone) {
        if (!phone) { onDispose { } }
        else {
            val srv = PairServer { m ->
                Handler(Looper.getMainLooper()).post {
                    onDone(Account((m["n"] ?: "").ifBlank { "My IPTV" }, m["type"] != "m", (m["s"] ?: "").trim(), (m["u"] ?: "").trim(), (m["p"] ?: "").trim(), (m["e"] ?: "").trim()))
                }
            }
            link = srv.start()
            onDispose { srv.stop() }
        }
    }
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).background(cs.surfaceVariant, RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Vexora", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Xtream Code", sel = x, pill = true) { x = true }
                Tile("M3U8", sel = !x, pill = true) { x = false }
            }
            Tile(if (phone) "Hide phone setup" else "Fill in from your phone (QR code)", pill = true) { phone = !phone }
            if (phone && link.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Qr(link, 170.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Scan with your phone camera", color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                        Text("or open this address in a browser on the same Wi-Fi:", color = cs.onSurfaceVariant, fontSize = 13.sp)
                        Text(link, color = cs.primary, fontSize = 15.sp)
                    }
                }
                Text(
                    "Chrome showing ERR_TOO_MANY_RETRIES? On your phone go to Settings > Apps > Chrome > Permissions, turn on Nearby devices, then scan the QR code again.",
                    color = cs.onSurfaceVariant, fontSize = 13.sp
                )
            }
            OutlinedTextField(n, { n = it }, Modifier.fillMaxWidth(), label = { Text("Playlist Name") }, singleLine = true, keyboardOptions = nx, shape = sh)
            OutlinedTextField(s, { s = it }, Modifier.fillMaxWidth(), label = { Text(if (x) "Server (http://host:port)" else "M3U8 URL") }, singleLine = true, keyboardOptions = nx, shape = sh)
            if (x) {
                OutlinedTextField(u, { u = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, keyboardOptions = nx, shape = sh)
                OutlinedTextField(p, { p = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, keyboardOptions = nx, shape = sh, visualTransformation = PasswordVisualTransformation())
            } else {
                OutlinedTextField(e, { e = it }, Modifier.fillMaxWidth(), label = { Text("EPG URL (optional, e.g. open-epg .xml.gz)") }, singleLine = true, keyboardOptions = nx, shape = sh)
            }
            Button(onClick = { if (s.isNotBlank()) onDone(Account(n.ifBlank { "My IPTV" }, x, s.trim(), u.trim(), p.trim(), e.trim())) }, shape = RoundedCornerShape(26.dp)) { Text("Login") }
        }
    }
}

@Composable fun Search(onLong: (Item) -> Unit, onOpen: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Item>>(emptyList()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    LaunchedEffect(q) {
        if (q.trim().length < 2) res = emptyList()
        else { delay(250); res = withContext(Dispatchers.Default) { run {
            val hid = listOf("live", "movie", "series").associateWith { Repo.hiddenCatIds(it) }
            Repo.items.asSequence().filter { it.name.contains(q.trim(), true) && !(Repo.organise && "##" in it.name) && it.cat !in (hid[it.type] ?: emptySet()) }
        }.take(200).toList() } }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(fr), placeholder = { Text("Search live TV, movies & series…") }, singleLine = true, shape = RoundedCornerShape(20.dp))
        Spacer(Modifier.height(10.dp))
        if (q.trim().length >= 2 && res.isEmpty()) Text("No results", color = cs.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(res) { i ->
                Tile(Repo.label(i), when (i.type) { "live" -> "Live TV"; "movie" -> "Movie"; else -> "Series" }, onLong = { onLong(i) }, mod = Modifier.fillMaxWidth()) { onOpen(i) }
            }
        }
    }
}

@Composable fun Home(nav: Nav, dark: Boolean, onTheme: () -> Unit, onLogout: () -> Unit, onPlay: (PlayReq) -> Unit) {
    val t = Repo.tick
    val cs = MaterialTheme.colorScheme
    var tab by nav::tab
    var cat by nav::cat
    var ser by nav::ser
    var searching by nav::searching
    var guide by nav::guide
    var settings by nav::settings
    var favs by nav::favs
    var home by nav::home
    var foc by remember { mutableStateOf<Item?>(null) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    fun go(s: String) {
        searching = false; guide = false; settings = false; favs = false; home = false; ser = null; cat = null; foc = null
        when (s) { "home" -> home = true; "guide" -> guide = true; "favs" -> favs = true; "search" -> searching = true; "settings" -> settings = true; else -> tab = s }
    }
    val overlay = searching || guide || settings || favs
    BackHandler(ser != null) { ser = null }
    BackHandler(cat != null && ser == null && tab != "live" && !overlay && !home) { cat = null }
    BackHandler(overlay || (!home && ser == null && (cat == null || tab == "live"))) { go("home") }
    fun open(i: Item, list: List<Item> = emptyList()) {
        nav.lastId = i.id
        if (i.type == "series" && i.url.isEmpty()) { Repo.addRecent(i); go("series"); ser = i }
        else {
            if (i.type != "episode") Repo.addRecent(i)
            val z = if (i.type == "live") list else emptyList()
            val ep = i.type == "episode"
            onPlay(PlayReq(i.url, z, z.indexOfFirst { it.id == i.id }.coerceAtLeast(0),
                if (ep) (ser?.name?.let { "$it · " } ?: "") + i.name else i.name, if (ep) ser?.icon ?: "" else i.icon, if (ep) "series" else i.type, if (i.type == "live") i else null))
        }
    }
    fun favOpt(i: Item): Pair<String, () -> Unit> = (if (Repo.isFav(i)) "Remove from favourites" else "Add to favourites") to { Repo.toggleFav(i) }
    fun favMenu(i: Item) { dlg = Dlg(i.name, listOf(favOpt(i))) }
    fun menu(title: String, pinKey: String, hideKey: String, id: String, pins: List<String>, item: Item? = null) {
        dlg = Dlg(title, listOfNotNull(
            item?.let { favOpt(it) },
            (if (id in pins) "Remove from top" else "Add to top") to { Repo.toggle(pinKey, id) },
            "Hide" to { Repo.toggle(hideKey, id) }
        ))
    }
    fun cwMenu(w: CW) { dlg = Dlg(w.name, listOf("Remove from Continue Watching" to { Repo.removeCW(w.url) })) }
    val loading = Repo.items.isEmpty() && !Repo.status.startsWith("Load failed") && !Repo.status.startsWith("Problem")
    val section = if (settings) "settings" else if (guide) "guide" else if (favs) "favs" else if (searching) "search" else if (home) "home" else tab
    LaunchedEffect(Unit) { Repo.checkUpdate() }
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(190.dp).fillMaxHeight().background(cs.surfaceVariant.copy(alpha = 0.45f)).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Vexora", color = VCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            listOf("home" to "Home", "live" to "Live TV", "movie" to "Movies", "series" to "Series", "guide" to "TV Guide", "favs" to "Favourites", "search" to "Search")
                .forEach { (k, l) -> Tile(l, sel = section == k, mod = Modifier.fillMaxWidth()) { go(k) } }
            Spacer(Modifier.height(10.dp))
            Tile("Update", mod = Modifier.fillMaxWidth()) { Repo.refresh() }
            Tile("Settings", sel = section == "settings", mod = Modifier.fillMaxWidth()) { go("settings") }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            Repo.update?.let { UpdateBanner(it) }
            if (Repo.status.isNotEmpty()) Text(Repo.status, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            val sr = ser
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    settings -> Settings(dark, onTheme, onLogout)
                    guide -> Guide(nav, { favMenu(it) }) { ch, l -> open(ch, l) }
                    favs -> Favourites({ foc = it }, { favMenu(it) }) { i, l -> open(i, l) }
                    searching -> Search({ favMenu(it) }) { i -> open(i) }
                    loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(36.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Loading your playlist…", color = cs.onSurfaceVariant)
                    }
                    home -> Overview(null, { }, { i, l -> open(i, l) }, onPlay, { favMenu(it) }, { cwMenu(it) })
                    sr != null -> Episodes(sr, nav.lastId) { open(it) }
                    tab == "live" -> {
                        val pc = Repo.list("pc_live"); val hcl = Repo.list("hc_live"); val hil = Repo.list("hi_live"); val pil = Repo.list("pi_live")
                        val cl = Repo.pinFirst(Repo.prefFirst(Repo.visibleCats("live")), pc) { it.id }
                        val c = cat ?: cl.firstOrNull()
                        val l = remember(c?.id, Repo.tick, Repo.organise) {
                            if (c == null) emptyList()
                            else Repo.pinFirst(Repo.organised((Repo.byCat["live|${c.id}"] ?: emptyList()).filter { it.id !in hil }), pil) { it.id }
                        }
                        val lid = remember(c?.id) { nav.lastId }
                        val idx0 = remember(c?.id) { l.indexOfFirst { it.id == lid } }
                        val fr = remember(c?.id) { FocusRequester() }
                        val ls = rememberLazyListState(maxOf(idx0, 0))
                        LaunchedEffect(c?.id) { if (idx0 >= 0) { delay(150); runCatching { fr.requestFocus() }; nav.lastId = null } }
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            LazyColumn(Modifier.width(210.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(cl) { k ->
                                    Tile((if (k.id in pc) "★ " else "") + k.name, sel = k.id == c?.id, lines = 2,
                                        onLong = { menu(k.name, "pc_live", "hc_live", k.id, pc) }, mod = Modifier.fillMaxWidth()) { cat = k; foc = null }
                                }
                            }
                            LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = ls, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(l) { i ->
                                    val p = Repo.nowProg(i)
                                    Tile((if (Repo.isFav(i)) "♥ " else "") + (if (i.id in pil) "★ " else "") + Repo.label(i),
                                        if (p != null) "${hm(p.s)}-${hm(p.e)}  ${p.t}" + (Repo.nextProg(i)?.let { "   ›  ${hm(it.s)} ${it.t}" } ?: "") else "",
                                        onLong = { menu(i.name, "pi_live", "hi_live", i.id, pil, i) }, onFocus = { foc = i },
                                        mod = Modifier.fillMaxWidth(), fr = if (i.id == lid) fr else null) { open(i, l) }
                                }
                            }
                        }
                    }
                    else -> {
                        val pinsC = Repo.list("pc_$tab"); val pinsI = Repo.list("pi_$tab"); val hcm = Repo.list("hc_$tab"); val him = Repo.list("hi_$tab")
                        val cl = Repo.pinFirst(Repo.prefFirst(Repo.visibleCats(tab)), pinsC) { it.id }
                        val allCat = remember(tab) { Cat("__all", "All", tab) }
                        Column(Modifier.fillMaxSize()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { Tile("Overview", sel = cat == null, pill = true) { cat = null; foc = null } }
                                item { Tile("All", sel = cat?.id == "__all", pill = true) { cat = allCat; foc = null } }
                                items(cl) { k ->
                                    Tile((if (k.id in pinsC) "★ " else "") + k.name, sel = cat?.id == k.id, pill = true,
                                        onLong = { menu(k.name, "pc_$tab", "hc_$tab", k.id, pinsC) }) { cat = k; foc = null }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            val c = cat
                            if (c == null) Overview(tab, { foc = it }, { i, l -> open(i, l) }, onPlay, { favMenu(it) }, { cwMenu(it) })
                            else {
                                val l = remember(c.id, Repo.tick) {
                                    Repo.pinFirst((if (c.id == "__all") { val hidc = Repo.hiddenCatIds(tab); Repo.items.filter { it.type == tab && it.cat !in hidc } } else (Repo.byCat["$tab|${c.id}"] ?: emptyList())).filter { it.id !in him }, pinsI) { it.id }
                                }
                                val lid = remember(c.id) { nav.lastId }
                                val idx0 = remember(c.id) { l.indexOfFirst { it.id == lid } }
                                val fr = remember(c.id) { FocusRequester() }
                                val gs = rememberLazyGridState(maxOf(idx0, 0))
                                LaunchedEffect(c.id) { if (idx0 >= 0) { delay(150); runCatching { fr.requestFocus() }; nav.lastId = null } }
                                LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize(), state = gs, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(l) { i -> Poster(i, i.id in pinsI, { foc = it }, { menu(i.name, "pi_$tab", "hi_$tab", i.id, pinsI, i) }, fr = if (i.id == lid) fr else null) { open(i) } }
                                }
                            }
                        }
                    }
                }
            }
            val f = foc
            if (f != null && sr == null) {
                if (f.type == "live" && section == "live") LiveInfo(f)
                else if (f.type != "live" && (section == "movie" || section == "series" || section == "favs")) Info(f)
            }
        }
    }
    dlg?.let { d ->
        // Ignore the select button until the long-press that opened this menu has been released
        var armed by remember(d) { mutableStateOf(false) }
        val last = remember(d) { longArrayOf(0L) }
        val fr0 = remember(d) { FocusRequester() }
        LaunchedEffect(d) {
            val t0 = System.currentTimeMillis()
            runCatching { fr0.requestFocus() }
            while (!armed) { delay(100); val n = System.currentTimeMillis(); if (n - t0 > 600 && n - last[0] > 350) armed = true }
        }
        AlertDialog(onDismissRequest = { dlg = null }, shape = RoundedCornerShape(24.dp), title = { Text(d.t) },
            text = {
                Column(Modifier.onPreviewKeyEvent { k ->
                    val c = k.nativeKeyEvent.keyCode
                    if (!armed && (c == AKE.KEYCODE_DPAD_CENTER || c == AKE.KEYCODE_ENTER || c == AKE.KEYCODE_NUMPAD_ENTER)) { last[0] = System.currentTimeMillis(); true } else false
                }) {
                    d.opts.forEachIndexed { n, (l, a) ->
                        TextButton(onClick = { if (armed) { a(); dlg = null } }, modifier = if (n == 0) Modifier.focusRequester(fr0) else Modifier) { Text(l) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dlg = null }) { Text("Cancel") } })
    }
}

@Composable fun LiveInfo(i: Item) {
    val cs = MaterialTheme.colorScheme
    val n = Repo.nowProg(i)
    val x = Repo.nextProg(i)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(Repo.label(i), color = cs.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(if (n != null) "Now  ${hm(n.s)}–${hm(n.e)}  ${n.t}" else "No guide data", color = cs.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
        if (x != null) Text("Next  ${hm(x.s)}–${hm(x.e)}  ${x.t}", color = cs.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable fun Overview(type: String?, onFocus: (Item) -> Unit, onOpen: (Item, List<Item>) -> Unit, onPlay: (PlayReq) -> Unit, onMenu: (Item) -> Unit, onCwMenu: (CW) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = Repo.tick
    val cw = Repo.cwList().filter { type == null || it.type == type }
    val favLive = if (type == null) Repo.favItems("live") else emptyList()
    val recLive = if (type == null || type == "live") Repo.list("r_live").mapNotNull { Repo.byKey[it] }.take(10) else emptyList()
    val recPoster = listOf("movie", "series").filter { type == null || it == type }.flatMap { ty -> Repo.list("r_$ty").mapNotNull { Repo.byKey[it] }.take(10) }
    if (cw.isEmpty() && favLive.isEmpty() && recLive.isEmpty() && recPoster.isEmpty()) {
        Text("Nothing here yet. Pick a category above, or use the menu on the left. What you watch will show up here.", color = cs.onSurfaceVariant)
    } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cw.isNotEmpty()) {
            item { Text("Continue Watching", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(cw) { w ->
                        Poster(Item(w.url, w.name, "", w.type, w.url, "", w.icon), false, { }, { onCwMenu(w) }, Modifier.width(130.dp), progress = if (w.dur > 0) w.pos.toFloat() / w.dur else 0f) {
                            onPlay(PlayReq(w.url, emptyList(), 0, w.name, w.icon, w.type))
                        }
                    }
                }
            }
        }
        if (favLive.isNotEmpty()) {
            item { Text("Favourite channels", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(favLive) { i -> Tile("♥ " + Repo.label(i), Repo.nowProg(i)?.t ?: "", lines = 2, onLong = { onMenu(i) }, mod = Modifier.width(250.dp)) { onOpen(i, favLive) } }
                }
            }
        }
        if (recLive.isNotEmpty()) {
            item { Text("Recent channels", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recLive) { i -> Tile(Repo.label(i), Repo.nowProg(i)?.t ?: "", lines = 2, onLong = { onMenu(i) }, mod = Modifier.width(250.dp)) { onOpen(i, recLive) } }
                }
            }
        }
        if (recPoster.isNotEmpty()) {
            item { Text(if (type == null) "Recently watched" else "Recent", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recPoster) { i -> Poster(i, false, onFocus, { onMenu(i) }, Modifier.width(130.dp)) { onOpen(i, emptyList()) } }
                }
            }
        }
    }
}

@Composable fun Episodes(s: Item, lastId: String?, onOpen: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var eps by remember { mutableStateOf<List<Item>?>(null) }
    var season by remember { mutableStateOf<String?>(null) }
    val ls = rememberLazyListState()
    val fr = remember { FocusRequester() }
    LaunchedEffect(s) { eps = withContext(Dispatchers.IO) { runCatching { Repo.episodes(s) }.getOrDefault(emptyList()) } }
    LaunchedEffect(season) { ls.scrollToItem(0) }
    LaunchedEffect(eps) {
        val l = eps ?: return@LaunchedEffect
        val hit = l.find { it.id == lastId }
        season = hit?.cat ?: l.firstOrNull()?.cat
        if (hit != null) {
            val idx = l.filter { it.cat == hit.cat }.indexOfFirst { it.id == hit.id }
            delay(200); ls.scrollToItem(maxOf(idx, 0)); delay(100); runCatching { fr.requestFocus() }
        }
    }
    val l = eps
    if (l == null) { Text("Loading…", color = cs.onSurfaceVariant); return }
    if (l.isEmpty()) { Text("No episodes found", color = cs.onSurfaceVariant); return }
    val seasons = l.map { it.cat }.distinct()
    val cur = season ?: seasons.first()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s.name, color = cs.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons) { sn -> Tile(if (sn == "0") "Specials" else "Season $sn", sel = sn == cur, pill = true) { season = sn } }
        }
        LazyColumn(Modifier.weight(1f), state = ls, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(l.filter { it.cat == cur }) { e ->
                Tile("Episode ${e.epgId}", e.name.substringAfter("  ", ""), mod = Modifier.fillMaxWidth(), fr = if (e.id == lastId) fr else null) { onOpen(e) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun UpdateBanner(u: Upd) {
    var f by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).onFocusChanged { f = it.hasFocus }
            .then(if (f) Modifier.background(VBrush, shape).border(2.dp, VCyan, shape) else Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF0E7A8C), VBlue)), shape)).clip(shape)
            .combinedClickable(onClick = { Repo.downloadUpdate() }).padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("⬆  Update available — ${u.tag}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(if (Repo.updProgress >= 0) "Downloading… ${Repo.updProgress}%" else Repo.updMsg.ifEmpty { "Tap to download and install" }, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}

@Composable fun Favourites(onFocus: (Item) -> Unit, onLong: (Item) -> Unit, onOpen: (Item, List<Item>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = Repo.tick
    val live = Repo.favItems("live")
    val movies = Repo.favItems("movie")
    val series = Repo.favItems("series")
    if (live.isEmpty() && movies.isEmpty() && series.isEmpty()) {
        Text("No favourites yet. Long-press any channel, movie or series and choose Add to favourites.", color = cs.onSurfaceVariant)
    } else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (live.isNotEmpty()) {
            item { Text("Live TV", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            items(live) { i ->
                val p = Repo.nowProg(i)
                Tile("♥ " + Repo.label(i), if (p != null) "${hm(p.s)}-${hm(p.e)}  ${p.t}" else "", onLong = { onLong(i) }, mod = Modifier.fillMaxWidth()) { onOpen(i, live) }
            }
        }
        if (movies.isNotEmpty()) {
            item { Text("Movies", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(movies) { i -> Poster(i, false, onFocus, { onLong(i) }, Modifier.width(130.dp)) { onOpen(i, emptyList()) } } } }
        }
        if (series.isNotEmpty()) {
            item { Text("Series", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(series) { i -> Poster(i, false, onFocus, { onLong(i) }, Modifier.width(130.dp)) { onOpen(i, emptyList()) } } } }
        }
    }
}

@Composable fun HideCats() {
    val cs = MaterialTheme.colorScheme
    val t = Repo.tick
    var type by remember { mutableStateOf("live") }
    val hp = Repo.hiddenPrefixes(type)
    val rows = Repo.prefixCounts(type)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Hide categories", color = cs.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("live" to "Live TV", "movie" to "Movies", "series" to "Series").forEach { (k, l) -> Tile(l, sel = type == k, pill = true) { type = k } }
        }
        Text("Tick a prefix to hide every category that starts with it. Untick to show them again.", color = cs.onSurfaceVariant, fontSize = 13.sp)
        if (rows.isEmpty()) Text("No prefixes found in this playlist.", color = cs.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows) { (p, n) ->
                val off = p in hp
                Tile((if (off) "☑  " else "☐  ") + (if (type == "live") "$p|" else "|$p|"), "$n categor" + (if (n == 1) "y" else "ies") + (if (off) " · hidden" else ""), sel = off, mod = Modifier.fillMaxWidth()) {
                    Repo.toggleHiddenPrefix(type, p)
                }
            }
        }
    }
}

@Composable fun Settings(dark: Boolean, onTheme: () -> Unit, onLogout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(Repo.sp.getInt("aspect", 0)) }
    val t = Repo.tick
    val hidden = listOf("live", "movie", "series").sumOf { Repo.list("hc_$it").size + Repo.list("hi_$it").size + Repo.list("hp_$it").size }
    var sub by remember { mutableStateOf("") }
    BackHandler(sub != "") { sub = "" }
    if (sub == "hide") { HideCats(); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", color = cs.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Tile("Auto-organise: " + if (Repo.organise) "On" else "Off", "Hides ## placeholders, groups HD/FHD/UHD together, removes UK| / US: prefixes", mod = Modifier.fillMaxWidth()) { Repo.toggleOrganise() }
        Tile("Appearance: " + if (dark) "Dark" else "Light", "Tap to switch", mod = Modifier.fillMaxWidth()) { onTheme() }
        Tile("Default video size: " + listOf("Fit", "Fill", "Zoom")[aspect], "Fit keeps the picture shape, Fill stretches, Zoom crops to fill", mod = Modifier.fillMaxWidth()) {
            aspect = (aspect + 1) % 3; Repo.sp.edit().putInt("aspect", aspect).apply()
        }
        Tile("Faster live start: " + if (Repo.fastLive) "On" else "Off", "Uses direct streams instead of HLS where the server allows it. Turn off if a channel won't play", mod = Modifier.fillMaxWidth()) { Repo.toggleFast() }
        Tile("Country first: " + Repo.firstPrefix.ifEmpty { "Off" }, "Categories starting with this prefix (like |EN|) are listed first. Tap to cycle through the ones in your playlist", mod = Modifier.fillMaxWidth()) { Repo.cyclePrefix() }
        Tile("Playback timing: " + if (Repo.showTiming) "On" else "Off", "Shows how long a stream took to start and which format it used", mod = Modifier.fillMaxWidth()) { Repo.toggleTiming() }
        Tile("Stream identity: " + Repo.uas[Repo.uaIdx].second, "Some providers respond faster to certain apps. Try each one", mod = Modifier.fillMaxWidth()) { Repo.cycleUa() }
        Tile("Sleep timer: " + (if (Repo.sleepHours == 0) "Off" else "${Repo.sleepHours}h"), "Stops playback after this long with no remote or touch input, so the screensaver can start", mod = Modifier.fillMaxWidth()) { Repo.cycleSleep() }
        Tile("Check for updates", Repo.updCheck.ifEmpty { "Installed version: ${Repo.ver()}" }, mod = Modifier.fillMaxWidth()) { Repo.checkUpdate(true) }
        Tile("Hide categories", "Hide whole groups like AU| or FR| in Live TV, Movies and Series", mod = Modifier.fillMaxWidth()) { sub = "hide" }
        Tile("Unhide everything", "$hidden hidden categories / channels", mod = Modifier.fillMaxWidth()) { Repo.clearHidden() }
        Tile("Switch playlist", "Logs out of ${Repo.account?.name ?: ""}", mod = Modifier.fillMaxWidth()) { confirm = true }
        Row(
            Modifier.fillMaxWidth().background(cs.surfaceVariant, RoundedCornerShape(16.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Qr("https://refer-nordvpn.com/BoZjAlNeKcb", 120.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Need a VPN?", color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Scan here with your phone", color = cs.onSurfaceVariant, fontSize = 14.sp)
                Text("refer-nordvpn.com/BoZjAlNeKcb", color = VCyan, fontSize = 12.sp)
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, shape = RoundedCornerShape(24.dp),
        title = { Text("Switch playlist?") }, text = { Text("This logs you out so you can enter a different playlist.") },
        confirmButton = { TextButton(onClick = { confirm = false; Repo.logout(); onLogout() }) { Text("Log out") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun Cell(text: String, sub: String, airing: Boolean, mod: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(mod.padding(end = 3.dp)) {
        Column(
            Modifier.fillMaxSize().onFocusChanged { f = it.hasFocus }
                .then(if (f) Modifier.background(VBrush, shape).border(2.dp, VCyan, shape) else Modifier.background(if (airing) cs.secondaryContainer else cs.surfaceVariant, shape))
                .clip(shape).combinedClickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            val c = if (f) cs.onPrimary else if (airing) cs.onSecondaryContainer else cs.onSurface
            Text(text, maxLines = 2, color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotEmpty()) Text(sub, maxLines = 1, color = c.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable fun Guide(nav: Nav, onLong: (Item) -> Unit, onPlay: (Item, List<Item>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = Repo.tick
    val hc = Repo.list("hc_live")
    val hi = Repo.list("hi_live")
    val cl = Repo.pinFirst(Repo.prefFirst(Repo.visibleCats("live")), Repo.list("pc_live")) { it.id }
    val cat = nav.gcat ?: cl.firstOrNull()
    val chans = remember(cat?.id, Repo.tick, Repo.organise) {
        if (cat == null) emptyList() else Repo.organised((Repo.byCat["live|${cat.id}"] ?: emptyList()).filter { it.id !in hi })
    }
    val lid = remember { nav.lastId }
    val idx0 = remember { chans.indexOfFirst { it.id == lid } }
    val fr = remember { FocusRequester() }
    val gls = rememberLazyListState(maxOf(idx0, 0))
    LaunchedEffect(Unit) { if (idx0 >= 0) { delay(150); runCatching { fr.requestFocus() }; nav.lastId = null } }
    val now = System.currentTimeMillis()
    val gs = now / 1800000L * 1800000L
    val ge = gs + 12 * 3600_000L
    val sc = rememberScrollState()
    val dpm = 5f
    val nameW = 230.dp
    Column(Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cl) { c -> Tile(c.name, sel = c.id == cat?.id, pill = true) { nav.gcat = c } }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Spacer(Modifier.width(nameW))
            Row(Modifier.weight(1f).horizontalScroll(sc)) {
                for (k in 0 until 24) Box(Modifier.width((30 * dpm).dp)) { Text(hm(gs + k * 1800000L), color = cs.onSurfaceVariant, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (Repo.epg.isEmpty()) Text("No guide data yet. Add an EPG URL at login (M3U) or tap Update.", color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        LazyColumn(state = gls, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(chans) { ch ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tile((if (Repo.isFav(ch)) "♥ " else "") + Repo.label(ch), onLong = { onLong(ch) }, lines = 2, mod = Modifier.width(nameW).height(72.dp).padding(end = 6.dp), fr = if (ch.id == lid) fr else null) { onPlay(ch, chans) }
                    Row(Modifier.weight(1f).horizontalScroll(sc)) {
                        var cur = gs
                        val ps = Repo.guideProgs(ch).filter { it.e > gs && it.s < ge }
                        if (ps.isEmpty()) Cell("No guide data", "", false, Modifier.width(300.dp).height(72.dp)) { onPlay(ch, chans) }
                        else ps.forEach { p ->
                            val st = maxOf(p.s, gs, cur); val en = minOf(p.e, ge)
                            if (en > st) {
                                if (st > cur) Spacer(Modifier.width(((st - cur) / 60000f * dpm).dp))
                                Cell(p.t, "${hm(p.s)}-${hm(p.e)}", p.s <= now && now < p.e, Modifier.width(((en - st) / 60000f * dpm).dp).height(72.dp)) { onPlay(ch, chans) }
                                cur = en
                            }
                        }
                    }
                }
            }
        }
    }
}

fun focusPlay(p: PlayerView) {
    val id = p.resources.getIdentifier("exo_play_pause", "id", p.context.packageName)
    if (id != 0) p.findViewById<View>(id)?.requestFocus()
}

@Composable fun Player(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? ComponentActivity
    var mode by remember { mutableStateOf(Repo.sp.getInt("aspect", 0)) }
    var ctrl by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val menuNow = rememberUpdatedState(menu)
    var banner by remember { mutableStateOf(if (req.type == "live") "show" else "") }
    var cur by remember { mutableStateOf(req.url) }
    var idx by remember { mutableStateOf(req.idx) }
    val chan = if (req.list.isNotEmpty()) req.list[idx.coerceIn(0, req.list.size - 1)] else req.ch
    var pv by remember { mutableStateOf<PlayerView?>(null) }
    var t0 by remember { mutableStateOf(System.currentTimeMillis()) }
    var startMs by remember { mutableStateOf(-1L) }
    var fmt by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    val ex = remember {
        val live = req.type == "live"
        val http = DefaultHttpDataSource.Factory().setUserAgent(Repo.ua()).setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(6000).setReadTimeoutMs(5000)
        val ext = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
        val lc = (if (live) DefaultLoadControl.Builder().setBufferDurationsMs(8000, 40000, 400, 1500)
                  else DefaultLoadControl.Builder().setBufferDurationsMs(15000, 60000, 1500, 3000)).build()
        ExoPlayer.Builder(ctx, DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(http, ext)).setLoadControl(lc).build().apply {
            val p = this
            val saved = Repo.sp.getLong("pos|" + req.url, 0L)
            setMediaItem(MediaItem.fromUri(Repo.playUrl(req.url)), if (saved > 10000) saved else 0L)
            // if a server doesn't support raw .ts, fall back to HLS
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (startMs < 0) {
                        startMs = System.currentTimeMillis() - t0
                        val u = p.currentMediaItem?.localConfiguration?.uri?.toString() ?: ""
                        fmt = if (u.endsWith(".ts")) "TS" else if (u.contains(".m3u8")) "HLS" else "file"
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    val u = p.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
                    if (u.endsWith(".ts") && "/live/" in u) { Repo.tsBad = true; p.setMediaItem(MediaItem.fromUri(u.removeSuffix(".ts") + ".m3u8")); p.prepare() }
                }
            })
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(Unit) {
        val w = act?.window
        val c = w?.let { WindowCompat.getInsetsController(it, it.decorView) }
        c?.hide(WindowInsetsCompat.Type.systemBars())
        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Repo.lastInput = System.currentTimeMillis()
        Repo.onMenu = { menu = !menu }
        Repo.onMedia = { k ->
            when (k) {
                AKE.KEYCODE_MEDIA_PLAY_PAUSE, AKE.KEYCODE_HEADSETHOOK -> {
                    if (!ex.playWhenReady && ex.isCurrentMediaItemLive) ex.seekToDefaultPosition()
                    ex.playWhenReady = !ex.playWhenReady; true
                }
                AKE.KEYCODE_MEDIA_PLAY -> { if (ex.isCurrentMediaItemLive) ex.seekToDefaultPosition(); ex.playWhenReady = true; true }
                AKE.KEYCODE_MEDIA_PAUSE -> { ex.playWhenReady = false; true }
                AKE.KEYCODE_MEDIA_STOP -> { onBack(); true }
                AKE.KEYCODE_MEDIA_FAST_FORWARD -> { if (!ex.isCurrentMediaItemLive) ex.seekTo(ex.currentPosition + 30000); true }
                AKE.KEYCODE_MEDIA_REWIND -> { if (!ex.isCurrentMediaItemLive) ex.seekTo(maxOf(0L, ex.currentPosition - 10000)); true }
                else -> false
            }
        }
        Repo.onZap = { dir ->
            if (ctrl || menu || req.list.size < 2) false
            else {
                idx = (idx + dir + req.list.size) % req.list.size
                val ch = req.list[idx]
                cur = ch.url
                startMs = -1L; t0 = System.currentTimeMillis()
                ex.setMediaItem(MediaItem.fromUri(Repo.playUrl(ch.url))); ex.prepare(); ex.playWhenReady = true
                banner = Repo.label(ch) + (Repo.nowProg(ch)?.let { "  •  ${it.t}" } ?: "")
                true
            }
        }
        onDispose {
            runCatching { if (req.type != "live" && req.list.isEmpty()) Repo.saveCW(cur, req.name, req.icon, req.type, ex.currentPosition, ex.duration.coerceAtLeast(0L)) }
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Repo.onMenu = null; Repo.onZap = null; Repo.onMedia = null; ex.release(); c?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(menu) { if (menu) runCatching { fr.requestFocus() } else pv?.let { if (it.isControllerFullyVisible) focusPlay(it) else it.requestFocus() } }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val h = Repo.sleepHours
            if (h > 0 && System.currentTimeMillis() - Repo.lastInput > h * 3600_000L) { onBack(); break }
        }
    }
    LaunchedEffect(startMs) { if (startMs >= 0 && Repo.showTiming) banner = "t$startMs" }
    // save progress while watching, so Continue Watching works even if the app is closed or the Firestick sleeps
    LaunchedEffect(Unit) {
        while (req.type != "live" && req.list.isEmpty()) {
            delay(5000)
            if (ex.playbackState == Player.STATE_READY) Repo.saveCW(cur, req.name, req.icon, req.type, ex.currentPosition, ex.duration.coerceAtLeast(0L))
        }
    }
    LaunchedEffect(banner) { if (banner.isNotEmpty()) { delay(2500); banner = "" } }
    BackHandler { if (menu) menu = false else if (ctrl) pv?.hideController() else onBack() }
    val rm = when (mode) {
        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ c ->
            PlayerView(c).apply {
                player = ex; useController = true; resizeMode = rm; isFocusable = true; requestFocus()
                val pvv = this
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                    ctrl = v == View.VISIBLE
                    if (v == View.VISIBLE && !menuNow.value) pvv.post { focusPlay(pvv) }
                })
                pv = this
            }
        }, Modifier.fillMaxSize(), update = { it.resizeMode = rm })
        if (ctrl || menu) {
            Row(Modifier.align(Alignment.TopEnd).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Fit", "Fill", "Zoom").forEachIndexed { i, l ->
                    Tile(l, sel = mode == i, pill = true, fr = if (i == mode) fr else null) { mode = i; Repo.sp.edit().putInt("aspect", i).apply() }
                }
            }
        }
        if ((ctrl || banner.isNotEmpty() || menu) && (chan != null || req.name.isNotEmpty())) {
            val n = chan?.let { Repo.nowProg(it) }
            val x = chan?.let { Repo.nextProg(it) }
            Column(
                Modifier.align(Alignment.TopStart).padding(24.dp).widthIn(max = 520.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(if (chan != null) Repo.label(chan) else req.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                if (chan != null) {
                    Text(if (n != null) "Now  ${hm(n.s)}–${hm(n.e)}  ${n.t}" else "Now  No guide data", color = Color(0xFFE2E8F0), fontSize = 15.sp, maxLines = 1)
                    if (Repo.showTiming && startMs >= 0) Text("Started in ${"%.1f".format(startMs / 1000.0)}s · $fmt", color = Color(0xFF7DD3A8), fontSize = 13.sp)
                    if (x != null) Text("Next  ${hm(x.s)}–${hm(x.e)}  ${x.t}", color = Color(0xFF9AA7BD), fontSize = 15.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable fun Qr(text: String, size: Dp) {
    val m = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }
    Canvas(Modifier.size(size).background(Color.White)) {
        val cell = this.size.width / m.width
        for (x in 0 until m.width) for (y in 0 until m.height)
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
    }
}
