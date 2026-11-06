package ir.zahra.vpn.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.zahra.vpn.Profile
import ir.zahra.vpn.Status
import ir.zahra.vpn.UiState
import kotlinx.coroutines.delay

private fun fmtSpeed(b: Long): String = when {
    b >= 1_048_576 -> String.format("%.1f MB/s", b / 1_048_576.0)
    b >= 1024 -> String.format("%.0f KB/s", b / 1024.0)
    else -> "$b B/s"
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
fun HomeScreen(
    profiles: List<Profile>,
    ui: UiState,
    selectedId: String,
    scope: String,
    instagramInstalled: Boolean,
    testText: String?,
    testing: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    onScope: (String) -> Unit,
    onTest: () -> Unit,
    onAdmin: () -> Unit,
) {
    val on = ui.status == Status.CONNECTED
    val busy = ui.status == Status.CONNECTING
    val locked = on || busy
    var taps by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, Bg)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ---- header (tap the title 5 times to open the admin panel)
        Column(
            Modifier
                .fillMaxWidth()
                .clickable {
                    taps++
                    if (taps >= 5) { taps = 0; onAdmin() }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("زهرا VPN", color = Emerald, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("اینستاگرام آزاد، امن و سریع", color = Muted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))

        // ---- scope selector
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .padding(4.dp),
        ) {
            ScopeTab("فقط اینستاگرام", scope == "instagram", !locked, Modifier.weight(1f)) { onScope("instagram") }
            ScopeTab("کل گوشی", scope == "all", !locked, Modifier.weight(1f)) { onScope("all") }
        }
        if (scope == "instagram" && !instagramInstalled) {
            Spacer(Modifier.height(6.dp))
            Text("⚠️ اینستاگرام روی این گوشی پیدا نشد", color = Amber, fontSize = 12.sp)
        }

        Spacer(Modifier.height(22.dp))

        ConnectButton(on = on, busy = busy, error = ui.status == Status.ERROR, onClick = onToggle)

        Spacer(Modifier.height(12.dp))

        val title = when (ui.status) {
            Status.CONNECTED -> "متصل شدید"
            Status.CONNECTING -> "در حال اتصال…"
            Status.ERROR -> "اتصال ناموفق"
            Status.IDLE -> "قطع است"
        }
        Text(
            title,
            color = when (ui.status) {
                Status.CONNECTED -> Emerald
                Status.CONNECTING -> Amber
                Status.ERROR -> Danger
                Status.IDLE -> Color.White
            },
            fontSize = 20.sp, fontWeight = FontWeight.Bold,
        )
        if (ui.message.isNotBlank() && ui.status != Status.CONNECTED) {
            Text(ui.message, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        if (on) {
            val now by produceState(System.currentTimeMillis()) {
                while (true) { value = System.currentTimeMillis(); delay(1000) }
            }
            Text(ui.profileName, color = Muted, fontSize = 13.sp)
            Text(fmtTime(now - ui.connectedAt), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("⬇ ${fmtSpeed(ui.rxSpeed)}", color = Emerald, fontSize = 14.sp)
                Text("⬆ ${fmtSpeed(ui.txSpeed)}", color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSel)
                    .clickable(enabled = !testing, onClick = onTest)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(if (testing) "در حال تست…" else "🔎 تست اتصال واقعی", color = Emerald, fontSize = 13.sp)
            }
            if (testText != null) {
                Spacer(Modifier.height(8.dp))
                Text(testText, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "انتخاب سرور", color = Muted, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        profiles.forEach { p ->
            ProfileCard(p, p.id == selectedId, !locked) { onSelect(p.id) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "ساخته شده با ❤ توسط فاطمه زهرا ساعی",
            color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Text("هسته: Aether (CluvexStudio) • AGPL-3.0", color = Muted.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

@Composable
private fun ScopeTab(text: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Emerald else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.Black else Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontWeight = FontWeight.Bold, fontSize = 14.sp,
        )
    }
}

@Composable
private fun ProfileCard(p: Profile, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) CardSel else Card)
            .border(1.5.dp, if (selected) Emerald else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(p.flag, fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(p.desc, color = Muted, fontSize = 12.sp)
        }
        if (p.experimental) {
            Text("آزمایشی", color = Amber, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) Emerald else Muted, CircleShape)
                .background(if (selected) Emerald else Color.Transparent),
        )
    }
}

@Composable
private fun ConnectButton(on: Boolean, busy: Boolean, error: Boolean, onClick: () -> Unit) {
    val accent = when {
        on -> Emerald
        busy -> Amber
        error -> Danger
        else -> Muted
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.88f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p",
    )
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            val k = if (on || busy) pulse else 1f
            drawCircle(accent.copy(alpha = 0.08f), radius = r * k)
            drawCircle(accent.copy(alpha = 0.16f), radius = r * 0.82f * k)
        }
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        if (on) listOf(Emerald, EmeraldDeep)
                        else listOf(Color(0xFF1B3A2B), Color(0xFF0E2218))
                    )
                )
                .border(3.dp, accent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = Amber, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            } else {
                Canvas(Modifier.size(60.dp)) {
                    val c = if (on) Color.Black else accent
                    val w = size.minDimension * 0.11f
                    val inset = w
                    drawArc(
                        color = c, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                        topLeft = Offset(inset, inset + size.height * 0.05f),
                        size = Size(size.width - 2 * inset, size.height - 2 * inset - size.height * 0.05f),
                        style = Stroke(width = w, cap = StrokeCap.Round),
                    )
                    drawLine(
                        c, Offset(size.width / 2, inset * 0.6f), Offset(size.width / 2, size.height * 0.5f),
                        strokeWidth = w, cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
