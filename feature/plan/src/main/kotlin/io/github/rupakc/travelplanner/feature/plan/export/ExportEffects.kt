package io.github.rupakc.travelplanner.feature.plan.export

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.rupakc.travelplanner.feature.plan.R
import kotlinx.coroutines.flow.Flow

/** Collects a screen's [ExportRequest]s while it is visible and runs them; tells the user when no app can. */
@Composable
internal fun ExportEffects(requests: Flow<ExportRequest>, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val noApp = stringResource(R.string.plan_no_app)
    LaunchedEffect(requests, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            requests.collect { request ->
                if (!PlatformExport.run(context, request)) snackbar.showSnackbar(noApp)
            }
        }
    }
}
