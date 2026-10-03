package com.bubblespend.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Repository contract preventing platform and persistence dependencies in pure Kotlin modules. */
class ArchitectureBoundaryTest {
    private val forbiddenImport = Regex("^\\s*import\\s+(android\\.|androidx\\.)")
    private val forbiddenDependency =
        Regex("(?i)(androidx\\.|com\\.android\\.|org\\.jetbrains\\.kotlin\\.android|compose|room|datastore)")
    private val catalogReference = Regex("libs\\.([A-Za-z0-9_.]+)")
    private val catalogEntry = Regex("^\\s*[\"']?([^=\"']+)[\"']?\\s*=\\s*(.+?)\\s*$")

    @Test
    fun domainAndApplicationModules_remainPlatformAndPersistenceFree() {
        val repositoryRoot =
            Path.of(System.getProperty("user.dir")).let { start ->
                generateSequence(start) { it.parent }.firstOrNull { Files.isDirectory(it.resolve("core/domain")) }
            }

        assertTrue("Could not locate repository core modules from test working directory", repositoryRoot != null)
        val violations = findBoundaryViolations(requireNotNull(repositoryRoot), listOf("domain", "application"))

        assertTrue(
            "Pure Kotlin modules contain forbidden platform/persistence references:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun boundaryCheck_resolvesInnocentCatalogAliasesAndScansJavaGroovyAndKotlin() {
        val root = Files.createTempDirectory("bubble-spend-architecture-boundary")
        try {
            Files.createDirectories(root.resolve("gradle"))
            Files.write(
                root.resolve("gradle/libs.versions.toml"),
                """
                [versions]
                room = "2.6.1"

                [libraries]
                platform = { module = "androidx.room:room-runtime", version.ref = "room" }
                """.trimIndent().toByteArray(),
            )
            val domain = root.resolve("core/domain")
            val application = root.resolve("core/application")
            Files.createDirectories(domain.resolve("src/main/java/sample"))
            Files.createDirectories(application.resolve("src/main/groovy/sample"))
            Files.createDirectories(domain.resolve("src/main/kotlin/sample"))
            Files.write(domain.resolve("build.gradle.kts"), "dependencies { implementation(libs.platform) }".toByteArray())
            Files.write(domain.resolve("src/main/java/sample/AndroidType.java"), "import android.content.Context;\n".toByteArray())
            Files.write(application.resolve("src/main/groovy/sample/RoomType.groovy"), "import androidx.room.Room\n".toByteArray())
            Files.write(
                domain.resolve("src/main/kotlin/sample/ComposeType.kt"),
                "import androidx.compose.runtime.Composable\n".toByteArray(),
            )

            val violations = findBoundaryViolations(root, listOf("domain", "application"))

            assertTrue(
                "The benign `platform` catalog alias must resolve to the Room coordinate",
                violations.any {
                    it.contains("androidx.room:room-runtime")
                },
            )
            assertTrue("Java sources must be scanned", violations.any { it.contains("AndroidType.java") })
            assertTrue("Groovy sources must be scanned", violations.any { it.contains("RoomType.groovy") })
            assertTrue("Kotlin sources must be scanned", violations.any { it.contains("ComposeType.kt") })
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun boundaryCheck_expandsBundleAliasesToForbiddenLibraryCoordinates() {
        val root = Files.createTempDirectory("bubble-spend-architecture-bundle-boundary")
        try {
            Files.createDirectories(root.resolve("gradle"))
            Files.write(
                root.resolve("gradle/libs.versions.toml"),
                """
                [versions]
                room = "2.6.1"

                [libraries]
                room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }

                [bundles]
                platform = [
                    "room-runtime",
                ]
                """.trimIndent().toByteArray(),
            )
            val domain = root.resolve("core/domain")
            val application = root.resolve("core/application")
            Files.createDirectories(domain)
            Files.createDirectories(application)
            Files.write(
                domain.resolve("build.gradle.kts"),
                "dependencies { implementation(libs.bundles.platform) }".toByteArray(),
            )

            val violations = findBoundaryViolations(root, listOf("domain", "application"))

            assertTrue(
                "The `platform` bundle must resolve through `room-runtime` to the Room coordinate",
                violations.any { it.contains("androidx.room:room-runtime") },
            )
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun findBoundaryViolations(
        root: Path,
        modules: List<String>,
    ): List<String> {
        val catalogCoordinates = readCatalogCoordinates(root.resolve("gradle/libs.versions.toml"))
        return modules.flatMap { module ->
            val moduleRoot = root.resolve("core/$module")
            assertTrue("Missing pure Kotlin module: $moduleRoot", Files.isDirectory(moduleRoot))

            val moduleViolations = mutableListOf<String>()
            Files.walk(moduleRoot).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { file ->
                        val name = file.fileName.toString()
                        name.endsWith(".kt") ||
                            name.endsWith(".java") ||
                            name.endsWith(".groovy") ||
                            name.endsWith(".gradle") ||
                            name.endsWith(".gradle.kts")
                    }.forEach { file ->
                        val fileName = file.fileName.toString()
                        val isSource = fileName.endsWith(".kt") || fileName.endsWith(".java") || fileName.endsWith(".groovy")
                        Files.readAllLines(file).forEachIndexed { index, line ->
                            if (isSource && forbiddenImport.containsMatchIn(line)) {
                                moduleViolations += "${root.relativize(file)}:${index + 1}: $line"
                            }
                            if (!isSource) {
                                if (forbiddenDependency.containsMatchIn(line)) {
                                    moduleViolations += "${root.relativize(file)}:${index + 1}: $line"
                                }
                                catalogReference.findAll(line).forEach { reference ->
                                    val alias = reference.groupValues[1].removePrefix("plugins.")
                                    val coordinate = catalogCoordinates[normalizeAlias(alias)]
                                    if (coordinate != null && forbiddenDependency.containsMatchIn(coordinate)) {
                                        moduleViolations +=
                                            "${root.relativize(file)}:${index + 1}: libs.$alias resolves to $coordinate"
                                    }
                                }
                            }
                        }
                    }
            }
            moduleViolations
        }
    }

    private fun readCatalogCoordinates(path: Path): Map<String, String> {
        if (!Files.isRegularFile(path)) return emptyMap()
        var section = ""
        val coordinates = mutableMapOf<String, String>()
        val bundleMembers = mutableMapOf<String, List<String>>()
        val lines = Files.readAllLines(path)
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                section = trimmed.removePrefix("[").removeSuffix("]")
            } else if (section == "libraries" || section == "plugins" || section == "bundles") {
                val entry = catalogEntry.matchEntire(line)
                if (entry != null) {
                    val alias = normalizeAlias(entry.groupValues[1].trim())
                    var value = entry.groupValues[2]
                    if (section == "bundles") {
                        var bracketDepth = value.count { it == '[' } - value.count { it == ']' }
                        while (bracketDepth > 0 && index + 1 < lines.size) {
                            value += " ${lines[++index].trim()}"
                            bracketDepth += lines[index].count { it == '[' } - lines[index].count { it == ']' }
                        }
                        bundleMembers[alias] =
                            Regex("[\"']([^\"']+)[\"']")
                                .findAll(value)
                                .map { normalizeAlias(it.groupValues[1]) }
                                .toList()
                    } else {
                        val coordinate =
                            when (section) {
                                "libraries" -> {
                                    Regex("(?:module)\\s*=\\s*[\"']([^\"']+)[\"']").find(value)?.groupValues?.get(1)
                                        ?: run {
                                            val group = Regex("(?:group)\\s*=\\s*[\"']([^\"']+)[\"']").find(value)?.groupValues?.get(1)
                                            val name = Regex("(?:name)\\s*=\\s*[\"']([^\"']+)[\"']").find(value)?.groupValues?.get(1)
                                            if (group != null && name != null) "$group:$name" else value.trim().trim('"', '\'')
                                        }
                                }
                                else -> Regex("(?:id)\\s*=\\s*[\"']([^\"']+)[\"']").find(value)?.groupValues?.get(1)
                            }
                        if (coordinate != null) coordinates[alias] = coordinate
                    }
                }
            }
            index++
        }

        fun resolveBundleMembers(
            alias: String,
            visiting: Set<String> = emptySet(),
        ): List<String> {
            val normalizedAlias = normalizeAlias(alias).removePrefix("bundles.")
            coordinates[normalizedAlias]?.let { return listOf(it) }
            if (normalizedAlias in visiting) return emptyList()
            return bundleMembers[normalizedAlias].orEmpty().flatMap { member ->
                resolveBundleMembers(member, visiting + normalizedAlias)
            }
        }

        return coordinates +
            bundleMembers.keys.associate { alias ->
                "bundles.$alias" to resolveBundleMembers(alias).joinToString(", ")
            }
    }

    private fun normalizeAlias(alias: String): String = alias.split(Regex("[-_.]")).joinToString(".")
}
