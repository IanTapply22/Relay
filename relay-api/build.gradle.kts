plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api("com.google.code.gson:gson:2.13.2")
}

publishing {
    publications {
        create<MavenPublication>("relayApi") {
            from(components["java"])
            artifactId = "relay-api"
        }
    }
}
