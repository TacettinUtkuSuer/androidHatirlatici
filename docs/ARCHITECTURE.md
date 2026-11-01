# Hatırlatıcı — Mimari Plan

Kotlin · Jetpack Compose · Material 3 · Room · WorkManager + AlarmManager · DataStore · Android Keystore
minSdk 26 · targetSdk 35 · Paket: `com.utkusuer.hatirlatici`

## 1. Dosya yapısı

```
app/src/main/java/com/utkusuer/hatirlatici/
├── HatirlaticiApp.kt                 Application: kanallar + periyodik senkron
├── di/AppContainer.kt                Manuel DI (lazy singleton'lar)
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt            Room v1 (şema: app/schemas)
│   │   ├── entity/                   PaymentEntity, ReminderTimeEntity, PaymentCycleEntity, Enums, Relations
│   │   └── dao/                      PaymentDao, CycleDao
│   ├── security/PinCipher.kt         Keystore AES-256/GCM ile PIN şifreleme
│   ├── settings/SettingsRepository   DataStore: varsayılan saatler, global anahtarlar
│   ├── repository/PaymentRepository  UI'ın tek giriş noktası
│   └── backup/        (Faz 4)        BackupSerializer (JSON), LocalBackup (SAF), DriveBackup
├── domain/
│   ├── CycleCalculator.kt            Ay → dönem penceresi (saf Kotlin, unit test'li)
│   └── Formatters.kt                 ₺ / tarih / saat biçimleri
├── notification/
│   ├── NotificationChannels.kt       ongoing_due (sessiz, kalıcı) · reminders (yüksek öncelik)
│   ├── PaymentNotifier.kt            Bildirim oluşturma / kaldırma
│   ├── ReminderScheduler.kt          AlarmManager: ödeme başına "sonraki hatırlatma" + gün dönümü
│   └── Receivers.kt                  ReminderAlarm · NotificationDismissed · Boot
├── work/
│   ├── ReminderEngine.kt             Bildirim sisteminin beyni (idempotent, Mutex'li)
│   └── ReminderSync.kt               WorkManager: anlık + 6 saatlik güvenlik senkronu
├── ui/
│   ├── theme/                        Monokrom palet + tek arduvaz vurgusu
│   ├── confirm/ConfirmPaidActivity   "Onaylıyor musunuz?" diyaloğu
│   ├── MainActivity.kt               (Faz 2) NavHost + adaptif navigasyon
│   ├── dashboard/     (Faz 2)        Bugün ödenecek / yaklaşan / gecikmiş
│   ├── calendar/      (Faz 2)        Aylık ızgara, gün işaretleri, alt sayfa detay
│   ├── edit/          (Faz 2)        Detaylı ekleme/düzenleme formu
│   └── settings/      (Faz 3)        Bildirim, saatler, yedekleme, pasif kayıtlar
└── widget/            (Faz 3)        Glance liste widget'ı
```

## 2. Veri modeli

| Tablo | Amaç | Önemli alanlar |
|---|---|---|
| `payments` | Ödeme tanımı | `scheduleType` (SINGLE_DAY / DATE_RANGE), `dayStart`, `dayEnd` (küçükse sonraki aya taşar), `reminderDelayDays` ("ilk X gün hatırlatma"), `leadDays` (tek günde X gün önce), `amountMinor: Long?` (kuruş; null = değişken), `isActive` (pasif = hiç bildirim yok), `useDefaultReminderTimes`, `encryptedPin`, `totalInstallments`, `endDateEpochDay` |
| `reminder_times` | Ödemeye özel gün içi saatler | `(paymentId, minuteOfDay)` |
| `payment_cycles` | Her ayın dönemi + ödendi durumu | `periodKey` "yyyy-MM" (unique), `windowStart/End`, `notifyFrom`, `status` (PENDING/PAID/SKIPPED), `paidAt` |

**v2 eklemeleri** (Migration 1→2): `payments.kind` (EXPENSE/INCOME), `weekendRule` (NONE/BEFORE/AFTER/NEAREST), `bankCode`, `encryptedSecrets` (kart no, SKT, CVV, PIN, internet şifresi, hesap no/IBAN — tek şifreli JSON) ve `activity_log` tablosu (ödeme hareketleri ve kayıt işlemleri ayrı türlerle).

Dönem ayrı tablo olduğu için: geçmiş aylar takvimde görünür, ödenmeyen eski dönemler "gecikti" olarak bildirim üretmeye devam eder, düzenlemede yalnızca PENDING dönemler yeniden hesaplanır.

## 3. Bildirim sistemi

```
Tetikleyiciler                          ReminderEngine (Mutex)                 Çıktı
──────────────                          ─────────────────────                  ─────
App açılışı / kayıt değişikliği ─┐      1. ensureCycles(önceki/bu/sonraki ay)
WorkManager (6 saatte bir)      ├──▶   2. due = PENDING ∧ notifyFrom ≤ bugün ──▶ Kalıcı bildirim (göster/kaldır)
Gün dönümü alarmı (00:01)       │      3. sonraki saat (10:00/14:00/19:00) ──▶ AlarmManager.setExactAndAllowWhileIdle
Boot / saat dilimi değişimi     │
Hatırlatma alarmı ──────────────┴──▶   + heads-up hatırlatma bildirimi
Kalıcı bildirim kaydırıldı ─────────▶  refreshPayment → bildirim anında geri gelir
"Ödemeyi Yaptım" → ConfirmPaidActivity → [Evet] → markPaid → bildirimler kalkar
                                         [Vazgeç] → hiçbir şey değişmez
```

Kararlar:
- **Saatli uyarılar AlarmManager ile**: WorkManager dakika hassasiyeti garanti etmez. `USE_EXACT_ALARM` (API 33+) kişisel kullanım için uygundur; izin yoksa inexact'e düşer.
- **Kaydırılamayan bildirim**: Android 14+ `setOngoing` bildirimlerini kullanıcı kaydırabiliyor. Çözüm: `deleteIntent` → `NotificationDismissedReceiver` → ödeme hâlâ bekliyorsa bildirim hemen yeniden gösterilir. (Foreground service kullanılmadı: pil ve Samsung'un arka plan kısıtları.)
- **Çift onay**: Bildirim aksiyonu doğrudan şeffaf `ConfirmPaidActivity` açar (Android 12+ trampoline yasağına uygun). Uygulama içinden de aynı diyalog kullanılacak.
- **Pasif kayıt**: Engine ilk adımda `isActive=false` ise tüm bildirim ve alarmları iptal eder.
- **Samsung notu**: Ayarlar ekranına "Pil → Kısıtlanmamış" yönlendirmesi eklenecek (One UI uygulamayı "uyku"ya alabiliyor).

## 3b. Pil tüketimi

- Arka planda sürekli çalışan servis yok; konum, sensör, ağ dinleme yok.
- Tüm kayıtlar için **tek** bir tam zamanlı alarm: cihaz yalnızca bir sonraki hatırlatma saatinde kısa süreliğine uyanır,
  aynı saatteki hatırlatmalar tek uyanmada gönderilir.
- Gün dönümü alarmı esnektir (`AlarmManager.set`, RTC — cihazı ayrıca uyandırmaz).
- Güvenlik ağı senkronu 12 saatte bir (WorkManager); haftalık Drive yedeği yalnızca internet varken ve pil düşük değilken.
- Widget periyodik güncellenmez (`updatePeriodMillis=0`); yalnızca veri değişince yenilenir.

## 4. Güvenlik

- PIN: `PinCipher` → Android Keystore'da üretilen, dışa aktarılamayan AES-256/GCM anahtarı. DB'de yalnızca `v1:Base64(iv‖ciphertext)`.
- Görüntüleme (Faz 3): `BiometricPrompt` ile doğrulama sonrası.
- Sistem yedeği kapalı (`allowBackup=false`); Keystore anahtarı cihaza bağlı olduğundan **yedeklerde PIN'ler kullanıcı parolasıyla (PBKDF2 + AES-GCM) yeniden şifrelenir** ya da hariç tutulur.

## 5. Yol haritası

| Faz | İçerik |
|---|---|
| 1 ✅ | Gradle, Room şeması, Keystore, DataStore, bildirim motoru, onay diyaloğu, CycleCalculator testleri |
| **2 ✅** | Dashboard, Takvim, Ekleme/Düzenleme formu, navigasyon (WindowSizeClass ile telefon/tablet/katlanabilir) |
| **3 ✅** | Ayarlar ekranı, Glance widget'ı, biyometrik doğrulamayla kart bilgileri, izin/pil yönlendirmeleri, gelir kayıtları, hafta sonu kuralı, banka rozetleri, işlem geçmişi (log), yeni ikon |
| **4 ✅** | JSON yedek: yerel (SAF) import/export + Google Sign-In (Credential Manager + AuthorizationClient, `drive.appdata` kapsamı) ile Drive yedekle/geri yükle |
