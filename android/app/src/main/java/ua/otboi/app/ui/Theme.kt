package ua.otboi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ua.otboi.app.R
import ua.otboi.app.data.Store

/** Палитра: чистый чёрный + фиолетовый. */
object C {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF0E0B14)
    val Surface2 = Color(0xFF140D22)
    val Sheet = Color(0xFF0B0812)
    val Stroke = Color(0xFF2E1F4D)
    val StrokeDim = Color(0xFF1E1830)
    val StrokeOff = Color(0xFF16121F)
    val Chip = Color(0xFF3B2A5C)
    val Text = Color(0xFFF3EEFF)
    val Text2 = Color(0xFFB3A8CC)
    val Text3 = Color(0xFF8E84A8)
    val Muted = Color(0xFF5E5675)
    val Track = Color(0xFF1E1830)
    val PurpleDeep = Color(0xFF2A1650)
    val PurpleLight = Color(0xFFC9A0FF)
    val PurplePale = Color(0xFFE2CCFF)
    val Mint = Color(0xFF5EEAD4)
    val MintText = Color(0xFF9EF0D6)
    val MintBg = Color(0xFF0F1F1B)
    val Red = Color(0xFFFF4D6D)
    val RedText = Color(0xFFFF9EB0)
    val RedBg = Color(0xFF2A0D16)
    val Amber = Color(0xFFFFB547)
    val AmberText = Color(0xFFFFD08A)
    val AmberBg = Color(0xFF261A07)
    val Grey = Color(0xFF16121F)
    val Nav = Color(0xFF07050C)
    val NavStroke = Color(0xFF1A1426)
}

val Onest = FontFamily(
    Font(R.font.onest_regular, FontWeight.Normal),
    Font(R.font.onest_medium, FontWeight.Medium),
    Font(R.font.onest_semibold, FontWeight.SemiBold),
    Font(R.font.onest_bold, FontWeight.Bold),
)

/** Стиль «красивых цифр» — выбирается в «Ещё». */
enum class DigitStyle(
    val id: String,
    val title: String,
    val hero: FontFamily,
    val heroWeight: FontWeight,
    val heroSize: Float,
    val heroSpacing: Float,
    val small: FontFamily,
    val smallWeight: FontWeight,
    val smallScale: Float,
) {
    ELEGANT(
        "elegant", "Элегантные",
        FontFamily(Font(R.font.bodoni_text, FontWeight.SemiBold)), FontWeight.SemiBold, 108f, -1f,
        FontFamily(Font(R.font.bodoni_text, FontWeight.SemiBold)), FontWeight.SemiBold, 1f,
    ),
    THIN(
        "thin", "Тонкие",
        FontFamily(Font(R.font.outfit_thin, FontWeight.Thin)), FontWeight.Thin, 116f, -4f,
        FontFamily(Font(R.font.outfit_light, FontWeight.Light)), FontWeight.Light, 1f,
    ),
    BOLD(
        "bold", "Плотные",
        FontFamily(Font(R.font.bebas_neue, FontWeight.Normal)), FontWeight.Normal, 136f, 2f,
        FontFamily(Font(R.font.bebas_neue, FontWeight.Normal)), FontWeight.Normal, 1.2f,
    );

    companion object {
        fun from(id: String): DigitStyle = entries.firstOrNull { it.id == id } ?: ELEGANT
    }
}

val LocalAccent = staticCompositionLocalOf { Color(0xFFA855F7) }
val LocalDigits = staticCompositionLocalOf { DigitStyle.ELEGANT }

val AccentOptions = listOf(0xFFA855F7, 0xFF8B5CF6, 0xFFC084FC, 0xFF7C3AED)

@Composable
fun OtboiTheme(content: @Composable () -> Unit) {
    val s by Store.settings.collectAsState()
    val accent = Color(s.accent)
    val digits = DigitStyle.from(s.digitStyle)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = accent,
        background = C.Bg,
        onBackground = C.Text,
        surface = C.Sheet,
        onSurface = C.Text,
        surfaceVariant = C.Surface,
        onSurfaceVariant = C.Text2,
        outline = C.Stroke,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalAccent provides accent, LocalDigits provides digits) {
            content()
        }
    }
}
