package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// YouTube Brand Colors
val YouTubeRed = Color(0xFFFF0000)
val YouTubeDarkRed = Color(0xFFCC0000)

// High Density Dark Theme Colors (Zinc & Pure Dark)
val YTDarkBackground = Color(0xFF0F0F0F)
val YTDarkSurface = Color(0xFF18181B) // zinc-900
val YTDarkSurfaceVariant = Color(0xFF27272A) // zinc-800
val YTDarkSurfaceContainer = Color(0xFF282828)
val YTDarkCardBorder = Color(0x1AFFFFFF) // border-white/10
val YTDarkSubtleBorder = Color(0x0DFFFFFF) // border-white/5
val YTDarkTextPrimary = Color(0xFFFFFFFF)
val YTDarkTextSecondary = Color(0xFFA1A1AA) // zinc-400
val YTDarkDivider = Color(0x1AFFFFFF)
val YTDarkChipActive = Color(0xFFFFFFFF)
val YTDarkChipActiveText = Color(0xFF000000)
val YTDarkChipInactive = Color(0x1AFFFFFF) // bg-white/10
val YTDarkChipInactiveText = Color(0xFFFFFFFF)

// High Density Light Theme Colors
val YTLightBackground = Color(0xFFFFFFFF)
val YTLightSurface = Color(0xFFF4F4F5) // zinc-100
val YTLightSurfaceVariant = Color(0xFFE4E4E7) // zinc-200
val YTLightSurfaceContainer = Color(0xFFFAFAFA)
val YTLightCardBorder = Color(0xFFE4E4E7)
val YTLightSubtleBorder = Color(0xFFF4F4F5)
val YTLightTextPrimary = Color(0xFF09090B) // zinc-950
val YTLightTextSecondary = Color(0xFF71717A) // zinc-500
val YTLightDivider = Color(0xFFE4E4E7)
val YTLightChipActive = Color(0xFF09090B)
val YTLightChipActiveText = Color(0xFFFFFFFF)
val YTLightChipInactive = Color(0xFFF4F4F5)
val YTLightChipInactiveText = Color(0xFF09090B)

// Accents — single canonical value per hue. Do NOT add near-duplicates:
// green = YTSuccess, blue = YTAccentBlue (verified-check keeps
// YTBlueVerified), amber = YTWarningAmber.
val YTBlueVerified = Color(0xFF3EA6FF)
val YTGreenSuccess = Color(0xFF2BA640)
val YTSuccess = YTGreenSuccess
val YTAccentBlue = Color(0xFF1E88E5)
val YTWarningAmber = Color(0xFFFFB300)
val YTLiveRed = YouTubeRed
val YTBadgeBackground = Color(0xCC000000)

// Scrim scale for media overlays. Player surfaces keep pure Black;
// everything else uses these so alpha stops are consistent.
val YTScrimStrong = Color(0xE6000000) // Black 90%
val YTScrim = Color(0xA6000000) // Black 65%
val YTScrimSoft = Color(0x66000000) // Black 40%
val YTTextOnScrim = Color(0xC7FFFFFF) // White 78%

// Brand accents moved out of call sites so hues stay canonical.
val ImdbYellow = Color(0xFFF5C518)
val ImdbBlack = Color(0xFF000000)
val YTMarvelRed = Color(0xFFE62429)
val YTBrandNavy = Color(0xFF111827)
val YTLogoFallback = Color(0xFFF1F1F1)

