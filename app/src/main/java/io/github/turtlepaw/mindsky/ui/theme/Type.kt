package io.github.turtlepaw.mindsky.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import fyi.kittens.shared.R

val SoraFontFamily = FontFamily(
    Font(R.font.sora)
)

val GoogleSansFontFamily = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold),
    Font(R.font.google_sans_italic, FontWeight.Normal, FontStyle.Italic)
)

val default = SoraFontFamily

@OptIn(ExperimentalTextApi::class)
fun getFontFamily(
    fontName: String,
    fontWeightValue: Int = 400,
    fontWidthValue: Int = 100,
    fontRoundnessValue: Int = 0
): FontFamily {
    val variation = FontVariation.Settings(
        FontVariation.weight(fontWeightValue),
        FontVariation.width(fontWidthValue.toFloat()),
        FontVariation.Setting("ROND", fontRoundnessValue.toFloat())
    )
    return when (fontName) {
        "Google Sans" -> FontFamily(
            Font(
                R.font.google_sans_flex,
                variationSettings = variation
            )
        )
        else -> FontFamily(
            Font(
                R.font.sora,
                variationSettings = variation
            )
        )
    }
}

fun Typography.withFontFamily(fontFamily: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)
