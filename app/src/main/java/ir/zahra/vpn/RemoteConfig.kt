package ir.zahra.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** "Admin panel": the owner edits servers.json on GitHub, the app pulls it. */
object RemoteConfig {
    suspend fun fetch(prefs: Prefs, viaTunnel: Boolean): String = withContext(Dispatchers.IO) {
        val url = prefs.remoteUrl.trim()
        if (!url.startsWith("https://")) return@withContext "آدرس باید با https:// شروع شود"
        try {
            val u = URL(url)
            val c = (if (viaTunnel) u.openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", AetherCore.PORT)))
            else u.openConnection()) as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            val text = c.inputStream.bufferedReader().readText()
            c.disconnect()
            val parsed = ProfileStore.parse(text) ?: return@withContext "فایل JSON معتبر نیست"
            prefs.remoteJson = text
            "✅ ${parsed.profiles.size} سرور دریافت شد"
        } catch (e: Exception) {
            "❌ دریافت نشد: ${e.message}"
        }
    }
}
