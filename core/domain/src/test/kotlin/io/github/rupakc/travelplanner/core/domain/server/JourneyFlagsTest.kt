package io.github.rupakc.travelplanner.core.domain.server

import io.github.rupakc.travelplanner.core.model.config.AuthConfigDto
import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.config.FeaturesDto
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyFlagsTest {
    private val all =
        FeaturesDto(accounts = true, game = true, trips = true, journey = true, offlinePack = true)

    @Test
    fun `no config or an older backend is everything off`() {
        assertEquals(JourneyFlags.OFF, JourneyFlags(null))
        assertEquals(JourneyFlags.OFF, JourneyFlags(ClientConfigDto()))
        assertEquals(JourneyFlags.OFF, JourneyFlags(TravelJson.decodeFromString(ClientConfigDto.serializer(), """{"public_base_url":"https://x"}""")))
        assertEquals(JourneyFlags.OFF, JourneyFlags(ClientConfigDto(auth = AuthConfigDto(signupAvailable = true))))
    }

    @Test
    fun `all flags on`() {
        val flags =
            JourneyFlags(ClientConfigDto(features = all, auth = AuthConfigDto(signupAvailable = true, passwordResetAvailable = true)))
        assertTrue(flags.accounts && flags.game && flags.trips && flags.journey && flags.offlinePack)
        assertTrue(flags.signupAvailable && flags.resetAvailable)
    }

    @Test
    fun `journey needs trips and journey`() {
        assertFalse(JourneyFlags(ClientConfigDto(features = all.copy(trips = false))).journey)
        assertFalse(JourneyFlags(ClientConfigDto(features = all.copy(journey = false))).journey)
        assertTrue(JourneyFlags(ClientConfigDto(features = all.copy(journey = false))).trips)
    }

    @Test
    fun `offline pack off hides the feature`() {
        val flags = JourneyFlags(ClientConfigDto(features = all.copy(offlinePack = false)))
        assertFalse(flags.offlinePack)
        assertTrue(flags.journey)
    }

    @Test
    fun `sign-up and reset need accounts and the auth block`() {
        val auth = AuthConfigDto(signupAvailable = true, passwordResetAvailable = true)
        assertFalse(JourneyFlags(ClientConfigDto(features = all.copy(accounts = false), auth = auth)).signupAvailable)
        assertFalse(JourneyFlags(ClientConfigDto(features = all)).signupAvailable)
        assertFalse(JourneyFlags(ClientConfigDto(features = all, auth = AuthConfigDto(signupAvailable = true))).resetAvailable)
    }

    @Test
    fun `sharing needs features sharing of at least 1`() {
        val on = TravelJson.decodeFromString(ClientConfigDto.serializer(), """{"features":{"accounts":true,"sharing":1}}""")
        assertTrue(JourneyFlags(on).sharing)
        val off = TravelJson.decodeFromString(ClientConfigDto.serializer(), """{"features":{"accounts":true,"sharing":0}}""")
        assertFalse(JourneyFlags(off).sharing)
        val absent = TravelJson.decodeFromString(ClientConfigDto.serializer(), """{"features":{"accounts":true}}""")
        assertFalse(JourneyFlags(absent).sharing)
    }
}
