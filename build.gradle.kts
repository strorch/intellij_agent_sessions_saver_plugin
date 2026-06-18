import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.io.File

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = property("pluginGroup") as String
version = property("pluginVersion") as String

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        bundledPlugin("org.jetbrains.plugins.terminal")
        instrumentationTools()
    }
}

sourceSets {
    val test by getting {
        kotlin.srcDirs("tests/unit", "tests/integration")
        resources.srcDirs("tests/fixtures")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("ideaSinceBuild")
            untilBuild = providers.gradleProperty("ideaUntilBuild")
        }
    }

    // Validate the advertised compatibility range (since-build 243 .. until-build 252.*)
    // with the IntelliJ Plugin Verifier so the metadata bounds are actually tested,
    // not just asserted. The terminal integration is only supported across these IDEs.
    pluginVerification {
        ides {
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.runIde {
    jvmArgs(
        "-Didea.suppressed.plugins.id=" +
            listOf(
                "com.intellij.gradle",
                "org.jetbrains.plugins.gradle",
                "org.jetbrains.plugins.gradle.java",
                "org.jetbrains.plugins.gradle.java.analysis",
                "org.jetbrains.plugins.gradle.maven",
                "org.jetbrains.plugins.gradle.dependency.updater",
            ).joinToString(","),
    )
    doFirst {
        val sandboxConfigDir = layout.buildDirectory.dir("idea-sandbox/config").get().asFile
        val sandboxSystemDir = layout.buildDirectory.dir("idea-sandbox/system").get().asFile
        val staleConfigFiles = listOf(
            File(sandboxConfigDir, "app-internal-state.db"),
            File(sandboxConfigDir, "updatedBrokenPlugins.db"),
        )
        staleConfigFiles.forEach {
            if (it.exists()) {
                it.delete()
            }
        }
        if (!sandboxConfigDir.exists()) {
            sandboxConfigDir.mkdirs()
        }
        if (!sandboxSystemDir.exists()) {
            sandboxSystemDir.mkdirs()
        }
        val disabledPluginsFile = File(sandboxConfigDir, "disabled_plugins.txt")
        val disabledPlugins = linkedSetOf(
            "com.intellij.gradle",
            "org.jetbrains.plugins.gradle",
            "org.jetbrains.plugins.gradle.java",
            "org.jetbrains.plugins.gradle.java.analysis",
            "org.jetbrains.plugins.gradle.maven",
            "org.jetbrains.plugins.gradle.dependency.updater",
        )
        if (disabledPluginsFile.exists()) {
            disabledPlugins.addAll(disabledPluginsFile.readLines().map { it.trim() }.filter { it.isNotEmpty() })
        }
        disabledPluginsFile.writeText(disabledPlugins.joinToString(System.lineSeparator(), postfix = System.lineSeparator()))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}
