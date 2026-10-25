# Build DriverKit

## GitHub Actions

1. Buka tab **Actions** -> **Build DriverKit APK**.
2. Pilih **Run workflow** pada branch `fix/android-sdk-workflow`.
3. Unduh artifact `driverkit-debug-apk`.

## Lokal

```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Spesifikasi

| Item | Nilai |
|---|---|
| Namespace | `id.driverkit.overlay` |
| minSdk | 26 |
| compileSdk / targetSdk | 35 |
| AGP | 8.5.2 |
| Kotlin | 1.9.24 |
| JDK | 17 |
