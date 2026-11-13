package io.github.rupakc.travelplanner.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R

/** Layout constants of the auth screens (defined once). */
internal object AuthLayout {
    /** Web `max-w-md` card. */
    val FormMaxWidth = 440.dp
    val LogoTile = 64.dp
    val LogoIcon = 32.dp
    const val WASH_ALPHA = 0.45f
}

/** Full-screen, scrollable, keyboard-aware column centred on a soft brand wash (web teal→slate→sky). */
@Composable
internal fun AuthBackdrop(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = TravelTheme.colors
    val wash =
        Brush.verticalGradient(
            listOf(
                colors.primaryContainer.copy(alpha = AuthLayout.WASH_ALPHA),
                colors.background,
                colors.secondaryContainer.copy(alpha = AuthLayout.WASH_ALPHA),
            ),
        )
    Box(
        modifier.fillMaxSize().background(colors.background).background(wash).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = AuthLayout.FormMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
            content = content,
        )
    }
}

/** Logo tile + display heading + subtitle (web LoginPage / ChangePasswordPage header). */
@Composable
internal fun AuthHeader(title: String, subtitle: String?, icon: Painter, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(AuthLayout.LogoTile)
                .clip(TravelShape.LargeCard)
                .background(Brush.linearGradient(listOf(TravelTheme.colors.primary, TravelTheme.colors.tertiary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = TravelTheme.colors.onPrimary, modifier = Modifier.size(AuthLayout.LogoIcon))
        }
        Text(
            title,
            style = TravelTheme.typography.headlineMedium,
            color = TravelTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TravelTheme.spacing.lg).semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = TravelTheme.typography.bodyLarge,
                color = TravelTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TravelTheme.spacing.xs),
            )
        }
    }
}

/** Red message box (web `bg-red-50 text-red-700`), announced politely. */
@Composable
internal fun AuthErrorBanner(message: AuthMessage, modifier: Modifier = Modifier) {
    val text =
        when (message) {
            is AuthMessage.Server -> message.text
            is AuthMessage.Local -> stringResource(message.res)
        }
    Text(
        text,
        style = TravelTheme.typography.bodyMedium,
        color = TravelTheme.colors.onErrorContainer,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(TravelShape.Input)
                .background(TravelTheme.colors.errorContainer)
                .padding(TravelTheme.spacing.md)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Plain text field with label, optional placeholder and leading icon. */
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    contentType: ContentType? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
) {
    TpOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        singleLine = true,
        enabled = enabled,
        shape = TravelShape.Input,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth().semantics { if (contentType != null) this.contentType = contentType },
        tid = "auth.auth_components_outlined_text_field",
    )
}

/** Password field with a show/hide toggle (48 dp, labelled, state described for TalkBack). */
@Composable
internal fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    contentType: ContentType = ContentType.Password,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    val toggleLabel = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password)
    val toggleState = stringResource(if (visible) R.string.auth_password_shown else R.string.auth_password_hidden)
    TpOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = {
            TpIconButton(
                onClick = onToggleVisible,
                modifier =
                    Modifier.semantics {
                        stateDescription = toggleState
                    },
                tid = "auth.auth_components_icon_button",
            ) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = toggleLabel,
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        enabled = enabled,
        shape = TravelShape.Input,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier.fillMaxWidth().semantics { this.contentType = contentType },
        tid = "auth.auth_components_outlined_text_field_2",
    )
}
