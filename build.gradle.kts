plugins {
    // Loom 1.18+ needs the Gradle daemon on Java 25; 1.17.x is the newest line that runs on 21.
    id("fabric-loom") version "1.17.21"
}

version = property("mod.version").toString()
group = property("mod.group").toString()
base.archivesName = property("mod.id").toString()

repositories {
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("deps.minecraft")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    // Cobblemon is written in Kotlin; its signatures reference kotlin.ranges types.
    modCompileOnly("maven.modrinth:cobblemon:${property("deps.cobblemon")}")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${property("deps.kotlin")}")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
