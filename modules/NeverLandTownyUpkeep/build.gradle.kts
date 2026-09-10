import org.gradle.api.attributes.java.TargetJvmVersion
plugins { java }
group = "ru.neverland"
version = "0.1.1"
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/towny/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
configurations.configureEach {
    if (isCanBeResolved) attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}
for (name in listOf("testCompileClasspath", "testRuntimeClasspath")) {
    configurations.named(name) { extendsFrom(configurations.named("compileOnly").get()) }
}
tasks {
    compileJava { options.encoding = "UTF-8"; options.release.set(17) }
    compileTestJava { options.encoding = "UTF-8" }
    jar { archiveBaseName.set("NeverLandTownyUpkeep") }
}
tasks.register<JavaExec>("smokeTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("ru.neverland.townyupkeep.UpkeepSmoke")
}

