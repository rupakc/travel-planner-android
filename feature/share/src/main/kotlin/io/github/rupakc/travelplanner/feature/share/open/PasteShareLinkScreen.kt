package io.github.rupakc.travelplanner.feature.share.open

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.share.R

private val MaxContentWidth = 640.dp

/**
 * "Open a share link": the fallback for a link the system did not hand to the app (R6: no verified App Links yet).
 * Paste a link or token; [onOpen] gets the token, which the shared-trip screen then loads.
 */
@Composable
internal fun PasteShareLinkScreen(onBack: () -> Unit, onOpen: (token: String) -> Unit, modifier: Modifier = Modifier) {
    var pasted by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val submit = {
        val token = ShareLinkInput.token(pasted)
        if (token == null) invalid = true else onOpen(token)
    }
    Scaffold(modifier = modifier, topBar = { TpTopBar(stringResource(R.string.share_paste_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(TravelTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            ) {
                Text(
                    stringResource(R.string.share_paste_title),
                    style = TravelTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.share_paste_message), style = TravelTheme.typography.bodyMedium)
                TpOutlinedTextField(
                    value = pasted,
                    onValueChange = {
                        pasted = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.share_paste_label)) },
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.share_paste_invalid)) }) else null,
                    singleLine = true,
                    shape = TravelShape.Input,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                    tid = "share.share_paste_label",
                )
                TpButton(
                    stringResource(R.string.share_paste_open),
                    onClick = { submit() },
                    enabled = pasted.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    tid = "share.paste_open",
                )
            }
        }
    }
}
