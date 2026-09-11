"Hey Pofu" uyandirma kelimesini buraya koy:

  1. https://console.picovoice.ai adresine gir (ucretsiz hesap).
  2. AccessKey'i kopyala -> uygulamada Ayarlar > Uyandirma kelimesi alanina yapistir.
  3. Porcupine > Train Wake Word ekraninda "Hey Pofu" yaz, platform: Android sec, egit.
  4. Inen .ppn dosyasini bu klasore "hey_pofu.ppn" adiyla koy.
  5. (Istege bagli) Turkce model kullanacaksan porcupine_params_tr.pv dosyasini da buraya koy.

Bu dosyalar yoksa uygulama hazir "Jarvis" kelimesine duser; AccessKey de yoksa
wake word tamamen kapanir ve sadece ekrandaki mikrofon butonu calisir.
