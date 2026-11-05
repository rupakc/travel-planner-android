package io.github.rupakc.travelplanner.core.common

/**
 * Build-time facts provided by `app` (from its BuildConfig) so core modules
 * don't need their own flavoured BuildConfig.
 *
 * @property defaultBaseUrl flavour default backend URL (`local`: http://10.0.2.2:8001; `prod`: PROD_BASE_URL, may be blank)
 * @property flavor "local" | "prod"
 * @property webBaseUrl base for web links such as `<web>/share/<token>`; blank when unknown
 */
data class AppBuildInfo(
    val defaultBaseUrl: String,
    val flavor: String,
    val versionName: String,
    val debug: Boolean,
    val cleartextAllowed: Boolean,
    val webBaseUrl: String = "",
)
