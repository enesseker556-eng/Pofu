package com.pofu.rider.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pofu.rider.core.Prefs
import com.pofu.rider.voice.Phase
import com.pofu.rider.voice.VoiceService

/** Ekran kenari ile icerik arasindaki bosluk; kapak bundan muaf, kenardan kenara. */
private val SIDE = 20.dp

/**
 * Ana ekran. Bilerek sade: surum numarasi, depo adresi, anahtar alani gibi
 * hicbir teknik ayrinti gosterilmiyor. Hepsi kodda sabit tutuluyor ki
 * yanlislikla bozulmasin.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContent { PofuTheme { HomeScreen() } }
    }
}

@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Ayarlar ekranindan donunce izin durumu degismis olabilir; her ON_RESUME'da tazele.
    var refreshKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val perms = remember(refreshKey) { Permissions.status(ctx) }
    val canStart = perms.filter { it.blocking }.all { it.granted }
    val missing = perms.filter { !it.granted && it.important }
    val state by VoiceService.state.collectAsStateWithLifecycle()
    val running = state.phase != Phase.STOPPED

    val requestPerms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    var tts by remember { mutableStateOf(Prefs.speakFeedback) }
    var directCall by remember { mutableStateOf(Prefs.directCall) }
    var autoStart by remember { mutableStateOf(Prefs.autoStart) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { PandaHeader(listening = state.phase == Phase.LISTENING) }

        item { Box(Modifier.padding(horizontal = SIDE)) { UpdateCard(refreshKey) } }

        // ---- baslat / durdur
        item {
            Button(
                onClick = { if (running) VoiceService.stop(ctx) else VoiceService.start(ctx) },
                enabled = canStart || running,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SIDE)
                    .height(66.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) Bad else Purple,
                    contentColor = Ink,
                    disabledContainerColor = Surface2,
                    disabledContentColor = TextLo
                )
            ) {
                Text(
                    if (running) "DURDUR" else "BAŞLAT",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
            }
        }

        // ---- tek satirlik, sade durum
        item {
            Box(Modifier.padding(horizontal = SIDE)) {
                StatusLine(state.phase, state.error)
            }
        }

        // ---- eksik izinler (varsa)
        if (missing.isNotEmpty()) {
            item {
                PofuCard {
                    Text(
                        if (canStart) "Şunlara da izin vermen gerekiyor:"
                        else "Başlamak için izin gerekiyor:",
                        color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            items(missing) { p ->
                PermissionRow(p) {
                    when (p.key) {
                        "overlay" -> ctx.startActivity(Permissions.overlayIntent(ctx))
                        "battery" -> ctx.startActivity(Permissions.batteryIntent(ctx))
                        else -> requestPerms.launch(arrayOf(p.key))
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(ctx, RideActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE).height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Sürüş ekranını aç", fontSize = 15.sp, color = Purple)
            }
        }

        item { SectionTitle("Komutlar") }
        item { PofuCard { CommandHelp() } }

        item { SectionTitle("Tercihler") }
        item {
            PofuCard {
                ToggleRow("Sesli cevap", "Komutu anlayınca kulağına söyler.", tts) {
                    tts = it; Prefs.speakFeedback = it
                }
                ToggleRow(
                    "Doğrudan ara",
                    "Kapalıysa numarayı ekrana yazar, tuşa sen basarsın.",
                    directCall
                ) { directCall = it; Prefs.directCall = it }
                ToggleRow(
                    "Telefon açılınca başlat",
                    "Yeniden başlatmadan sonra kendi gelir.",
                    autoStart
                ) { autoStart = it; Prefs.autoStart = it }
            }
        }
    }
}

/** Ne olup bittigini tek cumlede, teknik terim kullanmadan soyler. */
@Composable
private fun StatusLine(phase: Phase, error: String?) {
    val pulse = rememberInfiniteTransition(label = "durum")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "yanip sonme"
    )
    val live = phase == Phase.LISTENING || phase == Phase.STARTING

    val dot: androidx.compose.ui.graphics.Color
    val text: String
    when {
        error != null -> { dot = Bad; text = error }
        phase == Phase.STOPPED -> { dot = TextLo; text = "Kapalı" }
        phase == Phase.STARTING -> { dot = Purple; text = "Hazırlanıyor..." }
        phase == Phase.LISTENING -> { dot = Good; text = "Dinliyorum" }
        phase == Phase.WORKING -> { dot = Purple; text = "Bir saniye..." }
        phase == Phase.SPEAKING -> { dot = Purple; text = "Tamam" }
        else -> { dot = Good; text = "Hazır — \"Hey Panda\" de" }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .scale(if (live) alpha else 1f)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (error != null) Bad else TextHi, fontSize = 14.sp)
    }
}

@Composable
private fun CommandHelp() {
    val rows = listOf(
        "Ahmet'i ara" to "Rehberden bulur, arar",
        "sonraki" to "Sonraki şarkı",
        "önceki" to "Önceki şarkı",
        "çal / durdur" to "Müziği başlatır, duraklatır",
        "sesi aç / kıs" to "Ses seviyesi",
        "Kadıköy'e git" to "Navigasyon başlatır",
        "iptal" to "Vazgeçer"
    )
    rows.forEach { (cmd, desc) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(
                "“$cmd”",
                color = Purple, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(148.dp)
            )
            Text(desc, color = TextLo, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = TextLo,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = SIDE, end = SIDE, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun PofuCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun PermissionRow(item: PermissionItem, onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.label, color = TextHi,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(item.why, color = TextLo, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onFix,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Surface2, contentColor = Purple
                )
            ) { Text("İzin ver", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 15.sp)
            Text(sub, color = TextLo, fontSize = 12.sp)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}
