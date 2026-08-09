# Contributing to Relay

Thank you for improving Relay. Keep changes focused, testable, and compatible with both Paper and Velocity where applicable.

## Development setup

1. Install JDK 25. The build uses a Java toolchain, but running Gradle with JDK 25 most closely matches CI.
2. Clone the repository and use the checked-in Gradle wrapper.
3. Run `./gradlew installGitHooks` (`.\gradlew.bat installGitHooks` on Windows) to enable the tracked pre-commit hook.
4. Run `./gradlew check javadoc jar` (`.\gradlew.bat` on Windows) before submitting a change.
5. Use `./gradlew runServer` when a local Paper environment is useful.

The Redis tests use local protocol fixtures and do not require a separately installed Redis server.

## Making changes

- Add tests for behavior changes and regressions.
- Keep public messaging contracts in `relay-api` and platform-neutral behavior in `relay-core`.
- Keep Paper and Velocity APIs inside their respective platform modules.
- Treat public Java types, topic names, content types, envelope fields, schema versions, destination names, and Redis channel formats as compatibility surfaces.
- Preserve bounded queues, payload limits, channel/destination validation, and handler isolation.
- Never log message payloads, Redis credentials, or secret-file contents.
- Update the README, Javadocs, and example configuration when behavior changes.
- Do not commit credentials, local server files, build output, Gradle caches, or IDE metadata.

Use `./gradlew lintFix` to format source and project files. Avoid unrelated formatting or refactoring in the same pull request.

The pre-commit hook runs `spotlessApply` and re-stages files that were already part of the commit, ensuring formatter changes are included without staging unrelated working-tree changes.

## Testing

Run the complete local verification suite:

```shell
./gradlew check javadoc jar
```

Pull requests must pass formatting, unit tests, SpotBugs, aggregate coverage thresholds, Javadocs, Gradle wrapper validation, and the Linux, macOS, and Windows build matrix.

Redis transport changes should cover the relevant protocol and lifecycle behavior, including authentication, database selection, TLS settings, response bounds, reconnects, timeouts, and shutdown races. Routing changes should test broadcast, role, node, and mismatched channel/destination behavior.

## Pull requests

- Explain the problem and the chosen solution.
- Call out public API or wire-format compatibility effects.
- Include reproduction steps for bug fixes.
- Keep commits and pull requests small enough to review confidently.
- Confirm that no secrets or production Redis endpoints are present in the change.

By contributing, you agree that your contribution is licensed under the same license as Relay.
