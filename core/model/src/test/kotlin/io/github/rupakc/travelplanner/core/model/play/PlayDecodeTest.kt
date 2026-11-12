package io.github.rupakc.travelplanner.core.model.play

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The examples of docs/Social-API.md (Play) parse; unknown keys and enum values are tolerated. */
class PlayDecodeTest {
    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, json: String): T = TravelJson.decodeFromString(s, json)

    @Test
    fun `play state`() {
        val s =
            decode(
                PlayState.serializer(),
                """{"daily":{"available":true,"done":false,"score":null,"streak":{"current":3,"longest":5,"freeze_available":true}},
            "packs":[{"id":"flags","title_key":"play.pack.flags","questions":40},{"id":"mystery","questions":"9"}],
            "challenge":{"id":"2026-W46:JP","kind":"destination","country":"JP","done":false},"open_h2h":2,"x":1}""",
            )
        assertTrue(s.daily.available)
        assertNull(s.daily.score)
        assertEquals(3, s.daily.streak.current)
        assertTrue(s.daily.streak.freezeAvailable)
        assertEquals(QuizPack.FLAGS, s.packs[0].pack)
        assertNull(s.packs[1].pack)
        assertEquals(9, s.packs[1].questions)
        assertEquals("JP", s.challenge!!.country)
        assertEquals(2, s.openH2h)
    }

    @Test
    fun `no challenge and an older shape`() {
        val s = decode(PlayState.serializer(), """{"daily":{"available":false,"done":true,"score":340,"streak":{}},"packs":[],"challenge":null,"open_h2h":0}""")
        assertNull(s.challenge)
        assertEquals(340, s.daily.score)
        assertTrue(s.daily.done)
    }

    @Test
    fun `quiz session with options and codes`() {
        val q =
            decode(
                QuizSession.serializer(),
                """{"session_id":"s1","mode":"daily","pack":null,"expires_at":"2026-11-12T10:20:00Z","done":false,
            "questions":[{"index":0,"kind":"flag","prompt":{"template":"flag","flag":"x"},
            "options":[{"id":"a","label":"Japan","code":"JP"},{"id":"b","label":"Peru"}]},
            {"index":1,"kind":"quantum","prompt":{"template":"quantum"},"options":[]}],"answers":[]}""",
            )
        assertEquals(QuizMode.DAILY, q.quizMode)
        assertEquals(QuestionKind.FLAG, q.questions[0].questionKind)
        assertEquals("x", q.questions[0].promptText("flag"))
        assertEquals("JP", q.questions[0].options[0].code)
        assertNull(q.questions[0].options[1].code)
        assertEquals(QuestionKind.UNKNOWN, q.questions[1].questionKind)
        assertFalse(q.resumed)
    }

    @Test
    fun `resumed session with answers and summary with xp`() {
        val q =
            decode(
                QuizSession.serializer(),
                """{"session_id":"s1","mode":"pack","pack":"capitals","questions":[],"expires_at":"t","resumed":true,"done":true,
            "answers":[{"index":0,"choice":"b","correct":true,"correct_choice":"b","points":140,"elapsed_ms":2000}],
            "summary":{"score":600,"correct":5,"total":5,"xp":{"xp":120,"awards":[{"rule":"quiz_pack","points":5}],"badges":[],"level_up":null},
            "streak":{"current":2,"longest":3,"freeze_available":false}}}""",
            )
        assertTrue(q.resumed && q.done)
        assertEquals(140, q.answers.single().points)
        assertEquals(5, q.summary!!.correct)
        assertEquals(5, q.summary!!.xp!!.awards.single().points)
        assertEquals(2, q.summary!!.streak!!.current)
    }

    @Test
    fun `h2h session view carries the match id`() {
        val q =
            decode(
                QuizSession.serializer(),
                """{"id":"m1","session_id":"s9","mode":"h2h","pack":"flags","questions":[],"expires_at":"t","answers":[],"done":false}""",
            )
        assertEquals("m1", q.id)
        assertEquals(QuizMode.H2H, q.quizMode)
    }

    @Test
    fun `answer result`() {
        val a =
            decode(
                AnswerResult.serializer(),
                """{"correct":false,"correct_choice":"c","points":0,"elapsed_ms":4200,"done":true,"summary":{"score":240,"correct":2,"total":5}}""",
            )
        assertFalse(a.correct)
        assertEquals("c", a.correctChoice)
        assertEquals(4200, a.elapsedMs)
        assertNull(a.summary!!.xp)
        assertEquals(
            """{"index":2,"choice":"b"}""",
            TravelJson.encodeToString(AnswerRequest.serializer(), AnswerRequest(2, "b")),
        )
        assertEquals(
            """{"mode":"pack","pack":"flags"}""",
            TravelJson.encodeToString(StartQuizRequest.serializer(), StartQuizRequest("pack", "flags")),
        )
    }

    @Test
    fun `history`() {
        val h = decode(PlayHistory.serializer(), """{"sessions":[{"session_id":"s","mode":"daily","pack":null,"score":500,"correct":4,"finished_at":"t"}]}""")
        assertEquals(500, h.sessions.single().score)
    }

    @Test
    fun `head to head list states and winners`() {
        val l =
            decode(
                H2hList.serializer(),
                """{"matches":[
            {"id":"m1","opponent":{"handle":"bob"},
            "pack":"flags","state":"waiting_you","you":null,"them":null,"winner":null,"expires_at":"t","session_id":null},
            {"id":"m2","opponent":{"handle":"cy"},"pack":"capitals","state":"done","you":400,"them":350,"winner":"you","expires_at":"t","session_id":"s2"},
            {"id":"m3","opponent":{"handle":"di"},"pack":"flags","state":"rematch","winner":"tie"}]}""",
            )
        assertEquals(H2hState.WAITING_YOU, l.matches[0].matchState)
        assertNull(l.matches[0].matchWinner)
        assertEquals(H2hWinner.YOU, l.matches[1].matchWinner)
        assertEquals(350, l.matches[1].them)
        assertEquals(H2hState.UNKNOWN, l.matches[2].matchState)
        assertEquals(H2hWinner.UNKNOWN, l.matches[2].matchWinner)
        assertEquals("""{"handle":"bob","pack":"flags"}""", TravelJson.encodeToString(CreateH2hRequest.serializer(), CreateH2hRequest("bob", "flags")))
    }

    @Test
    fun `leaderboard with and without the caller`() {
        val b =
            decode(
                Leaderboard.serializer(),
                """{"week":"2026-W46","rows":[{"rank":1,"handle":"a","score":900,"level":5,"you":false},{"rank":"2","handle":"me","score":800,"you":true}],
            "you":{"rank":2,"score":800,"opted_in":true}}""",
            )
        assertEquals(5, b.rows[0].level)
        assertNull(b.rows[1].level)
        assertEquals(2, b.rows[1].rank)
        assertTrue(b.rows[1].you)
        assertTrue(b.you!!.optedIn)
        val none = decode(Leaderboard.serializer(), """{"week":"2026-W45","rows":[],"you":{"rank":null,"score":0,"opted_in":false}}""")
        assertNull(none.you!!.rank)
        assertNull(decode(Leaderboard.serializer(), """{"week":"w","rows":[],"you":null}""").you)
    }

    @Test
    fun `challenge`() {
        val c = decode(Challenge.serializer(), """{"id":"2026-W46:flags","kind":"featured","title_key":"play.challenge.featured.flags","done":true}""")
        assertNull(c.country)
        assertTrue(c.done)
    }
}
