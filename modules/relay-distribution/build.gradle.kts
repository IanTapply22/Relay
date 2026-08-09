plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper")
}

dependencies {
    implementation(project(":relay-platform-paper"))
    implementation(project(":relay-platform-velocity"))
}

tasks.jar {
    archiveClassifier = "thin"
}

tasks.shadowJar {
    archiveBaseName = "Relay"
    archiveClassifier = ""
    destinationDirectory = rootProject.layout.buildDirectory.dir("libs")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    relocate("com.google.gson", "com.iantapply.relay.libs.gson")
    relocate("org.yaml.snakeyaml", "com.iantapply.relay.libs.snakeyaml")
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifact(tasks.shadowJar)
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
    pluginJars.from(tasks.shadowJar.flatMap { it.archiveFile })
    jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}
