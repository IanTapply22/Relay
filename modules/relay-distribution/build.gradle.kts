plugins {
    `java-library`
    `maven-publish`
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":relay-platform-paper"))
    implementation(project(":relay-platform-velocity"))
}

tasks.jar {
    archiveBaseName = "Relay"
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(
        ":relay-api:jar",
        ":relay-core:jar",
        ":relay-redis:jar",
        ":relay-platform-paper:jar",
        ":relay-platform-velocity:jar",
    )

    from({
        configurations.runtimeClasspath
            .get()
            .filter { it.name.endsWith(".jar") }
            .map(::zipTree)
    })

    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifact(tasks.jar)
            artifactId = "relay"
            pom {
                name = "Relay"
                description = project.description.toString()
                url = "https://github.com/IanTapply22/Relay"
            }
        }
    }
}

tasks.runServer {
    minecraftVersion("26.2")
    jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}
