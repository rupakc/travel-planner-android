package io.github.rupakc.travelplanner.core.domain.telemetry

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Phase 1/2 mirror of the server registry: names and enum values are wire tokens, nothing is duplicated. */
class TelemetryRegistryMirrorTest {
    private val events =
        listOf(
            TelemetryEvents.AUTH_START,
            TelemetryEvents.AUTH_RESULT,
            TelemetryEvents.SIGNUP_STEP,
            TelemetryEvents.EMAIL_VERIFY,
            TelemetryEvents.PASSWORD_RESET,
            TelemetryEvents.ACCOUNT_ACTION,
            TelemetryEvents.QUOTA_BLOCK,
            TelemetryEvents.GAME_AWARD,
            TelemetryEvents.BADGE_EARNED,
            TelemetryEvents.LEVEL_UP,
            TelemetryEvents.GAME_VIEW,
            TelemetryEvents.TRIP_VIEW,
            TelemetryEvents.TRIP_ACTION,
            TelemetryEvents.PREP_TOGGLE,
            TelemetryEvents.PREP_COMPLETE,
            TelemetryEvents.TODAY_ACTION,
            TelemetryEvents.OFFLINE_PACK,
            TelemetryEvents.OUTBOX_FLUSH,
            TelemetryEvents.EXPENSE_ACTION,
            TelemetryEvents.JOURNAL_ACTION,
            TelemetryEvents.PLACE_ACTION,
            TelemetryEvents.RECAP_ACTION,
            TelemetryEvents.COMPLETENESS_VIEW,
            TelemetryEvents.ONBOARDING_STEP,
            TelemetryEvents.ONBOARDING_RESULT,
            TelemetryEvents.ALERT_STORY_VIEW,
            TelemetryEvents.ALERT_ADVICE,
            TelemetryEvents.NOTIF_SETTINGS_VIEW,
            TelemetryEvents.NOTIF_CATEGORY_TOGGLE,
            TelemetryEvents.NOTIF_QUIET_HOURS_CHANGE,
            TelemetryEvents.NOTIF_CAP_CHANGE,
            TelemetryEvents.NOTIF_OPEN,
            TelemetryEvents.SHARE_OPEN,
            TelemetryEvents.SHARE_CREATE,
            TelemetryEvents.SHARE_ACTION,
            TelemetryEvents.SHARE_REVOKE,
            TelemetryEvents.SHARE_VIEW,
            TelemetryEvents.SOCIAL_PROFILE_VIEW,
            TelemetryEvents.SOCIAL_PROFILE_SAVE,
            TelemetryEvents.SOCIAL_FEED_VIEW,
            TelemetryEvents.SOCIAL_IDEA_PUBLISH,
            TelemetryEvents.SOCIAL_IDEA_OPEN,
            TelemetryEvents.SOCIAL_IDEA_LIKE,
            TelemetryEvents.SOCIAL_IDEA_SAVE,
            TelemetryEvents.SOCIAL_COMMENT,
            TelemetryEvents.SOCIAL_FOLLOW,
            TelemetryEvents.SOCIAL_REPORT,
            TelemetryEvents.SOCIAL_BLOCK,
            TelemetryEvents.PLAY_STATE_VIEW,
            TelemetryEvents.PLAY_QUIZ_START,
            TelemetryEvents.PLAY_QUIZ_ANSWER,
            TelemetryEvents.PLAY_QUIZ_DONE,
            TelemetryEvents.PLAY_H2H_CREATE,
            TelemetryEvents.PLAY_H2H_OPEN,
            TelemetryEvents.PLAY_H2H_RESULT,
            TelemetryEvents.PLAY_BOARD_VIEW,
            TelemetryEvents.PLAY_CHALLENGE_VIEW,
        )

    @Test
    fun `event names are the server names and unique tokens`() {
        assertEquals(events.size, events.toSet().size)
        assertTrue(events.all(TelemetryProps::isToken))
        assertEquals("trip_view", TelemetryEvents.TRIP_VIEW)
        assertEquals("outbox_flush", TelemetryEvents.OUTBOX_FLUSH)
        assertEquals("onboarding_result", TelemetryEvents.ONBOARDING_RESULT)
    }

    @Test
    fun `enum values are tokens and match the registry sizes`() {
        val sets =
            listOf(
                TelemetryEnums.AUTH_METHOD to 5,
                TelemetryEnums.NOTIF_CATEGORY to 7,
                TelemetryEnums.NOTIF_SOURCE to 2,
                TelemetryEnums.AUTH_MODE to 3,
                TelemetryEnums.AUTH_RESULT to 6,
                TelemetryEnums.SIGNUP_STEP to 4,
                TelemetryEnums.EMAIL_VERIFY_RESULT to 6,
                TelemetryEnums.PASSWORD_RESET_STEP to 2,
                TelemetryEnums.PASSWORD_RESET_RESULT to 5,
                TelemetryEnums.ACCOUNT_ACTION to 11,
                TelemetryEnums.QUOTA_BUCKET to 6,
                TelemetryEnums.QUOTA_REASON to 2,
                TelemetryEnums.GAME_VIEW_SURFACE to 3,
                TelemetryEnums.TRIP_VIEW_SURFACE to 8,
                TelemetryEnums.TRIP_PHASE to 6,
                TelemetryEnums.TRIP_ACTION to 9,
                TelemetryEnums.PREP_ITEM to 12,
                TelemetryEnums.TODAY_ACTION to 6,
                TelemetryEnums.OFFLINE_PACK_ACTION to 4,
                TelemetryEnums.OFFLINE_PACK_RESULT to 4,
                TelemetryEnums.EXPENSE_ACTION to 3,
                TelemetryEnums.EXPENSE_CATEGORY to 8,
                TelemetryEnums.JOURNAL_ACTION to 3,
                TelemetryEnums.PLACE_ACTION to 4,
                TelemetryEnums.PLACE_KIND to 8,
                TelemetryEnums.RECAP_ACTION to 4,
                TelemetryEnums.COMPLETENESS_SURFACE to 2,
                TelemetryEnums.ONBOARDING_STEP_ACTION to 4,
                TelemetryEnums.ONBOARDING_TYPE to 8,
                TelemetryEnums.ALERT_KIND to 4,
                TelemetryEnums.ALERT_VERDICT to 5,
                TelemetryEnums.ALERT_ADVICE_OUTCOME to 3,
                TelemetryEnums.SHARE_KIND to 5,
                TelemetryEnums.SHARE_DATES to 3,
                TelemetryEnums.SHARE_TARGET to 7,
                TelemetryEnums.SOCIAL_VISIBILITY to 3,
                TelemetryEnums.SOCIAL_FEED_SCOPE to 3,
                TelemetryEnums.SOCIAL_IDEA_KIND to 2,
                TelemetryEnums.SOCIAL_FOLLOW_STATE to 3,
                TelemetryEnums.SOCIAL_REPORT_TARGET to 3,
                TelemetryEnums.SOCIAL_REPORT_REASON to 6,
                TelemetryEnums.PLAY_MODE to 4,
                TelemetryEnums.PLAY_PACK to 5,
                TelemetryEnums.PLAY_WINNER to 3,
                TelemetryEnums.PLAY_BOARD_SCOPE to 2,
            )
        for ((set, size) in sets) {
            assertEquals(size, set.size)
            assertTrue(set.all(TelemetryProps::isToken))
        }
    }

    /**
     * event name -> prop name -> registry type, from the committed snapshot of the server registry.
     *
     * To regenerate `src/test/resources/telemetry/registry-snapshot.txt` after the web registry changes: read
     * `EVENTS` from `backend/app/telemetry/registry.py` on the web repo's origin/main and write one line per event
     * (sorted): the name, then tab-separated `prop=type` pairs (enums as `enum:a|b|c`), keeping the `#` header.
     */
    private val registry: Map<String, Map<String, String>> by lazy {
        val text =
            checkNotNull(javaClass.getResourceAsStream("/telemetry/registry-snapshot.txt")) { "registry snapshot missing" }
                .bufferedReader().readText()
        text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val parts = line.split('\t')
            parts[0] to parts.drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
        }
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return checkNotNull(dir) { "settings.gradle.kts not found above ${System.getProperty("user.dir")}" }
    }

    private fun mainSources(): List<File> =
        repoRoot().walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path }
            .toList()

    private fun constants(): Map<String, String> =
        TelemetryEvents::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .associate { it.name to (it.apply { isAccessible = true }.get(null) as String) }

    @Test
    fun `every event constant exists in the server registry snapshot`() {
        val missing = constants().filterValues { it !in registry }
        assertTrue("Events missing from the registry snapshot: $missing", missing.isEmpty())
    }

    private val emitCall = Regex("""TelemetryEvents\.([A-Z_0-9]+)\s*,\s*mapOf\(""")
    private val propKey = Regex(""""([a-z_0-9]+)"\s+to\b""")

    /** The text of the `mapOf(...)` argument starting right after its opening parenthesis at [start]. */
    private fun balanced(src: String, start: Int): String {
        var depth = 1
        var i = start
        while (i < src.length && depth > 0) {
            if (src[i] == '(') {
                depth++
            } else if (src[i] == ')') {
                depth--
            }
            i++
        }
        return src.substring(start, i - 1)
    }

    private fun undeclared(file: File, consts: Map<String, String>): Pair<Int, List<String>> {
        val src = file.readText()
        val calls = emitCall.findAll(src).toList()
        val problems =
            calls.flatMap { m ->
                val event = consts[m.groupValues[1]]
                val declared = event?.let { registry[it] }
                if (event == null || declared == null) {
                    emptyList()
                } else {
                    propKey.findAll(balanced(src, m.range.last + 1)).map { it.groupValues[1] }
                        .filter { it !in declared }.map { "${file.name}: $event sends undeclared prop $it" }.toList()
                }
            }
        return calls.size to problems
    }

    @Test
    fun `every emit site sends only props the registry declares for that event`() {
        val consts = constants()
        val results = mainSources().map { undeclared(it, consts) }
        assertTrue("No emit sites found; the scan is broken", results.sumOf { it.first } > 20)
        val problems = results.flatMap { it.second }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `ui events carry only a tid and a route (never text or values)`() {
        assertEquals(mapOf("tid" to "id", "route" to "id"), registry.getValue(TelemetryEvents.UI_TAP))
        assertEquals(mapOf("tid" to "id", "route" to "id"), registry.getValue(TelemetryEvents.UI_CHANGE))
        assertEquals(mapOf("tid" to "id", "route" to "id"), registry.getValue(TelemetryEvents.UI_SUBMIT))
        assertEquals(mapOf("tid" to "id", "route" to "id", "on" to "bool"), registry.getValue(TelemetryEvents.UI_TOGGLE))
    }
}
