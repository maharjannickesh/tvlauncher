package com.nickesh.tvlauncher.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons traced from the Main.dc.html mockup SVGs (24x24 viewport, white stroke; tint at the call site). */
object Icons {
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float): String =
        "M${x + rx},$y H${x + w - rx} A$rx,$rx 0 0 1 ${x + w},${y + rx} V${y + h - rx} " +
            "A$rx,$rx 0 0 1 ${x + w - rx},${y + h} H${x + rx} A$rx,$rx 0 0 1 $x,${y + h - rx} " +
            "V${y + rx} A$rx,$rx 0 0 1 ${x + rx},$y Z"

    private fun vector(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(build).build()

    private fun ImageVector.Builder.stroked(
        d: String,
        width: Float,
        join: StrokeJoin = StrokeJoin.Round,
        fill: Color? = null,
    ) {
        addPath(
            pathData = addPathNodes(d),
            fill = fill?.let { SolidColor(it) },
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = join,
        )
    }

    private const val SUN_RAYS =
        "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"
    private const val PARTLY_RAYS = "M9 3v1.2M3 9h1.2M4.8 4.8l.9.9M13.2 4.8l-.9.9"
    private const val PARTLY_CLOUD = "M9 20h8a3.5 3.5 0 0 0 0-7 4.5 4.5 0 0 0-8.6 1.2A3 3 0 0 0 9 20z"

    val Pin = vector("Pin") {
        stroked("M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", 2f)
        stroked(circle(12f, 9.5f, 2.5f), 2f)
    }
    val Mic = vector("Mic") {
        stroked(rect(9f, 3f, 6f, 11f, 3f), 2f)
        stroked("M5 11a7 7 0 0 0 14 0M12 18v3", 2f)
    }
    val Search = vector("Search") {
        stroked(circle(11f, 11f, 7f), 1.8f, StrokeJoin.Miter)
        stroked("M20 20l-4-4", 1.8f, StrokeJoin.Miter)
    }
    val Inputs = vector("Inputs") {
        stroked(rect(3f, 5f, 18f, 12f, 2f), 1.8f)
        stroked("M8 21h8M12 17v4", 1.8f)
    }
    val Settings = vector("Settings") {
        stroked(circle(12f, 12f, 3f), 1.8f)
        stroked(
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
            1.8f,
        )
    }
    val Profile = vector("Profile") {
        stroked(circle(12f, 8f, 4f), 1.8f, StrokeJoin.Miter)
        stroked("M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6", 1.8f, StrokeJoin.Miter)
    }

    /** Day icon of the world-clock cards (stroke 1.7). */
    val Sun = vector("Sun") {
        stroked(circle(12f, 12f, 4f), 1.7f, StrokeJoin.Miter)
        stroked(SUN_RAYS, 1.7f, StrokeJoin.Miter)
    }
    val Moon = vector("Moon") {
        stroked("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z", 1.7f)
    }

    /** Forecast glyphs (stroke 1.6). */
    val SunForecast = vector("SunForecast") {
        stroked(circle(12f, 12f, 4f), 1.6f, StrokeJoin.Miter)
        stroked(SUN_RAYS, 1.6f, StrokeJoin.Miter)
    }
    val Partly = vector("Partly") {
        stroked(circle(9f, 9f, 3f), 1.6f)
        stroked(PARTLY_RAYS, 1.6f)
        stroked(PARTLY_CLOUD, 1.6f)
    }
    val Cloud = vector("Cloud") {
        stroked("M7 18h10a4 4 0 0 0 0-8 5.5 5.5 0 0 0-10.6 1.5A3.5 3.5 0 0 0 7 18z", 1.6f)
    }
    val Rain = vector("Rain") {
        stroked("M7 14h10a4 4 0 0 0 0-8 5.5 5.5 0 0 0-10.6 1.5A3.5 3.5 0 0 0 7 14z", 1.6f)
        stroked("M8 17l-1 3M12 17l-1 3M16 17l-1 3", 1.6f)
    }

    /** Big weather-card glyph: Partly at stroke 1.3 with the cloud filled white 12 %. Draw with Image (no tint). */
    val PartlyCard = vector("PartlyCard") {
        stroked(circle(9f, 9f, 3f), 1.3f)
        stroked(PARTLY_RAYS, 1.3f)
        stroked(PARTLY_CLOUD, 1.3f, fill = Color.White.copy(alpha = 0.12f))
    }

    val Grid = vector("Grid") {
        stroked(rect(4f, 4f, 6f, 6f, 1.5f), 1.8f, StrokeJoin.Miter)
        stroked(rect(14f, 4f, 6f, 6f, 1.5f), 1.8f, StrokeJoin.Miter)
        stroked(rect(4f, 14f, 6f, 6f, 1.5f), 1.8f, StrokeJoin.Miter)
        stroked(rect(14f, 14f, 6f, 6f, 1.5f), 1.8f, StrokeJoin.Miter)
    }

    // Back / Photo have no mockup SVG on the Home screen; later tasks trace them from their own mockups.
    val Back = vector("Back") { stroked("M15 5l-7 7 7 7", 1.8f) }
    val Photo = vector("Photo") {
        stroked(rect(3f, 5f, 18f, 14f, 2f), 1.8f)
        stroked(circle(9f, 10f, 1.5f), 1.8f)
        stroked("M21 16l-5-5-8 8", 1.8f)
    }
}
