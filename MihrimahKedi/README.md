# Pamuk'u Yakala! 🐱

Mihrimah ve babası Murat, bahçede kaçan yavru kedi **Pamuk**'u yakalamaya çalışıyor.
Android oyunu. Hazır APK: [`PamukuYakala.apk`](PamukuYakala.apk) (Android 7.0 ve üstü).

## Nasıl oynanır?
- **Ekrana dokun ve parmağını gezdir.** Mihrimah parmağının olduğu yere koşar.
- **Murat Baba** kendi başına hareket eder. Kedinin öbür tarafına geçer ve onu Mihrimah'la arasında sıkıştırır.
- **Balık at** 🐟: Pamuk balığın kokusunu alınca yemeğe koşar ve bir süre durur. Tam yakalama fırsatı!
- **Baba koş!** »: Murat kısa bir süre çok hızlı koşar (6 saniyede bir kullanılır).
- **Yıldız** ⭐ Mihrimah'ı hızlandırır. **Mavi balık** fazladan bir balık verir.
- Pamuk'un **enerjisi** var. Uzun süre kaçınca yorulur ve yavaşlar.
- Her seviyede Pamuk'u belli sayıda yakalamalısın. Kalan süre bonus puan olur.

## Sürprizler
- **Karton kutular** (2. seviyeden itibaren): Pamuk kutuya saklanır, kutu sallanır. Mihrimah ya da Murat kutuya ulaşırsa onu içeride yakalar. Yaklaşırsan "Böö!" diye fırlayabilir.
- **Zıplama**: İki kişi birden çok yaklaşınca Pamuk üstlerinden atlayıp kaçar.
- **Kelebekler**: Pamuk korkmadığında kelebek kovalar.
- **Üç ortam**: Arka Bahçe → Gün Batımı Parkı → Gece Bahçesi. Gece Mihrimah'ın feneri etrafı aydınlatır, Pamuk'un gözleri karanlıkta parlar, ateş böcekleri uçar.
- Karakterler konuşur ("Babaa, öbür taraftan dolaş!", "Aferin kızıma!", "Yakalayamazsınız!").
- Bütün sesler (miyav, mırlama, fanfar) kodla üretilir.

## APK'yı telefona kurmak
1. `PamukuYakala.apk` dosyasını telefona indir.
2. Dosyaya dokun. Telefon "bilinmeyen kaynak" izni isterse ver.
3. "Pamuk'u Yakala" uygulamasını aç.

## Kaynaktan derlemek
Android Studio gerekmez. JDK 11+, `curl` ve `python3` yeterli:

```bash
./build.sh
```

Betik gerekli araçları (aapt2, dx, apksig, android.jar) ilk çalıştırmada `buildtools/cache/` klasörüne indirir ve imzalı `PamukuYakala.apk` dosyasını üretir.

## Dosyalar
- `src/com/mihrimah/kedi/GameView.java`: oyun döngüsü, yapay zekâ, çizimler
- `src/com/mihrimah/kedi/Sound.java`: kodla üretilen ses efektleri
- `buildtools/IconGen.java`: uygulama ikonunu çizer
- `buildtools/Signer.java`: APK imzalama
