# Keyic

Offline-first Android password manager. No account, no Keyic cloud, and **no `INTERNET` permission** — the vault stays on your device unless you export it.

**Package:** `com.yishenghuang.keyic`  
**Privacy policy:** https://keyic-privacy.netlify.app/

## Features

- Encrypted vault (Argon2id + AES-GCM / SQLCipher)
- Logins, secure notes, cards, identities
- System Autofill + TOTP / Auth tab
- Password generator and security health checks
- Encrypted `.keyic` backup via SAF (Drive / OneDrive / local folder)
- CSV and KeePass (`.kdbx`) import / export
- Multi-vault, biometric unlock, tablet / foldable layouts

## Modules

| Module | Role |
|--------|------|
| `:app` | Compose UI, Autofill service, camera scan, WorkManager |
| `:core` | Domain models, crypto helpers, CSV, matching, health |
| `:data` | SQLCipher Room DB, session, settings, backup adapters |
| `:keepass-android` | Private Commons Codec packaging and StAX API for KeePass on Android |

## Requirements

- Android Studio + JDK 21 for the Gradle daemon (Android Studio JBR is fine); Gradle provisions the core module’s JDK 11 toolchain.
- `minSdk` 26 · `targetSdk` 36

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:bundleRelease   # needs keystore.properties (not in git)
```

### Release signing

Create `keystore.properties` at the repo root (gitignored):

```properties
storeFile=keystore/keyic-upload.jks
storePassword=…
keyAlias=keyic
keyPassword=…
```

Never commit `keystore/`, `*.jks`, or `keystore.properties`.

## Debug sample data

Debug builds include a seed activity (not in release):

```bash
adb shell am start -n com.yishenghuang.keyic/.debug.DebugSeedActivity -e password testdata1
```

Sample CSV also lives in `testdata/keyic-sample-import.csv`.

## License / attribution

Brand logos: Streamline Logos solid (CC BY 4.0) — [streamlinehq.com](https://streamlinehq.com). See `app/NOTICE_STREAMLINE.txt`.
