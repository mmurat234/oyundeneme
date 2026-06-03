# Miho'nun Yumurta Toplama Oyunu

2D Unity oyunu - Miho sepetini kullanarak düşen yumurtaları topla!

## Oyun Özellikleri
- 🧺 Miho karakteri sepet taşıyor
- 🥚 Çeşitli yumurta türleri (Normal, Altın, Özel)
- 📊 Seviye sistemi (ilerledikçe zorluk artar)
- 🎯 Skor sistemi
- 📈 Oyun hızı ilerledikçe artar

## Kurulum Adımları

### 1. Unity Projesi Oluştur
- Unity Hub'ı aç
- Yeni 2D proje oluştur (Unity 2022 LTS veya üstü)
- Proje adı: "MihoEggCollector"

### 2. Dosyaları Kopyala
- `Assets/Scripts/` klasörü oluştur
- Bu repository'deki tüm `.cs` dosyalarını `Assets/Scripts/` içine kopyala

### 3. Sahneyi Oluştur
1. "Main" adında yeni sahne oluştur
2. Aşağıdaki GameObjects'i oluştur:

#### GameObject Yapısı:
```
Canvas (UI)
├── ScoreText (Text)
├── LevelText (Text)
└── GameOverPanel (Panel)

Miho (GameObject)
├── SpriteRenderer (Basket sprite)
└── BoxCollider2D (Trigger)

Eggs (Parent)
└── (Yumurtalar buraya spawn olacak)

GameManager (Empty GameObject)
├── GameManager script
└── Egg spawner logic

Camera (Main Camera)
```

### 4. Setup Detayları

#### SceneManager Oluştur
- Boş bir GameObject: "GameManager"
- Script: `GameManager.cs`

#### Miho'yu Oluştur
- Yeni Sprite: Rectangle beyaz kare (sepet simülasyonu)
- Size: 1 x 1
- Konum: (0, -4, 0)
- Script: `PlayerController.cs`

#### Canvas oluştur
- Canvas > New Button - TextMeshPro
- Score göstermek için Text
- Level göstermek için Text

## Kontroller
- **A / Sol Ok** - Sola git
- **D / Sağ Ok** - Sağa git
- **Boşluk** - Yeniden Başla (Game Over'da)

## Oyun Mekanikleri
- Seviyelere göre yumurta türleri değişir
- Her yumurta türü farklı puan veriyor
- Seviye arttıkça hız artıyor
- Belirli sayıda yumurta topladığında sonraki seviyeye geç

## Build Ayarları (APK için)
1. File > Build Settings
2. Android seç
3. Switch Platform
4. Player Settings:
   - Package Name: com.miho.eggcollector
   - Minimum API Level: 24
5. Build APK
