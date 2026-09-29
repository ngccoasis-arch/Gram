# Telegram API compliance gates

This is an engineering checklist, not legal advice. Review Telegram's current [API Terms of Service](https://core.telegram.org/api/terms) before every public-capable release because Telegram may update them.

## Current conflicts and release blockers

- The current MVP intentionally omits several official-client features. Telegram requires basic features to function correctly and as users expect, so Gram must remain a private development build until the supported/unsupported behavior is reviewed against that requirement.
- Channel support must display and preserve official sponsored messages. A channel-capable release cannot suppress or interfere with them.
- Download scheduling may improve client-side efficiency, but it must not evade Premium, account, flood-control, rate, or server-side limits. Gram must accurately represent server throttling rather than claim a bypass.

## Prohibited designs

- No actions on a user's behalf without their knowledge and consent, unsolicited messaging, hidden automation, or deceptive read/typing/online behavior.
- No ghost mode, read-status tampering, typing-status suppression, last-seen falsification, or retention of content Telegram requires to self-destruct.
- No Telegram data collection or aggregation for training, fine-tuning, or developing AI/ML models.
- No requirement that recipients install Gram to view messages or content sent from Gram.
- No official Telegram logo. The product name must not contain “Telegram” unless it is explicitly prefixed by “Unofficial”. “Gram” currently avoids that naming conflict.

## Required safeguards

- Use Gram's own `api_id`/`api_hash`; never reuse credentials copied from another client.
- State prominently in any in-app introduction and store description that Gram uses the Telegram API and belongs to the Telegram ecosystem.
- Preserve privacy and protect authentication data, local databases, media, and logs. Never log phone numbers, message content, file identifiers, API credentials, or authorization data.
- Preserve protocol semantics and interoperability with other Telegram clients. Unsupported content must be shown honestly rather than silently altered.
- If monetization is ever added, disclose every monetization method in store descriptions.

Before implementing a feature that changes visibility, deletion, read state, presence, sponsored content, automation, data export, scraping, or limits, perform and document another Terms review.
