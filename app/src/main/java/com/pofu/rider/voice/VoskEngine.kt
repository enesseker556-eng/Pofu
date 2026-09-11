package com.pofu.rider.voice

import android.content.Context
import android.util.Log
import com.pofu.rider.core.WakeWord
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Tek bir cihaz ustu konusma tanima motoru: hem "Hey Pofu" uyandirmasini
 * hem de komutu ayni mikrofon akisindan cikariyor.
 *
 * Neden tek motor: iki ayri tanima motoru ayni mikrofonu paylasamiyor, surekli
 * birbirini kesiyorlardi. Tek akis olunca uyandirmadan komuta gecis anlik.
 *
 * Neden cihaz ustu: anahtar, hesap, internet gerekmiyor. Motorda sebeke
 * cekmedigi yerde de calisiyor.
 */
class VoskEngine(
    private val ctx: Context,
    private val onWake: () -> Unit,
    private val onCommand: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onState: (ready: Boolean, error: String?) -> Unit
) {
    private var model: Model? = null
    private var speech: SpeechService? = null

    /** Uyandirma bekleniyor mu, yoksa komut mu dinliyoruz. */
    @Volatile private var awake = false

    /** Uyandiktan sonra komut gelmezse bu kadar sure sonra vazgec. */
    @Volatile private var awakeUntil = 0L

    val isReady: Boolean get() = speech != null

    /** Su an komut bekliyor mu (uyandi ama komut gelmedi). */
    val isAwake: Boolean get() = awake

    fun start() {
        if (speech != null) return
        onState(false, null)
        StorageService.unpack(
            ctx, MODEL_ASSET, MODEL_DIR,
            { m ->
                model = m
                listenIn(wakePhase = true)
            },
            { e ->
                Log.e(TAG, "model acilamadi", e)
                onState(false, "Ses modeli yüklenemedi")
            }
        )
    }

    /**
     * Iki asamali dinleme.
     *
     * Bekleme asamasinda tanima motorunu sadece uyandirma sozune kilitliyoruz:
     * boylece model butun sozluk yerine "uyandirma mi, degil mi" diye karar
     * veriyor. Olculdu - serbest modda uyandirma kaciyordu, kilitli modda
     * aksanli ses bile tutuyor, gurultulu cumle ise hic tetiklemiyor.
     *
     * Uyandiktan sonra serbest moda geciyoruz, cunku komutta rehberdeki
     * isimler gibi onceden bilinemeyen kelimeler var.
     */
    private fun listenIn(wakePhase: Boolean) {
        val m = model ?: return
        try {
            speech?.let {
                runCatching { it.stop() }
                runCatching { it.shutdown() }
            }
            val rec = if (wakePhase) {
                Recognizer(m, SAMPLE_RATE, WAKE_GRAMMAR)
            } else {
                Recognizer(m, SAMPLE_RATE)
            }
            val service = SpeechService(rec, SAMPLE_RATE)
            service.startListening(listener)
            speech = service
            onState(true, null)
            Log.i(TAG, if (wakePhase) "uyandirma bekleniyor" else "komut dinleniyor")
        } catch (e: Throwable) {
            Log.e(TAG, "dinleme baslatilamadi", e)
            onState(false, "Mikrofon açılamadı")
        }
    }

    private val listener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            val text = hypothesis.toJsonField("partial")
            if (text.isBlank()) return

            if (!awake) {
                // Uyandirmayi kismi sonuctan yakaliyoruz: cumlenin bitmesini
                // beklemek yarim saniye gecikme demek, motorda fark ediliyor.
                if (WakeWord.isMatch(text)) wake()
            } else {
                onPartial(text)
            }
        }

        override fun onResult(hypothesis: String?) {
            val text = hypothesis.toJsonField("text")
            if (text.isBlank()) return

            if (!awake) {
                if (WakeWord.isMatch(text)) {
                    // "Hey Pofu Ahmet'i ara" tek nefeste soylenmis olabilir.
                    val rest = WakeWord.remainder(text)
                    wake()
                    if (rest.isNotBlank()) deliver(rest)
                }
                return
            }
            deliver(text)
        }

        override fun onFinalResult(hypothesis: String?) = Unit

        override fun onError(e: Exception?) {
            Log.e(TAG, "tanima hatasi", e)
            onState(false, "Dinleme koptu")
        }

        override fun onTimeout() = Unit
    }

    private fun wake() {
        if (awake) return
        awake = true
        awakeUntil = System.currentTimeMillis() + AWAKE_WINDOW_MS
        onWake()
        listenIn(wakePhase = false)
    }

    /** Komut gelmezse uyandirma beklemeye geri don. */
    fun tick() {
        if (awake && System.currentTimeMillis() > awakeUntil) {
            awake = false
            listenIn(wakePhase = true)
        }
    }

    private fun deliver(text: String) {
        val inTime = System.currentTimeMillis() <= awakeUntil
        awake = false
        listenIn(wakePhase = true)
        // Uyandirmanin uzerinden cok gectiyse bu cumle komut degil, sohbet.
        if (inTime) onCommand(text)
    }

    /** Komut islenirken geri bildirim konusuluyor; kendi sesimizi duymayalim. */
    fun pause(paused: Boolean) {
        speech?.setPause(paused)
    }

    /** Uyandirmayi disaridan tetikler (ekrandaki mikrofon tusu). */
    fun forceWake() {
        awake = false
        wake()
    }

    fun release() {
        speech?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        speech = null
        runCatching { model?.close() }
        model = null
        awake = false
    }

    /**
     * Sadece test icin: mikrofon yerine bir WAV dosyasini modele verir.
     * Emulatorde mikrofon olmadigi icin ses hattini ancak boyle dogrulayabiliyoruz.
     * 16 kHz, tek kanal, 16-bit PCM bekler.
     */
    fun transcribeFile(path: String, grammar: String? = null): String {
        val m = model ?: return "MODEL_YOK"
        return try {
            val rec = if (grammar == null) Recognizer(m, SAMPLE_RATE)
                      else Recognizer(m, SAMPLE_RATE, grammar)
            java.io.FileInputStream(path).use { input ->
                input.skip(44) // WAV basligi
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    rec.acceptWaveForm(buf, n)
                }
            }
            val out = JSONObject(rec.finalResult).optString("text").trim()
            rec.close()
            out
        } catch (e: Throwable) {
            Log.e(TAG, "dosya cozumlenemedi", e)
            "HATA: " + e.message
        }
    }

    private fun String?.toJsonField(field: String): String = try {
        if (this.isNullOrBlank()) "" else JSONObject(this).optString(field).trim()
    } catch (e: Exception) {
        ""
    }

    private companion object {
        const val TAG = "PofuVosk"
        const val SAMPLE_RATE = 16000.0f
        const val MODEL_ASSET = "model-tr"
        const val MODEL_DIR = "model"

        /** Uyandiktan sonra komut icin taninan sure. */
        const val AWAKE_WINDOW_MS = 9000L

        /**
         * Bekleme asamasinin sozlugu. "[unk]" olmadan motor her sesi
         * uyandirma sanmaya calisir ve surekli yanlis tetiklenir.
         */
        const val WAKE_GRAMMAR = "[\"hey panda\", \"panda\", \"[unk]\"]"
    }
}
