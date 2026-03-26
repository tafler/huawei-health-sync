# Huawei Health Extractor

Android-приложение для Samsung Galaxy S24 (Android 14+), которое:
1. Авторизуется в Huawei Health Kit через OAuth 2.0 (WebView).
2. Каждые 6 часов (WorkManager) забирает шаги, пульс и сон через REST API.
3. Загружает результат в папку Google Drive в формате JSON.

---

## Быстрый старт

### 1. Получить Huawei App Secret

1. Откройте [AppGallery Connect](https://developer.huawei.com/consumer/en/console) → ваше приложение.
2. Скопируйте **App Secret** (не путать с App ID).
3. В файле `HuaweiRepository.kt` замените:
   ```kotlin
   const val APP_SECRET = "YOUR_HUAWEI_APP_SECRET"
   ```

### 2. Получить Google Client Secret

1. Откройте [Google Cloud Console](https://console.cloud.google.com/) → Credentials.
2. Найдите OAuth 2.0 Client ID `179608551541-...` → скопируйте **Client secret**.
3. В файле `GoogleDriveRepository.kt` замените:
   ```kotlin
   const val GOOGLE_CLIENT_SECRET = "YOUR_GOOGLE_CLIENT_SECRET"
   ```

### 3. Получить Google refresh token

Вы можете получить refresh token одним из способов:

**OAuth Playground:**
```
https://developers.google.com/oauthplayground/
```
- Scope: `https://www.googleapis.com/auth/drive.file`
- Укажите ваш Client ID / Client Secret (Use your own OAuth credentials ✓)
- Нажмите Authorize → Exchange authorization code for tokens
- Скопируйте `refresh_token`

**Вставьте** полученный refresh token в поле ввода приложения при первом запуске.

---

## Файловая структура

```
app/
  build.gradle.kts
  proguard-rules.pro
  src/main/
    AndroidManifest.xml
    kotlin/ru/tafinceva/health/
      HealthApplication.kt          — создание notification channel
      MainActivity.kt               — главный экран
      AuthWebViewActivity.kt        — OAuth WebView для Huawei
      SyncWorker.kt                 — WorkManager CoroutineWorker
      SyncScheduler.kt              — планировщик (каждые 6 ч)
      SyncForegroundService.kt      — foreground service (dataSync)
      HuaweiApi.kt                  — Retrofit интерфейсы + фабрика
      HuaweiRepository.kt           — логика запросов + парсинг
      GoogleDriveRepository.kt      — загрузка JSON в Drive
      TokenManager.kt               — EncryptedSharedPreferences
      Models.kt                     — data classes
    res/layout/
      activity_main.xml
      activity_auth_webview.xml
    res/values/
      strings.xml / colors.xml / themes.xml
build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
```

---

## Формат выходного JSON

```json
{
  "date": "2026-03-26",
  "steps": 8500,
  "heartRate": { "avg": 72, "min": 55, "max": 130 },
  "sleep": { "totalHours": 7.5, "deepHours": 1.2, "remHours": 1.8 }
}
```

Имя файла: `health-YYYY-MM-DD.json`
Папка: Google Drive, ID `1oGxmtsrFKcG-2izXelGlQ5rZ_wNKyGgx`

---

## Зависимости

| Библиотека               | Версия |
|--------------------------|--------|
| Retrofit                 | 2.11.0 |
| OkHttp                   | 4.12.0 |
| Gson                     | 2.10.1 |
| WorkManager              | 2.9.0  |
| Security Crypto          | 1.1.0-alpha06 |
| Kotlin Coroutines        | 1.8.1  |
| Material Components      | 1.12.0 |

---

## Требования

- minSdk 26 (Android 8.0)
- targetSdk 34 (Android 14)
- Устройство с доступом к интернету
- Аккаунт Huawei с данными Health Kit
- Google аккаунт с правами на указанную папку Drive
