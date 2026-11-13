package io.github.rupakc.travelplanner.core.domain.telemetry

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every interaction must reach telemetry through the `core:designsystem` wrappers (`tpClickable`, `TpIconButton`,
 * `TpSwitch`, `TpOutlinedTextField`, ...). Raw foundation / Material3 controls are allowed only inside the
 * wrapper module, `core/designsystem/src/main`.
 */
class RawInteractionGuardTest {
    private val raw =
        listOf(
            Regex("""\.(clickable|combinedClickable|selectable|toggleable)\s*[({]"""),
            Regex(
                """(?<![A-Za-z0-9_.])(IconButton|FilledIconButton|FilledTonalIconButton|OutlinedIconButton|IconToggleButton|""" +
                    """Button|TextButton|OutlinedButton|ElevatedButton|FilledTonalButton|FloatingActionButton|""" +
                    """SmallFloatingActionButton|ExtendedFloatingActionButton|Tab|LeadingIconTab|DropdownMenuItem|""" +
                    """AssistChip|SuggestionChip|InputChip|FilterChip|ElevatedAssistChip|Slider|RangeSlider|""" +
                    """OutlinedTextField|TextField|BasicTextField)\s*\(""",
            ),
        )

    /** A Switch/Checkbox/RadioButton is fine only as a decorative indicator: its handler is the literal `null`. */
    private val toggles = Regex("""(?<![A-Za-z0-9_.])(Switch|Checkbox|RadioButton|TriStateCheckbox)\s*\(""")
    private val nullHandler = Regex("""(onCheckedChange|onClick)\s*=\s*null""")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "settings.gradle.kts not found" }
    }

    private fun mainSources(): List<File> =
        repoRoot().walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path && "/core/designsystem/src/main/" !in it.path }
            .toList()

    @Test
    fun `no raw clickable or Material control outside the design system`() {
        val violations = mutableListOf<String>()
        for (file in mainSources()) {
            val lines = file.readLines()
            lines.forEachIndexed { idx, line ->
                val code = line.substringBefore("//")
                if (code.trimStart().startsWith("import ") || code.trimStart().startsWith("*") || code.trimStart().startsWith("/*")) return@forEachIndexed
                if (raw.any { it.containsMatchIn(code) }) violations += "${file.path}:${idx + 1}: ${code.trim()}"
                if (toggles.containsMatchIn(code)) {
                    val window = lines.subList(idx, minOf(lines.size, idx + 4)).joinToString(" ")
                    if (!nullHandler.containsMatchIn(window)) violations += "${file.path}:${idx + 1}: ${code.trim()}"
                }
            }
        }
        assertTrue("Use the Tp* wrappers (core:designsystem Interactions.kt):\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `tids written as literals are valid wire tokens`() {
        val token = Regex("""^[A-Za-z0-9_.:/-]{1,64}$""")
        val literal = Regex("""\btid\s*=\s*"([^"$]*)"""")
        val bad = mutableListOf<String>()
        var count = 0
        for (file in repoRoot().walkTopDown().onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path }) {
            for (m in literal.findAll(file.readText())) {
                count++
                if (!token.matches(m.groupValues[1])) bad += "${file.name}: ${m.groupValues[1]}"
            }
        }
        assertTrue("No tids found; the scan is broken", count > 100)
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
