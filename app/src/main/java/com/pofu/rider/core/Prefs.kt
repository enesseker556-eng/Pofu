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

    // ------------------------------------------------------------ guncelleme

    /** "kullanici/depo" bicimi. CI bu depoya release yayinlar. */
    var updateRepo: String
        get() = sp.getString("update_repo", "enesseker556-eng/Pofu").orEmpty()
        set(v) = sp.edit().putString("update_repo", v.trim()).apply()

    /** Depo private ise gerekli. Fine-grained token, sadece Contents: Read yetkisi. */
    var githubToken: String
        get() = sp.getString("gh_token", "").orEmpty()
        set(v) = sp.edit().putString("gh_token", v.trim()).apply()

    /** Uygulama her acildiginda yeni surum var mi diye baksin. */
    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()

    var lastCheckAt: Long
        get() = sp.getLong("last_check", 0L)
        set(v) = sp.edit().putLong("last_check", v).apply()

    /** Servis su an calisiyor mu (BootReceiver ve UI icin). */
    var serviceEnabled: Boolean
        get() = sp.getBoolean("svc_on", false)
        set(v) = sp.edit().putBoolean("svc_on", v).apply()
}
