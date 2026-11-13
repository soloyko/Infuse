plugins {
    java
}

group = "dev.infuse"
version = "1.4.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

// Zips the resource pack in /resourcepack so it can be embedded in the plugin jar
// (the plugin self-hosts it) and handed out as a standalone download.
val packZip by tasks.registering(Zip::class) {
    from("resourcepack")
    archiveFileName.set("OPSwords-ResourcePack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/pack"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
    from(packZip)
}

tasks.jar {
    archiveFileName.set("OPSwords-${project.version}.jar")
}

// ./gradlew dist -> build/dist/{OPSwords-x.y.z.jar, OPSwords-ResourcePack.zip}
tasks.register<Copy>("dist") {
    group = "build"
    description = "Collects the plugin jar and the resource pack zip into build/dist."
    from(tasks.jar, packZip)
    into(layout.buildDirectory.dir("dist"))
}

tasks.build {
    dependsOn("dist")
}
