# Gram contributor guide

## Product boundaries

Gram is a private Android Telegram client. It uses TDLib and must never attempt to bypass Telegram account, Premium, flood, or server limits. Do not copy code from Plus Messenger or Telegram Android; those GPL projects are behavioral references only.

The MVP supports one account and cloud chats. Notifications, secret chats, calls, stories, multiple accounts, shared-storage export, and public distribution are intentionally out of scope.

## Engineering rules

- Kotlin and Compose; min SDK 34, compile/target SDK 36.
- Keep `org.drinkless.tdlib` types inside `core-tdlib`.
- TDLib must come only from `https://github.com/tdlib/td` at the exact SHA in `gradle/tdlib.versions.properties`. Do not add third-party TDLib Maven or JitPack dependencies.
- Native TDLib compilation is CI-only. Normal APK builds consume the checked, reusable Actions artifact for the pinned commit.
- Never commit Telegram `api_id`, `api_hash`, authentication data, TDLib databases, native AARs, or user content.
- Redact phone numbers, message text, file identifiers, and credentials from logs.
- Preserve partial media on cancellation. Never delete completed or partial downloads automatically.
- Warn below 5 GB free; hard-pause transfers below 1 GB.
- Foreground media has priority over bulk downloads. Preserve the persistent full-file intent across TDLib range retargeting; TDLib allows only one active request per file, so resume the full request after the foreground range is available.
- Add tests for state reducers and calculations whenever behavior changes.
- Treat `docs/telegram-api-compliance.md` as release-gating requirements. In particular, do not implement ghost mode, self-destruct retention, undisclosed automation, sponsored-message suppression, Premium-limit evasion, or Telegram-data AI training.

## Verification

The expensive `Build pinned TDLib` workflow checks out the official repository at the exact pin, invokes its Android scripts, packages the selected ABIs, and uploads a reusable checked artifact. Ordinary Android CI downloads that artifact and runs `./gradlew -PgramTdlibMode=production testDebugUnitTest lintDebug assembleDebug`. Local/demo verification remains JNI-free and requires no native toolchain.

