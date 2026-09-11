package com.pofu.rider

import com.pofu.rider.core.Command
import com.pofu.rider.core.CommandParser
import com.pofu.rider.core.TurkishText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Konusma tanimadan gelen gercekci metinler. Parser'i bozmadan degistirmek zor
 * oldugu icin buraya yeni ornek eklemek en ucuz guvence.
 */
class CommandParserTest {

    private fun call(text: String): String {
        val c = CommandParser.parse(text)
        assertTrue("beklenen Call, gelen $c  (girdi: $text)", c is Command.Call)
        return (c as Command.Call).name
    }

    @Test
    fun `isim sonda fiil sonda`() {
        assertEquals("ahmeti", call("Ahmet'i ara"))
        assertEquals("mehmet", call("Mehmet ara"))
    }

    @Test
    fun `fiil basta`() {
        assertEquals("ahmet", call("ara Ahmet abi"))
    }

    @Test
    fun `dolgu kelimeleri atilir`() {
        assertEquals("ayseyi", call("Ayşe'yi hemen ara"))
    }

    @Test
    fun `kesme isareti ekle birlesir`() {
        // "Ayse'yi" -> "ayseyi" -> ek atilinca "ayse"; rehberdeki "Ayşe" ile eslesir.
        assertEquals("ayse", TurkishText.stripSuffix(call("Ayşe'yi ara")))
        assertEquals("ahmet", TurkishText.stripSuffix(call("Ahmet'i ara")))
    }

    @Test
    fun `telefon et de arama sayilir`() {
        assertTrue(CommandParser.parse("Mehmet'e telefon et") is Command.Call)
    }

    @Test
    fun `muzik komutlari`() {
        assertEquals(Command.Next, CommandParser.parse("sonraki"))
        assertEquals(Command.Next, CommandParser.parse("şarkıyı değiştir"))
        assertEquals(Command.Next, CommandParser.parse("bunu geç"))
        assertEquals(Command.Previous, CommandParser.parse("önceki şarkı"))
        assertEquals(Command.Pause, CommandParser.parse("durdur"))
        assertEquals(Command.Play, CommandParser.parse("müzik çal"))
        assertEquals(Command.Volume(1), CommandParser.parse("sesi aç"))
        assertEquals(Command.Volume(-1), CommandParser.parse("sesi kıs"))
    }

    @Test
    fun `iptal her seyin onunde gelir`() {
        assertEquals(Command.Cancel, CommandParser.parse("iptal"))
        assertEquals(Command.Cancel, CommandParser.parse("boşver"))
    }

    @Test
    fun `navigasyon`() {
        val c = CommandParser.parse("Kadıköy'e git")
        assertTrue(c is Command.Navigate)
        // Hedef artik ham metinden geliyor; Maps'e Turkce hali gidiyor.
        assertEquals("Kadıköy'e", (c as Command.Navigate).destination)
        assertTrue(CommandParser.parse("yol tarifi") is Command.Navigate)
    }

    @Test
    fun `anlamsiz girdi Unknown doner`() {
        assertTrue(CommandParser.parse("hava bugün çok güzel ya") is Command.Unknown)
        assertTrue(CommandParser.parse("") is Command.Unknown)
    }

    @Test
    fun `muzik komutu isim sanilmamali`() {
        // "sesi ac" icinde "ac" var ama "ara" fiili yok; Call'a dusmemeli.
        assertTrue(CommandParser.parse("sesi aç") is Command.Volume)
    }
}

class TurkishTextTest {

    @Test
    fun `turkce karakterler ascii olur`() {
        assertEquals("sukru cigdem", TurkishText.normalize("Şükrü Çiğdem"))
        assertEquals("ismail", TurkishText.normalize("İsmail"))
        assertEquals("ali", TurkishText.normalize("  Ali!  "))
    }

    @Test
    fun `ek atma`() {
        assertEquals("ahmet", TurkishText.stripSuffix("ahmeti"))
        assertEquals("mehmet", TurkishText.stripSuffix("mehmete"))
        // Cok kisa isimlerde ek atmiyoruz, yoksa "ali" -> "al" olur.
        assertEquals("ali", TurkishText.stripSuffix("ali"))
    }

    @Test
    fun `benzerlik`() {
        assertEquals(1.0, TurkishText.similarity("ahmet", "ahmet"), 0.001)
        assertTrue(TurkishText.similarity("ahmet", "ahmed") > 0.7)
        assertTrue(TurkishText.similarity("ahmet", "zeynep") < 0.4)
    }
}
