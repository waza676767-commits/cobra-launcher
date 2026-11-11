package app.velo.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Velo's icon set: thin, rounded line icons on a 24-unit grid so everything looks like one family. */
object VIcons {
    private fun line(d: String, width: Float = 1.9f): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private fun solid(d: String): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)).build()

    val ArrowLeft by lazy { line("M19 12H5M12 19l-7-7 7-7") }
    val ArrowRight by lazy { line("M5 12h14M12 5l7 7-7 7") }
    val ArrowUp by lazy { line("M12 19V5M5 12l7-7 7 7", 2.2f) }
    val Refresh by lazy { line("M21 4v6h-6M19.5 14.5a8 8 0 1 1-1.9-8.3L21 10") }
    val Close by lazy { line("M18 6L6 18M6 6l12 12") }
    val Plus by lazy { line("M12 5v14M5 12h14") }
    val Search by lazy { line("M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM21 21l-4.3-4.3") }
    val More by lazy {
        solid(
            "M12 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2zM19 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2z" +
                "M5 10.4a1.6 1.6 0 1 0 0 3.2a1.6 1.6 0 1 0 0-3.2z"
        )
    }
    val Lock by lazy { line("M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V7a4 4 0 0 1 8 0v4") }
    val Unlock by lazy { line("M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V7a4 4 0 0 1 7.6-1.7") }
    val Shield by lazy { line("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z") }
    val ShieldCheck by lazy { line("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10zM9 12l2 2 4-4") }
    val Sparkles by lazy {
        line(
            "M10 3l1.8 4.9a2 2 0 0 0 1.2 1.2L18 11l-4.9 1.8a2 2 0 0 0-1.2 1.2L10 19l-1.8-4.9a2 2 0 0 0-1.2-1.2L2 11" +
                "l4.9-1.8a2 2 0 0 0 1.2-1.2L10 3zM19 15l.7 1.8 1.8.7-1.8.7L19 20l-.7-1.8-1.8-.7 1.8-.7L19 15z" +
                "M19 3v3M17.5 4.5h3"
        )
    }
    val Settings by lazy {
        line(
            "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0" +
                "l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09" +
                "A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06" +
                "a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9" +
                "a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9" +
                "a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33" +
                "l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21" +
                "a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z",
            1.7f,
        )
    }
    val History by lazy { line("M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3.5 2") }
    val Clock by lazy { line("M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM12 6v6l4 2") }
    val Pin by lazy {
        line(
            "M12 17v5M9 10.8a2 2 0 0 1-1.1 1.8l-1.8.9A2 2 0 0 0 5 15.2V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.8" +
                "a2 2 0 0 0-1.1-1.8l-1.8-.9a2 2 0 0 1-1.1-1.8V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z"
        )
    }
    val Share by lazy { line("M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7M16 6l-4-4-4 4M12 2v13") }
    val Download by lazy { line("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3") }
    val Home by lazy { line("M3 10l9-7 9 7v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2zM9 21v-8h6v8") }
    val Trash by lazy { line("M3 6h18M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6M9 6V4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2") }
    val Copy by lazy { line("M10 8h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-9a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2zM4 16a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2") }
    val Stop by lazy { solid("M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z") }
    val Check by lazy { line("M20 6L9 17l-5-5", 2.2f) }
    val ChevronRight by lazy { line("M9 18l6-6-6-6") }
    val ChevronDown by lazy { line("M6 9l6 6 6-6") }
    val ChevronUp by lazy { line("M6 15l6-6 6 6") }
    val Globe by lazy { line("M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z") }
    val Moon by lazy { line("M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z") }
    val Sun by lazy { line("M12 7a5 5 0 1 0 0 10a5 5 0 1 0 0-10zM12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4") }
    val Droplet by lazy { line("M12 2.7l5.7 5.7a8 8 0 1 1-11.3 0z") }
    val Image by lazy { line("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM8.5 7a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3zM21 15l-5-5L5 21") }
    val Monitor by lazy { line("M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM8 21h8M12 17v4") }
    val Cpu by lazy { line("M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM9 9h6v6H9zM9 1v3M15 1v3M9 20v3M15 20v3M20 9h3M20 15h3M1 9h3M1 15h3") }
    val EyeOff by lazy { line("M17.9 17.9A10 10 0 0 1 12 20c-7 0-11-8-11-8a18.5 18.5 0 0 1 5.1-5.9M9.9 4.2A9 9 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.2 3.2M14.1 14.1a3 3 0 1 1-4.2-4.2M1 1l22 22") }
    val Link by lazy { line("M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.8 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7") }
    val Edit by lazy { line("M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z") }
    val Ban by lazy { line("M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM4.9 4.9l14.2 14.2") }
    val Grid by lazy { line("M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1zM4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z") }
    val Info by lazy { line("M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zM12 16v-4M12 8h.01") }
    val External by lazy { line("M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6M15 3h6v6M10 14L21 3") }
    val Key by lazy { line("M15.5 7.5a4.5 4.5 0 1 1-4.2 6.1L3 21.9V18h3v-3h3l1.6-1.6a4.5 4.5 0 0 1 4.9-5.9zM16.5 8.5h.01") }
    val Chat by lazy { line("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z") }
    val Layout by lazy { line("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM3 9h18M9 21V9") }
    val Power by lazy { line("M18.4 6.6a9 9 0 1 1-12.8 0M12 2v10", 2.1f) }
    val Type by lazy { line("M4 7V4h16v3M9 20h6M12 4v16") }
    val Tabs by lazy { line("M8 7h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM3 15V5a2 2 0 0 1 2-2h11") }
    val Zap by lazy { line("M13 2L3 14h9l-1 8 10-12h-9l1-8z") }
    val Phone by lazy { line("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM11 18h2") }
    val Moonless by lazy { line("M3 12h18") }
    val Cookie by lazy { line("M12 2a10 10 0 1 0 10 10 4 4 0 0 1-5-5 4 4 0 0 1-5-5zM8.5 8.5h.01M16 15.5h.01M12 12h.01M11 17h.01M7 14h.01") }
    val Wand by lazy { line("M15 4V2M15 16v-2M8 9h2M20 9h2M17.8 11.8L19 13M17.8 6.2L19 5M3 21l9-9M12.2 6.2L11 5") }
    val File by lazy { line("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6") }
}
