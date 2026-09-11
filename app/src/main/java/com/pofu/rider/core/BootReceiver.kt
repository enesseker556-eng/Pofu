package com.pofu.rider.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pofu.rider.voice.VoiceService

/** Telefon yeniden basladiginda surus modunu geri getirir (ayar aciksa). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        if (Prefs.autoStart && Prefs.serviceEnabled) {
            VoiceService.start(context)
        }
    }
}
