import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
}

group = "ru.neverland"
version = "0.8.24"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/towny/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
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
        inputs.property("pluginVersion", project.version)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    jar {
        archiveBaseName.set("NeverLandTownyBuilds")
    }
}







// Bundle shared labels without adding a runtime plugin dependency.
sourceSets.main {
    java.srcDir("../../shared/localization/src/main/java")
    resources.srcDir("../../shared/localization/src/main/resources")
}

sourceSets.main { java.srcDir("../../shared/districts/src/main/java") }

sourceSets.main { java.srcDir("../../shared/upkeep/src/main/java") }

sourceSets.main { java.srcDir("../../shared/research/src/main/java") }

sourceSets.main { java.srcDir("../../shared/specialization/src/main/java") }

sourceSets.main { java.srcDir("../../shared/policies/src/main/java") }

sourceSets.main { java.srcDir("../../shared/treasury/src/main/java") }

sourceSets.main { java.srcDir("../../shared/jobs/src/main/java") }

sourceSets.main { java.srcDir("../../shared/core/src/main/java") }

apply(from = "../../scripts/smoke.gradle")
