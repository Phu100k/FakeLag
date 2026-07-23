package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Futuristic Mystic Dark Tech Palette
val TechDarkBg = Color(0xFF06050C)
val TechCardBg = Color(0xFF0E0D1B)
val TechCardBgElevated = Color(0xFF151329)
val TechBorder = Color(0xFF221F3F)
val TechAccent = Color(0xFF1B224A)
val TechTextPrimary = Color(0xFFF0EFFC)
val TechTextSecondary = Color(0xFF918EB2)

// Neon Arcane Glow Palette
val NeonFreeze = Color(0xFF00F5FF)   // Electric Cyan glow
val NeonGhost = Color(0xFF00FF88)    // Neon Mint / Lime Green glow
val NeonTeleport = Color(0xFFFF5E00) // Vibrant Orange/Flame glow
val NeonPink = Color(0xFFFF007F)     // Sharp Cyber Pink
val NeonYellow = Color(0xFFFFCC00)   // Golden Amber indicator
val NeonPurple = Color(0xFF9D00FF)   // Mystic Void Violet
val NeonIndigo = Color(0xFF6200EE)   // Deep Arcane Blue
val SilentGray = Color(0xFF232136)

// Mysterious Gradients
val MysticBgGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF070512),
        Color(0xFF0C091D),
        Color(0xFF05040A)
    )
)

val CyberGlowGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF00F5FF),
        Color(0xFF9D00FF),
        Color(0xFFFF007F)
    )
)

val ArcaneCardGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF131126),
        Color(0xFF0E0C1C)
    )
)

val FreezeGlowGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00F5FF), Color(0xFF0088FF))
)

val GhostGlowGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00FF88), Color(0xFF00B359))
)

val TeleportGlowGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF5E00), Color(0xFFFF0055))
)

