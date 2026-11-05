package io.github.rupakc.travelplanner.e2e

import androidx.test.platform.app.InstrumentationRegistry

/** Instrumentation arguments set by the `travelplanner.android.e2e` convention plugin from Gradle properties. */
object E2eConfig {
    private val args get() = InstrumentationRegistry.getArguments()

    /** Backend as the emulator sees it (`-Pe2eBaseUrl`, default `http://10.0.2.2:8031`). */
    val baseUrl: String get() = args.getString("e2eBaseUrl") ?: "http://10.0.2.2:8031"

    /** The backend's `ADMIN_PASSWORD` (`-Pe2eAdminPassword`). */
    val adminPassword: String get() = args.getString("e2eAdminPassword") ?: "e2e-Test-Pass1"

    const val ADMIN = "admin"

    /** Second, non-admin account the suite creates through the admin API when missing. */
    const val SECOND_USER = "e2e-traveller"
    const val SECOND_PASSWORD = "e2e-traveller-pw-1"
}
