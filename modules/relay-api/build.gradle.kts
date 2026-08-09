plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
}

publishing {
    publications {
        create<MavenPublication>("relayApi") {
            from(components["java"])
            artifactId = "relay-api"
            pom {
                name = "Relay API"
                description = "Public messaging contracts and codecs for Relay"
                url = "https://github.com/IanTapply22/Relay"
                scm {
                    connection = "scm:git:https://github.com/IanTapply22/Relay.git"
                    developerConnection = "scm:git:ssh://git@github.com/IanTapply22/Relay.git"
                    url = "https://github.com/IanTapply22/Relay"
                }
            }
        }
    }
}
