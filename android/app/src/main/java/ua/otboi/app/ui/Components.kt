package ua.otboi.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import ua.otboi.app.data.AppSettings
import ua.otboi.app.data.Fmt
import ua.otboi.app.data.Live
import ua.otboi.app.data.Reading
import ua.otboi.app.data.Status

/** Размер в dp → sp без учёта системного масштаба шрифта (для больших цифр). */
@Composable
fun Float.fixedSp(): TextUnit = with(LocalDensity.current) { this@fixedSp.dp.toSp() }

@Composable
fun Txt(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = C.Text,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = Onest,
        textAlign = align,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Время большими красивыми цифрами; двоеточие — своим цветом. */
@Composable
fun ClockText(
    time: String,
    sizeDp: Float,
    family: FontFamily,
    weight: FontWeight,
    color: Color,
    colon: Color,
    modifier: Modifier = Modifier,
    spacing: Float = 0f,
    struck: Color? = null,
) {
    val parts = time.split(":")
    val text: AnnotatedString = if (parts.size == 2) {
        buildAnnotatedString {
            append(parts[0])
            withStyle(SpanStyle(color = colon)) { append(":") }
            append(parts[1])
        }
    } else {
        AnnotatedString(time)
    }
    val strikeWidth = with(LocalDensity.current) { 3.dp.toPx() }
    val overhang = with(LocalDensity.current) { 8.dp.toPx() }
    Text(
        text = text,
        modifier = if (struck == null) modifier else modifier.drawWithContent {
            drawContent()
            val y = size.height * 0.52f
            drawLine(struck, Offset(-overhang, y), Offset(size.width + overhang, y), strikeWidth, StrokeCap.Round)
        },
        color = color,
        fontFamily = family,
        fontWeight = weight,
        fontSize = sizeDp.fixedSp(),
        letterSpacing = spacing.fixedSp(),
        lineHeight = (sizeDp * 1.08f).fixedSp(),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = C.PurpleLight) {
    Txt(text, 12.sp, color, FontWeight.Bold, modifier = modifier, letterSpacing = 1.2.sp)
}

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    background: Color = C.Surface,
    border: Color = C.StrokeDim,
    radius: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.fillMaxWidth().clip(shape).background(background).border(1.dp, border, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

data class PillLook(val text: String, val dot: Color, val bg: Color, val fg: Color)

fun pillFor(r: Reading?, s: AppSettings, now: Long): PillLook {
    if (s.stateId <= 0) return PillLook("Область не выбрана", C.Text3, C.Grey, C.Text2)
    if (r == null || r.status == Status.UNKNOWN) return PillLook("Нет данных", C.Text3, C.Grey, C.Text2)
    val dur = if (r.since > 0L) " · " + Fmt.duration(now - r.since) else ""
    return when (r.status) {
        Status.CLEAR -> PillLook("Тревоги нет", C.Mint, C.MintBg, C.MintText)
        Status.ALERT -> PillLook("Тревога$dur", C.Red, C.RedBg, C.RedText)
        else -> if (Live.isAlertForMe(r, s)) {
            PillLook("Тревога в части области$dur", C.Red, C.RedBg, C.RedText)
        } else {
            PillLook("Тревога в соседних районах", C.Amber, C.AmberBg, C.AmberText)
        }
    }
}

@Composable
fun StatusPill(look: PillLook, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(look.bg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(look.dot))
        Spacer(Modifier.width(8.dp))
        Txt(look.text, 14.sp, look.fg, FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun PlaceChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(C.Surface)
            .border(1.dp, C.Chip, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.pin, null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Txt(text, 14.sp, C.Text, FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    val x by animateDpAsState(if (on) 24.dp else 4.dp, label = "knob")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) accent else C.Track)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
    ) {
        Box(
            Modifier
                .offset(x = x, y = 4.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else C.Muted)
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, height: Dp = 54.dp, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, 16.sp, Color.White, FontWeight.SemiBold)
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, color: Color = C.PurplePale, height: Dp = 52.dp, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .border(1.dp, C.Chip, RoundedCornerShape(height / 2))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Txt(text, 15.sp, color, FontWeight.SemiBold)
    }
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { o ->
            val sel = o == selected
            Box(
                Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (sel) accent else C.Surface)
                    .border(1.dp, if (sel) accent else C.Stroke, RoundedCornerShape(19.dp))
                    .clickable { onSelect(o) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Txt(label(o), 14.sp, if (sel) Color.White else C.Text2, FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, tint: Color = C.Text2, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(C.Surface)
            .border(1.dp, C.Stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun InputField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val accent = LocalAccent.current
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(C.Surface)
            .border(1.dp, C.Stroke, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = C.Text3, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Txt(placeholder, 15.sp, C.Text3, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp, fontFamily = Onest),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Текущее время, обновляется раз в минуту. */
@Composable
fun rememberNow(): Long {
    val state = produceState(System.currentTimeMillis()) {
        while (true) {
            val n = System.currentTimeMillis()
            value = n
            delay(60_000L - n % 60_000L + 50L)
        }
    }
    return state.value
}

/** Счётчик, который растёт каждый раз, когда экран снова виден (например, вернулись из настроек). */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}
