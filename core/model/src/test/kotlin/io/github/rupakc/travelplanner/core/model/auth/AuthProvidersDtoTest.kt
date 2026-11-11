package io.github.rupakc.travelplanner.core.model.auth

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthProvidersDtoTest {
    private fun decode(json: String) = TravelJson.decodeFromString(AuthProvidersDto.serializer(), json)

    @Test
    fun `decodes the providers contract and ignores unknown fields`() {
        val dto =
            decode(
                """{"providers":[{"id":"google","configured":true,"start_url":"/api/auth/oauth/google/start","id_token":true,"x":1},
                   {"id":"apple","configured":false,"start_url":null,"id_token":false}],"extra":"y"}""",
            )
        assertEquals(listOf("google", "apple"), dto.providers.map { it.id })
        assertTrue(dto.providers[0].configured)
        assertEquals("/api/auth/oauth/google/start", dto.providers[0].startUrl)
        assertFalse(dto.providers[1].configured)
        assertNull(dto.providers[1].startUrl)
    }

    @Test
    fun `a malformed entry does not fail the list`() {
        val dto = decode("""{"providers":[{"id":"github","configured":true},"oops",{"id":{"bad":1}},42,{"id":"facebook"}]}""")
        assertEquals(listOf("github", "facebook"), dto.providers.map { it.id })
        assertFalse(dto.providers[1].configured)
    }

    @Test
    fun `a non-list or missing providers decodes to empty`() {
        assertTrue(decode("""{"providers":{"id":"google"}}""").providers.isEmpty())
        assertTrue(decode("""{"providers":null}""").providers.isEmpty())
        assertTrue(decode("""{}""").providers.isEmpty())
    }
}
