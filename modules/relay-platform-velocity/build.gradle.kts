plugins {
    `java-library`
}

dependencies {
    implementation(project(":relay-api"))
    implementation(project(":relay-core"))
    implementation(project(":relay-redis"))
    compileOnly("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:4.1.0-SNAPSHOT")
}
