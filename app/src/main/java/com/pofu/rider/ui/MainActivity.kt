package com.pofu.rider.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.pofu.rider.core.Prefs
import com.pofu.rider.voice.VoiceService

/** Kurulum ve ayar ekrani. Motordayken degil, garajda kullanilir. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContent { PofuTheme { SetupScreen() } }
    }
}

@Composable
private fun SetupScreen() {
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
    val allRequiredGranted = perms.filter { it.required }.all { it.granted }
    val running by VoiceService.state.collectAsStateWithLifecycle()

    val requestPerms = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshKey++ }

    var accessKey by remember { mutableStateOf(Prefs.accessKey) }
    var sensitivity by remember { mutableStateOf(Prefs.sensitivity) }
    var btMic by remember { mutableStateOf(Prefs.useBluetoothMic) }
    var tts by remember { mutableStateOf(Prefs.speakFeedback) }
    var directCall by remember { mutableStateOf(Prefs.directCall) }
    var autoStart by remember { mutableStateOf(Prefs.autoStart) }
    var autoUpdate by remember { mutableStateOf(Prefs.autoUpdate) }
    var updateRepo by remember { mutableStateOf(Prefs.updateRepo) }
    var ghToken by remember { mutableStateOf(Prefs.githubToken) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("POFU", color = Orange, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text(
                    "Motorcu sesli asistani",
                    color = TextLo, fontSize = 14.sp
                )
            }
        }

        // ---- baslat / durdur
        item {
            val isOn = running.phase != com.pofu.rider.voice.Phase.STOPPED
            Button(
                onClick = {
                    if (isOn) VoiceService.stop(ctx) else VoiceService.start(ctx)
                },
                enabled = allRequiredGranted || isOn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOn) Bad else Orange,
                    contentColor = Ink
                )
            ) {
                Text(
                    if (isOn) "SÜRÜŞ MODUNU DURDUR" else "SÜRÜŞ MODUNU BAŞLAT",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
            }
            if (!allRequiredGranted) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Önce aşağıdaki zorunlu izinleri ver.",
                    color = Bad, fontSize = 13.sp
                )
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Sürüş ekranını aç", fontSize = 15.sp)
            }
        }

        // ---- guncelleme
        item { SectionTitle("Güncelleme") }
        item { UpdateCard(refreshKey) }
        item {
            PofuCard {
                ToggleRow(
                    "Açılışta güncelleme ara",
                    "Uygulamayı her açtığında GitHub'daki son sürüme bakar.",
                    autoUpdate
                ) { autoUpdate = it; Prefs.autoUpdate = it }
                OutlinedTextField(
                    value = updateRepo,
                    onValueChange = { updateRepo = it; Prefs.updateRepo = it },
                    label = { Text("GitHub deposu (kullanıcı/depo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = ghToken,
                    onValueChange = { ghToken = it; Prefs.githubToken = it },
                    label = { Text("GitHub token (depo private ise)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Depo public ise token gerekmez. Private ise: GitHub > Settings > " +
                        "Developer settings > Personal access tokens > Fine-grained, " +
                        "sadece bu depoya \"Contents: Read\" yetkisi ver.",
                    color = TextLo, fontSize = 12.sp
                )
            }
        }

        // ---- izinler
        item { SectionTitle("İzinler") }
        items(perms) { p ->
            PermissionRow(p) {
                when (p.key) {
                    "overlay" -> ctx.startActivity(Permissions.overlayIntent(ctx))
                    "battery" -> ctx.startActivity(Permissions.batteryIntent(ctx))
                    else -> requestPerms.launch(arrayOf(p.key))
                }
            }
        }
        item {
            TextButton(onClick = { requestPerms.launch(Permissions.runtimePermissions()) }) {
                Text("Hepsini birden iste", color = Orange)
            }
        }

        // ---- wake word
        item { SectionTitle("Uyandırma kelimesi") }
        item {
            PofuCard {
                Text(
                    "\"Hey Pofu\" tamamen telefonda çalışır (Picovoice Porcupine). " +
                        "Ücretsiz bir AccessKey gerekiyor: console.picovoice.ai adresinden " +
                        "hesap aç, anahtarı kopyala, buraya yapıştır.",
                    color = TextLo, fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = accessKey,
                    onValueChange = { accessKey = it; Prefs.accessKey = it },
                    label = { Text("Picovoice AccessKey") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Hassasiyet: ${(sensitivity * 100).toInt()}%",
                    color = TextHi, fontSize = 14.sp
                )
                Text(
                    "Yüksek = rüzgârda daha kolay uyanır, ama boşuna da uyanır.",
                    color = TextLo, fontSize = 12.sp
                )
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    onValueChangeFinished = { Prefs.sensitivity = sensitivity },
                    valueRange = 0.1f..0.95f
                )
            }
        }

        // ---- ayarlar
        item { SectionTitle("Ayarlar") }
        item {
            PofuCard {
                ToggleRow(
                    "Kask mikrofonunu kullan",
                    "Bluetooth kulaklık bağlıysa mikrofonu oraya çevirir.",
                    btMic
                ) { btMic = it; Prefs.useBluetoothMic = it }
                ToggleRow(
                    "Sesli geri bildirim",
                    "Komutu anlayınca kulağına söyler.",
                    tts
                ) { tts = it; Prefs.speakFeedback = it }
                ToggleRow(
                    "Doğrudan ara",
                    "Kapalıysa numarayı çevirici ekranına yazar, tuşa sen basarsın.",
                    directCall
                ) { directCall = it; Prefs.directCall = it }
                ToggleRow(
                    "Telefon açılınca başlat",
                    "Yeniden başlatmadan sonra sürüş modu kendi gelir.",
                    autoStart
                ) { autoStart = it; Prefs.autoStart = it }
            }
        }

        // ---- komutlar
        item { SectionTitle("Komutlar") }
        item {
            PofuCard {
                CommandHelp()
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CommandHelp() {
    val rows = listOf(
        "Ahmet'i ara" to "Rehberden bulur, arar",
        "ara Mehmet abi" to "Aynı şey, ters sıra",
        "sonraki / geç / değiştir" to "Sonraki şarkı",
        "önceki / geri" to "Önceki şarkı",
        "çal / devam et" to "Müziği başlatır",
        "durdur / duraklat" to "Müziği duraklatır",
        "sesi aç / sesi kıs" to "Ses seviyesi",
        "Kadıköy'e git" to "Navigasyon başlatır",
        "yol tarifi" to "Haritayı açar",
        "iptal / boşver" to "Komutu iptal eder"
    )
    rows.forEach { (cmd, desc) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(
                "“$cmd”",
                color = Orange, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(160.dp)
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
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun PofuCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun PermissionRow(item: PermissionItem, onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (item.granted) Good else if (item.required) Bad else TextLo)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.label + if (item.required) "" else "  (isteğe bağlı)",
                    color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Text(item.why, color = TextLo, fontSize = 12.sp)
            }
            if (!item.granted) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onFix,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Surface2, contentColor = Orange
                    )
                ) { Text("Ver", fontSize = 13.sp) }
            }
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
