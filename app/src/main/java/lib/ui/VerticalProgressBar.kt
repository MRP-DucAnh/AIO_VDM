package lib.ui

import android.content.*
import android.content.res.*
import android.graphics.*
import android.util.*
import android.view.*
import androidx.core.content.*
import com.aio.*
import lib.process.*

class VerticalProgressBar
@JvmOverloads constructor(context: Context,
                          attrs: AttributeSet? = null,
                          defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	private var progress = 0
	private var max = 100

	private val backgroundPaint = Paint().apply {
		val colorResId = R.color.color_primary_variant
		color = ContextCompat.getColor(context, colorResId)
		style = Paint.Style.FILL
		isAntiAlias = true
	}

	private val progressPaint = Paint().apply {
		val colorResId = R.color.color_secondary
		color = ContextCompat.getColor(context, colorResId)
		style = Paint.Style.FILL
		isAntiAlias = true
	}

	suspend fun setProgress(value: Int) {
		withMainContext {
			progress = value.coerceIn(0, max)
			invalidate()
		}
	}

	suspend fun setMax(value: Int) {
		withMainContext {
			max = value
			invalidate()
		}
	}

	override fun onMeasure(widthMeasureSpec: Int,
	                       heightMeasureSpec: Int) {
		val desiredWidth = dpToPx(10)
		val desiredHeight = dpToPx(200)
		val width = resolveSize(desiredWidth, widthMeasureSpec)
		val height = resolveSize(desiredHeight, heightMeasureSpec)
		setMeasuredDimension(width, height)
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawRoundRect(
			0f, 0f, width.toFloat(), height.toFloat(),
			dpToPx(10).toFloat(), dpToPx(10).toFloat(),
			backgroundPaint
		)
		val progressHeight =
			if (max > 0) {
				(height * progress / max.toFloat())
			} else {
				0f
			}

		canvas.drawRoundRect(
			0f,
			height - progressHeight,
			width.toFloat(),
			height.toFloat(),
			dpToPx(10).toFloat(),
			dpToPx(10).toFloat(),
			progressPaint
		)
	}

	private fun dpToPx(dp: Int): Int {
		val displayResources = Resources.getSystem()
		return (dp * displayResources.displayMetrics.density).toInt()
	}
}