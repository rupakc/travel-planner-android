package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation

/*
 * Tracked interaction wrappers. Every tap/toggle/edit in a feature goes through one of these so it reaches
 * [LocalUiEventSink] (telemetry `ui_tap` / `ui_toggle` / `ui_change` / `ui_submit`) under a stable [tid]: an
 * element name, never its text or value. Reporting is a non-blocking hand-off (see [UiEventSink]); the click
 * action itself runs right after, so no latency is added. Raw foundation/Material3 equivalents are forbidden
 * outside this module (guarded by `RawInteractionGuardTest` in the `app` module).
 */

/** [clickable] that reports TAP under [tid]. */
@Composable
fun Modifier.tpClickable(
    tid: String,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val sink = LocalUiEventSink.current
    return clickable(enabled = enabled, onClickLabel = onClickLabel, role = role) {
        sink.report(UiEventKind.TAP, tid)
        onClick()
    }
}

/** Tap detector that reports TAP under [tid] and hands the tap position to [onTap] (for canvases such as the scratch map). */
@Composable
fun Modifier.tpTapPosition(tid: String, onTap: (androidx.compose.ui.geometry.Offset) -> Unit): Modifier {
    val sink = LocalUiEventSink.current
    return pointerInput(tid) {
        detectTapGestures { offset ->
            sink.report(UiEventKind.TAP, tid)
            onTap(offset)
        }
    }
}

/** [selectable] that reports TAP under [tid]. */
@Composable
fun Modifier.tpSelectable(
    tid: String,
    selected: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val sink = LocalUiEventSink.current
    return selectable(selected = selected, enabled = enabled, role = role) {
        sink.report(UiEventKind.TAP, tid)
        onClick()
    }
}

/** [toggleable] that reports TOGGLE (with the new state) under [tid]. */
@Composable
fun Modifier.tpToggleable(
    tid: String,
    value: Boolean,
    enabled: Boolean = true,
    role: Role? = null,
    onValueChange: (Boolean) -> Unit,
): Modifier {
    val sink = LocalUiEventSink.current
    return toggleable(value = value, enabled = enabled, role = role) {
        sink.report(UiEventKind.TOGGLE, tid, it)
        onValueChange(it)
    }
}

@Composable
fun TpIconButton(
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val sink = LocalUiEventSink.current
    IconButton(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun TpFilledIconButton(
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    content: @Composable () -> Unit,
) {
    val sink = LocalUiEventSink.current
    FilledIconButton(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        content = content,
    )
}

@Composable
fun TpIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val sink = LocalUiEventSink.current
    IconToggleButton(
        checked = checked,
        onCheckedChange = {
            sink.report(UiEventKind.TOGGLE, tid, it)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun TpSmallFloatingActionButton(
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sink = LocalUiEventSink.current
    SmallFloatingActionButton(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        content = content,
    )
}

@Composable
fun TpTextButton(
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val sink = LocalUiEventSink.current
    TextButton(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun TpOutlinedButton(
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    content: @Composable RowScope.() -> Unit,
) {
    val sink = LocalUiEventSink.current
    OutlinedButton(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        content = content,
    )
}

@Composable
fun TpRawButton(
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val sink = LocalUiEventSink.current
    Button(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun TpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val sink = LocalUiEventSink.current
    Switch(
        checked = checked,
        onCheckedChange = {
            sink.report(UiEventKind.TOGGLE, tid, it)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun TpCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val sink = LocalUiEventSink.current
    Checkbox(
        checked = checked,
        onCheckedChange = {
            sink.report(UiEventKind.TOGGLE, tid, it)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun TpRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val sink = LocalUiEventSink.current
    RadioButton(
        selected = selected,
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun TpTab(
    selected: Boolean,
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color = LocalContentColor.current,
    unselectedContentColor: Color = selectedContentColor,
    interactionSource: MutableInteractionSource? = null,
) {
    val sink = LocalUiEventSink.current
    Tab(
        selected = selected,
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        text = text,
        icon = icon,
        selectedContentColor = selectedContentColor,
        unselectedContentColor = unselectedContentColor,
        interactionSource = interactionSource,
    )
}

@Composable
fun TpDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val sink = LocalUiEventSink.current
    DropdownMenuItem(
        text = text,
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

@Composable
fun TpAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled),
) {
    val sink = LocalUiEventSink.current
    AssistChip(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        border = border,
    )
}

@Composable
fun TpSuggestionChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = SuggestionChipDefaults.shape,
) {
    val sink = LocalUiEventSink.current
    SuggestionChip(
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
    )
}

@Composable
fun TpInputChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = InputChipDefaults.shape,
    colors: SelectableChipColors = InputChipDefaults.inputChipColors(),
) {
    val sink = LocalUiEventSink.current
    InputChip(
        selected = selected,
        onClick = {
            sink.report(UiEventKind.TAP, tid)
            onClick()
        },
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
    )
}

/** [Slider] that reports CHANGE once per gesture (on release), never per value. */
@Composable
fun TpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
) {
    val sink = LocalUiEventSink.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = {
            sink.report(UiEventKind.CHANGE, tid)
            onValueChangeFinished?.invoke()
        },
        colors = colors,
    )
}

/** [RangeSlider] that reports CHANGE once per gesture (on release). */
@Composable
fun TpRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val sink = LocalUiEventSink.current
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = {
            sink.report(UiEventKind.CHANGE, tid)
            onValueChangeFinished?.invoke()
        },
    )
}

/**
 * [OutlinedTextField] that reports CHANGE under [tid] on the first edit of each focus session and SUBMIT on a
 * keyboard action. Only the field's [tid] is sent: never its text, not even its length.
 */
@Composable
fun TpOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    tid: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val sink = LocalUiEventSink.current
    var reported by remember { mutableStateOf(false) }
    val actions = keyboardActions.reporting(sink, tid)
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (!reported && it != value) {
                reported = true
                sink.report(UiEventKind.CHANGE, tid)
            }
            onValueChange(it)
        },
        modifier = modifier.onFocusChanged { if (!it.isFocused) reported = false },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        prefix = prefix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = actions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        colors = colors,
    )
}

/** Wraps every keyboard action handler so it reports SUBMIT under [tid] before running; absent handlers stay absent. */
private fun KeyboardActions.reporting(sink: UiEventSink, tid: String): KeyboardActions {
    fun wrap(f: (KeyboardActionScope.() -> Unit)?): (KeyboardActionScope.() -> Unit)? =
        f?.let { handler ->
            {
                sink.report(UiEventKind.SUBMIT, tid)
                handler()
            }
        }
    return KeyboardActions(
        onDone = wrap(onDone),
        onGo = wrap(onGo),
        onNext = wrap(onNext),
        onPrevious = wrap(onPrevious),
        onSearch = wrap(onSearch),
        onSend = wrap(onSend),
    )
}
