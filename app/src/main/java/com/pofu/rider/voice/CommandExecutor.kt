package com.pofu.rider.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.pofu.rider.core.Command
import com.pofu.rider.core.ContactResolver
import com.pofu.rider.core.Prefs

/** Komutu uygular ve surucuye okunacak kisa bir cevap dondurur. */
class CommandExecutor(private val ctx: Context) {

    private val am = ctx.getSystemService(AudioManager::class.java)

    /** @return sesli okunacak cevap; bos ise bir sey soyleme. */
    fun execute(cmd: Command): String = when (cmd) {
        is Command.Call -> call(cmd.name)
        is Command.Navigate -> navigate(cmd.destination)
        Command.Play -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY); "Çalıyor" }
        Command.Pause -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE); "Durdu" }
        Command.Next -> { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT); "Sonraki" }
        Command.Previous -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS); "Önceki" }
        is Command.Volume -> volume(cmd.delta)
        Command.HangUp -> { mediaKey(KeyEvent.KEYCODE_HEADSETHOOK); "Kapatıldı" }
        Command.Cancel -> ""
        is Command.Unknown -> "Anlamadım"
    }

    private fun call(spokenName: String): String {
        if (!ContactResolver.hasPermission(ctx)) return "Rehber izni verilmemiş"

        val match = ContactResolver.resolve(ctx, spokenName)
            ?: return "$spokenName rehberde bulunamadı"

        val uri = Uri.fromParts("tel", match.number, null)
        val action = if (Prefs.directCall && hasCallPermission()) {
            Intent.ACTION_CALL
        } else {
            Intent.ACTION_DIAL
        }
        val intent = Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            "${match.name} aranıyor"
        } catch (e: SecurityException) {
            "Arama izni yok"
        } catch (e: Exception) {
            "Arama başlatılamadı"
        }
    }

    private fun navigate(destination: String): String {
        val uri = if (destination.isBlank()) {
            Uri.parse("google.navigation:q=")
        } else {
            Uri.parse("google.navigation:q=" + Uri.encode(destination) + "&mode=d")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.google.android.apps.maps")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            if (destination.isBlank()) "Navigasyon açıldı" else "$destination için rota kuruluyor"
        } catch (e: Exception) {
            // Maps yoksa genel bir harita uygulamasina dus.
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Harita açıldı"
            } catch (e2: Exception) {
                "Navigasyon uygulaması bulunamadı"
            }
        }
    }

    private fun volume(delta: Int): String {
        val dir = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return if (delta > 0) "Ses açıldı" else "Ses kısıldı"
    }

    /**
     * Medya tusunu o an muzigi calan uygulamaya gonderir.
     * Spotify, YouTube Music, yerel calar - hangisi ses odagindaysa o alir.
     */
    private fun mediaKey(keyCode: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
}
