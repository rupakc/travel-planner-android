package io.github.rupakc.travelplanner.feature.plan.export

import androidx.core.content.FileProvider

/** Own FileProvider subclass so the manifest entry never clashes with another library's FileProvider. */
class PlanExportFileProvider : FileProvider()
