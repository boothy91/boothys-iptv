package com.my.iptv

import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface

/** Tiny web server so a phone on the same Wi-Fi can send login details to the TV (uses NanoHTTPD). */
class PairServer(private val onData: (Map<String, String>) -> Unit) {
    private val token = (100000..999999).random().toString()
    private var srv: NanoHTTPD? = null

    fun start(): String {
        var port = 0
        for (p in listOf(8080, 8081, 8090, 0)) {
            val s = Web(p)
            try { s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true); srv = s; port = s.listeningPort; break } catch (_: Exception) { }
        }
        return "http://${ip()}:$port/$token"
    }

    fun stop() { runCatching { srv?.stop() } }

    private fun ip(): String {
        val ifs = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        for (i in ifs) {
            if (!i.isUp || i.isLoopback) continue
            for (a in i.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress) return a.hostAddress ?: continue
        }
        return "0.0.0.0"
    }

    private fun page(inner: String) =
        "<!doctype html><meta name=viewport content=\"width=device-width,initial-scale=1\"><body style=\"font-family:sans-serif;background:#0B0F1A;color:#fff;padding:20px;max-width:480px;margin:auto\"><h2>Boothys IPTV Player</h2>$inner"

    private val form = page(
        "<style>input:not([type=radio]){display:block;width:100%;box-sizing:border-box;padding:14px;margin:8px 0;border-radius:12px;border:0;font-size:16px}" +
            "button{width:100%;padding:14px;border:0;border-radius:24px;background:#2F6BFF;color:#fff;font-size:17px;margin-top:10px}label{margin-right:16px}</style>" +
            "<form method=post><p><label><input type=radio name=type value=x checked> Xtream Code</label><label><input type=radio name=type value=m> M3U8</label></p>" +
            "<input name=n placeholder=\"Playlist name\" autocapitalize=off><input name=s placeholder=\"Server or M3U8 URL\" autocapitalize=off autocorrect=off>" +
            "<input name=u placeholder=\"Username (Xtream)\" autocapitalize=off autocorrect=off><input name=p placeholder=\"Password (Xtream)\" autocapitalize=off autocorrect=off>" +
            "<input name=e placeholder=\"EPG URL (optional, M3U8)\" autocapitalize=off autocorrect=off><button>Send to TV</button></form>"
    )

    private inner class Web(port: Int) : NanoHTTPD(port) {
        override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
            val html = "text/html; charset=utf-8"
            if (session.uri != "/$token") return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
            if (session.method == NanoHTTPD.Method.POST) {
                val m: Map<String, String> = try { session.parseBody(HashMap<String, String>()); session.parms } catch (e: Exception) { emptyMap() }
                return if ((m["s"] ?: "").isBlank())
                    NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, html, page("<p>Server / URL is required. Go back and try again.</p>"))
                else { onData(m); NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, html, page("<h3>Sent!</h3><p>Check your TV.</p>")) }
            }
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, html, form)
        }
    }
}
