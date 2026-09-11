package com.pofu.rider.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/**
 * Kask kulakliginin butonu bu servise ulasirsa dinlemeyi baslatir.
 * Modern Android'de medya tuslari genelde ses odagindaki uygulamaya gider,
 * bu yuzden asil tetikleyici "Hey Pofu" wake word'udur; burasi yedek yoldur.
 */
class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val event: KeyEvent? = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        if (event == null || event.action != KeyEvent.ACTION_UP) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> VoiceService.triggerListen(context)
        }
    }
}
