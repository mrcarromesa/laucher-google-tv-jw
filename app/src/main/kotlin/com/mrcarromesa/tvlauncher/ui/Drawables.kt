package com.mrcarromesa.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Converte um `android.graphics.drawable.Drawable` em `ImageBitmap` (ícones de apps). */
fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
  return when (drawable) {
    is BitmapDrawable -> drawable.bitmap.asImageBitmap()
    else -> {
      val bmp =
        Bitmap.createBitmap(
          maxOf(drawable.intrinsicWidth, 1),
          maxOf(drawable.intrinsicHeight, 1),
          Bitmap.Config.ARGB_8888,
        )
      drawable.setBounds(0, 0, bmp.width, bmp.height)
      drawable.draw(Canvas(bmp))
      bmp.asImageBitmap()
    }
  }
}