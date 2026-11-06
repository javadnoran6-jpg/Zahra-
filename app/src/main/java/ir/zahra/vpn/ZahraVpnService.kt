package ir.zahra.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import hev.htproxy.TProxyService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * apps -> TUN (this VpnService) -> hev-socks5-tunnel -> SOCKS5 127.0.0.1:1819 -> Aether -> internet
 */
class ZahraVpnService : VpnService() {
    companion object {
        const val ACTION_START = "ir.zahra.vpn.START"
        const val ACTION_STOP = "ir.zahra.vpn.STOP"
        private const val CHANNEL = "zahra_vpn"
        private const val NOTIF_ID = 7
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var core: AetherCore? = null
    private var tun: ParcelFileDescriptor? = null
    private var hevRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown(UiState(Status.IDLE))
            return START_NOT_STICKY
        }
        startForegroundCompat("در حال اتصال…")
        job?.cancel()
        job = scope.launch { connect() }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        shutdown(UiState(Status.IDLE))
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- connect flow

    private suspend fun connect() {
        val prefs = Prefs(this)
        val all = ProfileStore.load(this)
        val chain = if (prefs.selected == ProfileStore.AUTO) ProfileStore.autoChain(this, all)
        else listOfNotNull(all.find { it.id == prefs.selected })
        if (chain.isEmpty()) return fail("سروری برای اتصال انتخاب نشده")

        VpnState.state.value = UiState(Status.CONNECTING, "در حال راه‌اندازی هسته…")
        val c = AetherCore(this).also { core = it }
        if (!c.available()) return fail("هسته‌ی Aether داخل APK نیست (ABI گوشی پشتیبانی نمی‌شود؟)")

        val extra = prefs.extraArgs.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var used: Profile? = null
        for (p in chain) {
            if (!scope.isActive) return
            VpnState.state.value = UiState(Status.CONNECTING, "در حال اتصال با ${p.name} …", p.name)
            try {
                c.start(p, extra)
            } catch (e: Exception) {
                VpnState.log("خطا در اجرای هسته: ${e.message}")
                continue
            }
            if (c.waitReady(p.timeoutMs)) { used = p; break }
            VpnState.log("✗ ${p.name} وصل نشد")
            c.stop()
        }
        if (used == null) return fail("هیچ مسیری وصل نشد. روش دیگری انتخاب کنید یا لاگ را ببینید.")

        val err = startTun(prefs)
        if (err != null) return fail(err)

        val since = System.currentTimeMillis()
        VpnState.state.value = UiState(Status.CONNECTED, "متصل", used.name, since)
        startForegroundCompat("متصل — ${used.name}")

        var lastRx = 0L
        var lastTx = 0L
        while (scope.isActive) {
            delay(1000)
            if (!c.alive()) return fail("هسته‌ی Aether متوقف شد")
            val st = try { TProxyService.TProxyGetStats() } catch (_: Throwable) { null }
            val tx = st?.getOrNull(1) ?: 0L
            val rx = st?.getOrNull(3) ?: 0L
            VpnState.state.value = VpnState.state.value.copy(
                rxBytes = rx, txBytes = tx,
                rxSpeed = (rx - lastRx).coerceAtLeast(0), txSpeed = (tx - lastTx).coerceAtLeast(0),
            )
            lastRx = rx; lastTx = tx
        }
    }

    /** Returns an error message or null on success. */
    private fun startTun(prefs: Prefs): String? {
        val b = Builder()
            .setSession("ZahraVPN")
            .setMtu(8500)
            .addAddress("198.18.0.1", 32)
            .addAddress("fc00::1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addDnsServer("2606:4700:4700::1111")

        if (prefs.scope == "instagram") {
            var added = 0
            for (pkg in ProfileStore.instagramPackages(this)) {
                try { b.addAllowedApplication(pkg); added++ } catch (_: PackageManager.NameNotFoundException) {}
            }
            if (added == 0) return "اینستاگرام روی گوشی نصب نیست"
        } else {
            try { b.addDisallowedApplication(packageName) } catch (_: Exception) {}
        }

        val fd = try { b.establish() } catch (e: Exception) { null } ?: return "ساخت VPN انجام نشد (مجوز داده نشده؟)"
        tun = fd

        val cfg = File(filesDir, "hev.yml")
        cfg.writeText(
            """
            tunnel:
              mtu: 8500
              ipv4: 198.18.0.1
              ipv6: 'fc00::1'
            socks5:
              port: ${AetherCore.PORT}
              address: 127.0.0.1
              udp: 'udp'
            misc:
              task-stack-size: 81920
              tcp-buffer-size: 65536
              connect-timeout: 10000
              read-write-timeout: 60000
              log-file: stderr
              log-level: warn
            """.trimIndent()
        )
        return try {
            TProxyService.TProxyStartService(cfg.absolutePath, fd.fd)
            hevRunning = true
            null
        } catch (e: Throwable) {
            "موتور تونل (hev) بارگذاری نشد: ${e.message}"
        }
    }

    // ---------------------------------------------------------------- shutdown

    private fun fail(msg: String) {
        VpnState.log("ERROR: $msg")
        shutdown(UiState(Status.ERROR, msg))
    }

    private fun teardown() {
        if (hevRunning) {
            try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
            hevRunning = false
        }
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        core?.stop()
        core = null
    }

    private fun shutdown(result: UiState) {
        job?.cancel()
        teardown()
        VpnState.state.value = result
        stopForegroundCompat()
        stopSelf()
    }

    // ---------------------------------------------------------------- notification

    private fun startForegroundCompat(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "اتصال VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ZahraVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("زهرا VPN")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "قطع", stop)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            VpnState.log("foreground: ${e.message}")
        }
    }

    private fun stopForegroundCompat() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }
}
