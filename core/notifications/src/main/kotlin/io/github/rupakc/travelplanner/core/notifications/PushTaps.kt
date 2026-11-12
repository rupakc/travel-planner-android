package io.github.rupakc.travelplanner.core.notifications

import android.content.Intent
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory

/**
 * Reads (and strips) which category a tapped push belongs to, for the `notif_open` telemetry event (source `push`).
 *
 * Pushes this app posts carry the category in their deep link ([NotificationLinks.OPEN_CATEGORY_PARAM]). The value
 * is only ever accepted as one of the closed [NotificationCategory] names, so a link from another app can at most
 * count a tap; it opens nothing and carries no free text.
 */
object PushTaps {
    /**
     * The category of the `travelplanner://` VIEW [intent] when it is a tagged push tap, or null. The tag is removed
     * from the intent's data (in place), so the destination sees the plain link and a re-delivery counts nothing twice.
     */
    fun consume(intent: Intent?): NotificationCategory? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        if (data.scheme != DeepLinks.SCHEME) return null
        val tag = data.getQueryParameter(NotificationLinks.OPEN_CATEGORY_PARAM) ?: return null
        val rest = data.queryParameterNames.filter { it != NotificationLinks.OPEN_CATEGORY_PARAM }
        val clean = data.buildUpon().clearQuery()
        rest.forEach { name -> data.getQueryParameters(name).forEach { clean.appendQueryParameter(name, it) } }
        intent.data = clean.build()
        return NotificationCategory.from(tag)
    }
}
