package ir.zahra.vpn

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("zahra", Context.MODE_PRIVATE)

    var selected: String
        get() = sp.getString("selected", ProfileStore.AUTO) ?: ProfileStore.AUTO
        set(v) = sp.edit().putString("selected", v).apply()

    /** "instagram" = only Instagram goes through the VPN, "all" = whole phone. */
    var scope: String
        get() = sp.getString("scope", "instagram") ?: "instagram"
        set(v) = sp.edit().putString("scope", v).apply()

    var pin: String
        get() = sp.getString("pin", "1234") ?: "1234"
        set(v) = sp.edit().putString("pin", v).apply()

    var remoteUrl: String
        get() = sp.getString("remoteUrl", "") ?: ""
        set(v) = sp.edit().putString("remoteUrl", v).apply()

    var remoteJson: String
        get() = sp.getString("remoteJson", "") ?: ""
        set(v) = sp.edit().putString("remoteJson", v).apply()

    var extraArgs: String
        get() = sp.getString("extraArgs", "") ?: ""
        set(v) = sp.edit().putString("extraArgs", v).apply()
}
