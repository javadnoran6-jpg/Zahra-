package ir.zahra.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object ConnTest {
    private val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", AetherCore.PORT))

    private fun get(url: String, viaProxy: Boolean): Pair<Int, String> {
        val c = (if (viaProxy) URL(url).openConnection(proxy) else URL(url).openConnection()) as HttpURLConnection
        c.connectTimeout = 12_000
        c.readTimeout = 12_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        return try {
            val code = c.responseCode
            val body = try { c.inputStream.bufferedReader().readText().take(2000) } catch (_: Exception) { "" }
            code to body
        } finally {
            c.disconnect()
        }
    }

    /** Goes through the local Aether SOCKS5 port (the same path the VPN uses). */
    suspend fun run(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        try {
            val t0 = System.currentTimeMillis()
            val (_, body) = get("https://www.cloudflare.com/cdn-cgi/trace", true)
            val ms = System.currentTimeMillis() - t0
            val kv = body.lines().mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            sb.append("✅ تونل کار می‌کند (${ms}ms)\n")
            sb.append("IP خروجی: ${kv["ip"] ?: "?"} | کشور: ${kv["loc"] ?: "?"}")
            if (kv["warp"] != null) sb.append(" | warp=${kv["warp"]}")
        } catch (e: Exception) {
            return@withContext "❌ تونل جواب نداد: ${e.message}"
        }
        try {
            val t0 = System.currentTimeMillis()
            val (code, _) = get("https://www.instagram.com/", true)
            val ms = System.currentTimeMillis() - t0
            sb.append(if (code in 200..399) "\n✅ اینستاگرام در دسترس است ($code، ${ms}ms)" else "\n⚠️ اینستاگرام کد $code داد")
        } catch (e: Exception) {
            sb.append("\n❌ اینستاگرام باز نشد: ${e.message}")
        }
        sb.toString()
    }
}
