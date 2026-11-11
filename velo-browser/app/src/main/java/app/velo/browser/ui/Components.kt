@file:OptIn(ExperimentalFoundationApi::class)

package app.velo.browser.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The spring used for anything that slides or grows into place. Quick, with a hint of settle. */
val VeloSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

// ── Text and icons ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Velo.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, textAlign = align,
            lineHeight = lineHeight, letterSpacing = letterSpacing,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun VIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Velo.colors.text, size: Dp = 22.dp) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

// ── Touch feedback ─────────────────────────────────────────────────────────────────────────────

/** Tappable with a soft squeeze instead of a ripple. */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** What a screen reader says for an icon-only button showing [icon]. */
private fun spokenLabel(icon: ImageVector): String = when (icon) {
    VIcons.ArrowLeft -> "Back"
    VIcons.ArrowRight -> "Forward"
    VIcons.ArrowUp -> "Use this"
    VIcons.Refresh -> "Reload"
    VIcons.Close -> "Close"
    VIcons.More -> "Menu"
    VIcons.Sparkles -> "Assistant"
    VIcons.Home -> "Start page"
    VIcons.Share -> "Share"
    VIcons.Edit -> "Edit"
    VIcons.Copy -> "Copy"
    VIcons.ChevronUp -> "Previous"
    VIcons.ChevronDown -> "Next"
    else -> "Button"
}

@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Velo.colors.text,
    background: Color = Color.Transparent,
    size: Dp = 42.dp,
    iconSize: Dp = 21.dp,
    enabled: Boolean = true,
    label: String = spokenLabel(icon),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .bounceClick(enabled = enabled, pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VIcon(icon, tint = if (enabled) tint else tint.copy(alpha = 0.3f), size = iconSize)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Velo.colors.accent,
    onColor: Color = Velo.colors.onAccent,
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(Velo.shapes.button)
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .bounceClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            VIcon(icon, tint = onColor, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Txt(text, size = 15.sp, weight = FontWeight.SemiBold, color = onColor, maxLines = 1)
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Velo.colors.text,
    background: Color = Velo.colors.surfaceHi,
    height: Dp = 50.dp,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(Velo.shapes.button)
            .background(background)
            .bounceClick(onClick = onClick)
            .padding(horizontal = if (height < 44.dp) 14.dp else 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            VIcon(icon, tint = color, size = if (height < 44.dp) 15.dp else 18.dp)
            Spacer(Modifier.width(7.dp))
        }
        Txt(text, size = if (height < 44.dp) 13.sp else 15.sp, weight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

// ── Inputs ─────────────────────────────────────────────────────────────────────────────────────

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Velo.colors
    val x by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 520f), label = "toggle")
    val track by animateColorAsState(if (checked) c.accent else c.surfaceHi, tween(180), label = "track")
    val thumb by animateColorAsState(if (checked) c.onAccent else c.textFaint, tween(180), label = "thumb")
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(track)
            .bounceClick(enabled = enabled, pressedScale = 0.92f) { onChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .offset { IntOffset((x * 20.dp.toPx()).roundToInt(), 0) }
                .size(22.dp)
                .clip(CircleShape)
                .background(thumb)
        )
    }
}

/** A pill of mutually exclusive options whose highlight glides to the chosen one. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Velo.colors
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val pad = 4.dp
    val slot = if (options.isEmpty()) 0.dp else with(density) { ((width - pad.toPx() * 2) / options.size).toDp() }
    val x by animateDpAsState(slot * selected, spring(dampingRatio = 0.8f, stiffness = 480f), label = "segment")
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(Velo.shapes.button)
            .background(c.surfaceHi)
            .onSizeChanged { width = it.width }
            .padding(pad)
    ) {
        if (width > 0) {
            Box(
                Modifier
                    .offset(x = x)
                    .width(slot)
                    .fillMaxHeight()
                    .shadow(2.dp, Velo.shapes.button)
                    .clip(Velo.shapes.button)
                    .background(c.bg)
            )
        }
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bounceClick(pressedScale = 0.96f) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(
                        label,
                        size = 14.sp,
                        weight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (i == selected) c.text else c.textDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A slider drawn to match the rest of Velo. [value] is 0..1. */
@Composable
fun VeloSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onFinished: (() -> Unit)? = null,
) {
    val c = Velo.colors
    val current by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val shown by animateFloatAsState(value.coerceIn(0f, 1f), spring(stiffness = 900f), label = "slider")
    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(steps) {
                fun update(x: Float) {
                    val inset = 11.dp.toPx()
                    var v = ((x - inset) / (size.width - inset * 2)).coerceIn(0f, 1f)
                    if (steps > 0) v = (v * steps).roundToInt() / steps.toFloat()
                    current(v)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position.x)
                    drag(down.id) { change ->
                        update(change.position.x)
                        change.consume()
                    }
                    finished?.invoke()
                }
            }
            .drawBehind {
                val inset = 11.dp.toPx()
                val trackH = 6.dp.toPx()
                val y = size.height / 2
                val w = size.width - inset * 2
                val radius = CornerRadius(trackH / 2)
                drawRoundRect(c.surfaceHi, Offset(inset, y - trackH / 2), Size(w, trackH), radius)
                drawRoundRect(c.accent, Offset(inset, y - trackH / 2), Size(w * shown, trackH), radius)
                if (steps in 1..12) {
                    for (i in 0..steps) {
                        val x = inset + w * i / steps
                        drawCircle(if (i / steps.toFloat() <= shown) c.onAccent.copy(alpha = 0.5f) else c.textFaint.copy(alpha = 0.5f), 1.4.dp.toPx(), Offset(x, y))
                    }
                }
                drawCircle(Color.Black.copy(alpha = 0.18f), 11.dp.toPx(), Offset(inset + w * shown, y + 1.dp.toPx()))
                drawCircle(c.accent, 10.dp.toPx(), Offset(inset + w * shown, y))
                drawCircle(c.onAccent, 4.dp.toPx(), Offset(inset + w * shown, y))
            }
    )
}

@Composable
fun VeloField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    background: Color = Velo.colors.surfaceHi,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Velo.colors
    Row(
        modifier
            .heightIn(min = 50.dp)
            .clip(Velo.shapes.field)
            .background(background)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            VIcon(leading, tint = c.textDim, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (value.isEmpty()) Txt(placeholder, color = c.textFaint, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 6,
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction,
                    autoCorrectEnabled = keyboardType == KeyboardType.Text && !password,
                ),
                keyboardActions = KeyboardActions(onAny = { onDone?.invoke() }),
            )
        }
        trailing?.invoke()
    }
}

// ── Rows and cards ─────────────────────────────────────────────────────────────────────────────

@Composable
fun Card(modifier: Modifier = Modifier, color: Color = Velo.colors.surface, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Velo.shapes.card)
            .background(color),
        content = content,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(Velo.colors.outline.copy(alpha = 0.7f)))
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = Velo.colors.text,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Velo.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bounceClick(pressedScale = 0.985f, onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            VIcon(icon, tint = if (titleColor == c.text) c.textDim else titleColor, size = 20.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(title, size = 15.sp, weight = FontWeight.Medium, color = titleColor)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(3.dp))
                Txt(subtitle, size = 13.sp, color = c.textDim, lineHeight = 18.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingRow(title, subtitle = subtitle, icon = icon, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        Toggle(checked, onChange, enabled = enabled)
    }
}

/** A site's icon, or a coloured initial when it has none yet. */
@Composable
fun SiteIcon(icon: Bitmap?, label: String, modifier: Modifier = Modifier, size: Dp = 28.dp, shape: Shape = CircleShape) {
    if (icon != null) {
        val image = remember(icon) { icon.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val letter = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
        val hue = remember(label) { (abs(label.hashCode()) % 360).toFloat() }
        val dark = Velo.colors.dark
        Box(
            modifier.size(size).clip(shape).background(
                Color.hsl(hue, if (dark) 0.35f else 0.55f, if (dark) 0.3f else 0.9f)
            ),
            contentAlignment = Alignment.Center,
        ) {
            Txt(
                letter,
                size = (size.value * 0.46f).sp,
                weight = FontWeight.Bold,
                color = Color.hsl(hue, 0.6f, if (dark) 0.85f else 0.32f),
            )
        }
    }
}

// ── Sheets and dialogs ─────────────────────────────────────────────────────────────────────────

@Composable
fun Scrim(visible: Boolean, onTap: (() -> Unit)?) {
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Velo.colors.scrim)
                .pointerInput(onTap) { detectTapGestures { onTap?.invoke() } }
        )
    }
}

/**
 * A panel that springs up from the bottom edge. Drag the handle down, tap outside or press Back
 * to close it. With [tall] it takes most of the screen and its content can fill the height.
 */
@Composable
fun VeloSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tall: Boolean = false,
    dismissOnOutsideTap: Boolean = true,
    avoidKeyboard: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Velo.colors
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    Scrim(visible, if (dismissOnOutsideTap) onDismiss else null)
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(120)),
        exit = slideOutVertically(tween(230, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(230)),
    ) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .then(if (tall) Modifier.fillMaxHeight(0.94f) else Modifier.heightIn(max = 700.dp))
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .shadow(24.dp, Velo.shapes.sheet)
                    .clip(Velo.shapes.sheet)
                    .background(c.bg)
                    // Swallow taps so they don't fall through to the scrim.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(
                        if (avoidKeyboard) WindowInsets.navigationBars.union(WindowInsets.ime) else WindowInsets.navigationBars
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                            },
                            onDragStopped = { velocity ->
                                if (drag.value > 140f || velocity > 1400f) onDismiss() else drag.animateTo(0f, VeloSpring)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 38.dp, height = 4.dp).clip(CircleShape).background(c.outline))
                }
                content()
            }
        }
    }
    if (visible) BackHandler(onBack = onDismiss)
}

/** A centred card for questions that need an answer before anything else happens. */
@Composable
fun VeloDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    dismissOnOutsideTap: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scrim(visible, if (dismissOnOutsideTap) onDismiss else null)
    AnimatedVisibility(
        visible,
        enter = scaleIn(spring(dampingRatio = 0.78f, stiffness = 520f), initialScale = 0.9f) + fadeIn(tween(140)),
        exit = scaleOut(tween(140), targetScale = 0.94f) + fadeOut(tween(140)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars).union(WindowInsets.ime))
                .padding(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .shadow(28.dp, Velo.shapes.dialog)
                    .clip(Velo.shapes.dialog)
                    .background(Velo.colors.bg)
                    .border(1.dp, Velo.colors.outline.copy(alpha = 0.6f), Velo.shapes.dialog)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(22.dp),
                content = content,
            )
        }
    }
    if (visible) BackHandler(onBack = onDismiss)
}

@Composable
fun DialogButtons(
    confirm: String,
    onConfirm: () -> Unit,
    cancel: String = "Cancel",
    onCancel: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SoftButton(cancel, onCancel, Modifier.weight(1f))
        PrimaryButton(
            confirm, onConfirm, Modifier.weight(1f), enabled = confirmEnabled,
            color = if (destructive) Velo.colors.danger else Velo.colors.accent,
            onColor = if (destructive) Color.White else Velo.colors.onAccent,
        )
    }
}

/** Header used by full-screen pages such as Settings and History. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(VIcons.ArrowLeft, onBack)
        Spacer(Modifier.width(4.dp))
        Txt(title, Modifier.weight(1f), size = 20.sp, weight = FontWeight.SemiBold, maxLines = 1)
        actions()
    }
}

/**
 * Returns [value], or the last non-null value it had. Lets a closing sheet keep showing its
 * content while it animates away after the state behind it has been cleared.
 */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return holder[0] as T?
}
