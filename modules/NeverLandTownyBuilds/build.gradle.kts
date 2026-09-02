import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
}

group = "ru.neverland"
version = "0.5.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/towny/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

configurations.configureEach {
    if (isCanBeResolved) {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}
configurations.named("testCompileClasspath") {
    extendsFrom(configurations.named("compileOnly").get())
}

configurations.named("testRuntimeClasspath") {
    extendsFrom(configurations.named("compileOnly").get())
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    jar {
        archiveBaseName.set("NeverLandTownyBuilds")
    }
}

val smokeClasses = listOf(
    "ru.neverland.townybuilds.ImportedModelsSmoke",
    "ru.neverland.townybuilds.ExpansionIntegrationSmoke",
    "ru.neverland.townybuilds.BlueprintGeometrySmoke",
    "ru.neverland.townybuilds.BlueprintLocalizationSmoke",
    "ru.neverland.townybuilds.BlueprintSeamSmoke",
    "ru.neverland.townybuilds.WonderBlueprintSmoke",
    "ru.neverland.townybuilds.BlockOrientationSmoke",
    "ru.neverland.townybuilds.ResourceFundSmoke",
    "ru.neverland.townybuilds.ResourceTransferSmoke",
    "ru.neverland.townybuilds.OptionalArchaeologySmoke",
    "ru.neverland.townybuilds.ColorCompatibilitySmoke"
)

val smokeTasks = smokeClasses.map { className ->
    val suffix = className.substringAfterLast('.')
    tasks.register<JavaExec>("smoke$suffix") {
        group = "verification"
        dependsOn(tasks.named("testClasses"))
        classpath = sourceSets.test.get().runtimeClasspath
        mainClass.set(className)
    }
}

tasks.register("smokeTest") {
    group = "verification"
    description = "Runs the executable NeverLand Towny Builds regression suite."
    dependsOn(smokeTasks)
}
