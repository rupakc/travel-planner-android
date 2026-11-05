package io.github.rupakc.travelplanner.feature.settings.root

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Applies the per-app language (Android 13+ per-app locales; locales declared in `locales_config.xml`). */
interface AppLocales {
    /** True when the system supports per-app languages (API 33+). */
    val perAppSupported: Boolean

    /** [tag] null = follow the system language. */
    fun apply(tag: String?)

    /** The language currently set for the app (primary language subtag, e.g. "es"), or null for the system default. */
    fun current(): String?
}

/** [AppLocales] over `LocaleManager` (the system persists the choice and recreates the activity). */
class SystemAppLocales
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AppLocales {
        override val perAppSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        override fun apply(tag: String?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }

        override fun current(): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
            return if (locales.isEmpty) null else locales[0].language
        }
    }
