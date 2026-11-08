# 🐱 Merge Pets

A cozy idle merge game for Android. Hatch eggs, drag identical pets together to merge them into cuter ones (14 levels, from 🥚 to 🦄), earn coins — even while you're away.

- Languages: English (default) and Russian
- No ads, no network, no permissions
- Native Kotlin, no third-party libraries

## Build

Requirements: JDK 17 and the Android SDK (platform 36).

```bash
./gradlew assembleDebug      # debug APK for testing
./gradlew assembleRelease    # signed release APK
./gradlew bundleRelease      # signed AAB for Google Play
```

Release signing reads `keystore.properties` (not committed):

```properties
storeFile=keystore/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Without that file the release build is produced unsigned.

## Store assets

`store/` contains the Play Store icon, feature graphic, listing texts (EN/RU) and the script that generates the graphics. The privacy policy page is in `docs/` (served by GitHub Pages).
