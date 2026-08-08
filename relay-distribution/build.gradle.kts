plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

dependencies {
    implementation(project(":relay-platform-paper"))
    implementation(project(":relay-platform-velocity"))
}

tasks.jar {
    archiveBaseName = "Relay"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(
        ":relay-api:jar",
        ":relay-core:jar",
        ":relay-redis:jar",
        ":relay-platform-paper:jar",
        ":relay-platform-velocity:jar",
    )

    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map(::zipTree)
    })

    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}

tasks.runServer {
    minecraftVersion("26.2")
    jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}
