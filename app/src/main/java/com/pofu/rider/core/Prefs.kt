package com.pofu.rider.core

import android.content.Context
import android.content.SharedPreferences

/** Uygulama ayarlarinin tek kaynagi. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("pofu", Context.MODE_PRIVATE)
    }

    /** Komut anlasilinca sesli geri bildirim ver. */
    var speakFeedback: Boolean
        get() = sp.getBoolean("tts", true)
        set(v) = sp.edit().putBoolean("tts", v).apply()

    /** Numarayi dogrudan ara; kapali ise cevirici ekranini acar (daha guvenli). */
    var directCall: Boolean
        get() = sp.getBoolean("direct_call", true)
        set(v) = sp.edit().putBoolean("direct_call", v).apply()

    /** Telefon yeniden baslayinca surus modunu otomatik baslat. */
    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", false)
        set(v) = sp.edit().putBoolean("auto_start", v).apply()

    /** Servis su an calisiyor mu (BootReceiver ve UI icin). */
    var serviceEnabled: Boolean
        get() = sp.getBoolean("svc_on", false)
        set(v) = sp.edit().putBoolean("svc_on", v).apply()
}
