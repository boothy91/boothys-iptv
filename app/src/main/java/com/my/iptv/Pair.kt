package com.my.iptv

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/** Tiny web server so a phone on the same Wi-Fi can send login details to the TV. */
class PairServer(private val onData: (Map<String, String>) -> Unit) {
    private var ss: ServerSocket? = null
    private val token = (100000..999999).random().toString()
    @Volatile private var running = false

    fun start(): String {
        val s = runCatching { ServerSocket(8080) }.getOrElse { ServerSocket(0) }
        ss = s; running = true
        Thread { while (running) { try { val c = s.accept(); Thread { handle(c) }.start() } catch (_: Exception) { break } } }.start()
        return "http://${ip()}:${s.localPort}/$token"
    }

    fun stop() { running = false; runCatching { ss?.close() } }

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

    private fun handle(c: Socket) {
        runCatching {
            c.use { sock ->
                sock.soTimeout = 5000
                val r = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1))
                val first = r.readLine() ?: return
                val parts = first.split(' ')
                var len = 0
                while (true) {
                    val h = r.readLine() ?: break
                    if (h.isEmpty()) break
                    if (h.startsWith("content-length:", true)) len = h.substring(15).trim().toIntOrNull() ?: 0
                }
                val buf = CharArray(if (len in 1..8192) len else 0)
                var n = 0
                while (n < buf.size) { val k = r.read(buf, n, buf.size - n); if (k < 0) break; n += k }
                val body = String(buf, 0, n)
                val path = parts.getOrElse(1) { "/" }.substringBefore('?')
                val out = sock.getOutputStream()
                fun send(code: String, html: String) {
                    val b = html.toByteArray(Charsets.UTF_8)
                    out.write("HTTP/1.1 $code\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: ${b.size}\r\n\r\n".toByteArray())
                    out.write(b); out.flush()
                }
                if (path != "/$token") { send("404 Not Found", "Not found"); return }
                if (parts[0] == "POST") {
                    val m = body.split('&').mapNotNull { kv ->
                        val i = kv.indexOf('=')
                        if (i < 0) null else URLDecoder.decode(kv.substring(0, i), "UTF-8") to URLDecoder.decode(kv.substring(i + 1), "UTF-8")
                    }.toMap()
                    if ((m["s"] ?: "").isBlank()) send("400 Bad Request", page("<p>Server / URL is required. Go back and try again.</p>"))
                    else { onData(m); send("200 OK", page("<h3>Sent!</h3><p>Check your TV.</p>")) }
                } else send("200 OK", form)
            }
        }
    }
}
