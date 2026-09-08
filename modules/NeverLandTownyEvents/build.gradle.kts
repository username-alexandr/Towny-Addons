import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
}

group = "ru.neverland"
version = "0.1.8"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/towny/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
    compileOnly("me.clip:placeholderapi:2.12.3")
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
        archiveBaseName.set("NeverLandTownyEvents")
    }
}

configurations.named("testRuntimeClasspath") {
    extendsFrom(configurations.named("compileOnly").get())
}
tasks.register<JavaExec>("smokeTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ru.neverland.mintevents.RaidKillAndMessagesSmoke")
}
