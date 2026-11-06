package ir.zahra.vpn

import android.content.Context
import org.json.JSONObject

data class Profile(
    val id: String,
    val name: String,
    val flag: String,
    val desc: String,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 45_000L,
    val experimental: Boolean = false,
)

data class RemoteData(
    val profiles: List<Profile>,
    val instagramPackages: List<String>?,
    val autoChain: List<String>?,
)

object ProfileStore {
    const val AUTO = "auto"

    val defaultInstagram = listOf("com.instagram.android", "com.instagram.lite")
    val defaultChain = listOf("masque_h3", "masque_h2", "masque_h2_frag", "gool", "wireguard")

    val autoProfile = Profile(
        id = AUTO, name = "هوشمند (پیشنهادی)", flag = "🟢",
        desc = "خودکار بهترین روش را برای اپراتور شما پیدا می‌کند",
        args = emptyList(),
    )

    val builtin: List<Profile> = listOf(
        Profile(
            "masque_h3", "مسیر سریع (HTTP/3)", "⚡",
            "MASQUE روی QUIC — سریع‌ترین، اگر UDP باز باشد",
            listOf("--masque", "-4", "--scan", "turbo", "--noize", "firewall", "--quick-reconnect"),
        ),
        Profile(
            "masque_h2", "مسیر امن (HTTP/2)", "🛡️",
            "MASQUE روی TCP — وقتی اپراتور UDP را کند می‌کند",
            listOf("--masque", "--h2", "-4", "--scan", "turbo", "--noize", "firewall", "--quick-reconnect"),
        ),
        Profile(
            "masque_h2_frag", "مسیر مخفی (H2 + Fragment)", "🕶️",
            "برای شبکه‌هایی که هندشیک TLS را می‌بندند",
            listOf("--masque", "--h2", "--fragment", "-4", "--scan", "turbo", "--noize", "firewall", "--quick-reconnect"),
        ),
        Profile(
            "gool", "دو لایه (Gool)", "🧅",
            "WARP داخل WARP — کندتر ولی عبور بهتر",
            listOf("--gool", "--wiw-scan", "-4", "--scan", "turbo", "--noize", "balanced", "--quick-reconnect"),
            timeoutMs = 60_000L,
        ),
        Profile(
            "wireguard", "وایرگارد", "🚀",
            "سبک و سریع، برای شبکه‌های کم‌سخت‌گیر",
            listOf("--wg", "-4", "--scan", "turbo", "--noize", "balanced", "--quick-reconnect"),
        ),
        Profile(
            "psiphon_de", "آلمان (Psiphon)", "🇩🇪",
            "خروجی آلمان — آزمایشی",
            listOf("--psiphon-only", "--psiphon-region", "DE"),
            timeoutMs = 90_000L, experimental = true,
        ),
        Profile(
            "psiphon_us", "آمریکا (Psiphon)", "🇺🇸",
            "خروجی آمریکا — آزمایشی",
            listOf("--psiphon-only", "--psiphon-region", "US"),
            timeoutMs = 90_000L, experimental = true,
        ),
    )

    // Flags that a remote servers.json is allowed to use. Anything else (e.g. --upstream,
    // --bind, --routes) is rejected so a hijacked config URL cannot redirect your traffic.
    private val allowed = setOf(
        "--masque", "--wg", "--warp", "--gool", "--gool-classic", "--psiphon", "--psiphon-only",
        "--psiphon-reverse", "--psiphon-region", "--psiphon-mode", "-4", "-6", "--dual", "--scan",
        "--turbo", "--balanced", "--thorough", "--stealth", "--ironclad", "--noize", "--h2", "--http2",
        "--fragment", "--fragment-size", "--fragment-delay", "--quick-reconnect", "--no-quick-reconnect",
        "--wiw-scan", "--keepalive", "--dns", "--log-level", "--stats", "--exit-loc", "--mim",
    )

    private fun safe(args: List<String>) = args.all { !it.startsWith("-") || it in allowed }

    fun parse(json: String): RemoteData? = try {
        val root = JSONObject(json)
        val list = ArrayList<Profile>()
        val arr = root.optJSONArray("profiles")
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val a = o.getJSONArray("args")
            val args = (0 until a.length()).map { a.getString(it) }
            if (!safe(args)) continue
            list.add(
                Profile(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    flag = o.optString("flag", "🌐"),
                    desc = o.optString("desc", ""),
                    args = args,
                    timeoutMs = o.optLong("timeout_ms", 60_000L),
                    experimental = o.optBoolean("experimental", false),
                )
            )
        }
        fun strings(key: String): List<String>? =
            root.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }
        RemoteData(list, strings("instagram_packages"), strings("auto_chain"))
    } catch (e: Exception) {
        null
    }

    private fun remote(ctx: Context): RemoteData? =
        Prefs(ctx).remoteJson.takeIf { it.isNotBlank() }?.let { parse(it) }

    fun load(ctx: Context): List<Profile> {
        val r = remote(ctx)?.profiles ?: emptyList()
        val ids = r.map { it.id }.toSet()
        return listOf(autoProfile) + builtin.filter { it.id !in ids } + r
    }

    fun instagramPackages(ctx: Context): List<String> =
        remote(ctx)?.instagramPackages?.takeIf { it.isNotEmpty() } ?: defaultInstagram

    fun autoChain(ctx: Context, all: List<Profile>): List<Profile> {
        val ids = remote(ctx)?.autoChain?.takeIf { it.isNotEmpty() } ?: defaultChain
        return ids.mapNotNull { id -> all.find { it.id == id } }
    }
}
