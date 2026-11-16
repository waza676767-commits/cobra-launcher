package app.velo.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Clean tap feedback: the element eases down a touch and dims slightly while pressed, then
 * springs back. No ripple splash. Runs only during the press, so it costs nothing at rest.
 */
fun Modifier.pressable(scaleTo: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "press")
    val a by animateFloatAsState(if (pressed) 0.82f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "pressA")
    this.graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}
