# Google Play'e yükleme rehberi

Bu adımları yalnızca siz yapabilirsiniz: Play Console hesabı, ödeme ve kimlik doğrulama size aittir.
Gereken dosyaların hepsi hazır:

| Dosya | Ne işe yarar |
|---|---|
| `dist/YokdilKelime.aab` | Play'e yüklenecek paket (sürüm 4.0, API 36 hedefli, yükleme anahtarıyla imzalı) |
| `upload.keystore` + `upload.parola` | Yükleme anahtarı ve parolası. **Depoda yok, size ayrıca gönderildi. Güvenli bir yerde saklayın.** |
| `magaza/listeleme.md` | Ad, kısa ve tam açıklama, sürüm notu |
| `magaza/simge-512.png`, `magaza/one-cikan-1024x500.png`, `magaza/ekran/1-6.png` | Mağaza görselleri |
| `magaza/gizlilik-politikasi.html` | Gizlilik politikası (herkese açık bir adreste yayımlanmalı) |

## 1. Geliştirici hesabı
1. https://play.google.com/console adresinde hesap açın. Tek seferlik kayıt ücreti vardır.
2. Hesap türünü seçin:
   - **Kişisel hesap:** Kimlik doğrulaması istenir. Yeni kişisel hesaplarda üretime çıkmadan önce **12 test kullanıcısıyla 14 gün kesintisiz kapalı test** zorunludur (adım 6).
   - **Kuruluş hesabı** (ör. üniversite birimi): D-U-N-S numarası gerekir, kapalı test zorunluluğu yoktur.

## 2. Uygulamayı oluşturun
Play Console → **Uygulama oluştur**
- Uygulama adı: `YÖKDİL Sosyal Kelime Atölyesi`
- Varsayılan dil: Türkçe – tr-TR
- Uygulama / Oyun: Uygulama · Ücretsiz / Ücretli: Ücretsiz
- Geliştirici programı politikaları ve ABD ihracat yasaları beyanlarını onaylayın.

## 3. Uygulama içeriği formları (Politika → Uygulama içeriği)
- **Gizlilik politikası:** `gizlilik-politikasi.html` sayfasının herkese açık adresi.
- **Uygulama erişimi:** "Tüm işlevler özel erişim gerekmeden kullanılabilir."
- **Reklamlar:** Hayır, reklam içermiyor.
- **İçerik derecelendirmesi:** Kategori "Referans, haber veya eğitim". Şiddet, cinsellik, küfür, kumar, madde, kullanıcı etkileşimi, konum paylaşımı, dijital satın alma: hepsi **Hayır**. Beklenen sonuç: 3 yaş ve üzeri / Herkes.
- **Hedef kitle:** 18 ve üzeri. (13 yaş altı seçilirse Aileler politikası devreye girer; uygulama yetişkinlere yönelik.)
- **Veri güvenliği:** "Uygulamanız zorunlu kullanıcı verisi türlerinden herhangi birini topluyor veya paylaşıyor mu?" → **Hayır**. Uygulama internet izni istemez; ilerleme yalnızca cihazda kalır.
- **Devlet uygulaması:** Hayır. **Finansal özellikler:** Yok. **Sağlık:** Yok. **Haber uygulaması:** Hayır.

## 4. Mağaza girişi (Büyüme → Mağaza varlığı → Ana mağaza girişi)
`magaza/listeleme.md` içindeki metinleri ve `magaza/` klasöründeki görselleri yükleyin. Kategori: **Eğitim**. İletişim e-postası zorunludur.

## 5. Uygulama imzalama
İlk AAB'yi yüklediğinizde Play, **Play Uygulama İmzalama**'yı önerir; kabul edin. Uygulamayı kullanıcılara Google'ın uygulama imzalama anahtarı imzalar. Sizin `upload.keystore` dosyanız yalnızca yüklemeyi doğrulayan **yükleme anahtarıdır**. Kaybederseniz Play Console'dan sıfırlama istenebilir, ama bu zaman alır. Yedekleyin.

## 6. Test ve yayın
1. **Dahili test** (Test → Dahili test): Yeni sürüm oluşturun, `YokdilKelime.aab`'yi yükleyin, kendinizi test kullanıcısı olarak ekleyin. Uygulamayı Play üzerinden kurup deneyin. Bu, AAB'nin sorunsuz dönüştürüldüğünü görmenin en güvenilir yoludur.
2. **Kapalı test** (yalnız yeni kişisel hesaplar için zorunlu): En az 12 kişiyi e-posta listesiyle ekleyin (ör. öğrencileriniz, meslektaşlarınız). 12. kişi katılıp uygulamayı kurduğu andan itibaren 14 gün kesintisiz katılımda kalmaları gerekir.
3. **Üretim erişimi başvurusu:** 14 gün dolunca Kontrol Paneli'nden başvurun; kısa bir anket doldurulur.
4. **Üretim sürümü:** Aynı AAB'yi (ya da sonraki sürümü) üretim kanalına gönderin. İnceleme genellikle birkaç gün sürer.

## 7. Dikkat edilecekler
- **Elle kurduğunuz APK'yı kaldırın:** Telefonunuzdaki APK farklı bir anahtarla imzalı. Play sürümünü kurmadan önce onu silmeniz gerekir. Bu işlem uygulamadaki ilerlemeyi de siler.
- **"YÖKDİL" adı:** Uygulama ve açıklama, ÖSYM/YÖK ile bağlantı olmadığını açıkça belirtir. İnceleme ekibi yine de adı "taklit" politikası açısından sorgulayabilir. Ret gelirse ad "Sosyal Bilimler Kelime Atölyesi" gibi bir biçime çevrilebilir.
- **Paket adı kalıcıdır:** `com.mmurat.kelimeatolyesi` ilk yüklemeden sonra değiştirilemez.
- **Hedef API:** Paket Android 16'yı (API 36) hedefliyor; bu, 31 Ağustos 2026'dan beri yeni uygulamalar için zorunlu.
- **Sonraki sürümler:** `android/AndroidManifest.xml` içinde `versionCode` her yüklemede bir artmalı. Derleme: `android/build.sh` (yükleme anahtarı `android/upload.keystore` ve `android/upload.parola` olarak yerinde olmalı).
