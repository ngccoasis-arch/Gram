# Requirements

## Confirmed MVP

- Private sideloaded app, package `com.gram.client`, English-only and dark-only.
- One existing Telegram account and cloud chats: private, group, supergroup, and channel.
- Complete TDLib-driven authorization state handling except QR login.
- Render text, photo, video, document, voice/audio, sticker, animation, contact, location, poll, and service messages.
- Send text and system-picker photos/videos. Multiple selections are sent separately as inline, highest-practical-quality media.
- No incoming-message notifications.
- Persistent app-private media; manual cleanup only; partial bytes survive cancellation.
- Download queue with progress, sampled speed, pause/resume/cancel/retry, and Auto/1/2/4/8 concurrency settings.
- Warn below 5 GB free; pause all new transfers below 1 GB free.
- Same-chat photo/video gallery. Documents are excluded. Swipe, pan, pinch, and double-tap 1x/3x zoom are required.
- Full-resolution previous/next prefetch on all networks. Once started, prefetch finishes.
- Progressive Media3 playback backed by TDLib. The persistent full-download intent survives viewer seeks. Because TDLib permits only one active download request per file, an unavailable seek temporarily retargets that request to the required range; the coordinator restores the full download afterward and retains all cached bytes.
- Player thirds: left backward, center controls, right forward. Double taps seek five seconds. Hold starts at 2x and drag varies continuously up to 8x.
- HyperOS battery mode must be set to No restrictions for reliable long downloads.
- TDLib is built only from the official `tdlib/td` repository at the exact checked-in commit. Normal APK builds consume the matching prebuilt Actions artifact; no unpinned Maven/JitPack TDLib package is allowed.
- Telegram API credentials come from ignored local properties or the `TELEGRAM_API_ID`/`TELEGRAM_API_HASH` GitHub secrets. Placeholder builds must remain possible without real values.
- Telegram API Terms compliance is a release gate; see `docs/telegram-api-compliance.md`.

## Explicit limitations

- Gram does not remove Telegram's non-Premium speed limits.
- TDLib cannot run a full-file request and a different range request concurrently for the same file. "Continue the full download" therefore means preserve and resume its queue intent, not simultaneous per-file requests.
- Reverse playback is simulated with repeated seeks; smooth negative-rate decoding is not promised.
- Audio remains requested at accelerated positive rates, but devices may clamp, distort, drop frames, or buffer.
- App-private media is removed on uninstall.
- The initial native artifact supports `arm64-v8a`. Other ABIs require an explicit pin/configuration update and rebuilt artifact.

## Deferred

Chat folders, multiple accounts, push notifications, secret chats, calls, stories, PiP, subtitles, audio-track selection, background audio, broad custom theming, public distribution, and shared-storage export.

