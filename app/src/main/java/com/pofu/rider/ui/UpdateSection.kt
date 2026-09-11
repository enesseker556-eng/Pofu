package com.pofu.rider.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pofu.rider.core.Prefs
import com.pofu.rider.update.UpdateStatus
import com.pofu.rider.update.Updater
import kotlinx.coroutines.launch

/**
 * Uygulama her acildiginda GitHub'daki son surume bakar; yenisi varsa
 * indirip Android'in kurulum ekranini acar. Kurulumu kullanici onaylar -
 * sessiz kurulum sadece sistem uygulamalarina ait bir yetki.
 */
@Composable
fun UpdateCard(refreshKey: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }

    val current = remember { Updater.currentVersionCode(ctx) }
    val currentName = remember { Updater.currentVersionName(ctx) }

    fun check(manual: Boolean) {
        scope.launch {
            status = UpdateStatus.Checking
            val result = Updater.check(ctx)
            status = result.fold(
                onSuccess = { rel ->
                    if (rel == null) UpdateStatus.UpToDate
                    else UpdateStatus.Available(rel)
                },
                onFailure = { e ->
                    // Otomatik kontrolde sessiz kal; internet yoksa kullaniciyi rahatsiz etme.
                    if (manual) UpdateStatus.Failed(e.message ?: "Kontrol basarisiz")
                    else UpdateStatus.Idle
                }
            )
        }
    }

    fun downloadAndInstall(rel: com.pofu.rider.update.Release) {
        scope.launch {
            status = UpdateStatus.Downloading(0)
            val result = Updater.download(ctx, rel) { pct ->
                status = UpdateStatus.Downloading(pct)
            }
            result.fold(
                onSuccess = { file ->
                    status = UpdateStatus.Ready(file, rel)
                    if (Updater.canInstall(ctx)) {
                        Updater.install(ctx, file)
                    }
                },
                onFailure = { e ->
                    status = UpdateStatus.Failed(e.message ?: "Indirme basarisiz")
                }
            )
        }
    }

    // Uygulama her acildiginda (ve ayarlardan donuldugunde) bir kez bak.
    LaunchedEffect(refreshKey) {
        if (Prefs.autoUpdate && status is UpdateStatus.Idle) check(manual = false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status is UpdateStatus.Available) Surface2 else Surface1
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sürüm $currentName", color = TextHi, fontSize = 15.sp)
                    Text(statusLine(status, current), color = statusColor(status), fontSize = 12.sp)
                }
                TextButton(onClick = { check(manual = true) }) {
                    Text("Kontrol et", color = Orange, fontSize = 13.sp)
                }
            }

            when (val s = status) {
                is UpdateStatus.Available -> {
                    Spacer(Modifier.height(10.dp))
                    if (s.release.notes.isNotBlank()) {
                        Text(s.release.notes, color = TextLo, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (!Updater.canInstall(ctx)) {
                        InstallPermissionNotice(ctx)
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { downloadAndInstall(s.release) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange, contentColor = Ink
                        )
                    ) {
                        Text("İndir ve kur", fontWeight = FontWeight.Bold)
                    }
                }

                is UpdateStatus.Downloading -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { s.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Orange,
                        trackColor = Surface2
                    )
                }

                is UpdateStatus.Ready -> {
                    Spacer(Modifier.height(10.dp))
                    if (!Updater.canInstall(ctx)) InstallPermissionNotice(ctx)
                    Button(
                        onClick = { Updater.install(ctx, s.apk) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Orange, contentColor = Ink
                        )
                    ) {
                        Text("Kurulumu aç", fontWeight = FontWeight.Bold)
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun InstallPermissionNotice(ctx: Context) {
    Column {
        Text(
            "Kurulum için \"bu kaynaktan uygulama yükle\" iznini vermen gerekiyor.",
            color = TextLo, fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { ctx.startActivity(Updater.installPermissionIntent(ctx)) }) {
            Text("İzin ekranını aç", color = Orange, fontSize = 13.sp)
        }
    }
}

private fun statusLine(status: UpdateStatus, current: Long): String = when (status) {
    UpdateStatus.Idle -> "Sürüm kodu $current"
    UpdateStatus.Checking -> "Güncelleme aranıyor..."
    UpdateStatus.UpToDate -> "Güncel"
    is UpdateStatus.Available -> "Yeni sürüm var: ${status.release.versionName}"
    is UpdateStatus.Downloading -> "İndiriliyor %${status.percent}"
    is UpdateStatus.Ready -> "İndirildi, kuruluma hazır"
    is UpdateStatus.Failed -> status.message
}

private fun statusColor(status: UpdateStatus) = when (status) {
    is UpdateStatus.Available, is UpdateStatus.Ready -> Orange
    is UpdateStatus.Failed -> Bad
    UpdateStatus.UpToDate -> Good
    else -> TextLo
}
