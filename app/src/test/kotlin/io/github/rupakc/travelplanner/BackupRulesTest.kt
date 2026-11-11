package io.github.rupakc.travelplanner

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Device-specific and per-user state never goes to cloud backup or device transfer: a restored copy would
 * hide a needed push registration, post a backlog of alerts, or reuse another device's keys and session.
 */
class BackupRulesTest {
    private val mustExclude =
        listOf(
            "file" to "datastore/session.preferences_pb",
            "file" to "datastore/last_location.preferences_pb",
            "database" to "travel-planner.db",
            "sharedpref" to "tp_keys.xml",
            "sharedpref" to "alert_poll.xml",
            "sharedpref" to "trip_reminders.xml",
            "sharedpref" to "nearby_notifications.xml",
            "sharedpref" to "push_registration.xml",
            "sharedpref" to "server_capabilities.xml",
            // Telemetry anon id, consent choice and session (TelemetryDataStoreFiles.TELEMETRY).
            "file" to "datastore/telemetry.preferences_pb",
        )

    private fun excludes(section: String): Set<Pair<String, String>> =
        Regex("""<exclude domain="([^"]+)" path="([^"]+)" />""").findAll(section).map { it.groupValues[1] to it.groupValues[2] }.toSet()

    private fun xml(name: String) = File("src/main/res/xml/$name").readText()

    @Test
    fun `legacy full backup excludes every device-specific file`() {
        assertEquals(emptyList<Pair<String, String>>(), mustExclude - excludes(xml("backup_rules.xml")))
    }

    @Test
    fun `cloud backup and device transfer both exclude every device-specific file`() {
        val rules = xml("data_extraction_rules.xml")
        for (tag in listOf("cloud-backup", "device-transfer")) {
            val section = rules.substringAfter("<$tag>").substringBefore("</$tag>")
            assertEquals(tag, emptyList<Pair<String, String>>(), mustExclude - excludes(section))
        }
    }
}
