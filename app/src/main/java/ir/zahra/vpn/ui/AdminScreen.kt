package ir.zahra.vpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.zahra.vpn.Prefs
import ir.zahra.vpn.RemoteConfig
import ir.zahra.vpn.Status
import ir.zahra.vpn.VpnState
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(prefs: Prefs, onClose: () -> Unit, onProfilesChanged: () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(prefs.remoteUrl) }
    var extra by remember { mutableStateOf(prefs.extraArgs) }
    var newPin by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }
    val logs by VpnState.logs.collectAsState()
    val ui by VpnState.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("پنل مدیریت", color = Emerald, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

        if (!unlocked) {
            OutlinedTextField(
                value = pinInput, onValueChange = { pinInput = it },
                label = { Text("رمز مدیر") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                if (pinInput == prefs.pin) unlocked = true else info = "رمز اشتباه است"
            }) { Text("ورود") }
            if (info.isNotBlank()) Text(info, color = Danger)
            OutlinedButton(onClick = onClose) { Text("بازگشت") }
            return@Column
        }

        Text("لیست سرورها از فایل servers.json روی GitHub خوانده می‌شود (نمونه داخل پروژه).", color = Muted, fontSize = 12.sp)
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("آدرس servers.json (https)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                prefs.remoteUrl = url.trim()
                scope.launch {
                    info = "در حال دریافت…"
                    info = RemoteConfig.fetch(prefs, ui.status == Status.CONNECTED)
                    onProfilesChanged()
                }
            }) { Text("ذخیره و دریافت سرورها") }
            OutlinedButton(onClick = {
                prefs.remoteJson = ""; onProfilesChanged(); info = "به لیست پیش‌فرض برگشت"
            }) { Text("بازنشانی") }
        }

        OutlinedTextField(
            value = extra, onValueChange = { extra = it },
            label = { Text("پارامتر اضافه برای هسته (اختیاری)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { prefs.extraArgs = extra.trim(); info = "ذخیره شد" }) { Text("ذخیره پارامتر") }

        OutlinedTextField(
            value = newPin, onValueChange = { newPin = it },
            label = { Text("رمز جدید مدیر") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            if (newPin.length >= 4) { prefs.pin = newPin; newPin = ""; info = "رمز عوض شد" } else info = "حداقل ۴ کاراکتر"
        }) { Text("تغییر رمز") }

        if (info.isNotBlank()) Text(info, color = Emerald, fontSize = 13.sp)

        Text("لاگ هسته", color = Muted, fontSize = 13.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .verticalScroll(rememberScrollState(Int.MAX_VALUE))
                .padding(8.dp),
        ) {
            Text(
                logs.joinToString("\n").ifBlank { "—" },
                color = Color(0xFF9EE6C2), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            )
        }
        OutlinedButton(onClick = { clip.setText(AnnotatedString(logs.joinToString("\n"))) }) { Text("کپی لاگ") }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClose) { Text("بازگشت") }
    }
}
