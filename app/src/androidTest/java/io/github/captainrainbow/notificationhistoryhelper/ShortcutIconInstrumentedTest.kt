package io.github.captainrainbow.notificationhistoryhelper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShortcutIconInstrumentedTest {
    @Test fun previewCommonMasksWithoutModifyingTheIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val native = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_shortcut)) as AdaptiveIconDrawable
        native.setBounds(0, 0, 216, 216)
        val masks = listOf(
            "Native AOSP" to Path(native.iconMask),
            "Circle" to Path().apply { addCircle(108f, 108f, 108f, Path.Direction.CW) },
            "Rounded square" to Path().apply { addRoundRect(RectF(0f, 0f, 216f, 216f), 42f, 42f, Path.Direction.CW) },
        )
        val preview = Bitmap.createBitmap(720, 280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview).apply { drawColor(Color.rgb(238, 238, 238)) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f }
        masks.forEachIndexed { index, (name, mask) ->
            val original = renderWithMask(R.drawable.ic_launcher, mask)
            val shortcut = renderWithMask(R.drawable.ic_shortcut, mask)
            var whiteBadge = 0
            for (y in 0 until 216) for (x in 0 until 216) {
                if (x >= 80 || y <= 140) {
                    val a = original.getPixel(x, y)
                    val b = shortcut.getPixel(x, y)
                    val delta = maxOf(kotlin.math.abs(Color.alpha(a) - Color.alpha(b)),
                        kotlin.math.abs(Color.red(a) - Color.red(b)),
                        kotlin.math.abs(Color.green(a) - Color.green(b)),
                        kotlin.math.abs(Color.blue(a) - Color.blue(b)))
                    // The foreground clip causes the same <=8/255 antialias difference
                    // covered by the native-icon regression below, not an artwork change.
                    assertTrue("$name artwork outside badge ($x,$y), delta=$delta", delta <= 8)
                } else if (shortcut.getPixel(x, y) == Color.WHITE && original.getPixel(x, y) != Color.WHITE) {
                    whiteBadge++
                }
            }
            assertTrue("$name must keep a visible white corner", whiteBadge > 80)
            canvas.drawBitmap(shortcut, index * 240f + 12f, 48f, null)
            canvas.drawText(name, index * 240f + 12f, 30f, paint)
            original.recycle()
            shortcut.recycle()
        }
        File(context.cacheDir, "shortcut-masks-preview.png").outputStream().use {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        preview.recycle()
    }

    private fun renderWithMask(resource: Int, mask: Path): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val adaptive = requireNotNull(ContextCompat.getDrawable(context, resource)) as AdaptiveIconDrawable
        val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(mask)
        // AdaptiveIconDrawable's layers extend 25% beyond each side of the mask.
        for (layer in listOf(adaptive.background, adaptive.foreground)) {
            layer.setBounds(-54, -54, 270, 270)
            layer.draw(canvas)
        }
        return bitmap
    }

    @Test fun shortcutIconAddsAVisibleBadgeWithoutChangingTheOriginalArtwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = render(requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher)))
        val shortcut = render(requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_shortcut)))
        if (InstrumentationRegistry.getArguments().getString("renderShortcutPreview") == "true") {
            // Render app assets only, even on failure; no system or notification screenshot.
            val preview = Bitmap.createBitmap(472, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(preview)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(app, 12f, 20f, null)
            canvas.drawBitmap(shortcut, 244f, 20f, null)
            File(context.cacheDir, "shortcut-native-preview.png").outputStream().use {
                preview.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        var changed = 0
        var white = 0
        for (y in 0 until 216) for (x in 0 until 216) {
            val pixel = shortcut.getPixel(x, y)
            val original = app.getPixel(x, y)
            if (x >= 80 || y <= 140) {
                // Clipping changes edge antialiasing slightly (observed max 7/255).
                // Still reject changes to the artwork outside the badge region.
                val delta = maxOf(kotlin.math.abs(Color.alpha(original) - Color.alpha(pixel)),
                    kotlin.math.abs(Color.red(original) - Color.red(pixel)),
                    kotlin.math.abs(Color.green(original) - Color.green(pixel)),
                    kotlin.math.abs(Color.blue(original) - Color.blue(pixel)))
                assertTrue("Artwork outside the lower-left badge changed ($x,$y), delta=$delta", delta <= 8)
            } else if (original != pixel) {
                changed++
                if (pixel == Color.WHITE) white++
            }
        }
        assertTrue("Badge must be visible", changed > 250)
        assertTrue("Badge must have a white background", white > 80)
        assertEquals("Badge must sit by the rainbow icon's bottom-left corner, not on the folder",
            Color.WHITE, shortcut.getPixel(30, 198))
        val foreground = render(requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_shortcut_foreground)))
        val badge = render(requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_shortcut_badge)))
        val originalForeground = render(requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)))
        // Interior samples in 108dp coordinates, rendered at 2x. Opaque artwork
        // below either part of the arrow would hide the adaptive rainbow background.
        assertEquals("Arrow stem must reveal the rainbow", 0, Color.alpha(foreground.getPixel(54, 160)))
        assertEquals("Arrow head must reveal the rainbow; badge alpha=" + Color.alpha(badge.getPixel(68, 150)) +
            ", original artwork alpha=" + Color.alpha(originalForeground.getPixel(68, 150)),
            0, Color.alpha(foreground.getPixel(68, 150)))
        assertEquals("Badge must remain white around the arrow", Color.WHITE, foreground.getPixel(50, 144))
        assertEquals("Corner panel must reach the icon's left edge", Color.WHITE, badge.getPixel(40, 142))
        assertEquals("Corner panel must reach the icon's bottom edge", Color.WHITE, badge.getPixel(64, 178))
        assertEquals("Inward top-right corner must be rounded", 0, Color.alpha(badge.getPixel(80, 138)))
    }

    private fun render(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 216, 216)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
