package com.goat.kharcha.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One stroke icon set on a 24-unit grid, so every icon in the app has the same weight. */
object Glyphs {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private val paths: Map<String, String> = mapOf(
        // interface
        "home" to "M4 10.5 12 4l8 6.5V20h-5v-6H9v6H4z",
        "list" to "M4 6h16M4 12h16M4 18h10",
        "chart" to "M5 20V11M12 20V4M19 20v-6",
        "sliders" to "M4 7h9M17 7h3M15 5v4M4 17h3M11 17h9M9 15v4",
        "plus" to "M12 5v14M5 12h14",
        "search" to circle(10.5f, 10.5f, 6f) + "M15 15l5 5",
        "close" to "M6 6l12 12M18 6 6 18",
        "check" to "M5 12.5l4.5 4.5L19 7.5",
        "back" to "M15 5l-7 7 7 7",
        "chevron" to "M9 5l7 7-7 7",
        "up" to "M6 15l6-6 6 6",
        "down" to "M6 9l6 6 6-6",
        "bell" to "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5h4",
        "lock" to "M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3",
        "export" to "M12 4v11M7 10l5 5 5-5M5 20h14",
        "trash" to "M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13",
        "edit" to "M5 19h4L19 9l-4-4L5 15zM13 7l4 4",
        "sparkle" to "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM18.5 15.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z",
        "clock" to circle(12f, 12f, 8.5f) + "M12 7.5V12l3 2",
        "calendar" to "M4.5 6.5h15v13h-15zM4.5 10.5h15M8.5 4v4M15.5 4v4",
        "repeat" to "M4 11V9a3 3 0 0 1 3-3h12M16 3l3 3-3 3M20 13v2a3 3 0 0 1-3 3H5M8 21l-3-3 3-3",
        "in" to "M17 7 7 17M7 9v8h8",
        "out" to "M7 17 17 7M9 7h8v8",
        "link" to "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
        "flask" to "M9 3.5h6M10 3.5v5.5l-5 8.7A2 2 0 0 0 6.7 21h10.6a2 2 0 0 0 1.7-3.3L14 9V3.5M7.3 15h9.4",
        "wallet" to "M4 7.5A2.5 2.5 0 0 1 6.5 5H18v4M4 7.5V18a2 2 0 0 0 2 2h14V9H6.5A2.5 2.5 0 0 1 4 7.5zM16 14.5h.5",
        "shield" to "M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z",
        "moon" to "M19.5 14.5A7.5 7.5 0 1 1 9.5 4.5a6 6 0 0 0 10 10z",
        "user" to circle(12f, 8f, 3.5f) + "M5 20a7 7 0 0 1 14 0",
        "tag" to "M3.5 12.5V4h8.5l8.5 8.5-8.5 8.5zM8 8h.5",
        "info" to circle(12f, 12f, 8.5f) + "M12 11v5M12 8h.01",
        "apps" to "M4.5 4.5h6v6h-6zM13.5 4.5h6v6h-6zM4.5 13.5h6v6h-6zM13.5 13.5h6v6h-6z",
        "skip" to "M5 6l7 6-7 6zM15 6v12",
        "bulb" to "M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z",
        "hourglass" to "M7 3.5h10M7 20.5h10M8 3.5c0 4 8 5 8 8.5s-8 4.5-8 8.5M16 3.5c0 4-8 5-8 8.5s8 4.5 8 8.5",
        // categories
        "food" to "M7 3v7a2 2 0 0 0 4 0V3M9 3v18M17 21V3c-2.2 1-3.5 4-3.5 8H17",
        "coffee" to "M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM16 10h1.5a2.5 2.5 0 0 1 0 5H16M8.5 3.5V6M12 3.5V6",
        "cart" to "M3 4h2.5l2 11h10.5l2-8H6.5" + circle(9f, 19.5f, 1.4f) + circle(17f, 19.5f, 1.4f),
        "car" to "M4 16l1.8-6h12.4L20 16v3H4zM7 19v2M17 19v2M4 16h16M7.5 13h.5M16 13h.5",
        "fuel" to "M5 20V5a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v15M4 20h11M5 10h9M14 8l3 2v7a1.5 1.5 0 0 0 3 0V9l-3-3",
        "receipt" to "M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3",
        "bolt" to "M13 3 5 13.5h6L10 21l8-10.5h-6z",
        "phone" to "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2",
        "bag" to "M5 8h14l-1 13H6zM9 8V6.5a3 3 0 0 1 6 0V8",
        "health" to circle(12f, 12f, 9f) + "M12 8v8M8 12h8",
        "heart" to "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z",
        "ticket" to "M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4zM14.5 7.5v2M14.5 11v2M14.5 14.5v2",
        "box" to "M12 3l8 4.5v9L12 21l-8-4.5v-9zM12 12l8-4.5M12 12v9M12 12L4 7.5",
        "briefcase" to "M4 8h16v11H4zM9 8V5.5h6V8M4 13h16",
        "banknote" to "M3 7h18v10H3z" + circle(12f, 12f, 2.5f) + "M6 10v4M18 10v4",
        "arrows" to "M4 8h13M14 5l3 3-3 3M20 16H7M10 13l-3 3 3 3",
        "undo" to "M9 14 4 9l5-5M4 9h10a6 6 0 0 1 0 12h-3",
        "book" to "M4 5.5A1.5 1.5 0 0 1 5.5 4H11v16H5.5A1.5 1.5 0 0 1 4 18.5zM20 5.5A1.5 1.5 0 0 0 18.5 4H13v16h5.5a1.5 1.5 0 0 0 1.5-1.5z",
        "gift" to "M4 9h16v4H4zM5.5 13h13v7h-13zM12 9v11M12 9c-1.5-3-5-3.5-5-1.5S10 9 12 9c2 0 5 .5 5-1.5S13.5 6 12 9",
        "travel" to "M3 12.5 21 4l-5 16.5-4.5-6zM11.5 14.5 21 4",
        "dumbbell" to "M6.5 8v8M3.5 10v4M17.5 8v8M20.5 10v4M6.5 12h11",
        "star" to "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-.8z",
        "dots" to circle(6f, 12f, 1.2f) + circle(12f, 12f, 1.2f) + circle(18f, 12f, 1.2f),
    )

    /** Icons offered when making a category. */
    val categoryIcons = listOf(
        "food", "coffee", "cart", "car", "fuel", "receipt", "bolt", "phone", "bag", "health", "heart", "ticket",
        "home", "box", "briefcase", "banknote", "arrows", "undo", "book", "gift", "travel", "dumbbell", "star", "dots",
    )

    private val cache = HashMap<String, ImageVector>()

    fun get(name: String): ImageVector = synchronized(cache) {
        cache.getOrPut(name) {
            val d = paths[name] ?: paths.getValue("dots")
            ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                .addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
                .build()
        }
    }
}

@Composable
fun Glyph(name: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(imageVector = Glyphs.get(name), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}
