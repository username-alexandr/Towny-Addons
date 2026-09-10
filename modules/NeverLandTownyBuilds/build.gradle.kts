import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    java
}

group = "ru.neverland"
version = "0.8.8"

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

val smokeClasses = listOf(
    "ru.neverland.townybuilds.PowerBlueprintSmoke",
    "ru.neverland.townybuilds.ShipmentStorageSmoke",
    "ru.neverland.townybuilds.TradeStorageSmoke",
    "ru.neverland.townybuilds.MarketStorageSmoke",
    "ru.neverland.townybuilds.BuildingFootprintsSmoke",
    "ru.neverland.townybuilds.MobilizationSmoke",
    "ru.neverland.townybuilds.RoofSupportSmoke",
    "ru.neverland.townybuilds.SpecializationBlueprintSmoke",
    "ru.neverland.townybuilds.ProjectIconsSmoke",
    "ru.neverland.townybuilds.MaterialLocalizationSmoke",
    "ru.neverland.townybuilds.ImportedModelsSmoke",
    "ru.neverland.townybuilds.ResourceBalanceSmoke",
    "ru.neverland.townybuilds.construction.ObstructionValidationSmoke",
    "ru.neverland.townybuilds.ExcavationPlannerSmoke",
    "ru.neverland.townybuilds.ConstructionPoliciesSmoke",
    "ru.neverland.townybuilds.ExpansionIntegrationSmoke",
    "ru.neverland.townybuilds.BlueprintGeometrySmoke",
    "ru.neverland.townybuilds.BlueprintLocalizationSmoke",
    "ru.neverland.townybuilds.BlueprintSeamSmoke",
    "ru.neverland.townybuilds.WonderBlueprintSmoke",
    "ru.neverland.townybuilds.BlockOrientationSmoke",
    "ru.neverland.townybuilds.ResourceFundSmoke",
    "ru.neverland.townybuilds.ResourceTransferSmoke",
    "ru.neverland.townybuilds.OptionalArchaeologySmoke",
    "ru.neverland.townybuilds.ColorCompatibilitySmoke",
    "ru.neverland.townybuilds.CivicExpansionSmoke",
    "ru.neverland.townybuilds.service.DefinitionOverlaySmoke"
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
