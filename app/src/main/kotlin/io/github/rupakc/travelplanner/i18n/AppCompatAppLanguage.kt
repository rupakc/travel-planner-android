package io.github.rupakc.travelplanner.i18n

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.domain.i18n.AppLanguages
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The effective UI language from the per-app locale (AppCompat) and the system locales. Both reads are
 * in-memory (AppCompatDelegate keeps the app locales in a static; Resources.getSystem() is a global), so
 * this is safe on any thread, including OkHttp's.
 */
@Singleton
class AppCompatAppLanguage
    @Inject
    constructor() : AppLanguage {
        override fun current(): String =
            AppLanguages.resolve(
                tags(AppCompatDelegate.getApplicationLocales().toLanguageTags()),
                tags(Resources.getSystem().configuration.locales.toLanguageTags()),
            )

        private fun tags(joined: String): List<String> = joined.split(',').filter(String::isNotBlank)
    }
