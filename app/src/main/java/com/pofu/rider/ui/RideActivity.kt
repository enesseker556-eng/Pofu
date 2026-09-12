package com.pofu.rider.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pofu.rider.core.Command
import com.pofu.rider.voice.CommandExecutor
import com.pofu.rider.voice.Phase
import com.pofu.rider.voice.VoiceService

/**
 * Motordayken gorulen ekran. Kilit ekraninin ustunde acilir; telefonun kilidini ACMAZ,
 * sadece kilitliyken uzerinde calisir. Butonlar eldivenle basilabilsin diye kocaman.
 */
class RideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent { PofuTheme { RideScreen() } }
    }
}

@Composable
private fun RideScreen() {
    val ctx = LocalContext.current
    val state by VoiceService.state.collectAsStateWithLifecycle()
    val executor = remember { CommandExecutor(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            when (state.phase) {
                Phase.STOPPED -> "KAPALI"
                Phase.STARTING -> "HAZIRLANIYOR"
                Phase.WAITING -> "HAZIR"
                Phase.LISTENING -> "DİNLİYORUM"
                Phase.WORKING -> "..."
                Phase.SPEAKING -> "TAMAM"
            },
            color = if (state.phase == Phase.LISTENING) Orange else TextLo,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (state.phase == Phase.WAITING) "\"Hey Panda\" de" else " ",
            color = TextLo, fontSize = 13.sp
        )

        Spacer(Modifier.height(18.dp))

        MicButton(state.phase) { VoiceService.triggerListen(ctx) }

        Spacer(Modifier.height(18.dp))

        // Duyulan satiri artik uyandirmayi beklerken de canli akiyor: mikrofonun
        // seni duyup duymadigi tek bakista belli oluyor. Bos kaliyorsa ses hic
        // gelmiyor demektir, uyandirma sozunu aramaya gerek yok.
        Text(
            state.heard.ifBlank { "—" },
            color = TextHi, fontSize = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.error ?: state.reply,
            color = if (state.error != null) Bad else Orange,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        // Sesli komutun tutmadigi anlar icin elle basilabilir kocaman tuslar.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BigKey(Icons.Filled.SkipPrevious, "Önceki", Modifier.weight(1f)) {
                executor.execute(Command.Previous)
            }
            BigKey(Icons.Filled.SkipNext, "Sonraki", Modifier.weight(1f)) {
                executor.execute(Command.Next)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BigKey(Icons.Filled.VolumeDown, "Ses −", Modifier.weight(1f)) {
                executor.execute(Command.Volume(-1))
            }
            BigKey(Icons.Filled.VolumeUp, "Ses +", Modifier.weight(1f)) {
                executor.execute(Command.Volume(+1))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MicButton(phase: Phase, onTap: () -> Unit) {
    val listening = phase == Phase.LISTENING
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        Modifier
            .fillMaxWidth(0.62f)
            .aspectRatio(1f)
            .scale(scale)
            .clip(CircleShape)
            .background(if (listening) Orange else Surface2)
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = "Dinle",
            tint = if (listening) Ink else Orange,
            modifier = Modifier.fillMaxSize(0.42f)
        )
    }
}

@Composable
private fun BigKey(icon: ImageVector, label: String, modifier: Modifier, onTap: () -> Unit) {
    Column(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .clickable { onTap() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = Orange, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextLo, fontSize = 13.sp)
    }
}
