package io.github.rupakc.travelplanner.feature.social.report

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.feature.social.R

/** The fixed copy of a report reason (the server only knows the enum; R11: no free text for reasons). */
@StringRes
fun ReportReason.labelRes(): Int =
    when (this) {
        ReportReason.SPAM -> R.string.social_report_spam
        ReportReason.ABUSE -> R.string.social_report_abuse
        ReportReason.SEXUAL -> R.string.social_report_sexual
        ReportReason.VIOLENCE -> R.string.social_report_violence
        ReportReason.MISINFORMATION -> R.string.social_report_misinformation
        ReportReason.OTHER -> R.string.social_report_other
    }
