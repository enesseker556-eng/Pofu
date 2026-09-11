# Pofu — Motorcu Sesli Asistanı

Motordayken telefona dokunmadan **arama yapmak**, **müzik değiştirmek** ve **navigasyon açmak** için Android uygulaması. Kask kulaklığıyla çalışır, telefon cebinde ve kilitliyken bile.

---

## Ne yapar

| Dersen | Yapar |
|---|---|
| "Hey Pofu" | Uyanır, bip çalar, dinlemeye başlar |
| "Ahmet'i ara" / "ara Mehmet abi" | Rehberde bulur, arar |
| "sonraki" / "geç" / "şarkıyı değiştir" | Sonraki şarkı |
| "önceki" / "geri" | Önceki şarkı |
| "çal" / "devam et" | Müziği başlatır |
| "durdur" / "duraklat" | Müziği duraklatır |
| "sesi aç" / "sesi kıs" | Ses seviyesi |
| "Kadıköy'e git" / "yol tarifi" | Google Maps navigasyonu |
| "iptal" / "boşver" | Komutu iptal eder |

Müzik komutları **hangi uygulama çalıyorsa ona** gider — Spotify, YouTube Music, yerel çalar, fark etmez.

---

## Ne YAPMAZ (ve neden)

- **Telefonun kilidini açmaz.** Android'de hiçbir üçüncü parti uygulama bunu yapamaz; yüz/parmak izi tanıma sistem tekelindedir. Bunun yerine sürüş ekranı **kilit ekranının üstünde** açılır (`showWhenLocked`) — kilidi açmana gerek kalmadan komut verirsin, arama başlar, müzik değişir.
- **Play Store'a çıkmaz.** Sürekli mikrofon dinleme + arama izinleri olan bir uygulama Play politikalarına takılır. Bu APK kendi telefonuna kurulmak için.
- **Root gerektirmez**, ama root olmadan da bu tavan.

---

## Kurulum

### 1. GitHub'a yükle

```bash
git remote add origin https://github.com/KULLANICI_ADIN/pofu.git
```

```bash
git push -u origin main
```

Push'tan sonra GitHub **Actions** sekmesinde build başlar (~3-5 dk). Bittiğinde en alttaki **Artifacts → pofu-apk** dosyasını indir, zip'ten `app-debug.apk`'yı çıkar.

### 2. Telefona kur

APK'yı telefona at, dokun, "Bilinmeyen kaynaklara izin ver" de. (Chrome veya Dosyalar uygulamasına bir kereliğine izin vermen istenir.)

### 3. İzinleri ver

Uygulamayı aç, izin listesindeki her satır yeşil olana kadar "Ver" tuşlarına bas. İkisi özellikle önemli:

- **Diğer uygulamaların üstünde göster** — bu olmadan telefon kilitliyken arama başlatılamaz. Android arka plandan Activity açılmasını engelliyor, bu izin muafiyet sağlıyor.
- **Pil optimizasyonundan muaf** — olmadan Android birkaç dakika sonra dinlemeyi sessizce öldürür.

### 4. "Hey Pofu" wake word'ünü kur

Wake word cihaz üzerinde çalışır (Picovoice Porcupine), internet istemez, ama ücretsiz bir anahtar gerekiyor:

1. https://console.picovoice.ai → ücretsiz hesap aç
2. **AccessKey**'i kopyala → uygulamada *Uyandırma kelimesi* alanına yapıştır
3. Aynı sitede **Porcupine → Train Wake Word**: metin `Hey Pofu`, platform **Android**, eğit
4. İnen `.ppn` dosyasını `app/src/main/assets/hey_pofu.ppn` olarak koy, GitHub'a push et, yeni APK'yı kur

`hey_pofu.ppn` yoksa hazır **"Jarvis"** kelimesine düşer. AccessKey de yoksa wake word tamamen kapanır; uygulama yine çalışır ama tetiklemek için sürüş ekranındaki büyük mikrofon tuşuna basman gerekir.

### 5. Sürüş öncesi

- Kask kulaklığını bağla (*Kask mikrofonunu kullan* ayarı açık olsun)
- **SÜRÜŞ MODUNU BAŞLAT**
- **Sürüş ekranını aç** → telefonu cebe/tanka koy

---

## Otomatik güncelleme

Her `git push` sonrası CI yeni bir **Release** yayınlıyor ve APK'yı ekliyor. Sürüm numarası Actions'ın çalışma numarası — kendiliğinden artıyor, elle bir şey yapmıyorsun.

Telefondaki uygulama her açılışta son sürüme bakar. Yenisi varsa ana ekranın üstünde **"Yeni sürüm var"** kartı çıkar → *İndir ve kur* → Android'in kurulum ekranı açılır → onayla, bitti.

İlk seferde **"bu kaynaktan uygulama yükle"** izni istenir; kart üzerinden tek tuşla verirsin.

**Depo private ise** bir token gerekiyor (public ise gerekmez):

1. GitHub → Settings → Developer settings → Personal access tokens → **Fine-grained tokens**
2. Repository access: sadece `Pofu`
3. Permissions → Repository permissions → **Contents: Read-only**
4. Üretilen token'ı uygulamadaki *GitHub token* alanına yapıştır

Kurulum tamamen sessiz olamaz — sessiz kurulum sadece sistem uygulamalarına verilen bir yetki. Yapabileceğin en yakını bu: tek tuş indirme + tek tuş onay.

---

## Motorda çalışması için ayarlar

Rüzgâr sesi bu işin en büyük düşmanı. Sırayla dene:

- **Hassasiyet**: düşük hızda 0.5–0.6 yeter. 100 km/s üstünde 0.8'e çık — bedeli boşuna uyanmalar olur.
- **Kask mikrofonu şart.** Telefonun kendi mikrofonu cepteyken hiçbir şey duymaz. Cardo/Sena türü bir interkom en iyi sonucu verir.
- Konuşma tanıma (komutun kendisi) Google'ın servisini kullanır ve **internet ister**. Çevrimdışı çalışması için: Android Ayarlar → Sistem → Diller ve giriş → Sesle yazma → Çevrimdışı konuşma tanıma → **Türkçe** indir.

---

## Proje yapısı

```
app/src/main/java/com/pofu/rider/
  core/
    Prefs.kt             ayarlar
    TurkishText.kt       Türkçe normalizasyon + ek atma + benzerlik
    Command.kt           komut modeli
    CommandParser.kt     Türkçe metin -> komut
    ContactResolver.kt   isim -> rehber numarası (bulanık eşleme)
    BootReceiver.kt      açılışta otomatik başlatma
  voice/
    VoiceService.kt      foreground servis, dinleme döngüsü
    WakeWordEngine.kt    Porcupine sarmalayıcı (yoksa zarifçe düşer)
    Speaker.kt           Türkçe TTS geri bildirim
    AudioRoute.kt        Bluetooth SCO mikrofon yönlendirme
    CommandExecutor.kt   arama / medya / navigasyon
  ui/
    MainActivity.kt      kurulum + ayarlar
    RideActivity.kt      kilit ekranı üstü sürüş HUD'u
    Permissions.kt       izin durumu tek yerden
```

Komut ayrıştırıcının testleri `app/src/test/` altında ve her build'de CI'da koşuyor. Yeni bir söyleyiş biçimi eklerken oraya bir örnek ekle.

---

## Sorun giderme

**"Hey Pofu" hiç uyanmıyor**
AccessKey girili mi? Sürüş ekranında "HAZIR" yazısının altında hangi kelimenin aktif olduğu yazıyor — "yok" diyorsa anahtar veya `.ppn` eksik.

**Komutu duyuyor ama "Anlamadım" diyor**
İnternet bağlantısını kontrol et; konuşma tanıma çevrimiçi çalışıyor. Ya da çevrimdışı Türkçe paketini indir (yukarıda).

**Arama başlamıyor, "Arama izni yok" diyor**
*Telefon etme* izni verilmemiş. Vermek istemiyorsan *Doğrudan ara* ayarını kapat — numarayı çevirici ekranına yazar, tuşa sen basarsın.

**Kilitliyken hiçbir şey olmuyor**
*Diğer uygulamaların üstünde göster* izni eksik. Bu izin olmadan Android arka plandan arama ekranı açtırmıyor.

**Bir süre sonra kendi kendine susuyor**
Pil optimizasyonu muafiyeti verilmemiş. Xiaomi/Huawei/Oppo kullanıyorsan üreticinin kendi "otomatik başlatma" ve "arka planda çalıştır" ayarlarını da ayrıca açman gerekiyor.
