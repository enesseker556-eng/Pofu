package com.pofu.rider.core

/**
 * Turkce sesli komutlari [Command]'a cevirir.
 * Kurallar bilincli olarak kaba: motorda net cumle kurulmaz, tek kelime yakalamak yeter.
 */
object CommandParser {

    // Dikkat: buraya yalin "kapat" koyma. "muzigi kapat" da onunla eslesip
    // muzigi durdurmak yerine komutu iptal ediyordu.
    private val CANCEL = listOf("iptal", "bosver", "bos ver", "vazgectim", "bir sey yok", "birsey yok")
    private val HANGUP = listOf("kapat telefonu", "telefonu kapat", "aramayi kapat")
    private val PLAY = listOf("cal", "muzik cal", "muzigi ac", "muzik ac", "devam", "devam et", "baslat", "oynat")
    private val PAUSE = listOf("durdur", "duraklat", "muzigi durdur", "sus", "sustur", "muzigi kapat", "dur")
    private val NEXT = listOf("sonraki", "ileri", "gec", "atla", "degistir", "sonraki sarki",
        "diger sarki", "baska sarki", "bunu gec", "sarkiyi degistir", "muzigi degistir")
    private val PREV = listOf("onceki", "geri", "geri al", "onceki sarki", "bastan")
    private val VOL_UP = listOf("sesi ac", "sesi acar misin", "sesi artir", "sesi yukselt", "ses yukselt", "daha yuksek")
    private val VOL_DOWN = listOf("sesi kis", "sesi azalt", "sesi dusur", "ses kis", "daha kisik")

    fun parse(rawInput: String): Command {
        val raw = rawInput.trim()
        val t = TurkishText.normalize(raw)
        if (t.isBlank()) return Command.Unknown(raw)

        // Sirali kontrol: en spesifik olan once.
        if (matches(t, CANCEL)) return Command.Cancel
        if (matches(t, HANGUP)) return Command.HangUp

        parseCall(t)?.let { return it }
        parseNavigate(t)?.let { return it }

        if (matches(t, VOL_UP)) return Command.Volume(+1)
        if (matches(t, VOL_DOWN)) return Command.Volume(-1)
        if (matches(t, NEXT)) return Command.Next
        if (matches(t, PREV)) return Command.Previous
        if (matches(t, PAUSE)) return Command.Pause
        if (matches(t, PLAY)) return Command.Play

        return Command.Unknown(raw)
    }

    /** "ahmeti ara", "ara ahmet", "ahmet abiyi arar misin", "mehmete telefon et" */
    private fun parseCall(t: String): Command.Call? {
        val verbs = listOf("arar misin", "aramak istiyorum", "telefon et", "telefon ac", "ara")
        for (v in verbs) {
            // Fiil sonda: "... ara"
            if (t.endsWith(" $v")) {
                val name = t.removeSuffix(" $v").trim()
                cleanName(name)?.let { return Command.Call(it) }
            }
            // Fiil basta: "ara ..."
            if (t.startsWith("$v ")) {
                val name = t.removePrefix("$v ").trim()
                cleanName(name)?.let { return Command.Call(it) }
            }
            // Fiil ortada: "ahmeti ara hemen"
            val idx = t.indexOf(" $v ")
            if (idx > 0) {
                val name = t.substring(0, idx).trim()
                cleanName(name)?.let { return Command.Call(it) }
            }
        }
        return null
    }

    /** "eve git", "kadikoye yol tarifi", "navigasyon besiktas" */
    private fun parseNavigate(t: String): Command.Navigate? {
        val markers = listOf("yol tarifi", "navigasyon", "rota")
        for (m in markers) {
            if (t == m) return Command.Navigate("")
            if (t.startsWith("$m ")) return Command.Navigate(t.removePrefix("$m ").trim())
            if (t.endsWith(" $m")) return Command.Navigate(t.removeSuffix(" $m").trim())
        }
        for (v in listOf("gidelim", "git")) {
            if (t.endsWith(" $v")) {
                val dest = t.removeSuffix(" $v").trim()
                if (dest.isNotBlank() && dest.split(" ").size <= 4) return Command.Navigate(dest)
            }
        }
        return null
    }

    /** Isim adayini temizler; anlamsizsa null doner. */
    private fun cleanName(candidate: String): String? {
        var n = candidate
        for (filler in listOf("hemen", "lutfen", "bir", "su", "abi", "abla", "bey", "hanim")) {
            n = n.removePrefix("$filler ").removeSuffix(" $filler")
        }
        n = n.trim()
        if (n.isBlank() || n.length < 2) return null
        if (n.split(" ").size > 4) return null
        return n
    }

    private fun matches(text: String, phrases: List<String>): Boolean =
        phrases.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") || text.contains(" $it ") }
}
