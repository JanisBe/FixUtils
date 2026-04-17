plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group = "com.fixutils"
version = "1.0.1"

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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.fixutils.fix-parser"
        name = "FIX Message Parser"
        version = project.version.toString()
        changeNotes = "Initial release"

        ideaVersion {
            sinceBuild = "231"
            untilBuild = provider { null }
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

tasks.test {
    useJUnitPlatform()
}
