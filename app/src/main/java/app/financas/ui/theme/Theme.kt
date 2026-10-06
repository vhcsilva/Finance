package app.financas.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.financas.domain.TxType

/** Paleta do app (mesma das telas validadas). */
object Palette {
    val Ground = Color(0xFFF2F4F3)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF15201C)
    val InkSoft = Color(0xFF34403B)
    val Muted = Color(0xFF5B6661)
    val Line = Color(0xFFE2E6E4)
    val LineSoft = Color(0xFFEEF1EF)
    val Border = Color(0xFFD5DBD8)
    val Track = Color(0xFFE4E8E6)
    val Accent = Color(0xFF0E6B4E)
    val AccentDark = Color(0xFF0A4F3A)
    val AccentSoft = Color(0xFFE3F0EA)
    val Deep = Color(0xFF0E3B2E)
    val DeepMuted = Color(0xFFB9D3C8)
    val Out = Color(0xFFB4400A)
    val OutSoft = Color(0xFFFBE6D8)
    val OutOnDark = Color(0xFFFFB48A)
    val In = Color(0xFF1F4FC8)
    val InSoft = Color(0xFFE1E9FB)
    val InOnDark = Color(0xFFA9C4FF)
    val ChartOut = Color(0xFFE07A3F)
    val Warn = Color(0xFF8A3006)

    fun amount(type: TxType) = if (type == TxType.IN) In else Out

    /** Cores oferecidas para contas, cartões, categorias e pessoas. */
    val choices: List<Int> = listOf(
        0xFF6D28D9, 0xFF7C3AED, 0xFF1F4FC8, 0xFF0E7490, 0xFF0E6B4E, 0xFF4D7C0F, 0xFFCA8A04, 0xFFE06A00,
        0xFFB4400A, 0xFFBE123C, 0xFFDB2777, 0xFF9D174D, 0xFF1F2937, 0xFF475569, 0xFF78716C, 0xFF0F766E,
    ).map { it.toInt() }
}

private val AppColors = lightColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    primaryContainer = Palette.AccentSoft,
    onPrimaryContainer = Palette.Accent,
    secondary = Palette.Deep,
    onSecondary = Color.White,
    background = Palette.Ground,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.LineSoft,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Surface,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.LineSoft,
    outline = Palette.Border,
    outlineVariant = Palette.Line,
    error = Color(0xFFB3261E),
)

private val base = Typography()

private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 15.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, color = Palette.Muted),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

/** Estilo de números (dígitos com largura fixa para alinhar valores). */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun FinancasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
