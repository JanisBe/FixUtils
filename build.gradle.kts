plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.fixutils"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.fixutils.fix-parser"
        name = "FIX Message Parser"
        version = project.version.toString()
        changeNotes = "Initial release"

        ideaVersion {
            sinceBuild = "231"
            untilBuild = "999.*"
        }
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.processResources {
    from("Dictionaries") {
        into("Dictionaries")
    }
}
