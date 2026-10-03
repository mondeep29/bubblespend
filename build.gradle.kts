plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            ktlint(libs.versions.ktlint.get())
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
        }
    }
}

tasks.register("spotlessCheck") {
    dependsOn(subprojects.map { it.tasks.named("spotlessCheck") })
}
