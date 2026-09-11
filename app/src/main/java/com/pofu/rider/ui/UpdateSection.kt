package com.pofu.rider.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pofu.rider.update.Release
import com.pofu.rider.update.UpdateStatus
import com.pofu.rider.update.Updater
import kotlinx.coroutines.launch

/**
 * Guncelleme tamamen kendiliginden yurur: uygulama acilinca bakar, yenisi
 * varsa indirir, kurulum ekranini acar. Kullaniciya sadece is olduğunda
 * bir sey gosteriyoruz - guncelken ekranda hicbir sey gorunmuyor.
 */
@Composable
fun UpdateCard(refreshKey: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }

    fun downloadAndInstall(rel: Release) {
        scope.launch {
            status = UpdateStatus.Downloading(0)
            Updater.download(ctx, rel) { pct -> status = UpdateStatus.Downloading(pct) }
                .fold(
                    onSuccess = { file ->
                        status = UpdateStatus.Ready(file, rel)
                        if (Updater.canInstall(ctx)) Updater.install(ctx, file)
                    },
                    // Sessiz kal: internet yoksa kullaniciyi rahatsiz etmenin anlami yok,
                    // bir sonraki acilista tekrar denenecek.
                    onFailure = { status = UpdateStatus.Idle }
                )
        }
    }

    LaunchedEffect(refreshKey) {
        if (status !is UpdateStatus.Idle) return@LaunchedEffect
        Updater.check(ctx).onSuccess { rel -> if (rel != null) downloadAndInstall(rel) }
    }

    val visible = status is UpdateStatus.Downloading || status is UpdateStatus.Ready
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2)
        ) {
            Column(Modifier.padding(16.dp)) {
                when (val s = status) {
                    is UpdateStatus.Downloading -> {
                        Text("Güncelleme iniyor", color = TextHi, fontSize = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { s.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Purple,
                            trackColor = Ink
                        )
                    }

                    is UpdateStatus.Ready -> {
                        Text("Güncelleme hazır", color = TextHi, fontSize = 15.sp)
                        Text(
                            "Kurmak için tek dokunuş yeter.",
                            color = TextLo, fontSize = 12.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (Updater.canInstall(ctx)) {
                                    Updater.install(ctx, s.apk)
                                } else {
                                    ctx.startActivity(Updater.installPermissionIntent(ctx))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple, contentColor = Ink
                            )
                        ) {
                            Text(
                                if (Updater.canInstall(ctx)) "Kur" else "İzin ver ve kur",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
