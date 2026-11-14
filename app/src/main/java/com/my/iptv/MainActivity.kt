@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.my.iptv

import android.os.Bundle
import android.view.KeyEvent as AKE
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

val DarkC = darkColorScheme(
    primary = Color(0xFF4C8DFF), onPrimary = Color.White, background = Color(0xFF0B0F1A), surface = Color(0xFF0B0F1A),
    onSurface = Color(0xFFF1F5F9), surfaceVariant = Color(0xFF182033), onSurfaceVariant = Color(0xFF9AA7BD),
    secondaryContainer = Color(0xFF26385C), onSecondaryContainer = Color.White
)
val LightC = lightColorScheme(
    primary = Color(0xFF2F6BFF), onPrimary = Color.White, background = Color(0xFFF2F4F9), surface = Color(0xFFF2F4F9),
    onSurface = Color(0xFF111827), surfaceVariant = Color(0xFFE3E8F2), onSurfaceVariant = Color(0xFF5B6678),
    secondaryContainer = Color(0xFFC9D9FF), onSecondaryContainer = Color(0xFF0B2A6B)
)

class Nav {
    var tab by mutableStateOf("live")
    var cat by mutableStateOf<Cat?>(null)
    var ser by mutableStateOf<Item?>(null)
    var searching by mutableStateOf(false)
    var guide by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var gcat by mutableStateOf<Cat?>(null)
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
    LaunchedEffect(Unit) { delay(1800); splash = false }
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
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1226), Color(0xFF2B1B5E)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(88.dp).background(Brush.linearGradient(listOf(Color(0xFF4C8DFF), Color(0xFF9B5CFF))), RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White, fontSize = 40.sp)
            }
            Spacer(Modifier.height(24.dp))
            Text("Boothys IPTV Player", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Your entertainment starts here", color = Color(0xFFB8C2DB), fontSize = 16.sp)
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(color = Color(0xFF4C8DFF), strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
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
@Composable fun Tile(text: String, sub: String = "", sel: Boolean = false, onLong: (() -> Unit)? = null, mod: Modifier = Modifier, pill: Boolean = false, lines: Int = 1, fr: FocusRequester? = null, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val sc by animateFloatAsState(if (f) 1.04f else 1f, label = "s")
    val shape = RoundedCornerShape(if (pill) 26.dp else 16.dp)
    Column(
        mod.scale(sc)
            .then(if (fr != null) Modifier.focusRequester(fr) else Modifier)
            .onFocusChanged { f = it.hasFocus }
            .longKeys(onLong)
            .background(if (f) cs.primary else if (sel) cs.secondaryContainer else cs.surfaceVariant, shape)
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
            .background(if (f) cs.primary else cs.surfaceVariant, shape)
            .clip(shape)
            .combinedClickable(onLongClick = onLong, onClick = onClick)
            .padding(6.dp)
    ) {
        AsyncImage(
            model = i.icon.ifBlank { null }, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(13.dp)).background(cs.background)
        )
        if (progress > 0f) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(cs.background)) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(if (f) cs.onPrimary else cs.primary))
        }
        Text((if (pinned) "★ " else "") + i.name, maxLines = 1, color = if (f) cs.onPrimary else cs.onSurface, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp))
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
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 560.dp).verticalScroll(rememberScrollState()).background(cs.surfaceVariant, RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Boothys IPTV Player", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Xtream Code", sel = x, pill = true) { x = true }
                Tile("M3U8", sel = !x, pill = true) { x = false }
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

@Composable fun Search(onOpen: (Item) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var res by remember { mutableStateOf<List<Item>>(emptyList()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    LaunchedEffect(q) {
        if (q.trim().length < 2) res = emptyList()
        else { delay(250); res = withContext(Dispatchers.Default) { Repo.items.asSequence().filter { it.name.contains(q.trim(), true) && !(Repo.organise && "##" in it.name) }.take(200).toList() } }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(fr), placeholder = { Text("Search live TV, movies & series…") }, singleLine = true, shape = RoundedCornerShape(20.dp))
        Spacer(Modifier.height(10.dp))
        if (q.trim().length >= 2 && res.isEmpty()) Text("No results", color = cs.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(res) { i ->
                Tile(Repo.label(i), when (i.type) { "live" -> "Live TV"; "movie" -> "Movie"; else -> "Series" }, mod = Modifier.fillMaxWidth()) { onOpen(i) }
            }
        }
    }
}

@Composable fun Home(nav: Nav, dark: Boolean, onTheme: () -> Unit, onLogout: () -> Unit, onPlay: (PlayReq) -> Unit) {
    val t = Repo.tick
    val cs = MaterialTheme.colorScheme
    val types = listOf("live" to "Live TV", "movie" to "Movies", "series" to "Series")
    var tab by nav::tab
    var cat by nav::cat
    var ser by nav::ser
    var searching by nav::searching
    var guide by nav::guide
    var settings by nav::settings
    var foc by remember { mutableStateOf<Item?>(null) }
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    BackHandler(ser != null) { ser = null }
    BackHandler(cat != null && ser == null) { cat = null }
    BackHandler(searching) { searching = false }
    BackHandler(guide) { guide = false }
    BackHandler(settings) { settings = false }
    fun open(i: Item, list: List<Item> = emptyList()) {
        nav.lastId = i.id
        if (i.type == "series" && i.url.isEmpty()) { Repo.addRecent(i); ser = i }
        else {
            if (i.type != "episode") Repo.addRecent(i)
            val z = if (i.type == "live") list else emptyList()
            val ep = i.type == "episode"
            onPlay(PlayReq(i.url, z, z.indexOfFirst { it.id == i.id }.coerceAtLeast(0),
                if (ep) (ser?.name?.let { "$it · " } ?: "") + i.name else i.name, if (ep) ser?.icon ?: "" else i.icon, if (ep) "series" else i.type, if (i.type == "live") i else null))
        }
    }
    fun menu(title: String, pinKey: String, hideKey: String, id: String, pins: List<String>) {
        dlg = Dlg(title, listOf(
            (if (id in pins) "Remove from top" else "Add to top") to { Repo.toggle(pinKey, id) },
            "Hide" to { Repo.toggle(hideKey, id) }
        ))
    }
    val hc = Repo.list("hc_$tab")
    val hi = Repo.list("hi_$tab")
    val poster = tab != "live"
    val loading = Repo.items.isEmpty() && !Repo.status.startsWith("Load failed") && !Repo.status.startsWith("Problem")
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            types.forEach { (k, l) -> Tile(l, sel = tab == k && !searching && !guide && !settings, pill = true) { tab = k; cat = null; ser = null; foc = null; searching = false; guide = false; settings = false } }
            Tile("Update", pill = true) { Repo.refresh() }
            Tile("Search", sel = searching, pill = true) { searching = true; guide = false; settings = false }
            Tile("TV Guide", sel = guide, pill = true) { guide = true; searching = false; settings = false }
            Tile("Settings", sel = settings, pill = true) { settings = true; searching = false; guide = false }
        }
        if (Repo.status.isNotEmpty()) Text(Repo.status, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(12.dp))
        val c = cat
        val sr = ser
        if (settings) {
            Box(Modifier.weight(1f)) { Settings(dark, onTheme, onLogout) }
        } else if (guide) {
            Box(Modifier.weight(1f)) { Guide(nav) { ch, l -> open(ch, l) } }
        } else if (searching) {
            Box(Modifier.weight(1f)) {
                Search { i ->
                    if (i.type == "series" && i.url.isEmpty()) { searching = false; tab = "series"; cat = null }
                    open(i)
                }
            }
        } else if (loading) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(36.dp))
                Spacer(Modifier.height(16.dp))
                Text("Loading your playlist…", color = cs.onSurfaceVariant)
            }
        } else if (sr != null) {
            Box(Modifier.weight(1f)) { Episodes(sr) { open(it) } }
        } else if (c != null) {
            val pins = Repo.list("pi_$tab")
            val shown = remember(c.id, tab, Repo.tick, Repo.organise) {
                val b = (Repo.byCat["$tab|${c.id}"] ?: emptyList()).filter { it.id !in hi }
                if (tab == "live") Repo.organised(b) else b
            }
            val l = Repo.pinFirst(shown, pins) { it.id }
            val lid = remember(c.id) { nav.lastId }
            val idx0 = remember(c.id) { l.indexOfFirst { it.id == lid } }
            val fr = remember(c.id) { FocusRequester() }
            val ls = rememberLazyListState(maxOf(idx0, 0))
            val gs = rememberLazyGridState(maxOf(idx0, 0))
            LaunchedEffect(c.id) { if (idx0 >= 0) { delay(150); runCatching { fr.requestFocus() }; nav.lastId = null } }
            if (poster) {
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.weight(1f), state = gs, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(l) { i -> Poster(i, i.id in pins, { foc = it }, { menu(i.name, "pi_$tab", "hi_$tab", i.id, pins) }, fr = if (i.id == lid) fr else null) { open(i) } }
                }
            } else {
                LazyColumn(Modifier.weight(1f), state = ls, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(l) { i ->
                        val p = Repo.nowProg(i)
                        Tile((if (i.id in pins) "★ " else "") + Repo.label(i),
                            if (p != null) "${hm(p.s)}-${hm(p.e)}  ${p.t}" + (Repo.nextProg(i)?.let { "   ›  ${hm(it.s)} ${it.t}" } ?: "") else "",
                            onLong = { menu(i.name, "pi_$tab", "hi_$tab", i.id, pins) },
                            mod = Modifier.fillMaxWidth(), fr = if (i.id == lid) fr else null) { open(i, l) }
                    }
                }
            }
        } else {
            val pins = Repo.list("pc_$tab")
            val l = Repo.pinFirst(Repo.cats.filter { it.type == tab && it.id !in hc }, pins) { it.id }
            val rec = Repo.list("r_$tab").mapNotNull { Repo.byKey[it] }.filter { it.id !in hi }
            val cw = if (poster) Repo.cwList().filter { it.type == tab } else emptyList()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cw.isNotEmpty()) {
                    item { Text("Continue Watching", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(cw) { w ->
                                Poster(Item(w.url, w.name, "", w.type, w.url, "", w.icon), false, { },
                                    { dlg = Dlg(w.name, listOf("Remove from Continue Watching" to { Repo.removeCW(w.url) })) },
                                    Modifier.width(130.dp), progress = if (w.dur > 0) w.pos.toFloat() / w.dur else 0f) {
                                    onPlay(PlayReq(w.url, emptyList(), 0, w.name, w.icon, w.type))
                                }
                            }
                        }
                    }
                }
                if (rec.isNotEmpty()) {
                    item { Text("Recent", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(rec) { i ->
                                if (poster) Poster(i, false, { foc = it }, { menu(i.name, "pi_$tab", "hi_$tab", i.id, emptyList()) }, Modifier.width(130.dp)) { open(i) }
                                else Tile(Repo.label(i), Repo.nowProg(i)?.t ?: "", lines = 2, mod = Modifier.width(280.dp)) { open(i) }
                            }
                        }
                    }
                }
                item { Text("Categories", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                items(l) { k ->
                    Tile((if (k.id in pins) "★ " else "") + k.name, "${Repo.byCat["$tab|${k.id}"]?.size ?: 0}",
                        onLong = { menu(k.name, "pc_$tab", "hc_$tab", k.id, pins) },
                        mod = Modifier.fillMaxWidth()) { cat = k; foc = null }
                }
            }
        }
        if (poster && sr == null && foc != null && !searching) Info(foc)
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

@Composable fun Episodes(s: Item, onOpen: (Item) -> Unit) {
    var eps by remember { mutableStateOf<List<Item>?>(null) }
    LaunchedEffect(s) { eps = withContext(Dispatchers.IO) { runCatching { Repo.episodes(s) }.getOrDefault(emptyList()) } }
    val l = eps
    val g = MaterialTheme.colorScheme.onSurfaceVariant
    if (l == null) Text("Loading…", color = g)
    else if (l.isEmpty()) Text("No episodes found", color = g)
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(l) { e -> Tile(e.name, mod = Modifier.fillMaxWidth()) { onOpen(e) } } }
}

@Composable fun Settings(dark: Boolean, onTheme: () -> Unit, onLogout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var confirm by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(Repo.sp.getInt("aspect", 0)) }
    val t = Repo.tick
    val hidden = listOf("live", "movie", "series").sumOf { Repo.list("hc_$it").size + Repo.list("hi_$it").size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", color = cs.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Tile("Auto-organise: " + if (Repo.organise) "On" else "Off", "Hides ## placeholders, groups HD/FHD/UHD together, removes UK| / US: prefixes", mod = Modifier.fillMaxWidth()) { Repo.toggleOrganise() }
        Tile("Appearance: " + if (dark) "Dark" else "Light", "Tap to switch", mod = Modifier.fillMaxWidth()) { onTheme() }
        Tile("Default video size: " + listOf("Fit", "Fill", "Zoom")[aspect], "Fit keeps the picture shape, Fill stretches, Zoom crops to fill", mod = Modifier.fillMaxWidth()) {
            aspect = (aspect + 1) % 3; Repo.sp.edit().putInt("aspect", aspect).apply()
        }
        Tile("Faster live start: " + if (Repo.fastLive) "On" else "Off", "Uses direct streams instead of HLS where the server allows it. Turn off if a channel won't play", mod = Modifier.fillMaxWidth()) { Repo.toggleFast() }
        Tile("Sleep timer: " + (if (Repo.sleepHours == 0) "Off" else "${Repo.sleepHours}h"), "Stops playback after this long with no remote or touch input, so the screensaver can start", mod = Modifier.fillMaxWidth()) { Repo.cycleSleep() }
        Tile("Unhide everything", "$hidden hidden categories / channels", mod = Modifier.fillMaxWidth()) { Repo.clearHidden() }
        Tile("Switch playlist", "Logs out of ${Repo.account?.name ?: ""}", mod = Modifier.fillMaxWidth()) { confirm = true }
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
                .background(if (f) cs.primary else if (airing) cs.secondaryContainer else cs.surfaceVariant, shape)
                .clip(shape).combinedClickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            val c = if (f) cs.onPrimary else if (airing) cs.onSecondaryContainer else cs.onSurface
            Text(text, maxLines = 1, color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotEmpty()) Text(sub, maxLines = 1, color = c.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable fun Guide(nav: Nav, onPlay: (Item, List<Item>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val t = Repo.tick
    val hc = Repo.list("hc_live")
    val hi = Repo.list("hi_live")
    val cl = Repo.pinFirst(Repo.cats.filter { it.type == "live" && it.id !in hc }, Repo.list("pc_live")) { it.id }
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
    val nameW = 170.dp
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
                    Tile(Repo.label(ch), mod = Modifier.width(nameW).padding(end = 6.dp), fr = if (ch.id == lid) fr else null) { onPlay(ch, chans) }
                    Row(Modifier.weight(1f).horizontalScroll(sc)) {
                        var cur = gs
                        val ps = Repo.guideProgs(ch).filter { it.e > gs && it.s < ge }
                        if (ps.isEmpty()) Cell("No guide data", "", false, Modifier.width(300.dp).height(60.dp)) { onPlay(ch, chans) }
                        else ps.forEach { p ->
                            val st = maxOf(p.s, gs, cur); val en = minOf(p.e, ge)
                            if (en > st) {
                                if (st > cur) Spacer(Modifier.width(((st - cur) / 60000f * dpm).dp))
                                Cell(p.t, "${hm(p.s)}-${hm(p.e)}", p.s <= now && now < p.e, Modifier.width(((en - st) / 60000f * dpm).dp).height(60.dp)) { onPlay(ch, chans) }
                                cur = en
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable fun Player(req: PlayReq, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? ComponentActivity
    var mode by remember { mutableStateOf(Repo.sp.getInt("aspect", 0)) }
    var ctrl by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf(if (req.type == "live") "show" else "") }
    var cur by remember { mutableStateOf(req.url) }
    var idx by remember { mutableStateOf(req.idx) }
    val chan = if (req.list.isNotEmpty()) req.list[idx.coerceIn(0, req.list.size - 1)] else req.ch
    var pv by remember { mutableStateOf<PlayerView?>(null) }
    val fr = remember { FocusRequester() }
    val ex = remember {
        val live = req.type == "live"
        val http = DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0").setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000).setReadTimeoutMs(10000)
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
                override fun onPlayerError(error: PlaybackException) {
                    val u = p.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
                    if (u.endsWith(".ts") && "/live/" in u) { p.setMediaItem(MediaItem.fromUri(u.removeSuffix(".ts") + ".m3u8")); p.prepare() }
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
        Repo.onZap = { dir ->
            if (ctrl || menu || req.list.size < 2) false
            else {
                idx = (idx + dir + req.list.size) % req.list.size
                val ch = req.list[idx]
                cur = ch.url
                ex.setMediaItem(MediaItem.fromUri(Repo.playUrl(ch.url))); ex.prepare(); ex.playWhenReady = true
                banner = Repo.label(ch) + (Repo.nowProg(ch)?.let { "  •  ${it.t}" } ?: "")
                true
            }
        }
        onDispose {
            runCatching {
                val d = ex.duration
                if (req.list.isEmpty() && !ex.isCurrentMediaItemLive && d > 0) Repo.saveCW(cur, req.name, req.icon, req.type, ex.currentPosition, d)
            }
            w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Repo.onMenu = null; Repo.onZap = null; ex.release(); c?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(menu) { if (menu) runCatching { fr.requestFocus() } else pv?.requestFocus() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val h = Repo.sleepHours
            if (h > 0 && System.currentTimeMillis() - Repo.lastInput > h * 3600_000L) { onBack(); break }
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
                setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v -> ctrl = v == View.VISIBLE })
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
                    if (x != null) Text("Next  ${hm(x.s)}–${hm(x.e)}  ${x.t}", color = Color(0xFF9AA7BD), fontSize = 15.sp, maxLines = 1)
                }
            }
        }
    }
}
