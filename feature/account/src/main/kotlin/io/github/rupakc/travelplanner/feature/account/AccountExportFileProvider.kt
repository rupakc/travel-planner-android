package io.github.rupakc.travelplanner.feature.account

import androidx.core.content.FileProvider

/** Own FileProvider subclass so the manifest entry never clashes with another library's provider. */
class AccountExportFileProvider : FileProvider()
