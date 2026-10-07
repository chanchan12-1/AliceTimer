package cn.alice.timer.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AlicePurple = Color(0xFF8071C3)
val AliceInk = Color(0xFF34344E)
val AliceMuted = Color(0xFF858499)
val AliceBackground = Color(0xFFF8F7FC)

@Composable fun AliceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = AlicePurple, onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE9FA), onPrimaryContainer = AliceInk,
        secondary = Color(0xFF94BBA7), secondaryContainer = Color(0xFFE6F2EC),
        background = AliceBackground, surface = Color.White, onSurface = AliceInk,
        onBackground = AliceInk, onSurfaceVariant = AliceMuted,
        surfaceContainer = Color(0xFFF1EEF9), outline = Color(0xFFD8D4E7),
    ), content = content)
}
