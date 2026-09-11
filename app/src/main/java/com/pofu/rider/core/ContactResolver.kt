package com.pofu.rider.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(val name: String, val number: String, val score: Double)

/** Sesli komuttaki ismi rehberdeki bir numaraya baglar. */
object ContactResolver {

    private const val ACCEPT_THRESHOLD = 0.62

    /** Rehberde yazmayan hitaplar. Normalize edilmis, eki atilmis halleri. */
    private val HONORIFICS = setOf(
        "abi", "abla", "bey", "hanim", "amca", "teyze", "dayi", "hala",
        "hoca", "usta", "kardes", "kanka", "reis", "baskan"
    )

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * En iyi eslesmeyi dondurur. Tam esitlik > kelime esitligi > benzerlik siralamasiyla.
     * Esik altinda kalan aday null olarak doner ki yanlis kisiyi aramayalim.
     */
    fun resolve(ctx: Context, spokenName: String): ContactMatch? {
        if (!hasPermission(ctx)) return null

        // Ekleri kelime kelime atiyoruz: "mehmet abiyi" -> ["mehmet", "abi"].
        // Tum cumleye birden atsaydik "abiyi" oldugu gibi kalir ve skoru dusururdu.
        val words = TurkishText.normalize(spokenName)
            .split(" ")
            .filter { it.isNotBlank() }
            .map { TurkishText.stripSuffix(it) }

        // "abi", "bey" gibi hitaplar rehberde yazmaz; eslestirmede sayarsak
        // "Mehmet abiyi ara" dogru kisiyi bulamaz.
        val queryWords = words.filterNot { it in HONORIFICS }.ifEmpty { words }
        val query = queryWords.joinToString(" ")
        if (query.isBlank()) return null

        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return null

        var best: ContactMatch? = null
        cursor.use { c ->
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val seen = HashSet<String>()
            while (c.moveToNext()) {
                val name = c.getString(nameIdx) ?: continue
                val number = c.getString(numIdx) ?: continue
                val key = name + "|" + number.filter { it.isDigit() }
                if (!seen.add(key)) continue

                val score = score(TurkishText.normalize(name), queryWords, query)
                if (score > (best?.score ?: 0.0)) {
                    best = ContactMatch(name, number, score)
                }
            }
        }
        return best?.takeIf { it.score >= ACCEPT_THRESHOLD }
    }

    private fun score(contactNorm: String, queryWords: List<String>, query: String): Double {
        if (contactNorm == query) return 1.0
        val contactWords = contactNorm.split(" ").filter { it.isNotBlank() }
        if (contactWords.isEmpty()) return 0.0

        // Sorgudaki her kelimenin rehber isminde bir karsiligi var mi?
        var total = 0.0
        for (qw in queryWords) {
            val bestWord = contactWords.maxOfOrNull { cw ->
                when {
                    cw == qw -> 1.0
                    cw.startsWith(qw) && qw.length >= 3 -> 0.92
                    else -> TurkishText.similarity(cw, qw)
                }
            } ?: 0.0
            total += bestWord
        }
        var s = total / queryWords.size

        // Tek kelime sorguyken cok kelimeli rehber kaydini hafif cezalandir:
        // "ahmet" derken "Ahmet Yilmaz Usta" yerine "Ahmet"i tercih et.
        if (contactWords.size > queryWords.size) {
            s -= 0.04 * (contactWords.size - queryWords.size)
        }
        return s.coerceIn(0.0, 1.0)
    }
}
