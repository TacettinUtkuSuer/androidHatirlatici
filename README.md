# Hatırlatıcı

Kişisel fatura / kredi kartı ödeme takip ve hatırlatıcı Android uygulaması.

Kotlin · Jetpack Compose · Material 3 · Room · WorkManager/AlarmManager

Mimari ve yol haritası: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Derleme

Android Studio (Ladybug+) ile açın veya:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Telefona kurulum (bilgisayarsız)

`main`'e her birleştirmede GitHub Actions APK'yı derler.

1. Telefondan GitHub'da repo → **Releases** → **latest** → `Hatirlatici-XX.apk` dosyasını indir.
2. İlk kurulumda "Bilinmeyen uygulamaları yükle" iznini ver.
3. Sonraki sürümler aynı imzayla derlendiği için eskisinin üzerine kurulur; veriler silinmez.

Derleme hatası olursa: **Actions** sekmesi → kırmızı çalışma → hata log'u.
