//package com.openear.maestro.ui.theme
//
//import android.app.Activity
//import androidx.compose.foundation.isSystemInDarkTheme
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.darkColorScheme
//import androidx.compose.material3.lightColorScheme
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.SideEffect
//import androidx.compose.ui.graphics.toArgb
//import androidx.compose.ui.platform.LocalView
//import androidx.core.view.WindowCompat
//
//private val DarkColorScheme = darkColorScheme(
//    primary = Purple80,
//    secondary = PurpleGrey80,
//    tertiary = Pink80
//)
//
//private val LightColorScheme = lightColorScheme(
//    primary = Purple40,
//    secondary = PurpleGrey40,
//    tertiary = Pink40
//)
//
//@Composable
//fun MaestroTheme(
//    darkTheme: Boolean = isSystemInDarkTheme(),
//    content: @Composable () -> Unit
//) {
//    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
//    val view = LocalView.current
//    if (!view.isInEditMode) {
//        SideEffect {
//            val window = (view.context as Activity).window
//            window.statusBarColor = colorScheme.primary.toArgb()
//            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
//        }
//    }
//
//    MaterialTheme(
//        colorScheme = colorScheme,
//        typography = Typography,
//        content = content
//    )
//}
//// Note: You will need to define Purple80, PurpleGrey80, Pink80, etc. in a Color.kt file
//// or use standard Material colors. For this example, we assume they are defined elsewhere.


package com.openear.maestro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MaestroTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    content = content
  )
}
