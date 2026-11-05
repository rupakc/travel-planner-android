package io.github.rupakc.travelplanner.feature.chat.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.chat.ChatHotel
import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.ChatItineraryDay
import io.github.rupakc.travelplanner.core.model.chat.ChatSlot
import io.github.rupakc.travelplanner.core.model.chat.TripMapCity
import io.github.rupakc.travelplanner.core.model.chat.TripSummary
import io.github.rupakc.travelplanner.core.model.chat.TripSummaryCity
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.chat.ChatUiState
import io.github.rupakc.travelplanner.feature.chat.model.ChatTripMap
import io.github.rupakc.travelplanner.feature.chat.model.ChatTurn
import io.github.rupakc.travelplanner.feature.chat.model.TurnError

// Preview-only sample content (never shown at runtime).

private val previewThreads = listOf(ChatThread(1, "Tokyo in spring", 0, 0), ChatThread(2, "Visa for Thailand", 0, 0))

private val previewQa =
    listOf(
        ChatTurn(1, "user", "What do I need for a visa to Thailand as a US citizen?"),
        ChatTurn(
            2,
            "assistant",
            "US citizens can visit **Thailand** visa-free for up to 60 days.\n\n- Passport valid 6 months\n- Proof of onward travel",
            suggestions = listOf("Plan a week in Bangkok", "Best time to visit?"),
        ),
    )

private val previewTrip =
    ChatTurn(
        id = 4,
        role = "assistant",
        planning = true,
        planningDone = true,
        tripMap = ChatTripMap("New York", null, null, listOf(TripMapCity("Tokyo"), TripMapCity("Kyoto")), "2026-04-01", "2026-04-08", 2),
        itinerary =
            ChatItinerary(
                tripSummary = TripSummary(cities = listOf(TripSummaryCity("Tokyo", nights = 4), TripSummaryCity("Kyoto", nights = 3)), totalNights = 7),
                days =
                    listOf(
                        ChatItineraryDay(
                            dayNumber = 1,
                            date = "2026-04-01",
                            city = "Tokyo",
                            country = "Japan",
                            theme = "Old Tokyo",
                            hotel = ChatHotel(name = "Hotel Gracery", neighbourhood = "Shinjuku", stars = 3.0, tier = "mid-range"),
                            morning = ChatSlot(activity = "Senso-ji Temple", place = "Asakusa"),
                            evening = ChatSlot(activity = "Ramen in Shinjuku"),
                        ),
                    ),
            ),
    )

@Composable
private fun PreviewHost(state: ChatUiState) {
    // SYSTEM: each preview's uiMode picks light or dark.
    TravelTheme(themeMode = ThemeMode.SYSTEM) { ChatScreen(state = state, onEvent = {}) }
}

@Preview(name = "Empty", showBackground = true)
@Preview(name = "Empty dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyPreview() = PreviewHost(ChatUiState(threads = previewThreads))

@Preview(name = "Conversation", showBackground = true)
@Composable
private fun ConversationPreview() = PreviewHost(ChatUiState(threads = previewThreads, currentThreadId = 2, turns = previewQa))

@Preview(name = "Conversation dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConversationDarkPreview() = PreviewHost(ChatUiState(threads = previewThreads, currentThreadId = 2, turns = previewQa))

@Preview(name = "Streaming", showBackground = true)
@Composable
private fun StreamingPreview() =
    PreviewHost(
        ChatUiState(
            currentThreadId = 2,
            turns = previewQa.take(1),
            streaming = ChatTurn(ChatTurn.PENDING_ID, "assistant", "US citizens can", streaming = true),
        ),
    )

@Preview(name = "Trip plan", showBackground = true, heightDp = 1200)
@Composable
private fun TripPreview() = PreviewHost(ChatUiState(currentThreadId = 1, turns = listOf(ChatTurn(3, "user", "Plan Tokyo and Kyoto"), previewTrip)))

@Preview(name = "Trip plan dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TripDarkPreview() = PreviewHost(ChatUiState(currentThreadId = 1, turns = listOf(ChatTurn(3, "user", "Plan Tokyo and Kyoto"), previewTrip)))

@Preview(name = "Error", showBackground = true)
@Preview(name = "Error dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ErrorPreview() =
    PreviewHost(ChatUiState(currentThreadId = 1, turns = listOf(ChatTurn(1, "user", "hi"), ChatTurn(2, "assistant", error = TurnError.EMPTY))))

@Preview(name = "Tablet", showBackground = true, widthDp = 1100, heightDp = 800)
@Preview(name = "Tablet dark", showBackground = true, widthDp = 1100, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TabletPreview() = PreviewHost(ChatUiState(threads = previewThreads, currentThreadId = 2, turns = previewQa))
