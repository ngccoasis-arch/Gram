# Roadmap

1. **Foundation** — repository, Gradle modules, documentation, dark shell, demo backend, deterministic tests.
2. **TDLib** — pinned official-source CI build, credential injection, typed existing-account authorization/update adapter, and safe close/logout (foundation complete; CI/device verification remaining).
3. **Chats** — chat list/history, common content mapping, text and media sends, upload state.
4. **Transfers** — persistent queue, speed sampling, controls, storage safety, Android UIDT job and notification.
5. **Media** — same-chat gallery, original prefetch, progressive Media3 source, priority remote seeking, gestures.
6. **Hardening** — POCO/HyperOS lifecycle tests, matched Plus/Telegram benchmarks, redacted diagnostics, recovery UI.

Implemented foundation pieces: Gradle modules and wrapper, durable internal contracts, deterministic demo backend, dark Compose shell, common-content placeholders, picker sends, queue controls, policy tests, same-chat media sequencing, 3× photo transforms, Media3 TDLib-range data source, requested player gesture math/UI, Keystore bootstrap, DataStore concurrency, and the visible UIDT job.

Still required for the full media MVP: Android picker URI-to-private-file media upload, complete persistent queue recovery/concurrency gating, continuous storage monitoring, full error recovery, device/build verification, and all POCO/Plus/official benchmark trials. The login-capable foundation precedes those remaining stages. Server-side throttling is reported as an environmental constraint, not hidden or misrepresented as a client defect.

