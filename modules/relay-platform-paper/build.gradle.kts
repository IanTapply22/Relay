plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

dependencies {
    implementation(project(":relay-api"))
    implementation(project(":relay-core"))
    implementation(project(":relay-redis"))
    paperweight.paperDevBundle("26.2.build.111-stable")
}

val relayVersion = project.version.toString()

tasks.processResources {
    inputs.property("relayVersion", relayVersion)
    expand("version" to relayVersion)
}
