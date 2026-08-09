plugins {
    `java-library`
}

dependencies {
    implementation(project(":relay-api"))
    implementation(project(":relay-core"))
    implementation(project(":relay-redis"))
    implementation("org.yaml:snakeyaml:2.4")
    compileOnly("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")
}

val relayVersion = project.version.toString()

tasks.processResources {
    inputs.property("relayVersion", relayVersion)
    expand("version" to relayVersion)
}
