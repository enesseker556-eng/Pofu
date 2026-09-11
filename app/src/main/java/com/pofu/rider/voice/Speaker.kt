package com.pofu.rider.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turkce sesli geri bildirim. Konusurken mikrofonu kendi sesimizle tetiklememek icin
 * [say]'in `after` geri cagrisi ile dinlemeyi ne zaman geri acacagimizi bildirir.
 */
class Speaker(context: Context) {

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var pending: Pair<String, (() -> Unit)?>? = null

    @Volatile private var onDone: (() -> Unit)? = null

    init {
        // Not: listener constructor donmeden tetiklenebilir, o yuzden `engine` uzerinden
        // calisiyoruz; `tts` alanina guvenmiyoruz.
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val res = engine.setLanguage(TR)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.getDefault()
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finish()
                @Deprecated("API 21 oncesi imza")
                override fun onError(utteranceId: String?) = finish()
                override fun onError(utteranceId: String?, errorCode: Int) = finish()
            })
            ready.set(true)
            pending?.let { (text, cb) -> pending = null; say(text, cb) }
        }
        tts = engine
    }

    private fun finish() {
        val cb = onDone
        onDone = null
        cb?.invoke()
    }

    /** Metni okur; bitince [after] cagrilir (hata olsa bile). */
    fun say(text: String, after: (() -> Unit)? = null) {
        val engine = tts
        if (!ready.get() || engine == null) {
            pending = text to after
            return
        }
        onDone = after
        val id = "pofu-" + System.nanoTime()
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            finish()
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private companion object { val TR: Locale = Locale("tr", "TR") }
}
