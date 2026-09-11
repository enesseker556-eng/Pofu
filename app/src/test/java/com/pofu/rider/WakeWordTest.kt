package com.pofu.rider

import com.pofu.rider.core.WakeWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uyandirma sozunun iki ayri riski var ve ikisi de bu dosyada tutuluyor:
 *  - cok kati olursa hic uyanmaz ("pofu" sozlukte olmadigi icin motor onu
 *    her seferinde farkli yaziyor)
 *  - cok gevsek olursa normal konusmada durmadan uyanir
 */
class WakeWordTest {

    @Test
    fun `duzgun soylenmis hali`() {
        listOf(
            "hey panda",
            "Hey Panda",
            "panda",
            "hey panda ahmeti ara",
            "hey, panda!"
        ).forEach { assertTrue("uyanmadi: \"$it\"", WakeWord.isMatch(it)) }
    }

    /** Tanima motorunun gercekte urettigi sapmalar ve ekli haller. */
    @Test
    fun `sapmalari da yakalamali`() {
        listOf(
            "hey pan da",     // iki kelimeye bolunmus
            "hey pandayi",    // ek almis
            "hey pandaya",
            "hey panda'",     // noktalama yapismis
            "hey pantda"
        ).forEach { assertTrue("uyanmadi: \"$it\"", WakeWord.isMatch(it)) }
    }

    /**
     * Motorda surekli konusuluyor ve radyo calisiyor; bunlarin hicbiri
     * uyandirmamali, yoksa uygulama durmadan araya girer.
     */
    @Test
    fun `gunluk konusma uyandirmamali`() {
        listOf(
            "hava bugün çok güzel",
            "bankaya uğrayacağım",
            "köprüden geçiyoruz",
            "panel toplantısı",
            "polis var dikkat et",
            "toplu taşıma",
            "kolay gelsin",
            "bu yol çok bozuk",
            "sağa dön",
            "",
            "a",
            "trafik kilit"
        ).forEach { assertFalse("yanlislikla uyandi: \"$it\"", WakeWord.isMatch(it)) }
    }

    @Test
    fun `uyandirmadan sonraki komut ayrilmali`() {
        assertEquals("ahmeti ara", WakeWord.remainder("hey panda ahmeti ara"))
        assertEquals("sonraki", WakeWord.remainder("panda sonraki"))
        assertEquals("sesi aç", WakeWord.remainder("hey pan da sesi aç"))
        // Tek basina soylendiyse geriye komut kalmiyor.
        assertEquals("", WakeWord.remainder("hey panda"))
        // Uyandirma yoksa hicbir sey dondurmemeli.
        assertEquals("", WakeWord.remainder("hava çok güzel"))
    }

    @Test
    fun `komut buyuk harf ve turkce karakteri korumali`() {
        // Maps'e ve rehbere ham metin gidiyor; burada bozulmamali.
        assertEquals("Kadıköy'e git", WakeWord.remainder("hey panda Kadıköy'e git"))
        assertEquals("Ahmet'i ara", WakeWord.remainder("Hey Panda Ahmet'i ara"))
    }
}
