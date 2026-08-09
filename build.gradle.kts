import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.plugins.signing.SigningExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    jacoco
    id("com.diffplug.spotless") version "8.9.0"
    id("com.github.spotbugs") version "6.5.10" apply false
    id("org.cyclonedx.bom") version "3.4.0"
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

allprojects {
    group = providers.gradleProperty("group").get()
    version =
        providers
            .environmentVariable("RELAY_VERSION")
            .orElse(providers.gradleProperty("version"))
            .map { it.removePrefix("v") }
            .get()
    description = providers.gradleProperty("description").get()

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.github.spotbugs")

    extensions.configure<SpotBugsExtension> {
        excludeFilter = rootProject.file("config/spotbugs-exclude.xml")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
        withJavadocJar()
    }

    dependencyLocking {
        lockAllConfigurations()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.2"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") { required = true }
    }
    tasks.named("spotbugsTest").configure { enabled = false }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            html.required = true
            xml.required = true
        }
    }

    pluginManager.withPlugin("maven-publish") {
        apply(plugin = "signing")
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    scm {
                        connection = "scm:git:https://github.com/IanTapply22/Relay.git"
                        developerConnection = "scm:git:ssh://git@github.com/IanTapply22/Relay.git"
                        url = "https://github.com/IanTapply22/Relay"
                    }
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    val repository =
                        providers
                            .environmentVariable("GITHUB_REPOSITORY")
                            .orElse("IanTapply22/Relay")
                            .get()
                            .lowercase()
                    url = uri("https://maven.pkg.github.com/$repository")
                    credentials {
                        username = providers.environmentVariable("GITHUB_ACTOR").orNull
                        password = providers.environmentVariable("GITHUB_TOKEN").orNull
                    }
                }
            }
        }
        extensions.configure<SigningExtension> {
            val signingKey = providers.environmentVariable("SIGNING_KEY")
            val signingPassword = providers.environmentVariable("SIGNING_PASSWORD")
            setRequired(signingKey.isPresent)
            if (signingKey.isPresent) {
                useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
            }
            sign(project.extensions.getByType<PublishingExtension>().publications)
        }
    }
}

spotless {
    java {
        target("modules/**/src/**/*.java")
        targetExclude("**/build/**")
        palantirJavaFormat()
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts", "modules/**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("projectFiles") {
        target(
            "*.md",
            "docs/**/*.html",
            "config/**/*.xml",
            "*.properties",
            ".gitattributes",
            ".gitignore",
            ".github/**/*.yml",
            ".github/**/*.yaml",
            "modules/**/*.yml",
            "modules/**/*.yaml",
            "modules/**/*.properties",
        )
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

val documentedProjects = subprojects.filter { it.name != "relay-distribution" }

val aggregateJavadoc =
    tasks.register<Sync>("javadoc") {
        group = "documentation"
        description = "Aggregates Javadocs from every Relay module."
        dependsOn(documentedProjects.map { it.tasks.named("javadoc") })
        into(layout.buildDirectory.dir("docs/javadoc"))
        from("docs/javadoc-index.html") { rename { "index.html" } }
        documentedProjects.forEach { module ->
            from(module.layout.buildDirectory.dir("docs/javadoc")) { into(module.name) }
        }
    }

val aggregateCoverageExclusions =
    listOf(
        "**/com/iantapply/relay/Relay.class",
        "**/platform/paper/**/*.class",
        "**/velocity/RelayVelocity*.class",
    )

val aggregateCoverageReport =
    tasks.register<JacocoReport>("jacocoTestReport") {
        dependsOn(documentedProjects.map { it.tasks.named("test") })
        executionData.from(documentedProjects.map { it.layout.buildDirectory.file("jacoco/test.exec") })
        sourceDirectories.from(
            documentedProjects.map {
                it.extensions
                    .getByType<SourceSetContainer>()
                    .named("main")
                    .get()
                    .allSource.srcDirs
            },
        )
        classDirectories.from(
            documentedProjects.map { module ->
                module.extensions
                    .getByType<SourceSetContainer>()
                    .named("main")
                    .get()
                    .output
                    .asFileTree
                    .matching { exclude(aggregateCoverageExclusions) }
            },
        )
        reports {
            html.required = true
            xml.required = true
        }
    }

val aggregateCoverageVerification =
    tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn(aggregateCoverageReport)
        executionData.from(aggregateCoverageReport.map { it.executionData })
        sourceDirectories.from(aggregateCoverageReport.map { it.sourceDirectories })
        classDirectories.from(aggregateCoverageReport.map { it.classDirectories })
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.70".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    minimum = "0.50".toBigDecimal()
                }
            }
        }
    }

tasks.register("lint") {
    group = "verification"
    description = "Checks source code and project file formatting."
    dependsOn(tasks.named("spotlessCheck"))
}

tasks.register("lintFix") {
    group = "formatting"
    description = "Formats source code and project files, then verifies the result."
    dependsOn(tasks.named("spotlessApply"))
    finalizedBy(tasks.named("spotlessCheck"))
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") }, aggregateCoverageVerification)
}

tasks.named("assemble") {
    dependsOn(":relay-distribution:assemble")
}

tasks.register("test") {
    group = "verification"
    description = "Runs the test suites in every Relay module."
    dependsOn(subprojects.map { it.tasks.named("test") })
}

tasks.register("jar") {
    group = "build"
    description = "Builds the combined Paper and Velocity plugin JAR."
    dependsOn(":relay-distribution:shadowJar")
}

tasks.register("runServer") {
    group = "run paper"
    description = "Runs a Paper server with the combined Relay plugin."
    dependsOn(":relay-distribution:runServer")
}

tasks.register("publish") {
    group = "publishing"
    description = "Publishes the Relay API and distribution to GitHub Packages."
    dependsOn(":relay-api:publish", ":relay-distribution:publish")
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Publishes the Relay API and distribution to the local Maven repository."
    dependsOn(":relay-api:publishToMavenLocal", ":relay-distribution:publishToMavenLocal")
}

tasks.register<Exec>("installGitHooks") {
    group = "build setup"
    description = "Configures this Git checkout to use the tracked hooks in .githooks."
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        commandLine("git", "config", "core.hooksPath", ".githooks")
    } else {
        commandLine("sh", "-c", "chmod +x .githooks/pre-commit && git config core.hooksPath .githooks")
    }
}
