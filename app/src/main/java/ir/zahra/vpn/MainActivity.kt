package ir.zahra.vpn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ir.zahra.vpn.ui.AdminScreen
import ir.zahra.vpn.ui.HomeScreen
import ir.zahra.vpn.ui.ZahraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) startVpn()
            else Toast.makeText(this, "برای اتصال باید مجوز VPN را بدهید", Toast.LENGTH_LONG).show()
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            ZahraTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val ui by VpnState.state.collectAsState()
                    var profiles by remember { mutableStateOf(ProfileStore.load(this@MainActivity)) }
                    var selected by remember { mutableStateOf(prefs.selected) }
                    var scopeMode by remember { mutableStateOf(prefs.scope) }
                    var admin by remember { mutableStateOf(false) }
                    var testText by remember { mutableStateOf<String?>(null) }
                    var testing by remember { mutableStateOf(false) }
                    val coScope = rememberCoroutineScope()

                    BackHandler(enabled = admin) { admin = false }

                    if (admin) {
                        AdminScreen(
                            prefs = prefs,
                            onClose = { admin = false },
                            onProfilesChanged = { profiles = ProfileStore.load(this@MainActivity) },
                        )
                    } else {
                        HomeScreen(
                            profiles = profiles,
                            ui = ui,
                            selectedId = selected,
                            scope = scopeMode,
                            instagramInstalled = instagramInstalled(),
                            testText = testText,
                            testing = testing,
                            onToggle = { toggle(ui.status) },
                            onSelect = { selected = it; prefs.selected = it },
                            onScope = { scopeMode = it; prefs.scope = it },
                            onTest = {
                                coScope.launch {
                                    testing = true
                                    testText = ConnTest.run()
                                    testing = false
                                }
                            },
                            onAdmin = { admin = true },
                        )
                    }
                }
            }
        }

        // Refresh the server list from GitHub in the background (if the admin set a URL).
        lifecycleScope.launch {
            if (prefs.remoteUrl.isNotBlank()) {
                withContext(Dispatchers.IO) { RemoteConfig.fetch(prefs, VpnState.state.value.status == Status.CONNECTED) }
            }
        }
    }

    private fun instagramInstalled(): Boolean =
        ProfileStore.instagramPackages(this).any {
            try { packageManager.getPackageInfo(it, 0); true } catch (_: Exception) { false }
        }

    private fun toggle(status: Status) {
        if (status == Status.CONNECTING || status == Status.CONNECTED) {
            startService(Intent(this, ZahraVpnService::class.java).setAction(ZahraVpnService.ACTION_STOP))
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpn()
    }

    private fun startVpn() {
        ContextCompat.startForegroundService(
            this, Intent(this, ZahraVpnService::class.java).setAction(ZahraVpnService.ACTION_START)
        )
    }
}
