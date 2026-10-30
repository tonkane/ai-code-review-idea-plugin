plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.aicodereview"
version = "1.1.8"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1.7")
    }

    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    instrumentCode = false

    pluginConfiguration {
        id = "com.aicodereview.git"
        name = "tonkane AI Code Review"
        version = project.version.toString()

        vendor {
            name = "tonkane"
        }

        ideaVersion {
            sinceBuild = "241"
            untilBuild = "299.*"
        }
    }

    buildSearchableOptions = false
}

