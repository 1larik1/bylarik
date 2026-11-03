package ua.otboi.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Линейные иконки 24×24 (как в макете). */
object Ic {
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

    private fun icon(name: String, vararg paths: String, width: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val pin by lazy { icon("pin", "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", circle(12f, 9.5f, 2.5f)) }
    val clock by lazy { icon("clock", circle(12f, 13f, 8f), "M12 9v4l2 2", "M5 4L2.5 6.5", "M19 4l2.5 2.5") }
    val rss by lazy { icon("rss", "M4 11a9 9 0 0 1 9 9", "M4 4a16 16 0 0 1 16 16", circle(5f, 19f, 1.5f)) }
    val map by lazy { icon("map", "M9 4L3 6v14l6-2 6 2 6-2V4l-6 2-6-2z", "M9 4v14", "M15 6v14") }
    val dots by lazy { icon("dots", circle(5f, 12f, 1.5f), circle(12f, 12f, 1.5f), circle(19f, 12f, 1.5f)) }
    val plus by lazy { icon("plus", "M12 5v14", "M5 12h14", width = 2.4f) }
    val hourglass by lazy {
        icon("hourglass", "M7 3h10", "M7 21h10", "M8 3c0 5 8 5 8 9s-8 4-8 9", "M16 3c0 5-8 5-8 9s8 4 8 9")
    }
    val check by lazy { icon("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.4f) }
    val refresh by lazy { icon("refresh", "M20 12a8 8 0 1 1-2.34-5.66", "M20 4v5h-5") }
    val trash by lazy { icon("trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13") }
    val chevron by lazy { icon("chevron", "M9 6l6 6-6 6") }
    val back by lazy { icon("back", "M15 6l-6 6 6 6") }
    val search by lazy { icon("search", circle(11f, 11f, 7f), "M20 20l-4-4") }
    val bell by lazy { icon("bell", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4l2-2z", "M10 20a2 2 0 0 0 4 0") }
    val warn by lazy { icon("warn", "M12 3l10 18H2L12 3z", "M12 10v5", "M12 18v0.01") }
}
