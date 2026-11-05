package io.github.rupakc.travelplanner.core.domain.globalsearch

/** Settings screens that own rows (root list or a sub-screen route). */
enum class SettingsPage { ROOT, PREFERENCES, TASTE_PROFILE, FEATURES, APPEARANCE, SERVER, NOTIFICATIONS }

/**
 * Every settings row that global search indexes. [key] is the stable id carried by
 * `SettingsEntryRoute(entry)`; the settings feature scrolls [page] to the row with that key.
 * Titles are string resources in the UI modules (domain stays Android-free).
 */
enum class SettingsEntry(val key: String, val page: SettingsPage) {
    TRAVEL_PREFERENCES("travel_preferences", SettingsPage.ROOT),
    TASTE_GRAPH("taste_graph", SettingsPage.TASTE_PROFILE),
    BUDGET("budget", SettingsPage.PREFERENCES),
    NATIONALITY("nationality", SettingsPage.PREFERENCES),
    RESIDENCE("residence", SettingsPage.PREFERENCES),
    EMAIL("email", SettingsPage.PREFERENCES),
    DOCUMENTS("documents", SettingsPage.PREFERENCES),
    TRAVEL_GROUP("travel_group", SettingsPage.PREFERENCES),
    INTERESTS("interests", SettingsPage.PREFERENCES),
    TRAVELLER_TYPE("traveller_type", SettingsPage.ROOT),
    FEATURES("features", SettingsPage.FEATURES),
    THEME("theme", SettingsPage.APPEARANCE),
    DYNAMIC_COLOR("dynamic_color", SettingsPage.APPEARANCE),
    LANGUAGE("language", SettingsPage.ROOT),
    NOTIFICATIONS("notifications", SettingsPage.NOTIFICATIONS),
    SERVER("server", SettingsPage.SERVER),
    CHANGE_PASSWORD("change_password", SettingsPage.ROOT),
    SIGN_OUT("sign_out", SettingsPage.ROOT),
    ABOUT("about", SettingsPage.ROOT),
    ;

    companion object {
        fun fromKey(key: String): SettingsEntry? = entries.firstOrNull { it.key == key }
    }
}
