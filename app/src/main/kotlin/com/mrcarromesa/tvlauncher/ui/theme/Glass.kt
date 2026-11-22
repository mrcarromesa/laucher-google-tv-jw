package com.mrcarromesa.tvlauncher.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

val GlassShape = RoundedCornerShape(28.dp)

/**
 * Painel "Liquid Glass" (contracts/ui-behavior.md §6):
 * `Brush.verticalGradient` de branco 0.18 → 0.08, `border` de 1 dp com gradiente,
 * `clip(RoundedCornerShape(28.dp))`, sombra suave, reflexo diagonal branco 0.06 no topo.
 */
fun Modifier.glassPanel(): Modifier =
  this
    .shadow(elevation = 12.dp, shape = GlassShape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
    .clip(GlassShape)
    .backgroundGlass()
    .reflectionGlass()
    .border(
      width = 1.dp,
      brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.12f))),
      shape = GlassShape,
    )

private fun Modifier.backgroundGlass(): Modifier =
  drawWithCache {
    onDrawBehind {
      drawRect(
        Brush.verticalGradient(
          colors = listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.08f)),
          startY = 0f,
          endY = size.height,
        ),
      )
    }
  }

private fun Modifier.reflectionGlass(): Modifier =
  drawWithCache {
    onDrawBehind {
      drawRect(
        Brush.linearGradient(
          colors = listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0f)),
          start = Offset(0f, 0f),
          end = Offset(size.width, size.height * 0.35f),
        ),
      )
    }
  }

/**
 * Anel de foco: escala 1.06 + brilho da borda, `tween(120)` (contracts/ui-behavior.md §6).
 * Aplicar APÓS `glassPanel()` para que a borda brilhante fique por cima do painel.
 */
@Composable
fun Modifier.tvFocusRing(focused: Boolean, shape: Shape = GlassShape): Modifier {
  val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(120), label = "focusScale")
  val glow by animateFloatAsState(if (focused) 0.95f else 0f, tween(120), label = "focusGlow")
  return this
    .graphicsLayer { scaleX = scale; scaleY = scale }
    .border(
      width = 2.dp,
      brush = Brush.verticalGradient(listOf(GlassWhite.copy(alpha = glow), GlassWhite.copy(alpha = glow * 0.55f))),
      shape = shape,
    )
}
