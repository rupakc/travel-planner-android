package io.github.rupakc.travelplanner.buildlogic

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiltViewModelGuardTest {
    @Test
    fun `finds every HiltViewModel in a file, with package, ignoring comments and other classes`() {
        val source =
            """
            package io.example.alerts

            import dagger.hilt.android.lifecycle.HiltViewModel

            // @HiltViewModel class Commented
            /** Not this one: @HiltViewModel class InKdoc */
            data class State(val x: Int)

            @HiltViewModel
            class NotificationBellViewModel @Inject constructor(repo: Repo) : ViewModel()

            @HiltViewModel(assistedFactory = Factory::class)
            internal class NotificationsViewModel @AssistedInject constructor() : ViewModel()

            @dagger.hilt.android.lifecycle.HiltViewModel
            @Suppress("unused")
            public class QualifiedViewModel : ViewModel()

            class PlainViewModel : ViewModel()
            """.trimIndent()

        assertEquals(
            listOf(
                "io.example.alerts.NotificationBellViewModel",
                "io.example.alerts.NotificationsViewModel",
                "io.example.alerts.QualifiedViewModel",
            ),
            HiltViewModelGuard.findHiltViewModels(source),
        )
    }

    @Test
    fun `component check accepts imported and inline references and names the missing ones`() {
        val component =
            """
            import a.auth.ServerViewModel_HiltModules_KeyModule_Provide_LazyMapKey;
            mapBuilder.put(ServerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, x);
            mapBuilder.put(a.settings.ServerViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, y);
            """.trimIndent()

        val missing =
            HiltViewModelGuard.missingFromComponent(
                listOf("a.auth.ServerViewModel", "a.settings.ServerViewModel", "a.alerts.NotificationBellViewModel"),
                component,
            )

        assertEquals(listOf("a.alerts.NotificationBellViewModel"), missing)
    }

    @Test
    fun `component check accepts a simple-name reference only for the component's own package`() {
        val component =
            """
            package a;
            mapBuilder.put(MainViewModel_HiltModules_KeyModule_Provide_LazyMapKey.lazyClassKeyName, x);
            """.trimIndent()

        val missing = HiltViewModelGuard.missingFromComponent(listOf("a.MainViewModel", "a.other.MainViewModel"), component)

        assertEquals(listOf("a.other.MainViewModel"), missing)
    }

    @Test
    fun `dex check reports R8-removed and unkeyed ViewModels`() {
        val mapping =
            HiltViewModelGuard.parseMapping(
                sequenceOf(
                    "# compiler: R8",
                    "a.HomeViewModel -> b1:",
                    "    int x -> a",
                    "a.BellViewModel -> sv3:",
                ),
            )
        val strings = HiltViewModelGuard.dexStrings(dex("Lb1;", "b1", "Lsv3;", "hello"))

        val missing = HiltViewModelGuard.missingFromDex(listOf("a.HomeViewModel", "a.BellViewModel", "a.GoneViewModel"), mapping, strings)

        assertEquals(listOf("a.BellViewModel (R8 name sv3 is not a dex string)", "a.GoneViewModel (removed by R8)"), missing)
    }

    @Test
    fun `failure message names the ViewModels and the fix`() {
        val message = HiltViewModelGuard.failureMessage("prodRelease", listOf("a.BellViewModel"), emptyList())

        assertTrue(message.contains("a.BellViewModel"))
        assertTrue(message.contains("./gradlew clean"))
        assertTrue(message.contains("assembleProdRelease"))
    }

    /** A minimal dex: header with the string table pointers, the id table, then string_data_items. */
    private fun dex(vararg strings: String): ByteArray {
        val headerSize = 0x70
        val idsOffset = headerSize
        var dataOffset = idsOffset + strings.size * 4
        val ids = ByteArrayOutputStream()
        val data = ByteArrayOutputStream()
        for (s in strings) {
            ids.write(le32(dataOffset))
            val bytes = byteArrayOf(s.length.toByte()) + s.toByteArray() + 0
            data.write(bytes)
            dataOffset += bytes.size
        }
        val header = ByteArray(headerSize)
        "dex\n035".toByteArray().copyInto(header)
        le32(strings.size).copyInto(header, 0x38)
        le32(idsOffset).copyInto(header, 0x3C)
        return header + ids.toByteArray() + data.toByteArray()
    }

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
}
