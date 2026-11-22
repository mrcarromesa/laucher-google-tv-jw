package com.mrcarromesa.tvlauncher.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val GlassShape = RoundedCornerShape(28.dp)
val GlassShapeMedium = RoundedCornerShape(20.dp)
val GlassShapeSmall = RoundedCornerShape(16.dp)

/**
 * Painel "Liquid Glass" fiel à referência visual model-v1.png:
 * Translucidez refinada com gradiente suave, borda de 1 dp branca semi-translúcida,
 * cantos arredondados (28 dp) e sombra suave.
 */
fun Modifier.glassPanel(
  shape: Shape = GlassShape,
  elevation: Dp = 10.dp,
  borderAlphaTop: Float = 0.42f,
  borderAlphaBottom: Float = 0.15f,
  bgAlphaTop: Float = 0.22f,
  bgAlphaBottom: Float = 0.08f,
): Modifier =
  this
    .shadow(
      elevation = elevation,
      shape = shape,
      ambientColor = Color.Black.copy(alpha = 0.35f),
      spotColor = Color.Black.copy(alpha = 0.35f),
    )
    .clip(shape)
    .drawWithCache {
      onDrawBehind {
        // Fundo com tint translúcido escurecido levemente para contraste sobre a foto de fundo
        drawRect(Color(0x30122438))
        // Gradiente vítreo branco
        drawRect(
          Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = bgAlphaTop), Color.White.copy(alpha = bgAlphaBottom)),
            startY = 0f,
            endY = size.height,
          ),
        )
        // Reflexo diagonal vítreo no topo
        drawRect(
          Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.7f, size.height * 0.45f),
          ),
        )
      }
    }
    .border(
      width = 1.dp,
      brush = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = borderAlphaTop), Color.White.copy(alpha = borderAlphaBottom)),
      ),
      shape = shape,
    )

/**
 * Anel de foco da TV inspirado em model-v1.png (glow azul neon + borda brilhante e escala):
 * Escala 1.05 + borda brilhante com halo azul/ciano.
 */
@Composable
fun Modifier.tvFocusRing(
  focused: Boolean,
  shape: Shape = GlassShape,
  scaleFocused: Float = 1.05f,
  glowColor: Color = Color(0xFF3898EC),
): Modifier {
  val scale by animateFloatAsState(if (focused) scaleFocused else 1f, tween(120), label = "focusScale")
  val glowAlpha by animateFloatAsState(if (focused) 0.95f else 0f, tween(120), label = "focusGlow")

  return if (focused) {
    this
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .drawBehind {
        // Halo exterior azul neon brilhante (como no card Netflix da referência)
        drawRoundRect(
          color = glowColor.copy(alpha = 0.55f * glowAlpha),
          cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
          topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
          size = size.copy(
            width = size.width + 8.dp.toPx(),
            height = size.height + 8.dp.toPx(),
          ),
        )
      }
      .border(
        width = 2.5.dp,
        brush = Brush.verticalGradient(
          listOf(Color.White.copy(alpha = glowAlpha), Color(0xFF64B5F6).copy(alpha = glowAlpha)),
        ),
        shape = shape,
      )
  } else {
    this.graphicsLayer {
      scaleX = scale
      scaleY = scale
    }
  }
}
