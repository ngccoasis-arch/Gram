# Gram

Gram is a private, Android-only Telegram client focused on fast photo viewing, responsive video playback, and transparent download management. The project is not a Telegram Premium replacement and does not attempt to bypass Telegram server-side limits.

## Current state

The repository contains the three-module Android foundation, a deterministic offline demo backend, and a production backend over the pinned official TDLib Java/JNI API. The production path handles existing-account authorization, real chat/history updates, text sending, common message/media mapping, and TDLib downloads. The Compose shell includes download controls, gallery paging and 3× zoom, Media3 player plumbing, the requested video gestures, Keystore bootstrap, DataStore settings, and an Android user-initiated transfer job. Unit tests cover the central transfer, gallery, and gesture policies.

Live Telegram access requires a production CI build: first build the pinned official TDLib artifact, then build the APK with the repository secrets `TELEGRAM_API_ID` and `TELEGRAM_API_HASH`. CI refuses to upload a production APK if either secret is absent. See [setup](docs/setup.md), [requirements](docs/requirements.md), [architecture](docs/architecture.md), [Telegram API compliance](docs/telegram-api-compliance.md), and [roadmap](docs/roadmap.md).

## Toolchain

- JDK 17
- Android SDK/API 36 and Build Tools 35+
- Gradle 8.13 / Android Gradle Plugin 8.13.2
- Kotlin 2.3.21
- Android 14 minimum; Android 16 primary target

```powershell
./gradlew -PgramTdlibMode=demo testDebugUnitTest lintDebug assembleDebug
```

`Build pinned TDLib` performs the expensive native build only when the pin/tooling changes or the workflow is dispatched. It checks out the official `tdlib/td` repository at the exact SHA in `gradle/tdlib.versions.properties`, invokes the upstream Android scripts, packages the configured ABI set (initially `arm64-v8a`) into an AAR, and uploads both the reusable output and a TDLib-backed debug APK.

Ordinary `Build Android APK` runs never compile native code. They download the matching unexpired TDLib Actions artifact, verify its checksums and commit metadata, run tests/lint, and upload `app-debug.apk`.

The project does not require a local NDK, CMake, Ninja, Docker, or TDLib compilation. A local JDK/Android SDK is optional for JNI-free demo work; production CI owns native tooling.

## Safety

Use a dedicated `api_id` and `api_hash` from `my.telegram.org`. Configure the repository secrets `TELEGRAM_API_ID` and `TELEGRAM_API_HASH`; never commit them. Missing values compile to inert placeholders. Early live testing uses the main account only through explicit user actions; Gram contains no automated messaging.

