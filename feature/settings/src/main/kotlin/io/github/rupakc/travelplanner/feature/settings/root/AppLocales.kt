package io.github.rupakc.travelplanner.feature.settings.root

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import javax.inject.Inject

/** Applies the per-app language (AppCompat per-app locales; locales declared in `locales_config.xml`). */
interface AppLocales {
    /** True when per-app languages work on this device (always, via AppCompat, on minSdk 26+). */
    val perAppSupported: Boolean

    /** [tag] null = follow the system language. */
    fun apply(tag: String?)

    /** The language currently set for the app (primary language subtag, e.g. "es"), or null for the system default. */
    fun current(): String?
}

/**
 * [AppLocales] over `AppCompatDelegate`: on Android 13+ it forwards to the system `LocaleManager`; below that
 * AppCompat stores the choice (`AppLocalesMetadataHolderService` with `autoStoreLocales`) and applies it to the
 * AppCompat activity, which it recreates.
 */
class SystemAppLocales
    @Inject
    constructor() : AppLocales {
        override val perAppSupported: Boolean = true

        override fun apply(tag: String?) {
            AppCompatDelegate.setApplicationLocales(if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
        }

        override fun current(): String? {
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) null else locales[0]?.language
        }
    }
