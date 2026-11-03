package ua.otboi.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ua.otboi.app.R
import ua.otboi.app.data.Store

/** Набор цветов одной темы. */
data class Palette(
    val id: String,
    val light: Boolean,
    val bg: Long, val surface: Long, val surface2: Long, val sheet: Long,
    val stroke: Long, val strokeDim: Long, val strokeOff: Long, val chip: Long,
    val text: Long, val text2: Long, val text3: Long, val muted: Long, val track: Long,
    val accent: Long, val accentDeep: Long, val accentLight: Long, val accentPale: Long, val onAccent: Long,
    val hero: Long, val ghost: Long, val dim: Long,
    val mint: Long, val mintText: Long, val mintBg: Long,
    val red: Long, val redText: Long, val redBg: Long, val redBorder: Long,
    val amber: Long, val amberText: Long, val amberBg: Long,
    val grey: Long, val nav: Long, val navStroke: Long,
)

object Palettes {
    val night = Palette(
        id = "night", light = false,
        bg = 0xFF000000, surface = 0xFF0E0B14, surface2 = 0xFF140D22, sheet = 0xFF0B0812,
        stroke = 0xFF2E1F4D, strokeDim = 0xFF1E1830, strokeOff = 0xFF16121F, chip = 0xFF3B2A5C,
        text = 0xFFF3EEFF, text2 = 0xFFB3A8CC, text3 = 0xFF8E84A8, muted = 0xFF5E5675, track = 0xFF1E1830,
        accent = 0xFFA855F7, accentDeep = 0xFF2A1650, accentLight = 0xFFC9A0FF, accentPale = 0xFFE2CCFF, onAccent = 0xFFFFFFFF,
        hero = 0xFFFFFFFF, ghost = 0xFF2A2438, dim = 0xFF4A4260,
        mint = 0xFF5EEAD4, mintText = 0xFF9EF0D6, mintBg = 0xFF0F1F1B,
        red = 0xFFFF4D6D, redText = 0xFFFF9EB0, redBg = 0xFF2A0D16, redBorder = 0xFF4A1726,
        amber = 0xFFFFB547, amberText = 0xFFFFD08A, amberBg = 0xFF261A07,
        grey = 0xFF16121F, nav = 0xFF07050C, navStroke = 0xFF1A1426,
    )
    val graphite = Palette(
        id = "graphite", light = false,
        bg = 0xFF111214, surface = 0xFF1A1B1E, surface2 = 0xFF212226, sheet = 0xFF16171A,
        stroke = 0xFF33353B, strokeDim = 0xFF26282D, strokeOff = 0xFF1F2024, chip = 0xFF3D4047,
        text = 0xFFEDEEF0, text2 = 0xFFB4B7BE, text3 = 0xFF8B8F98, muted = 0xFF5D616B, track = 0xFF2A2C31,
        accent = 0xFFFF8A4C, accentDeep = 0xFF3A2418, accentLight = 0xFFFFAD80, accentPale = 0xFFFFD2BA, onAccent = 0xFF1C0F07,
        hero = 0xFFFFFFFF, ghost = 0xFF2C2E33, dim = 0xFF52555D,
        mint = 0xFF5EEAD4, mintText = 0xFF9EF0D6, mintBg = 0xFF10201C,
        red = 0xFFFF5470, redText = 0xFFFFA2B3, redBg = 0xFF2B1318, redBorder = 0xFF4A1E27,
        amber = 0xFFFFC14D, amberText = 0xFFFFD88F, amberBg = 0xFF2A1F0B,
        grey = 0xFF1F2024, nav = 0xFF0C0D0F, navStroke = 0xFF232529,
    )
    val ocean = Palette(
        id = "ocean", light = false,
        bg = 0xFF03101A, surface = 0xFF0A1A26, surface2 = 0xFF0E2232, sheet = 0xFF071520,
        stroke = 0xFF1C3B53, strokeDim = 0xFF132B3D, strokeOff = 0xFF0F2232, chip = 0xFF24475F,
        text = 0xFFE8F4FB, text2 = 0xFFA6C3D4, text3 = 0xFF7C9AAD, muted = 0xFF4C687A, track = 0xFF132B3D,
        accent = 0xFF22B8CF, accentDeep = 0xFF0E3442, accentLight = 0xFF7DD8E8, accentPale = 0xFFBDEEF6, onAccent = 0xFF032229,
        hero = 0xFFFFFFFF, ghost = 0xFF15304A, dim = 0xFF3E5B70,
        mint = 0xFF4ADE80, mintText = 0xFFA7F3C0, mintBg = 0xFF0B2418,
        red = 0xFFFF5C7A, redText = 0xFFFFA3B5, redBg = 0xFF2A0F1C, redBorder = 0xFF4A1A2C,
        amber = 0xFFFFB547, amberText = 0xFFFFD08A, amberBg = 0xFF2A1D0A,
        grey = 0xFF0F2232, nav = 0xFF020B12, navStroke = 0xFF10263A,
    )
    val light = Palette(
        id = "light", light = true,
        bg = 0xFFF6F4FA, surface = 0xFFFFFFFF, surface2 = 0xFFF3EEFC, sheet = 0xFFFFFFFF,
        stroke = 0xFFE1D8F0, strokeDim = 0xFFECE6F5, strokeOff = 0xFFEFEBF5, chip = 0xFFD6C9EC,
        text = 0xFF17121F, text2 = 0xFF51486A, text3 = 0xFF7B7292, muted = 0xFFB3ABC4, track = 0xFFE6E0EF,
        accent = 0xFF8B3DF0, accentDeep = 0xFFEBDDFD, accentLight = 0xFF7C3AED, accentPale = 0xFF5B21B6, onAccent = 0xFFFFFFFF,
        hero = 0xFF17121F, ghost = 0xFFE2DCEE, dim = 0xFFC6BED6,
        mint = 0xFF0F9F86, mintText = 0xFF0A7563, mintBg = 0xFFDDF4EE,
        red = 0xFFE11D48, redText = 0xFFB8123A, redBg = 0xFFFDE6EC, redBorder = 0xFFF6C2CF,
        amber = 0xFFD97706, amberText = 0xFF925404, amberBg = 0xFFFEF1D6,
        grey = 0xFFECE8F2, nav = 0xFFFFFFFF, navStroke = 0xFFECE6F5,
    )
    val all = listOf(night, graphite, ocean, light)
    fun byId(id: String): Palette = all.firstOrNull { it.id == id } ?: night
}

/**
 * Текущая тема. Это состояние Compose: все, кто читает цвета из C, перерисуются при смене темы.
 * При запуске выставляется из настроек в OtboiApp, чтобы не было мигания.
 */
object Th {
    var p: Palette by mutableStateOf(Palettes.night)
}

/** Цвета текущей темы. */
object C {
    val Bg get() = Color(Th.p.bg)
    val Surface get() = Color(Th.p.surface)
    val Surface2 get() = Color(Th.p.surface2)
    val Sheet get() = Color(Th.p.sheet)
    val Stroke get() = Color(Th.p.stroke)
    val StrokeDim get() = Color(Th.p.strokeDim)
    val StrokeOff get() = Color(Th.p.strokeOff)
    val Chip get() = Color(Th.p.chip)
    val Text get() = Color(Th.p.text)
    val Text2 get() = Color(Th.p.text2)
    val Text3 get() = Color(Th.p.text3)
    val Muted get() = Color(Th.p.muted)
    val Track get() = Color(Th.p.track)
    val Accent get() = Color(Th.p.accent)
    val OnAccent get() = Color(Th.p.onAccent)
    val PurpleDeep get() = Color(Th.p.accentDeep)
    val PurpleLight get() = Color(Th.p.accentLight)
    val PurplePale get() = Color(Th.p.accentPale)
    val Hero get() = Color(Th.p.hero)
    val Ghost get() = Color(Th.p.ghost)
    val Dim get() = Color(Th.p.dim)
    val Mint get() = Color(Th.p.mint)
    val MintText get() = Color(Th.p.mintText)
    val MintBg get() = Color(Th.p.mintBg)
    val Red get() = Color(Th.p.red)
    val RedText get() = Color(Th.p.redText)
    val RedBg get() = Color(Th.p.redBg)
    val RedBorder get() = Color(Th.p.redBorder)
    val Amber get() = Color(Th.p.amber)
    val AmberText get() = Color(Th.p.amberText)
    val AmberBg get() = Color(Th.p.amberBg)
    val Grey get() = Color(Th.p.grey)
    val Nav get() = Color(Th.p.nav)
    val NavStroke get() = Color(Th.p.navStroke)
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
    val hero: FontFamily,
    val heroWeight: FontWeight,
    val heroSize: Float,
    val heroSpacing: Float,
    val small: FontFamily,
    val smallWeight: FontWeight,
    val smallScale: Float,
) {
    ELEGANT(
        "elegant",
        FontFamily(Font(R.font.bodoni_text, FontWeight.SemiBold)), FontWeight.SemiBold, 108f, -1f,
        FontFamily(Font(R.font.bodoni_text, FontWeight.SemiBold)), FontWeight.SemiBold, 1f,
    ),
    THIN(
        "thin",
        FontFamily(Font(R.font.outfit_thin, FontWeight.Thin)), FontWeight.Thin, 116f, -4f,
        FontFamily(Font(R.font.outfit_light, FontWeight.Light)), FontWeight.Light, 1f,
    ),
    BOLD(
        "bold",
        FontFamily(Font(R.font.bebas_neue, FontWeight.Normal)), FontWeight.Normal, 136f, 2f,
        FontFamily(Font(R.font.bebas_neue, FontWeight.Normal)), FontWeight.Normal, 1.2f,
    );

    companion object {
        fun from(id: String): DigitStyle = entries.firstOrNull { it.id == id } ?: ELEGANT
    }
}

val LocalAccent = staticCompositionLocalOf { Color(0xFFA855F7) }
val LocalDigits = staticCompositionLocalOf { DigitStyle.ELEGANT }

@Composable
fun OtboiTheme(content: @Composable () -> Unit) {
    val s by Store.settings.collectAsState()
    val p = Palettes.byId(s.themeId)
    LaunchedEffect(p.id) { if (Th.p.id != p.id) Th.p = p }
    val digits = DigitStyle.from(s.digitStyle)
    val accent = Color(p.accent)
    val scheme = if (p.light) {
        lightColorScheme(
            primary = accent, onPrimary = Color(p.onAccent), secondary = accent,
            background = Color(p.bg), onBackground = Color(p.text),
            surface = Color(p.sheet), onSurface = Color(p.text),
            surfaceVariant = Color(p.surface), onSurfaceVariant = Color(p.text2), outline = Color(p.stroke),
        )
    } else {
        darkColorScheme(
            primary = accent, onPrimary = Color(p.onAccent), secondary = accent,
            background = Color(p.bg), onBackground = Color(p.text),
            surface = Color(p.sheet), onSurface = Color(p.text),
            surfaceVariant = Color(p.surface), onSurfaceVariant = Color(p.text2), outline = Color(p.stroke),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalAccent provides accent, LocalDigits provides digits) {
            content()
        }
    }
}
