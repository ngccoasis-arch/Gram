# Setup

## Toolchains

No local native toolchain is required. `Build pinned TDLib` provisions JDK 17 and invokes Telegram's official Android build scripts on a GitHub-hosted Linux runner; those scripts own the NDK, CMake, Ninja, OpenSSL, and generated Java/JNI work. Ordinary APK CI only provisions JDK 17 and Android SDK 36, then consumes the reusable TDLib artifact.

A local JDK 17 and Android SDK 36 are optional for demo-mode Gradle work. Local TDLib compilation is not part of the supported workflow.

## Telegram credentials

Create a dedicated application at `https://my.telegram.org`, then configure these GitHub Actions repository secrets:

- `TELEGRAM_API_ID`
- `TELEGRAM_API_HASH`

For an optional local build, copy `telegram.properties.example` to the ignored `telegram.properties` file:

```properties
telegram.apiId=123456
telegram.apiHash=replace_me
```

Gradle exposes the values as `BuildConfig.TELEGRAM_API_ID`, `BuildConfig.TELEGRAM_API_HASH`, and `BuildConfig.TELEGRAM_API_CREDENTIALS_CONFIGURED`. Missing values become `0`, `not-configured`, and `false`, so demo source builds remain configurable without real credentials. Production GitHub workflows fail before publishing an APK when either secret is absent; this prevents another apparently live but unusable artifact. Do not print these fields or paste values into Kotlin, documentation, issues, or logs. Any API credentials embedded in an APK can ultimately be extracted, so artifact access must remain private even though the values are not user authorization tokens.

## TDLib

TDLib is pinned to the exact official commit in `gradle/tdlib.versions.properties`. The pin currently selects `42e6a5259551178d1dab54a22ad96d14bd906e20` from `https://github.com/tdlib/td.git` and the `arm64-v8a` ABI. Adding an ABI is a deliberate edit to `TDLIB_ANDROID_ABIS` followed by a new native workflow run.

The workflow uses the upstream `example/android/fetch-sdk.sh`, `build-openssl.sh`, and `build-tdlib.sh`. A guarded CI-only patch restricts the two upstream ABI loops to the checked-in ABI list; it fails closed if upstream changes that code. The official output's generated Java and JNI library are packaged into `tdlib.aar`, with the upstream archive, embedded and standalone license, metadata, and SHA-256 manifest retained in the Actions artifact. NDK r28 is pinned because it produces 16 KB-aligned libraries by default; CI verifies every TDLib ELF LOAD segment plus final APK ZIP alignment.

The checked-in production backend uses the generated API from that exact AAR for existing-account authorization, chat/history updates, text messages, common media mapping, and downloads. Demo mode remains available without an AAR; production mode additionally verifies the downloaded AAR hash and commit metadata from the ignored `prebuilts/tdlib-maven` repository at Gradle configuration time. QR login, new-account registration, and media upload from Android picker URIs remain outside this foundation milestone.

## GitHub Actions build

Bootstrap a new pin by running `Build pinned TDLib`. It uploads `tdlib-android-<commit>-<abis>` for 90 days and also produces a debug APK. Subsequent `Build Android APK` runs download that exact artifact and run:

```text
./gradlew --no-daemon -PgramTdlibMode=production testDebugUnitTest lintDebug assembleDebug
```

The resulting `app/build/outputs/apk/debug/app-debug.apk` is uploaded for 14 days. If the pinned TDLib artifact has expired, rerun `Build pinned TDLib`; ordinary APK CI never falls back to an unpinned dependency or recompiles TDLib.

## POCO configuration

Set Gram battery usage to **No restrictions** and allow background/autostart. Long downloads use a visible user-initiated transfer job and remain stoppable from Android system UI.

