package gg.strims.android

import android.R.attr
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ImageSpan


// https://stackoverflow.com/a/60763554
// https://stackoverflow.com/questions/25628258/align-text-around-imagespan-center-vertical
class CenteredImageSpan(
    context: Context,
    private val bitmap: Bitmap
) : ImageSpan(context, bitmap) {
    private var initialDescent: Int = 0
    private var extraSpace: Int = 0
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val rect = drawable.bounds
        if (fm != null) {
            // Centers the text with the ImageSpan
            if (rect.bottom - (fm.descent - fm.ascent) >= 0) {
                // Stores the initial descent and computes the margin available
                initialDescent = fm.descent;
                extraSpace = rect.bottom - (fm.descent - fm.ascent);
            }

            fm.descent = extraSpace / 2 + initialDescent;
            fm.bottom = fm.descent;

            fm.ascent = -rect.bottom + fm.descent;
            fm.top = fm.ascent;
        }

        return rect.right;
    }

//    override fun draw(
//        canvas: Canvas,
//        text: CharSequence?,
//        start: Int,
//        end: Int,
//        x: Float,
//        top: Int,
//        y: Int,
//        bottom: Int,
//        paint: Paint
//    ) {
//        canvas.save()
//
//        val drawableHeight: Int = drawable.intrinsicHeight
//        val fontAscent = paint.fontMetricsInt.ascent
//        val fontDescent = paint.fontMetricsInt.descent
//        val transY: Int = attr.bottom - drawable.bounds.bottom +  // align bottom to bottom
//                (drawableHeight - fontDescent + fontAscent) / 2 // align center to center
//
//
//        canvas.translate(attr.x.toFloat(), transY.toFloat())
//        drawable.draw(canvas)
//        canvas.restore()
//    }
}