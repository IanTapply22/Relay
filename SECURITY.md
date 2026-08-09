# Security policy

## Supported versions

Security fixes are provided for the latest released version of Relay. Upgrade to the newest release before reporting behavior that may already have been corrected.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use GitHub's [private security-advisory reporting flow](https://github.com/IanTapply22/Relay/security/advisories/new) and include:

- affected Relay and platform versions;
- the expected and observed behavior;
- impact and realistic attack conditions;
- minimal reproduction steps or a proof of concept;
- relevant configuration with all credentials removed; and
- any proposed mitigation.

Allow time for investigation and a coordinated fix before publicly disclosing the issue. Do not access systems, Redis instances, messages, or credentials that you do not own or have explicit permission to test.

## Operational security

- Keep Redis credentials out of committed configuration. Prefer the configured environment variable or secret-file setting.
- Use `rediss://` outside trusted networks and keep certificate hostname verification enabled.
- Restrict Redis to trusted networks, enable authentication or ACLs, and grant Relay only the commands and channels it requires.
- Give every node a unique identifier and isolate unrelated environments with separate Redis namespaces and credentials.
- Treat anyone able to publish to Relay's Redis channels as a trusted message producer; Redis Pub/Sub does not authenticate individual messages.
- Keep payload limits, message-age checks, bounded dispatch queues, and channel/destination validation enabled.
- Do not include secrets or sensitive player data in payloads, headers, diagnostics, or logs.
- Keep host clocks synchronized because Relay rejects stale and significantly future-dated messages.
- Rotate credentials after suspected exposure and restart every affected Relay node.

Relay provides transient, at-most-once messaging rather than durable storage. Store authoritative application state in an appropriately secured system and make handlers idempotent.
