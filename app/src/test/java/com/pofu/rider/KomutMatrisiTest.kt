package com.pofu.rider

import com.pofu.rider.core.Command
import com.pofu.rider.core.CommandParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Belgelenmis her komutun ve motorda gercekten soylenen varyantlarinin tam listesi.
 * Parser'a dokunan herkes once buraya bakmali: bir satir kirmizi olursa
 * o soyleyis bicimi artik calismiyor demektir.
 */
class KomutMatrisiTest {

    /** Beklenen sonucu Call ise sadece "arama mi" diye bakiyoruz, isim ayri testte. */
    private fun assertCmd(input: String, expected: Command) {
        val actual = CommandParser.parse(input)
        if (expected is Command.Call) {
            assertEquals("girdi: \"$input\"", Command.Call::class, actual::class)
        } else if (expected is Command.Navigate) {
            assertEquals("girdi: \"$input\"", Command.Navigate::class, actual::class)
        } else {
            assertEquals("girdi: \"$input\"", expected, actual)
        }
    }

    @Test
    fun `arama - her soyleyis bicimi`() {
        listOf(
            "Ahmet'i ara",
            "Ahmeti ara",
            "ara Ahmet",
            "Mehmet abiyi ara",
            "ara Mehmet abi",
            "Ayşe'yi hemen ara",
            "Şükrü'yü ara",
            "Zeynep'i arar mısın",
            "Mehmet'e telefon et",
            "annemi ara",
            "babamı ara lütfen"
        ).forEach { assertCmd(it, Command.Call("")) }
    }

    @Test
    fun `sonraki sarki - her soyleyis bicimi`() {
        listOf(
            "sonraki", "sonraki şarkı", "ileri", "geç", "atla", "değiştir",
            "bunu geç", "şarkıyı değiştir", "müziği değiştir", "diğer şarkı", "başka şarkı"
        ).forEach { assertCmd(it, Command.Next) }
    }

    @Test
    fun `onceki sarki`() {
        listOf("önceki", "geri", "önceki şarkı", "baştan")
            .forEach { assertCmd(it, Command.Previous) }
    }

    @Test
    fun `calma ve durdurma`() {
        listOf("çal", "müzik çal", "müziği aç", "müzik aç", "devam", "devam et", "başlat", "oynat")
            .forEach { assertCmd(it, Command.Play) }
        listOf("durdur", "duraklat", "müziği durdur", "sus", "sustur", "müziği kapat", "dur")
            .forEach { assertCmd(it, Command.Pause) }
    }

    @Test
    fun `ses seviyesi`() {
        listOf("sesi aç", "sesi artır", "sesi yükselt", "ses yükselt", "daha yüksek")
            .forEach { assertCmd(it, Command.Volume(1)) }
        listOf("sesi kıs", "sesi azalt", "sesi düşür", "ses kıs", "daha kısık")
            .forEach { assertCmd(it, Command.Volume(-1)) }
    }

    @Test
    fun `navigasyon`() {
        listOf(
            "Kadıköy'e git", "eve git", "yol tarifi", "navigasyon",
            "Beşiktaş yol tarifi", "navigasyon Ankara", "rota"
        ).forEach { assertCmd(it, Command.Navigate("")) }
    }

    @Test
    fun `iptal ve kapatma`() {
        listOf("iptal", "boşver", "boş ver", "vazgeçtim", "bir şey yok")
            .forEach { assertCmd(it, Command.Cancel) }
        listOf("telefonu kapat", "aramayı kapat")
            .forEach { assertCmd(it, Command.HangUp) }
    }

    @Test
    fun `anlamsiz girdiler komut sanilmamali`() {
        listOf(
            "hava bugün çok güzel",
            "ya bu trafik bitmez",
            "",
            "ıııı",
            "kask çok sıkıyor"
        ).forEach { input ->
            val c = CommandParser.parse(input)
            assertEquals("girdi: \"$input\" komut sanildi -> $c", Command.Unknown::class, c::class)
        }
    }

    /**
     * Konusma tanima motordaki ruzgarda kelimeleri bozuyor. Bu satirlar
     * gercek hatalari degil, tolere etmemiz gerekeni belgeliyor:
     * bozuk metin komut sanilmamali, "Anlamadim" demeli.
     */
    @Test
    fun `bozuk tanima ciktisi guvenli sekilde reddedilmeli`() {
        listOf("so na ki", "se si a c", "a ra")
            .forEach { input ->
                val c = CommandParser.parse(input)
                assertEquals(
                    "girdi: \"$input\" yanlislikla komut oldu -> $c",
                    Command.Unknown::class, c::class
                )
            }
    }
}
