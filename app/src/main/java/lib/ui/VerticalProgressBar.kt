package lib.ui

import android.content.*
import android.content.res.*
import android.graphics.*
import android.util.*
import android.view.*
import androidx.core.content.*
import com.aio.*
import lib.process.*

/**
 * A custom [View] implementation that provides a vertical progress indicator.
 *
 * This component extends the standard Android View class and uses the [JvmOverloads]
 * annotation to generate multiple constructors for compatibility with Java-based
 * layout inflation and programmatic instantiation. It encapsulates the logic
 * for rendering a bottom-to-top progress fill within a rounded track.
 *
 * @param context The [Context] the view is running in, through which it can
 * access the current theme, resources, etc.
 * @param attrs The attributes of the XML tag that is inflating the view.
 * @param defStyleAttr An attribute in the current theme that contains a
 * reference to a style resource that supplies default values for
 * the view. Can be 0 to not look for defaults.
 */
class VerticalProgressBar @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

	/**
	 * The current progress level, ranging from 0 to [max].
	 * Changes to this value will affect the height of the progress fill
	 * during the next draw pass.
	 */
	private var progress = 0

	/**
	 * The maximum value representing 100% completion.
	 * Used as the denominator to calculate the relative height of the progress indicator.
	 */
	private var max = 100

	/**
	 * Configuration for the background track's appearance.
	 * Defines the color, fill style, and antialiasing properties for the
	 * empty portion of the progress bar.
	 */
	private val backgroundPaint = Paint()
		.apply {
			color = ContextCompat.getColor(context, R.color.color_primary_variant)
			style = Paint.Style.FILL
			isAntiAlias = true
		}

	/**
	 * Configuration for the active progress indicator's appearance.
	 * * Uses the secondary theme color and a fill style to render the progress amount.
	 * Antialiasing is enabled to ensure smooth edges on the rounded corners.
	 */
	private val progressPaint = Paint().apply {
		color = ContextCompat.getColor(context, R.color.color_secondary)
		style = Paint.Style.FILL
		isAntiAlias = true
	}

	/**
	 * Updates the current progress value asynchronously on the Main thread.
	 *
	 * The provided value is clamped between 0 and [max] to ensure rendering remains
	 * within the view's bounds. Calling this function triggers [invalidate],
	 * forcing the view to redraw with the new progress height.
	 *
	 * @param value The new progress level to display.
	 */
	suspend fun setProgress(value: Int) {
		withMainContext {
			progress = value.coerceIn(0, max)
			invalidate()
		}
	}

	/**
	 * Defines the maximum bounds for the progress scale asynchronously on the Main thread.
	 *
	 * Changing the maximum value adjusts the relative scale of the progress indicator.
	 * The view is invalidated to reflect the updated proportions immediately.
	 *
	 * @param value The new maximum value for the progress bar.
	 */
	suspend fun setMax(value: Int) {
		withMainContext {
			max = value
			invalidate()
		}
	}

	/**
	 * Calculates the dimensions required for the vertical progress bar.
	 *
	 * This implementation enforces a default "intrinsic" size of 10dp by 200dp.
	 * It uses [View.resolveSize] to negotiate with the parent's [MeasureSpec], allowing
	 * the view to adapt to `wrap_content` or specific layout constraints while
	 * maintaining its desired aspect ratio.
	 *
	 * @param widthMeasureSpec Horizontal space requirements as imposed by the parent.
	 * @param heightMeasureSpec Vertical space requirements as imposed by the parent.
	 */
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val desiredWidth = dpToPx(10)
		val desiredHeight = dpToPx(200)
		val width = resolveSize(desiredWidth, widthMeasureSpec)
		val height = resolveSize(desiredHeight, heightMeasureSpec)
		setMeasuredDimension(width, height)
	}

	/**
	 * Renders the visual components of the progress bar onto the provided [Canvas].
	 *
	 * The drawing process follows a layered approach:
	 * 1. **Track Rendering:** Draws the full-height background rounded rectangle using [backgroundPaint].
	 * 2. **Progress Calculation:** Determines the pixel height of the progress fill by calculating
	 * the ratio of [progress] to [max] relative to the total view height.
	 * 3. **Fill Rendering:** Draws the active progress rounded rectangle. The top boundary is
	 * calculated as `height - progressHeight`, ensuring the bar "fills up" from the bottom
	 * of the view toward the top.
	 *
	 * Both rectangles use a 10dp corner radius to maintain a consistent rounded aesthetic.
	 *
	 * @param canvas The [Canvas] on which the background and progress indicator will be drawn.
	 */
	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawRoundRect(
			0f, 0f, width.toFloat(), height.toFloat(),
			dpToPx(10).toFloat(), dpToPx(10).toFloat(),
			backgroundPaint
		)
		val progressHeight =
			if (max > 0) (height * progress / max.toFloat()) else 0f

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

	/**
	 * Converts density-independent pixels (dp) to screen pixels (px) based on the current
	 * system display density.
	 *
	 * This utility uses the system-wide [Resources] to fetch the [DisplayMetrics.density]
	 * factor. It is useful for programmatically setting dimensions that must remain
	 * consistent across different screen resolutions (e.g., LDPI, MDPI, HDPI, etc.).
	 *
	 * @param dp The dimension value in density-independent pixels.
	 * @return The equivalent value in physical pixels, rounded down to the nearest integer.
	 */
	private fun dpToPx(dp: Int): Int {
		return (dp * Resources.getSystem().displayMetrics.density).toInt()
	}
}