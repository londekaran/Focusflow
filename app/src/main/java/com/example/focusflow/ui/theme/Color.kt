package com.example.focusflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Colours that Material 3's ColorScheme has no slot for. Read them with FF.colors. */
data class FFColors(
    val primaryDark: Color,
    /** Olive that is dark enough to read as text on the app's surfaces. */
    val primaryText: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val elevated: Color,
    val border: Color,
    val successContainer: Color,
    val warningContainer: Color,
    val infoContainer: Color,
    val errorContainer: Color,
    val catWork: Color,
    val catStudy: Color,
    val catPersonal: Color,
    val catHealth: Color,
    val catGeneral: Color
)

internal val LightFF = FFColors(
    primaryDark = Color(0xFF3F4F1B), primaryText = Color(0xFF4A5C20),
    success = Color(0xFF4F7A2E), warning = Color(0xFFB98319), info = Color(0xFF5E8A78),
    elevated = Color(0xFFFEFFFA), border = Color(0xFFDDE1CA),
    successContainer = Color(0xFFE3EDCB), warningContainer = Color(0xFFF6E7BF),
    infoContainer = Color(0xFFDCE9E1), errorContainer = Color(0xFFFBE4DE),
    catWork = Color(0xFF56692A), catStudy = Color(0xFFB98319), catPersonal = Color(0xFFA8643F),
    catHealth = Color(0xFF5E8A78), catGeneral = Color(0xFF787D66)
)

internal val DarkFF = FFColors(
    primaryDark = Color(0xFF8FA64A), primaryText = Color(0xFFB4CC6A),
    success = Color(0xFF9CC25E), warning = Color(0xFFE2B554), info = Color(0xFF8DB5A0),
    elevated = Color(0xFF1A1E14), border = Color(0xFF33392A),
    successContainer = Color(0xFF273414), warningContainer = Color(0xFF3A2F12),
    infoContainer = Color(0xFF1E2B25), errorContainer = Color(0xFF3B1D1A),
    catWork = Color(0xFFB4CC6A), catStudy = Color(0xFFE2B554), catPersonal = Color(0xFFD9976E),
    catHealth = Color(0xFF8DB5A0), catGeneral = Color(0xFFA9AE94)
)

/** Olive-green palette. Brown is kept only for the "Personal" category colour. */
internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF56692A), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE8B8), onPrimaryContainer = Color(0xFF2B3510),
    secondary = Color(0xFF66744A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EDD3), onSecondaryContainer = Color(0xFF2F3A1C),
    tertiary = Color(0xFF8A7A22), onTertiary = Color.White,
    background = Color(0xFFF6F7EE), onBackground = Color(0xFF1A1D12),
    surface = Color(0xFFFDFEF9), onSurface = Color(0xFF1A1D12),
    surfaceVariant = Color(0xFFEBEEDD), onSurfaceVariant = Color(0xFF5B6049),
    surfaceTint = Color(0xFF56692A),
    surfaceContainerLowest = Color(0xFFFDFEF9), surfaceContainerLow = Color(0xFFFDFEF9),
    surfaceContainer = Color(0xFFFDFEF9), surfaceContainerHigh = Color(0xFFFDFEF9),
    surfaceContainerHighest = Color(0xFFEBEEDD),
    outline = Color(0xFF7D826A), outlineVariant = Color(0xFFDDE1CA),
    error = Color(0xFFC8422E), onError = Color.White,
    errorContainer = Color(0xFFFBE4DE), onErrorContainer = Color(0xFF7A1C0E)
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB4CC6A), onPrimary = Color(0xFF1D2807),
    primaryContainer = Color(0xFF3A4A17), onPrimaryContainer = Color(0xFFD6E69F),
    secondary = Color(0xFF9DB07A), onSecondary = Color(0xFF1E2A0C),
    secondaryContainer = Color(0xFF2F3A1C), onSecondaryContainer = Color(0xFFD3E2B2),
    tertiary = Color(0xFFD2C06A), onTertiary = Color(0xFF2A2304),
    background = Color(0xFF0C0E08), onBackground = Color(0xFFEEF0E2),
    surface = Color(0xFF151810), onSurface = Color(0xFFEEF0E2),
    surfaceVariant = Color(0xFF20241A), onSurfaceVariant = Color(0xFFB0B59C),
    surfaceTint = Color(0xFFB4CC6A),
    surfaceContainerLowest = Color(0xFF0C0E08), surfaceContainerLow = Color(0xFF151810),
    surfaceContainer = Color(0xFF151810), surfaceContainerHigh = Color(0xFF20241A),
    surfaceContainerHighest = Color(0xFF2B3022),
    outline = Color(0xFF666B54), outlineVariant = Color(0xFF33392A),
    error = Color(0xFFF08A73), onError = Color(0xFF3B0E04),
    errorContainer = Color(0xFF3B1D1A), onErrorContainer = Color(0xFFF7B3A3)
)
