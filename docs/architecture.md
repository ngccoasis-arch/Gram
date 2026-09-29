# Architecture

## Modules

- `app`: Compose UI, navigation, DataStore preferences, Android jobs, and dependency assembly.
- `core-tdlib`: Gram domain models, session/file/download contracts, demo session, and the artifact-gated typed JNI integration boundary.
- `core-media`: Media3 data source, viewer/player state, speed/gesture calculations, and media presentation components.

TDLib objects never leave `core-tdlib`. UI observes immutable `StateFlow`/`Flow` values containing Gram-owned models.

## Runtime flow

`TelegramSession` owns one TDLib client and maps authorization and message updates. `TelegramFileRepository` maintains canonical file state. `DownloadCoordinator` owns persistent full-download intent and serializes requests per file. `TdlibMediaDataSource` reads available bytes directly and asks the coordinator for missing ranges.

TDLib supports only one concurrent `downloadFile` request for a given file. A seek to unavailable bytes therefore supersedes the current offset/limit request; after the sought range is available, the coordinator reissues the full request at its prior queue priority. Already downloaded parts remain cached. This is a scheduler handoff, not two simultaneous network streams. See the official [`downloadFile` contract](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1download_file.html) and the TDLib maintainer's [one-request-per-file clarification](https://github.com/tdlib/td/issues/3017#issuecomment-2283539106).

Persistent download priorities are: viewer range 32, current full file 31, explicit downloads 16, and adjacent prefetch 8. The effective active limit starts at two; fixed 1/2/4/8 benchmarks select the later Auto value separately for Wi-Fi and mobile.

## Security

TDLib databases and files live in app-private storage. `DatabaseKeyStore` creates a random database key and encrypts it using an Android Keystore AES-GCM key. Credentials are injected via untracked local Gradle properties or environment variables.

## Demo and production modes

`gramTdlibMode` explicitly selects the backend and defaults to `demo`. Demo mode never compiles or resolves JNI classes, even if a local AAR happens to exist. Production mode requires the exact module and metadata in the ignored `prebuilts/tdlib-maven` repository. Gradle verifies the AAR SHA-256 and exact TDLib commit against `gradle/tdlib.versions.properties` before compiling. Publishing the AAR as a file-backed Maven module keeps it a transitive dependency of `core-tdlib` without embedding or duplicating the native library. The production source set owns the typed authorization, chat, message, file, and download mapping while TDLib types remain behind Gram-owned interfaces.

The native workflow builds one immutable logical unit: official generated Java classes plus the matching `libtdjni.so` for each configured ABI. Initially only `arm64-v8a` is packaged. The pinned NDK r28 build and CI checks require 16 KB ELF/APK alignment for Android 15/16-era devices. Ordinary APK jobs download the exact reusable Actions artifact and never install an NDK or invoke CMake/Ninja. Telegram credentials enter only the app module's generated `BuildConfig`; application assembly passes them into TDLib's parameters at runtime, and no real value is stored in source or logs.

