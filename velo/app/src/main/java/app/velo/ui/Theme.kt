package app.velo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF000000)
    val text = Color(0xFFF2F4F7)
    val sub = Color(0xFF9AA0A8)
    val faint = Color(0xFF5E636B)
    val glassTop = Color(0x1FFFFFFF)
    val glassBottom = Color(0x0DFFFFFF)
    val stroke = Color(0x26FFFFFF)
    val danger = Color(0xFFFF6B6B)
}

val LocalAccent = staticCompositionLocalOf { Color(0xFF6EC8FF) }

@Composable
fun VeloTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent, onPrimary = Color.Black, background = C.bg, surface = Color(0xFF111316),
            onSurface = C.text, onBackground = C.text, surfaceVariant = Color(0xFF1A1D21)
        )
    ) {
        // Everything ~10% more compact than Android's default size.
        val d = androidx.compose.ui.platform.LocalDensity.current
        CompositionLocalProvider(
            LocalAccent provides accent,
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * 0.9f, d.fontScale),
            content = content
        )
    }
}

object Type {
    val hero = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal, color = C.text, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal, color = C.text)
    val h = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.text)
    val body = TextStyle(fontSize = 15.sp, color = C.text)
    val sub = TextStyle(fontSize = 13.sp, color = C.sub)
    val label = TextStyle(fontSize = 12.sp, color = C.sub, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
}

/** Frosted-glass card: translucent gradient + hairline border. No real blur, so it stays cheap to draw. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    padding: PaddingValues = PaddingValues(18.dp),
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(radius)
    val fill = if (highlight) Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.08f)))
    else Brush.verticalGradient(listOf(C.glassTop, C.glassBottom))
    val border = Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x0FFFFFFF)))
    var m = modifier.clip(shape).background(fill).border(1.dp, border, shape)
    if (onClick != null) m = Modifier.pressable(0.98f, onClick).then(m)
    Column(m.padding(padding), content = content)
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color = LocalAccent.current, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun ToggleRow(icon: ImageVector?, title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null
        ) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { IconBubble(icon); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body.copy(fontWeight = FontWeight.Medium))
            if (sub != null) Text(sub, style = Type.sub)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black, checkedTrackColor = accent,
                uncheckedThumbColor = C.sub, uncheckedTrackColor = Color(0xFF1C1F23), uncheckedBorderColor = C.stroke
            )
        )
    }
}

@Composable
fun NavRow(icon: ImageVector?, title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable(0.98f, onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { IconBubble(icon); Spacer(Modifier.width(14.dp)) }
        Text(title, style = Type.body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = Type.body.copy(color = C.sub, fontWeight = FontWeight.SemiBold))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.faint)
    }
}

@Composable
fun SubHeader(title: String, sub: String? = null, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp)) {
        Box(
            Modifier.size(44.dp).pressable(0.9f, onBack).clip(CircleShape).background(C.glassTop),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = C.text) }
        Spacer(Modifier.height(22.dp))
        Text(title, style = Type.title)
        if (sub != null) { Spacer(Modifier.height(6.dp)); Text(sub, style = Type.sub) }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(C.glassBottom)
            .border(1.dp, C.stroke, RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val bg by androidx.compose.animation.animateColorAsState(if (on) accent else Color.Transparent,
                androidx.compose.animation.core.tween(220), label = "seg")
            Box(
                Modifier.weight(1f).pressable(0.96f) { onSelect(i) }.clip(RoundedCornerShape(50))
                    .background(bg).padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(o, style = Type.body.copy(
                    color = if (on) Color.Black else C.sub, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
            }
        }
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, filled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier.pressable(0.95f, onClick).clip(RoundedCornerShape(50))
            .background(if (filled) accent else C.glassTop)
            .border(1.dp, if (filled) Color.Transparent else C.stroke, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Color.Black else C.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.body.copy(color = if (filled) Color.Black else C.text, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), style = Type.label, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
}
