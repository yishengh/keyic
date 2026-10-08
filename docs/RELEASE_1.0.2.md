# Keyic 1.0.2 (3) production release

The user authorized pushing main, building signed artifacts, and publishing to Google Play production on 2026-10-07 (America/New_York). This supersedes the previous development-only phase for this release; the privacy website remains unchanged.

- Package: `com.yishenghuang.keyic`; versionName `1.0.2`, versionCode `3` (Play already contains codes 1 and 2).
- Existing upload signing configuration and Google Play App Signing retained; no new signing identity.
- Fixed release-only duplicate ListenableFuture classes by relocating bundled KeePass Guava. No cryptographic algorithms changed.
- `:app:bundleRelease :app:assembleRelease :app:lintRelease :data:testDebugUnitTest :data:assembleDebugAndroidTest` passed. Release lint: 0 errors, 90 warnings, same documented warning categories as the preceding local validation.
- APK signature and 16 KiB ZIP alignment verified. Package metadata confirms minSdk26, targetSdk36, no INTERNET permission and no debuggable flag.
- AAB: all 1,222 non-META-INF payload entries verified with JarFile signature verification. Android upload certificates are self-signed; the JDK jarsigner also emits streaming ZIP-order warnings for the Gradle AAB. Google Play upload validation remains the distribution authority.
- Release APK installed and MainActivity started successfully on isolated KeyicValidation26/API26; process remained running.
- After Guava relocation: 3 KeePass unit tests and all 13 data-device regression tests passed, including encrypted backup/attachment restoration, SQL failure rollback, migrations, and SAF failure preserving old copies. Test data is synthetic.

## Artifacts

- `app/build/outputs/bundle/release/app-release.aab` — SHA-256 `C8926A6EE12E0A41C9FA340C891034391F4D7EA06CC3AFDB7BCBA5CE36E8D0D1`
- `app/build/outputs/apk/release/app-release.apk` — SHA-256 `DB04F31A19828BCE1C8CB2C011162BCCF49A20B03C02F1E2D40E39F4A9AB6184`
- Logs: `build/production-release-build.log`, `build/production-data-regression.log`.

## Release notes

Improved vault reliability, backup and restore, attachment preservation, and KeePass compatibility. Strengthened autofill matching, automatic locking, and clipboard handling. Updated settings, help, accessibility labels, and translations.

## Play status

Production access is granted. Submission outcome will be recorded after the Console operation; local artifact validation does not mean that Google Play has approved or published the app.
