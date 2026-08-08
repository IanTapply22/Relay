plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
}

dependencies {
    implementation(project(":relay-api"))
    implementation(project(":relay-core"))
    implementation(project(":relay-redis"))
    paperweight.paperDevBundle("26.2.build.+")
}

paperPluginYaml {
    name = "Relay"
    main = "com.iantapply.relay.Relay"
    apiVersion = "26.2"
    authors.add("Gucci Fox")
    prefix = "Relay"

    permissions {
        register("relay.admin") {
            description = "Allows use of Relay administration commands"
            default = xyz.jpenilla.resourcefactory.bukkit.Permission.Default.OP
        }
    }
}
