package com.pofu.rider.core

/**
 * "Hey Panda" uyandirma sozunun yakalanmasi.
 *
 * Neden "panda" ve neden "pofu" degil: cihaz ustu Turkce tanima modelinin
 * sabit bir kelime dagarcigi var ve "pofu" o dagarcikta YOK - model onu
 * fiziksel olarak yazamiyor, duydugunda "tuhaf", "tarafin" gibi baska
 * kelimelere ceviriyor. Cihazda olculdu:
 *
 *     pofu   -> "Ignoring word missing in vocabulary"
 *     panda  -> kabul edildi
 *
 * Bu yuzden uyandirma sozu, modelin gercekten uretebildigi bir kelime olmak
 * zorunda. "panda" hem dagarcikta var, hem uygulamanin simgesiyle uyuyor,
 * hem de surus sirasinda gunluk konusmada neredeyse hic gecmiyor.
 *
 * Sozlukte oldugu olculmus diger adaylar: kaptan, asistan, pilot, kanka, usta.
 */
object WakeWord {

    private const val TARGET = "panda"

    /**
     * Esik dar: "panda" artik modelin uretebildigi bir kelime oldugu icin
     * gevsek eslesmeye ihtiyac yok. Sadece tanima motorunun kacirdigi tek
     * harflik sapmalari tolere ediyoruz ("pandayi", "pandan").
     */
    private const val THRESHOLD = 0.80

    /** Panda'ya benzeyen ama uyandirmamasi gereken gunluk kelimeler. */
    private val BLOCKLIST = setOf(
        "banka", "manda", "kanda", "panel", "pantol", "bando", "randa", "vanda"
    )

    fun isMatch(text: String): Boolean = findIndex(text) >= 0

    /**
     * Uyandirma sozunden SONRAKI kismi dondurur.
     * "hey panda ahmeti ara" -> "ahmeti ara"; uyandirma yoksa bos string.
     *
     * Tek nefeste soylenen cumleyi bolmek icin gerekli: kullanici bip sesini
     * beklemeden konusmaya devam ediyor.
     */
    fun remainder(raw: String): String {
        val rawWords = raw.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val normWords = words(raw)
        // Noktalama kelime sayisini degistirmis olabilir; boyle bir durumda
        // yanlis yerden kesmektense hic kesmiyoruz.
        if (rawWords.size != normWords.size) return ""

        val idx = findIndex(raw)
        if (idx < 0) return ""
        return rawWords.drop(idx + 1).joinToString(" ")
    }

    /** Uyandirma sozunun bittigi kelime sirasi; yoksa -1. */
    private fun findIndex(text: String): Int {
        val w = words(text)
        for (i in w.indices) {
            if (isWake(w[i])) return i
            // Motor kelimeyi iki parcaya bolebiliyor: "pan da"
            if (i + 1 < w.size && isWake(w[i] + w[i + 1])) return i + 1
        }
        return -1
    }

    private fun words(text: String) =
        TurkishText.normalize(text).split(" ").filter { it.isNotBlank() }

    private fun isWake(word: String): Boolean {
        if (word.length < 4 || word.length > 8) return false
        if (word in BLOCKLIST) return false
        if (word == TARGET) return true
        // "pandayi", "pandaya" gibi ekli halleri de kabul et.
        if (word.startsWith(TARGET)) return true
        return TurkishText.similarity(word, TARGET) >= THRESHOLD
    }
}
