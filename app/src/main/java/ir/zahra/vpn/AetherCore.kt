package ir.zahra.vpn

import android.content.Context
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Runs the real Aether core (libaether.so from the official release) as a child process.
 * Aether opens its SOCKS5 port only after the tunnel has been verified end to end.
 */
class AetherCore(private val ctx: Context) {
    companion object {
        const val PORT = 1819
        private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    }

    private var proc: Process? = null
    private val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
    private val binary = File(nativeDir, "libaether.so")
    private val workDir = File(ctx.filesDir, "aether").apply { mkdirs() }

    fun available() = binary.exists()
    fun alive() = proc?.isAlive == true

    /** Psiphon/Tor helper binaries are shipped as libpt_N.so; recreate the pt/ layout with symlinks. */
    private fun linkTransports() {
        try {
            val lines = ctx.assets.open("pt_manifest.txt").bufferedReader().readLines()
            val root = File(workDir, "pt")
            root.deleteRecursively()
            for (l in lines) {
                val p = l.trim().split("=", limit = 2)
                if (p.size != 2) continue
                val target = File(nativeDir, p[0])
                if (!target.exists()) continue
                val link = File(root, p[1])
                link.parentFile?.mkdirs()
                try { Os.symlink(target.absolutePath, link.absolutePath) } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        }
    }

    fun start(profile: Profile, extra: List<String>) {
        stop()
        linkTransports()
        val cmd = ArrayList<String>()
        cmd.add(binary.absolutePath)
        cmd.addAll(listOf("--bind", "127.0.0.1:$PORT"))
        cmd.addAll(profile.args)
        cmd.addAll(extra)

        val pb = ProcessBuilder(cmd).directory(workDir).redirectErrorStream(true)
        pb.environment().apply {
            put("HOME", workDir.absolutePath)
            put("TMPDIR", ctx.cacheDir.absolutePath)
            put("SSL_CERT_DIR", "/system/etc/security/cacerts")
            putAll(profile.env)
        }
        VpnState.log("> aether " + cmd.drop(1).joinToString(" "))
        val p = pb.start()
        proc = p

        // Safety net: answer any prompt that is still left with its default.
        Thread {
            try {
                p.outputStream.write("\n\n\n\n\n\n".toByteArray())
                p.outputStream.flush()
            } catch (_: Exception) {
            }
        }.start()

        Thread {
            try {
                p.inputStream.bufferedReader().forEachLine { VpnState.log(ansi.replace(it, "")) }
            } catch (_: Exception) {
            }
            VpnState.log("(Aether process ended)")
        }.apply { isDaemon = true; start() }
    }

    suspend fun waitReady(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (!alive()) return@withContext false
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 300) }
                return@withContext true
            } catch (_: Exception) {
                delay(500)
            }
        }
        false
    }

    fun stop() {
        val p = proc ?: return
        proc = null
        try { p.destroy() } catch (_: Exception) {}
        try {
            if (!p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) p.destroyForcibly()
        } catch (_: Exception) {
        }
    }
}
