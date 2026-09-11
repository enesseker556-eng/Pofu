package com.pofu.rider.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pofu.rider.R

/**
 * Ana ekranin ustundeki hareketli kapak.
 *
 * Uc ayri dongu ust uste biniyor ve hepsinin periyodu farkli, boylece
 * desen kendini hemen tekrar etmiyor: arkadaki mor isik kayar, panda
 * hafifce suzulur, arkasindaki halka nefes alir.
 */
@Composable
fun PandaHeader(listening: Boolean = false) {
    val t = rememberInfiniteTransition(label = "kapak")

    val sweep by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7000), RepeatMode.Reverse),
        label = "isik"
    )
    val float by t.animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "suzulme"
    )
    val halo by t.animateFloat(
        initialValue = 0.92f, targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(if (listening) 700 else 1900), RepeatMode.Reverse),
        label = "halka"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(258.dp)
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(Ink)
            .drawBehind {
                // Kayan mor isik: iki odak noktasi zit yonde hareket ediyor.
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(PurpleDeep.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(w * (0.18f + 0.64f * sweep), h * 0.30f),
                        radius = w * 0.78f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Purple.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(w * (0.86f - 0.66f * sweep), h * 0.72f),
                        radius = w * 0.62f
                    )
                )
                // Alt kenari sayfaya baglayan yumusak gecis.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Ink.copy(alpha = 0.85f),
                        startY = h * 0.55f,
                        endY = h
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                // Nefes alan halka - pandanin arkasindaki mor parilti.
                Box(
                    Modifier
                        .size(150.dp)
                        .scale(halo)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(PurpleGlow.copy(alpha = 0.34f), Color.Transparent)
                                )
                            )
                        }
                )
                Image(
                    painter = painterResource(R.drawable.panda_cover),
                    contentDescription = "Pofu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(y = float.dp)
                        .size(132.dp)
                        .clip(RoundedCornerShape(30.dp))
                )
            }

            Spacer(Modifier.height(14.dp))
            Text0("POFU", 30.sp, FontWeight.Black, TextHi)
            Text0(
                if (listening) "dinliyorum" else "motorcu sesli asistanı",
                13.sp, FontWeight.Normal, PurpleGlow
            )
        }
    }
}

/** Basliktaki iki satir icin kucuk yardimci - ayni hizalama, ayni renk mantigi. */
@Composable
private fun Text0(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight,
    color: Color
) {
    androidx.compose.material3.Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = if (weight == FontWeight.Black) 6.sp else 0.sp
    )
}
