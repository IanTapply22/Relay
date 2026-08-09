# Relay

Relay is a small, typed Redis Pub/Sub messaging layer for Paper and Velocity networks. The same distribution JAR is loadable by both platforms and supports broadcast, role-targeted, and node-targeted delivery.

Relay deliberately provides transient notifications and commands, not durable application state. A successful publish means Redis accepted the message. Nodes that are disconnected miss messages, and handlers should be idempotent. Store authoritative party/player state elsewhere and use Relay to announce that it changed.

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

## Build

Relay requires Java 25 for the configured Paper 26.2 development bundle.

```text
./gradlew clean test jar
```

The combined plugin is written to `build/libs/Relay-<version>.jar`.

## Modules

All modules live under the `modules/` directory while retaining their short Gradle paths, such as `:relay-api` and `:relay-distribution`.

- `relay-api`: separately publishable developer API and standard codecs.
- `relay-core`: envelopes, routing, validation, dispatch isolation, in-memory transport, and metrics.
- `relay-redis`: Redis publisher, reconnecting subscriptions, TLS, authentication, and health state.
- `relay-platform-paper`: Paper lifecycle, Bukkit service registration, configuration, and administration command.
- `relay-platform-velocity`: Velocity lifecycle, service exposure, configuration, and administration command.
- `relay-distribution`: combined Paper/Velocity plugin JAR with runtime dependencies.

Useful root build tasks include:

- `check`: tests, SpotBugs, Spotless, and aggregate coverage verification.
- `javadoc`: aggregate module documentation under `build/docs/javadoc`.
- `jar`: combined Paper and Velocity distribution under `build/libs`.
- `cyclonedxBom`: JSON and XML software bills of materials.
- `lint` / `lintFix`: verify or apply the shared source and project formatting rules.
- `runServer`: launch Paper with the combined Relay distribution.
- `publish`: sign and publish `relay-api` and `relay` to GitHub Packages.

Publish both Maven artifacts locally for consumer development with:

```text
./gradlew publishToMavenLocal
```

## Configuration

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

## Operations

`/relay status`, `/relay subscriptions`, and `/relay diagnostics` require `relay.admin`. On Paper, the active service builds a literal command tree and registers it from `JavaPlugin#getLifecycleManager()` through `LifecycleEvents.COMMANDS`, matching Orchestra's command registration pattern. Diagnostics expose published, received, rejected, handler-failure, reconnect, connectivity, and queue metrics without logging payload contents.

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

Relay validates envelope schema, topic/node syntax, timestamps, content types, payload size, and bounded headers. Version 1 rejects unknown schemas and stale messages. Durable delivery, request/reply, wildcard routing, player-aware routing, database storage, and workflow scheduling are outside this API; a future Redis Streams implementation should use a separate `DurableMessagingService` contract.
