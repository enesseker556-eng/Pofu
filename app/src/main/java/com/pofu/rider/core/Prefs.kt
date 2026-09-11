package com.pofu.rider.core

import android.content.Context
import android.content.SharedPreferences

/** Uygulama ayarlarinin tek kaynagi. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences("pofu", Context.MODE_PRIVATE)
    }

    /** Picovoice Porcupine erisim anahtari. Bos ise wake word kapali, sadece buton calisir. */
    var accessKey: String
        get() = sp.getString("access_key", "").orEmpty()
        set(v) = sp.edit().putString("access_key", v.trim()).apply()

    /** Wake word hassasiyeti. Yuksek = daha kolay tetiklenir ama yanlis alarm artar. */
    var sensitivity: Float
        get() = sp.getFloat("sensitivity", 0.7f)
        set(v) = sp.edit().putFloat("sensitivity", v).apply()

    /** Kask Bluetooth kulakliginin mikrofonuna zorla yonlendir. */
    var useBluetoothMic: Boolean
        get() = sp.getBoolean("bt_mic", true)
        set(v) = sp.edit().putBoolean("bt_mic", v).apply()

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
