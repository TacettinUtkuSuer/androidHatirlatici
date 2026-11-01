# Google Drive yedeklemesi — tek seferlik kurulum

Google, bir uygulamanın Drive'a erişebilmesi için o uygulamanın Google Cloud'da tanımlı olmasını ister.
Bu kurulum **bir kez** yapılır, telefondan tarayıcıyla da yapılabilir (~10 dk). Ücretsizdir.

## 1. Proje oluştur
1. https://console.cloud.google.com adresine Google hesabınızla girin.
2. Üstteki proje seçiciden **Yeni Proje** → ad: `Hatirlatici` → Oluştur.

## 2. Drive API'yi aç
**API'ler ve Hizmetler → Kitaplık** → "Google Drive API" → **Etkinleştir**.

## 3. OAuth izin ekranı
**API'ler ve Hizmetler → OAuth izin ekranı** (Google Auth Platform):
- Kullanıcı türü: **Harici (External)**
- Uygulama adı: `Hatırlatıcı`, destek e-postası: kendi adresiniz
- Kapsamlar (Scopes): `.../auth/drive.file` ekleyin (hassas olmayan kapsam — Google doğrulaması gerekmez)
- Test kullanıcıları: kendi Gmail adresinizi ekleyin
- İsterseniz **Yayınla (Publish app / In production)** deyin; `drive.file` hassas olmadığı için onay beklemeden geçer
  ve oturum 7 günde bir düşmez.

## 4. Android OAuth istemcisi
**API'ler ve Hizmetler → Kimlik bilgileri → Kimlik bilgisi oluştur → OAuth istemci kimliği**:
- Uygulama türü: **Android**
- Paket adı: `com.utkusuer.hatirlatici`
- SHA-1 sertifika parmak izi (GitHub Actions ile derlenen APK'nın imzası):

```
C6:E8:64:E6:08:EA:23:DA:34:DE:6C:16:8B:70:1F:FC:7B:A2:79:80
```

Oluştur deyin. Uygulamaya herhangi bir anahtar/kod girmeniz gerekmez; Google eşleşmeyi paket adı + SHA-1 ile yapar.

## 5. Uygulamada
Ayarlar → Yedekleme → **Drive'a yedekle** → Google hesabınızı seçin → izin verin.
Yedekler Drive'ınızda **"Hatırlatıcı Yedekleri"** klasöründe görünür; son 10 yedek tutulur.

## Notlar
- Uygulama yalnızca kendi oluşturduğu dosyaları görebilir (`drive.file`); Drive'ınızdaki diğer dosyalara erişemez.
- Kart / hesap bilgileri yalnızca siz **parola belirleyerek** dahil ederseniz yedeğe girer ve o parolayla
  (PBKDF2 + AES-256-GCM) şifrelenir. Haftalık otomatik yedekte bu bilgiler yoktur.
- "OAuth istemcisi tanımlı değil" hatası alırsanız 4. adımdaki paket adı ve SHA-1'i kontrol edin
  (ayarın Google tarafında etkinleşmesi birkaç dakika sürebilir).
