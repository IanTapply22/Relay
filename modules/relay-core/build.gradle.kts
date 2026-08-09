plugins {
    `java-library`
}

dependencies {
    api(project(":relay-api"))
    implementation("com.google.code.gson:gson:2.13.2")
}
