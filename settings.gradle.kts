rootProject.name = "Relay"

include(
    "relay-api",
    "relay-core",
    "relay-redis",
    "relay-platform-paper",
    "relay-platform-velocity",
    "relay-distribution",
)

listOf(
    "relay-api",
    "relay-core",
    "relay-redis",
    "relay-platform-paper",
    "relay-platform-velocity",
    "relay-distribution",
).forEach { moduleName ->
    project(":$moduleName").projectDir = file("modules/$moduleName")
}
