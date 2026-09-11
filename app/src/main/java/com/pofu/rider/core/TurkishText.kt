package com.pofu.rider.core

import java.text.Normalizer
import java.util.Locale

/**
 * Turkce metin karsilastirmasi. Konusma tanima "Şükrü" yerine "sukru" dondurebilir,
 * rehberde de "ŞÜKRÜ" yazabilir; ikisini de ayni sepete indiriyoruz.
 */
object TurkishText {
    private val TR = Locale("tr", "TR")

    /** Kucuk harfe indirir, Turkce karakterleri ASCII karsiligina cevirir, noktalamayi atar. */
    fun normalize(input: String): String {
        // Kesme isaretini bosluga degil hicbir seye ceviriyoruz: "Ayse'yi" -> "ayseyi",
        // boylece ek atma calisiyor. Bosluk yapsaydik "ayse yi" olur, rehberde tutmazdi.
        val lowered = input.lowercase(TR).replace(Regex("['’‘`´]"), "")
        val sb = StringBuilder(lowered.length)
        for (ch in lowered) {
            when (ch) {
                'ı', 'i', 'î' -> sb.append('i')
                'ş' -> sb.append('s')
                'ğ' -> sb.append('g')
                'ü', 'û' -> sb.append('u')
                'ö' -> sb.append('o')
                'ç' -> sb.append('c')
                'â' -> sb.append('a')
                else -> sb.append(ch)
            }
        }
        val stripped = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")
        return stripped.replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Isim sonundaki belirtme/yonelme eklerini atar: "ahmeti" -> "ahmet", "aysaya" -> "aysa".
     * Sesli komutta "Ahmet'i ara" denince rehberdeki "Ahmet" ile eslesmesi icin gerekli.
     */
    fun stripSuffix(name: String): String {
        var n = name
        // Metin zaten normalize edildigi icin burada sadece ASCII karsiliklari var.
        val suffixes = listOf("yi", "yu", "ya", "ye", "ni", "nu", "na", "ne",
            "i", "u", "a", "e")
        for (s in suffixes) {
            if (n.length > s.length + 2 && n.endsWith(s)) {
                return n.dropLast(s.length)
            }
        }
        return n
    }

    /** 0..1 arasi benzerlik. Levenshtein tabanli. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val d = levenshtein(a, b)
        return 1.0 - d.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}
