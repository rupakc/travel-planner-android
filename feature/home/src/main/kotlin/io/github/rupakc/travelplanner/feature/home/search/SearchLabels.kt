package io.github.rupakc.travelplanner.feature.home.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.ui.descriptionRes
import io.github.rupakc.travelplanner.core.ui.titleRes
import io.github.rupakc.travelplanner.feature.home.R
import javax.inject.Inject

/** Localised text for the static part of the global-search index (features and settings rows). */
interface SearchLabels {
    fun featureTitle(feature: FeatureId): String

    fun featureDescription(feature: FeatureId): String

    fun settingTitle(entry: SettingsEntry): String

    /** The settings section the row lives in (shown as the subtitle). */
    fun settingSection(entry: SettingsEntry): String

    /** Extra words that should find the row ("dark mode" → Theme). */
    fun settingKeywords(entry: SettingsEntry): List<String>
}

/** [SearchLabels] from string resources (reads the current per-app locale). */
class ResourceSearchLabels
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SearchLabels {
        override fun featureTitle(feature: FeatureId): String = context.getString(feature.titleRes())

        override fun featureDescription(feature: FeatureId): String = context.getString(feature.descriptionRes())

        override fun settingTitle(entry: SettingsEntry): String = context.getString(entry.titleRes())

        override fun settingSection(entry: SettingsEntry): String = context.getString(entry.sectionRes())

        override fun settingKeywords(entry: SettingsEntry): List<String> =
            entry.keywordsRes()?.let { res -> context.getString(res).split(',').map(String::trim).filter(String::isNotEmpty) }.orEmpty()
    }

internal fun SettingsEntry.titleRes(): Int =
    when (this) {
        SettingsEntry.TRAVEL_PREFERENCES -> R.string.home_setting_travel_preferences
        SettingsEntry.TASTE_GRAPH -> R.string.home_setting_taste_graph
        SettingsEntry.BUDGET -> R.string.home_setting_budget
        SettingsEntry.NATIONALITY -> R.string.home_setting_nationality
        SettingsEntry.RESIDENCE -> R.string.home_setting_residence
        SettingsEntry.EMAIL -> R.string.home_setting_email
        SettingsEntry.DOCUMENTS -> R.string.home_setting_documents
        SettingsEntry.TRAVEL_GROUP -> R.string.home_setting_travel_group
        SettingsEntry.INTERESTS -> R.string.home_setting_interests
        SettingsEntry.TRAVELLER_TYPE -> R.string.home_setting_traveller_type
        SettingsEntry.FEATURES -> R.string.home_setting_features
        SettingsEntry.THEME -> R.string.home_setting_theme
        SettingsEntry.DYNAMIC_COLOR -> R.string.home_setting_dynamic_color
        SettingsEntry.LANGUAGE -> R.string.home_setting_language
        SettingsEntry.NOTIFICATIONS -> R.string.home_setting_notifications
        SettingsEntry.SERVER -> R.string.home_setting_server
        SettingsEntry.CHANGE_PASSWORD -> R.string.home_setting_change_password
        SettingsEntry.SIGN_OUT -> R.string.home_setting_sign_out
        SettingsEntry.ABOUT -> R.string.home_setting_about
    }

internal fun SettingsEntry.sectionRes(): Int =
    when (this) {
        SettingsEntry.TRAVEL_PREFERENCES, SettingsEntry.TASTE_GRAPH, SettingsEntry.BUDGET, SettingsEntry.NATIONALITY,
        SettingsEntry.RESIDENCE, SettingsEntry.EMAIL, SettingsEntry.DOCUMENTS, SettingsEntry.TRAVEL_GROUP, SettingsEntry.INTERESTS,
        -> R.string.home_setting_section_preferences

        SettingsEntry.TRAVELLER_TYPE -> R.string.home_setting_section_type

        SettingsEntry.FEATURES -> R.string.home_setting_section_features

        SettingsEntry.THEME, SettingsEntry.DYNAMIC_COLOR -> R.string.home_setting_section_appearance

        SettingsEntry.LANGUAGE -> R.string.home_setting_section_language

        SettingsEntry.NOTIFICATIONS -> R.string.home_setting_section_notifications

        SettingsEntry.SERVER -> R.string.home_setting_section_server

        SettingsEntry.CHANGE_PASSWORD, SettingsEntry.SIGN_OUT -> R.string.home_setting_section_account

        SettingsEntry.ABOUT -> R.string.home_setting_section_about
    }

private fun SettingsEntry.keywordsRes(): Int? =
    when (this) {
        SettingsEntry.THEME -> R.string.home_setting_theme_keywords
        SettingsEntry.TRAVELLER_TYPE -> R.string.home_setting_traveller_type_keywords
        SettingsEntry.SERVER -> R.string.home_setting_server_keywords
        SettingsEntry.ABOUT -> R.string.home_setting_about_keywords
        SettingsEntry.NOTIFICATIONS -> R.string.home_setting_notifications_keywords
        SettingsEntry.TRAVEL_GROUP -> R.string.home_setting_travel_group_keywords
        else -> null
    }
