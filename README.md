# Relay

Relay is a small, typed Redis Pub/Sub messaging layer for Paper and Velocity networks. The same distribution JAR is loadable by both platforms and supports broadcast, role-targeted, and node-targeted delivery.

Relay deliberately provides transient notifications and commands, not durable application state. A successful publish means Redis accepted the message. Nodes that are disconnected miss messages, and handlers should be idempotent. Store authoritative party/player state elsewhere and use Relay to announce that it changed.

## Requirements

- Java 25
- Paper 26.2 or Velocity 4.1
- Redis, reachable by every participating server and proxy

Every Relay node sharing a namespace must have a unique node ID and use the role matching its platform.

## Build

Use the checked-in wrapper:

```shell
./gradlew check javadoc jar
```

On Windows, use `.\gradlew.bat`. The combined Paper and Velocity plugin is written to `build/libs/Relay-<version>.jar`. Install the same JAR on participating backend servers and proxies.

Useful tasks:

```shell
./gradlew lint                 # verify formatting
./gradlew lintFix              # apply formatting
./gradlew test                 # run every test suite
./gradlew javadoc              # build aggregate API documentation
./gradlew cyclonedxBom         # create JSON and XML SBOMs
./gradlew runServer            # launch a disposable Paper server
./gradlew publishToMavenLocal  # publish API and plugin artifacts locally
```

## Releases and publications

Pushing a semantic version tag such as `v1.2.3` runs the release workflow. It verifies the project, publishes `relay-api` and the combined `relay` artifact to GitHub Packages, then creates a GitHub Release containing the plugin JAR, SHA-256 checksum, and CycloneDX SBOM. Maven publications are PGP-signed when the `SIGNING_KEY` and `SIGNING_PASSWORD` secrets are configured; the release JAR also receives a GitHub build-provenance attestation.

Aggregate Javadocs are built and deployed to GitHub Pages from the `main` branch.

To trigger the publishing workflow, you must run the following:
```shell
git tag -a v<version> -m "Relay <version>"
git push origin v<version>
```

## Quick start

1. Build or download `Relay-<version>.jar`.
2. Install the same JAR on each participating Paper server and Velocity proxy.
3. Start each platform once to create its default configuration.
4. Give every node a unique `node.id` and set `node.role` to `paper` or `velocity`.
5. Configure every node with the same Redis endpoint and namespace, then restart.
6. Run `/relay status` to confirm the node initialized and subscribed successfully.

### Configuration

Paper creates `plugins/Relay/config.yml`; Velocity creates `plugins/relay/config.yml` from its Velocity-specific default. Both use the same fields:

```yaml
node:
  id: "survival-1"
  role: "paper" # paper or velocity

redis:
  uri: "redis://localhost:6379"
  uri-environment-variable: "RELAY_REDIS_URI"
  uri-file: ""
  namespace: "production"

messaging:
  maximum-payload-bytes: 65536
  dispatch-workers: 2
  dispatch-queue-capacity: 1024
  reject-messages-older-than-seconds: 60
```

The Redis URI precedence is the `relay.redis.uri` system property, configured environment variable, secret file, then inline URI. `redis://` and TLS-enabled `rediss://` URIs are supported, including credentials and a database path.

Each node subscribes only to:

- `relay:<namespace>:broadcast`
- `relay:<namespace>:paper` or `relay:<namespace>:velocity`
- `relay:<namespace>:node:<node-id>`

### Administration commands

Operators with the `relay.admin` permission can use:

```text
/relay status
/relay subscriptions
/relay diagnostics
```

On Paper, the active service builds a literal command tree and registers it from `JavaPlugin#getLifecycleManager()` through `LifecycleEvents.COMMANDS`, matching Orchestra's command registration pattern. Diagnostics expose published, received, rejected, handler-failure, reconnect, connectivity, and queue metrics without logging payload contents.

## How it works

```text
                                      RELAY

  PRODUCERS                                                     DESTINATIONS

  Paper plugin -----------+                         +---- Every Relay node
                          |                         |
  Velocity plugin --------+                         +---- Every Paper server
                          |                         |
  Plugin extension -------+                         +---- Every Velocity proxy
                          |                         |
                          v                         +---- One named node
                 +-------------------+              |
                 | MessagingService  |<-------------+
                 | typed publish API |
                 +---------+---------+
                           |
                 topic codec encodes payload
                           |
                           v
                 +-------------------+
                 | Wire envelope     |
                 | ID, topic, origin,|
                 | destination, time,|
                 | type + metadata   |
                 +---------+---------+
                           |
                 validate and route destination
                           |
                           v
                 +-------------------+
                 | Redis publisher   |
                 | AUTH, SELECT, TLS |
                 +---------+---------+
                           |
                       PUBLISH
                           |
                           v
                 +------------------------------+
                 | Redis Pub/Sub channels       |
                 | broadcast | paper | velocity |
                 | node:<node-id>               |
                 +---------------+--------------+
                                 |
             +-------------------+-------------------+
             |                                       |
             v                                       v
  +----------------------+                 +----------------------+
  | Paper Relay node     |                 | Velocity Relay node  |
  | broadcast + paper +  |                 | broadcast + velocity|
  | its node channel     |                 | + its node channel   |
  +----------+-----------+                 +-----------+----------+
             |                                         |
             +-------------------+---------------------+
                                 |
                                 v
                     +-----------------------+
                     | Envelope validation   |
                     | schema, age, payload, |
                     | headers, destination  |
                     | and incoming channel  |
                     +-----------+-----------+
                                 |
                     match topic and content type
                                 |
                                 v
                     +-----------------------+
                     | Bounded dispatch pool |
                     | isolated subscribers  |
                     +-----------+-----------+
                                 |
                                 v
                     +-----------------------+
                     | Plugin handlers       |
                     | schedule platform work|
                     | on Paper/Folia as     |
                     | required              |
                     +-----------------------+

  CONFIGURATION                                       OPERATIONS

  config.yml ----------------+             +---- /relay status
  system properties ---------|             +---- /relay subscriptions
  environment variables -----+--> Relay <--+---- /relay diagnostics
  secret files --------------+             +---- metrics + logs
```

Each node subscribes only to the broadcast channel, its platform-role channel, and its own node channel. Relay validates the destination against the channel before decoding and dispatching the typed payload on a bounded worker pool. Delivery is transient and at-most-once: Redis does not retain these messages, so disconnected nodes do not receive them and handlers should remain idempotent.

## Modules

| Module | Responsibility |
| --- | --- |
| `relay-api` | Public messaging contracts and standard codecs |
| `relay-core` | Envelopes, routing, validation, dispatch isolation, in-memory transport, and metrics |
| `relay-redis` | Redis publication, reconnecting subscriptions, TLS, authentication, and health state |
| `relay-platform-paper` | Paper lifecycle, Bukkit service registration, configuration, and administration commands |
| `relay-platform-velocity` | Velocity lifecycle, service exposure, configuration, and administration commands |
| `relay-distribution` | Combined Paper and Velocity plugin JAR with relocated runtime dependencies |

All modules live under `modules/` while retaining short Gradle paths such as `:relay-api` and `:relay-distribution`.

## Developer API

Consumers should declare Relay as a required dependency and add this artifact as `compileOnly`; they must not shade the API into their plugin.

```java
public record PartyUpdated(UUID partyId, String operation, UUID playerId) {}

Topic<PartyUpdated> PARTY_UPDATED = Topic.of(
    "party:updated",
    Codecs.json(PartyUpdated.class));

MessagingService relay = Objects.requireNonNull(
    Bukkit.getServicesManager().load(MessagingService.class),
    "Relay is unavailable");

relay.publish(
    PARTY_UPDATED,
    Destination.broadcast(),
    new PartyUpdated(partyId, "MEMBER_JOINED", playerId));

relay.publish(
    PARTY_UPDATED,
    Destination.broadcast(),
    new PartyUpdated(partyId, "MEMBER_LEFT", playerId),
    new PublishOptions(previousMessageId, Map.of("trace", traceId)));

Subscription subscription = relay.subscribe(PARTY_UPDATED, message -> {
    // This is a Relay worker, not Paper's server thread.
    partyCache.invalidate(message.payload().partyId());
});
```

Velocity consumers can obtain the plugin with `ProxyServer#getPluginManager()#getPlugin("relay")` and cast its instance to `MessagingService` after proxy initialization.

Built-in codecs are `Codecs.utf8()`, `Codecs.bytes()`, and the explicitly typed `Codecs.json(Class<T>)`. Relay does not use Java serialization or transmit Java class names.

Handlers run on a bounded dispatch executor. One handler failure is isolated from other subscribers. Paper/Folia world and entity work must be scheduled back onto the appropriate platform scheduler. Closing a `Subscription` immediately prevents future delivery.

## Operational model

The corresponding metric names are:

- `relay_messages_published_total`
- `relay_messages_received_total`
- `relay_messages_rejected_total`
- `relay_dispatch_queue_drops_total`
- `relay_handler_failures_total`
- `relay_redis_reconnects_total`
- `relay_dispatch_queue_size`
- `relay_redis_publisher_connected`
- `relay_redis_subscriber_connected`

Relay validates envelope schema, topic/node syntax, timestamps, content types, payload size, bounded headers, and agreement between the destination and incoming Redis channel. Version 1 rejects unknown schemas and stale messages.

Redis Pub/Sub is transient and at-most-once. Keep clocks synchronized, use authenticated TLS connections outside trusted networks, store authoritative state elsewhere, and make handlers idempotent. Durable delivery, request/reply, wildcard routing, player-aware routing, database storage, and workflow scheduling are outside this API; a future Redis Streams implementation should use a separate `DurableMessagingService` contract.

Contributions are welcome under the guidelines in [CONTRIBUTING.md](CONTRIBUTING.md). Report suspected vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

Relay is licensed under the [GNU Affero General Public License v3.0 or later](LICENSE).
