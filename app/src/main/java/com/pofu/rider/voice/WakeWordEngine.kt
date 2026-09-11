package com.pofu.rider.voice

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import com.pofu.rider.core.Prefs

/**
 * "Hey Pofu" uyandirma kelimesi (Picovoice Porcupine, tamamen cihaz uzerinde calisir).
 *
 * Calismasi icin iki sey gerekir:
 *  1. Ayarlar ekranina yapistirilmis bir Picovoice AccessKey
 *  2. app/src/main/assets/hey_pofu.ppn dosyasi (Picovoice Console'da uretilir)
 *
 * .ppn yoksa hazir "jarvis" kelimesine duser; AccessKey de yoksa tamamen devre disi kalir
 * ve uygulama sadece kask butonu / ekran butonu ile calisir.
 */
class WakeWordEngine(
    private val ctx: Context,
    private val onWake: () -> Unit
) {
    private var manager: PorcupineManager? = null

    /** Wake word yerine hangi kelimenin aktif oldugunu kullaniciya gostermek icin. */
    var activeKeyword: String = "yok"
        private set

    var lastError: String? = null
        private set

    val isAvailable: Boolean get() = manager != null

    fun start(): Boolean {
        if (manager != null) return true

        val key = Prefs.accessKey
        if (key.isBlank()) {
            lastError = "Picovoice AccessKey girilmemiş"
            return false
        }

        val customKeyword = findAsset(KEYWORD_ASSET)
        val customModel = findAsset(MODEL_ASSET)

        return try {
            val builder = PorcupineManager.Builder()
                .setAccessKey(key)
                .setSensitivity(Prefs.sensitivity)

            if (customKeyword != null) {
                builder.setKeywordPath(customKeyword)
                activeKeyword = "Hey Pofu"
            } else {
                builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                activeKeyword = "Jarvis (hey_pofu.ppn yüklenmemiş)"
            }
            if (customModel != null) builder.setModelPath(customModel)

            manager = builder.build(ctx) { _ -> onWake() }.also { it.start() }
            lastError = null
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Wake word başlatılamadı", e)
            lastError = e.message ?: e::class.java.simpleName
            activeKeyword = "yok"
            manager = null
            false
        }
    }

    /** Komut dinlerken wake word mikrofonu birakmali, yoksa iki motor cakisir. */
    fun pause() {
        try { manager?.stop() } catch (e: Exception) { Log.w(TAG, "stop", e) }
    }

    fun resume() {
        try { manager?.start() } catch (e: Exception) { Log.w(TAG, "start", e) }
    }

    fun release() {
        try {
            manager?.stop()
            manager?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "release", e)
        }
        manager = null
        activeKeyword = "yok"
    }

    private fun findAsset(name: String): String? = try {
        ctx.assets.open(name).close()
        name
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TAG = "PofuWakeWord"
        const val KEYWORD_ASSET = "hey_pofu.ppn"
        const val MODEL_ASSET = "porcupine_params_tr.pv"
    }
}
