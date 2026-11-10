package com.goat.kharcha.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.goat.kharcha.core.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val P: Palette @Composable get() = LocalPalette.current

/** Clickable with a soft press-in, so every tappable surface responds to the finger. */
fun Modifier.press(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    color: Color = P.surface,
    border: Color = P.line,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
            .background(color, shape)
            .border(1.dp, border, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, color: Color = P.muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.label, color = color, modifier = modifier)
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = T.h2, color = P.fg, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = T.chip, color = P.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun CatIcon(cat: Category?, size: Dp = 38.dp, iconSize: Dp = 20.dp, unknownColor: Color = P.warn) {
    val dark = P.dark
    val c = if (cat == null) unknownColor else catColor(cat.color, dark)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(c.copy(alpha = if (dark) 0.16f else 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        if (cat == null) Text("?", style = T.title.copy(fontFamily = Display), color = c)
        else Glyph(cat.icon, c, iconSize)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = P.accent, textColor: Color = P.onAccent, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(17.dp))
            .press(enabled = enabled, onClick = onClick)
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.button, color = if (enabled) textColor else textColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, textColor: Color = P.fg, icon: String? = null, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(17.dp))
            .press(onClick = onClick)
            .background(P.surface2)
            .border(1.dp, P.line, RoundedCornerShape(17.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Glyph(icon, textColor, 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = T.button, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A destructive button that needs two taps. The first tap arms it and changes its label. */
@Composable
fun ConfirmButton(text: String, confirmText: String, modifier: Modifier = Modifier, onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) { delay(3500); armed = false }
    }
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(17.dp))
            .press { if (armed) { armed = false; onConfirm() } else armed = true }
            .background(if (armed) P.bad.copy(alpha = 0.16f) else P.surface2)
            .border(1.dp, if (armed) P.bad.copy(alpha = 0.5f) else P.line, RoundedCornerShape(17.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (armed) confirmText else text, style = T.button, color = P.bad, maxLines = 1)
    }
}

@Composable
fun IconButton(icon: String, modifier: Modifier = Modifier, tint: Color = P.fg, bg: Color = P.surface2, size: Dp = 40.dp, contentDescription: String? = null, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).press(onClick = onClick).background(bg),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, tint, size * 0.5f, contentDescription = contentDescription) }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text, style = T.chip.copy(fontSize = T.chip.fontSize * 0.9f), color = color, maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (filled) color.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, dot: Color? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(99.dp)
    Row(
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .background(if (selected) P.fg else P.surface)
            .border(1.dp, if (selected) P.fg else P.line, shape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = T.chip, color = if (selected) P.bg else P.muted, maxLines = 1)
    }
}

@Composable
fun <K> Segmented(options: List<Pair<K, String>>, selected: K?, modifier: Modifier = Modifier, colorFor: @Composable (K) -> Color? = { null }, onSelect: (K) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(15.dp)).background(P.surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (k, label) ->
            val on = k == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { onSelect(k) }
                    .background(if (on) P.surface else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, P.line, RoundedCornerShape(11.dp)) else Modifier)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = T.chip.copy(fontSize = T.chip.fontSize * 1.04f), color = if (on) (colorFor(k) ?: P.fg) else P.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun KSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = P.onAccent,
            checkedTrackColor = P.accent,
            checkedBorderColor = P.accent,
            uncheckedThumbColor = P.muted,
            uncheckedTrackColor = P.surface2,
            uncheckedBorderColor = P.line,
        ),
    )
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    style: TextStyle = T.body.copy(fontSize = T.body.fontSize * 1.04f),
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        if (label != null) {
            Text(label, style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = P.muted, modifier = Modifier.padding(bottom = 7.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = style.copy(color = P.fg),
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = keyboard,
            keyboardActions = actions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(P.surface2)
                        .border(1.dp, P.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = style, color = P.faint, maxLines = if (singleLine) 1 else 3)
                        inner()
                    }
                    if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
                }
            },
        )
    }
}

/** Horizontal meter with an optional tick for where the value should be by now. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, marker: Float? = null, height: Dp = 8.dp, track: Color = P.surface2) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "meter")
    Box(modifier.fillMaxWidth().height(height + 10.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(99.dp)).background(track)) {
            Box(Modifier.fillMaxWidth(anim).height(height).clip(RoundedCornerShape(99.dp)).background(color))
        }
        if (marker != null) {
            Box(Modifier.fillMaxWidth(marker.coerceIn(0.005f, 1f)).height(height + 10.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(2.5.dp).height(height + 10.dp).clip(RoundedCornerShape(2.dp)).background(P.fg))
            }
        }
    }
}

@Composable
fun SettingRow(
    icon: String,
    title: String,
    subtitle: String? = null,
    tint: Color = P.fg,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(P.surface2), contentAlignment = Alignment.Center) {
            Glyph(icon, tint, 18.dp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = P.fg)
            if (subtitle != null) Text(subtitle, style = T.small, color = P.muted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        } else if (onClick != null) {
            Glyph("chevron", P.faint, 18.dp)
        }
    }
}

@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Card(padding = PaddingValues(vertical = 4.dp), radius = 22.dp, content = content)
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 63.dp).height(1.dp).background(P.line))
}

// ---------- toast with undo ----------

@Stable
class Toaster {
    data class Msg(val text: String, val action: String?, val onAction: (() -> Unit)?, val id: Long)

    var current by mutableStateOf<Msg?>(null)
        private set

    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        current = Msg(text, action, onAction, System.nanoTime())
    }

    fun dismiss() {
        current = null
    }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun BoxScope.ToastHost(toaster: Toaster, bottomPadding: Dp) {
    val msg = toaster.current
    var shown by remember { mutableStateOf<Toaster.Msg?>(null) }
    if (msg != null) shown = msg
    LaunchedEffect(msg?.id) {
        if (msg != null) {
            delay(if (msg.action != null) 4500 else 2800)
            if (toaster.current?.id == msg.id) toaster.dismiss()
        }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding).padding(horizontal = 16.dp),
    ) {
        val m = shown ?: return@AnimatedVisibility
        Row(
            Modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(P.fg)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.text, style = T.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.bg, modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp))
            if (m.action != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    m.action, style = T.chip, color = if (P.dark) LightPalette.accent else DarkPalette.accent,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { m.onAction?.invoke(); toaster.dismiss() }.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else Spacer(Modifier.width(10.dp))
        }
    }
}

// ---------- bottom sheet ----------

/**
 * A bottom sheet drawn by the app itself: dims the screen, slides up, closes with back,
 * a tap on the dimmed area, or a drag down on its handle.
 */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(P.scrim).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
        }
        AnimatedVisibility(
            visible,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) { it },
            exit = slideOutVertically(tween(220)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var drag by remember { mutableFloatStateOf(0f) }
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 24.dp)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .offset { IntOffset(0, drag.roundToInt()) }
                    .clip(shape)
                    .background(P.surface)
                    .border(BorderStroke(1.dp, P.line), shape)
                    .imePadding()
                    .navigationBarsPadding(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) },
                            onDragStopped = { v ->
                                val limit = with(density) { 110.dp.toPx() }
                                if (drag > limit || v > 1800f) {
                                    onDismiss()
                                } else {
                                    scope.launch { animate(drag, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ -> drag = value } }
                                }
                            },
                        )
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(42.dp).height(5.dp).clip(CircleShape).background(P.line))
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                    content = content,
                )
            }
        }
    }
}
