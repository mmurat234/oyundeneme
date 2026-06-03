# Detaylı Kurulum Rehberi - Miho Yumurta Toplama Oyunu

## Adım 1: Unity Kurulumu
1. [Unity Hub](https://unity.com/download) indir
2. Unity 2022 LTS veya 2023 kurulumunu yap
3. Yeni 2D proje oluştur: **File** > **New Project** > **2D**

## Adım 2: Proje Yapısı
```
Assets/
├── Scripts/
│   ├── GameManager.cs
│   ├── PlayerController.cs
│   ├── Egg.cs
│   └── EggSpawner.cs
├── Scenes/
│   └── Main.unity
└── Resources/
```

## Adım 3: Script Dosyalarını Kopyala
1. Bu repository'deki `.cs` dosyalarını kopyala
2. Unity'de `Assets` > `Scripts` klasörünü oluştur
3. `.cs` dosyalarını `Assets/Scripts/` içine kopyala
4. Unity IDE'ye dön ve dosyaların yüklendiğini kontrol et

## Adım 4: Sahneyi Oluştur

### 4.1 Yeni Sahne Oluştur
- **File** > **New Scene** > **2D Core**
- Adlandır: "Main"
- Kaydet: `Assets/Scenes/Main.unity`

### 4.2 Camera Ayarları
- **Main Camera** seçiliyken Inspector'da:
  - Position: (0, 0, -10)
  - Size: 10

### 4.3 GameManager Oluştur
1. **Hierarchy** > **+ (Create)** > **Empty** seç
2. Adlandır: "GameManager"
3. **Add Component** > "GameManager" script ekle
4. **Add Component** > "EggSpawner" script ekle
5. EggSpawner'a bir Prefab atama yapmayacağız (script oluşturacak)

### 4.4 Miho (Player) Oluştur
1. **Hierarchy** > **+ (Create)** > **2D Object** > **Sprite** > **Square** seçin
2. Adlandır: "Miho"
3. Inspector'da ayarlar:
   - Position: (0, -4, 0)
   - Scale: (1, 1, 1)
   - Color: Açık mavi (0.5, 0.7, 1)
4. **Add Component**:
   - **Rigidbody 2D**:
     - Body Type: Dynamic
     - Gravity Scale: 0
     - Constraints: Freeze Rotation Z
   - **BoxCollider2D**:
     - İsActive: true
     - Tag: "Untagged"
5. **Add Component** > "PlayerController" script ekle

### 4.5 Ground Oluştur (Oyun Bittikten Sonra)
1. **Hierarchy** > **+ (Create)** > **2D Object** > **Sprite** > **Square**
2. Adlandır: "Ground"
3. Position: (0, -6, 0)
4. Scale: (20, 1, 1)
5. Color: Gri (0.5, 0.5, 0.5)
6. **Add Component** > **BoxCollider2D**:
   - isTrigger: **ON** (✓)
   - Tag: "Ground" (yeni tag oluştur)

### 4.6 Eggs Parent Oluştur
1. **Hierarchy** > **+ (Create)** > **Empty**
2. Adlandır: "Eggs"
3. GameManager'da:
   - EggSpawner bileşenini seç
   - Spawn Parent: "Eggs" GameObject'i sürükle

### 4.7 UI Canvas Oluştur
1. **Hierarchy** > **+ (Create)** > **UI** > **Canvas**
2. Canvas'ı seçip scale ayarla:
   - Scale Mode: Scale with Screen Size
   - Reference Resolution: (1920, 1080)

3. **Canvas** içine ScoreText oluştur:
   - **Hierarchy** > **+ (Create)** > **UI** > **Text - TextMeshPro**
   - Adlandır: "ScoreText"
   - Properties:
     - Anchor: Top-Left
     - Position: (100, -50)
     - Size: (300, 60)
     - Text: "Skor: 0"
     - Font Size: 36
     - Alignment: Left

4. **Canvas** içine LevelText oluştur:
   - **Hierarchy** > **+ (Create)** > **UI** > **Text - TextMeshPro**
   - Adlandır: "LevelText"
   - Properties:
     - Anchor: Top-Right
     - Position: (-100, -50)
     - Size: (400, 60)
     - Text: "Seviye: 1 (0/5)"
     - Font Size: 36
     - Alignment: Right

5. **Canvas** içine GameOverPanel oluştur:
   - **Hierarchy** > **+ (Create)** > **UI** > **Image**
   - Adlandır: "GameOverPanel"
   - Color: Black, Opacity: 200
   - Properties:
     - Anchor: Center
     - Position: (0, 0)
     - Size: (1920, 1080)

6. GameOverPanel'e GameOverText oluştur:
   - **Hierarchy** > **+ (Create)** > **UI** > **Text - TextMeshPro**
   - Adlandır: "GameOverText"
   - Parent: GameOverPanel
   - Properties:
     - Anchor: Center
     - Text: "OYUN BİTTİ!\n\nFinal Skor: XXX\n\nYeniden Başlamak için BOŞLUK Tuşuna Bas"
     - Font Size: 48
     - Color: White
     - Alignment: Center

### 4.8 GameManager Bağlantıları
1. **Hierarchy** > GameManager seçin
2. **Inspector** > **GameManager** script bölümü:
   - **Score Text**: Canvas > ScoreText sürükle
   - **Level Text**: Canvas > LevelText sürükle
   - **Game Over Panel**: Canvas > GameOverPanel sürükle

## Adım 5: Tag'ları Oluştur
1. **Hierarchy** > herhangi bir GameObject seç
2. **Inspector** > **Tag** > **Add Tag**
3. Ekle:
   - "Egg"
   - "Ground"

## Adım 6: Test Et
1. Play butonuna tıkla
2. **A/D** veya **Sol/Sağ Ok** tuşlarıyla Miho'yu hareket ettir
3. Yumurtaları topla
4. Skor artmasını kontrol et

## Sorun Giderme

### Yumurtalar Spawn Olmuyorsa:
- GameManager > EggSpawner > Spawn Parent'ın "Eggs" olduğunu kontrol et
- Egg prefab'ın otomatik oluşturulduğunu (console'da hata olmadığını) kontrol et

### Yumurtalar Yakalanmıyorsa:
- Miho'nun BoxCollider2D'sinin isTrigger **OFF** olduğunu kontrol et
- Yumurtaların CircleCollider2D'sinin isTrigger **ON** olduğunu kontrol et
- Her iki tarafın da "Egg" tag'ı taşıdığını kontrol et

### Game Over Paneli Görünmüyorsa:
- Canvas'ı seçip GameOverPanel'in deactivated olduğunu kontrol et
- GameManager'da Game Over Panel referansının doğru olduğunu kontrol et

## Android APK Build

### Kurulum
1. **Edit** > **Project Settings** > **Player**
2. **Android** sekmesine git
3. Ayarlar:
   - **Company Name**: Miho Games
   - **Product Name**: Egg Collector
   - **Package Name**: com.mihogames.eggcollector
   - **Minimum API Level**: Android 7.0 (API Level 24)
   - **Target API Level**: Android 13 (API Level 33)

### Build
1. **File** > **Build Settings**
2. **Android** seçin, **Switch Platform** tıkla
3. Scene'i ekle: Main scene'i dosya alanına sürükle
4. **Build** veya **Build and Run** tıkla
5. APK dosyası oluşturulacak
