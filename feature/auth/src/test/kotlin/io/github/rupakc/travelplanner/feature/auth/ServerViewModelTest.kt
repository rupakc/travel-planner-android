package io.github.rupakc.travelplanner.feature.auth

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.domain.server.ServerUrlResult
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.feature.auth.server.ServerEvent
import io.github.rupakc.travelplanner.feature.auth.server.ServerTest
import io.github.rupakc.travelplanner.feature.auth.server.ServerViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

val BUILD_INFO = AppBuildInfo(defaultBaseUrl = "http://10.0.2.2:8001", flavor = "local", versionName = "1.0", debug = true, cleartextAllowed = true)

class ServerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val health = FakeHealthRepository(Health(status = "ok", serpapiConfigured = false, pushConfigured = true))

    private fun vm(info: AppBuildInfo = BUILD_INFO) = ServerViewModel(settings, health, info).apply { onEvent(ServerEvent.Open) }

    @Test
    fun `opens with the address in use`() {
        val vm = vm()
        assertEquals("http://10.0.2.2:8001", vm.state.value.input)
        assertEquals(false, vm.state.value.isOverride)
    }

    @Test
    fun `invalid input is explained and nothing is stored`() {
        val vm = vm()
        vm.onEvent(ServerEvent.InputChanged("192.168.1.20:8001"))
        vm.onEvent(ServerEvent.Save)
        assertEquals(ServerUrlResult.Malformed, vm.state.value.inputError)
        assertNull(settings.state.value.baseUrlOverride)
        vm.onEvent(ServerEvent.InputChanged(""))
        assertNull(vm.state.value.inputError)
    }

    @Test
    fun `prod flavour requires https`() {
        val vm = vm(BUILD_INFO.copy(cleartextAllowed = false, defaultBaseUrl = ""))
        vm.onEvent(ServerEvent.InputChanged("http://example.com"))
        vm.onEvent(ServerEvent.Test)
        assertEquals(ServerUrlResult.HttpsRequired, vm.state.value.inputError)
    }

    @Test
    fun `test shows reachable flags, dismiss restores the previous address`() =
        runTest {
            settings.state.value = AppSettings(baseUrlOverride = "http://old:8001")
            val vm = vm()
            vm.state.test {
                skipItems(1)
                vm.onEvent(ServerEvent.InputChanged("http://new:8001"))
                vm.onEvent(ServerEvent.Test)
                var s = awaitItem()
                while (s.testing || s.test == null) s = awaitItem()
                assertEquals(ServerTest.Reachable(serpapiConfigured = false, pushConfigured = true), s.test)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("http://new:8001", settings.state.value.baseUrlOverride)
            vm.onEvent(ServerEvent.Dismiss)
            assertEquals("http://old:8001", settings.state.value.baseUrlOverride)
        }

    @Test
    fun `unreachable server uses our copy, never exception text`() {
        health.set(null) // refresh fails with a client-side Unknown error
        val vm = vm()
        vm.onEvent(ServerEvent.InputChanged("http://nowhere:8001"))
        vm.onEvent(ServerEvent.Test)
        assertEquals(ServerTest.Unreachable(AuthMessage.Local(R.string.auth_server_unreachable)), vm.state.value.test)
    }

    @Test
    fun `save keeps the address and reset goes back to the default`() {
        val vm = vm()
        vm.onEvent(ServerEvent.InputChanged("https://Travel.Example.com/"))
        vm.onEvent(ServerEvent.Test)
        vm.onEvent(ServerEvent.Save)
        assertTrue(vm.state.value.done)
        vm.onEvent(ServerEvent.Dismiss)
        assertEquals("https://travel.example.com", settings.state.value.baseUrlOverride)

        vm.onEvent(ServerEvent.Open)
        vm.onEvent(ServerEvent.Reset)
        assertNull(settings.state.value.baseUrlOverride)
        assertEquals("http://10.0.2.2:8001", vm.state.value.input)
    }

    @Test
    fun `login and change-password messages hide client-side exception text`() {
        assertEquals(AuthMessage.Local(R.string.auth_error_not_configured, suggestServer = true), AppError.NotConfigured(null).toLoginMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_login_failed), AppError.Decoding("Unexpected JSON token at offset 0").toLoginMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_generic), AppError.Unknown("boom").toChangePasswordMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_generic), AppError.Decoding("Unexpected JSON").toChangePasswordMessage())
    }
}
