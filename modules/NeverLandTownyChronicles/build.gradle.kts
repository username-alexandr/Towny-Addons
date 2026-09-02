plugins { java }
group = "ru.neverland"
version = "0.1.2"
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/towny/")
    maven("https://repo.extendedclip.com/releases/")
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    compileOnly("com.palmergames.bukkit.towny:towny:0.103.2.0")
    compileOnly("me.clip:placeholderapi:2.12.3")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks {
    compileJava { options.encoding = "UTF-8"; options.release.set(17) }
    processResources { filteringCharset = "UTF-8" }
    jar { archiveBaseName.set("NeverLandTownyChronicles") }
}
