@file:Suppress("DEPRECATION")

package lib.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator.ofFloat
import android.app.Activity
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.content.Context.WINDOW_SERVICE
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.decodeByteArray
import android.graphics.BitmapFactory.decodeFile
import android.graphics.BitmapFactory.decodeStream
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.util.DisplayMetrics
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils.loadAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import app.core.AIOApp.Companion.INSTANCE
import app.core.bases.BaseActivity
import app.core.engines.settings.AIOSettings.Companion.AIO_SETTING_DARK_MODE_FILE_NAME
import com.aio.R
import com.bumptech.glide.Glide
import lib.files.FileSystemUtility
import lib.process.LogHelperUtils
import lib.process.ThreadsUtility
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_OK
import java.net.URL
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utility object providing commonly used view-related helper functions for Android UI components.
 *
 * This comprehensive utility class offers a wide range of static methods for efficient
 * Android UI development and maintenance. Key capabilities include:
 *
 * - **Screen Dimension Management**: Retrieving accurate device screen dimensions using both modern
 * 	 WindowMetrics API (Android R+) and legacy DisplayMetrics for backward compatibility, ensuring consistent
 * 	 layout calculations across all Android versions
 *
 * - **Dynamic Layout Control**: Automatically calculating and adjusting view dimensions based on content,
 * 	  particularly useful for GridView height calculation and column distribution to eliminate internal scrolling
 * 	  in scrollable containers
 *
 * - **Memory Optimization**: Systematic resource cleanup through drawable unbinding  and view hierarchy
 *    deconstruction to prevent memory leaks during view destruction  and configuration changes
 *
 * - **View Retrieval & Navigation**: Safe view lookup within hierarchies using tags, IDs, and parent relationships
 *   with comprehensive null safety and error handling
 *
 * - **Animation Utilities**: Pre-built animations for common UI transitions including fades, slides, rotations, and
 *   combined effects with proper lifecycle management
 *
 * - **Display Adaptation**: Tools for handling modern display features like cutouts,  notches, and safe areas to ensure
 *   content visibility across diverse device designs
 *
 * These utilities help maintain cleaner UI code, reduce boilerplate, ensure better
 * compatibility across all supported Android versions, and improve application
 * performance through optimized resource management and efficient view operations.
 */
object ViewUtility {

	/**
	 * Logger instance for tracking function execution, debugging issues, and monitoring performance.
	 *
	 * This logger provides structured logging capabilities throughout the utility class,
	 * enabling detailed tracing of method calls, parameter values, and error conditions.
	 * Essential for diagnosing layout calculation issues and screen dimension retrieval problems.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Retrieves the complete width of the device screen in pixels with version-appropriate APIs.
	 *
	 * This function provides a unified interface for obtaining screen width across all Android
	 * versions, using the modern WindowMetrics API on Android R+ for accurate window dimensions
	 * that account for system UI, and falling back to legacy DisplayMetrics for older devices.
	 * Returns the full screen width including any system decorations and navigation bars.
	 *
	 * @return The total width of the device screen in pixels, representing the complete
	 *         available display area from edge to edge including system UI elements.
	 */
	@JvmStatic
	fun getDeviceWidth(): Int {
		return if (VERSION.SDK_INT >= VERSION_CODES.R) {
			// Use modern WindowMetrics API for accurate window dimensions on Android R+
			getCurrentWindowMetrics().bounds.width()
		} else {
			// Fall back to legacy DisplayMetrics API for devices below Android R
			getLegacyDisplayMetrics().widthPixels
		}
	}

	/**
	 * Retrieves the current [WindowMetrics] representing the application's window dimensions and state.
	 *
	 * This function uses the modern WindowManager API introduced in Android R (API level 30)
	 * to obtain precise information about the current window's size, bounds, and configuration.
	 * Provides more accurate measurements than legacy APIs by accounting for window insets,
	 * system bars, and display cutouts in the calculation.
	 *
	 * @return The current [WindowMetrics] object containing the window's bounding rectangle
	 *         and other display characteristics for precise layout calculations.
	 * @throws ClassCastException if the window service cannot be cast to WindowManager
	 */
	@RequiresApi(VERSION_CODES.R)
	private fun getCurrentWindowMetrics(): WindowMetrics {
		// Retrieve the window service for accessing window management functionality
		val windowService = INSTANCE.getSystemService(WINDOW_SERVICE)
		val windowManager = windowService as WindowManager
		// Return current window metrics including bounds and insets
		return windowManager.currentWindowMetrics
	}

	/**
	 * Retrieves [DisplayMetrics] using the legacy window management API for pre-Android R devices.
	 *
	 * This function provides backward compatibility for devices running Android versions
	 * below API level 30. It uses the deprecated but widely supported DisplayMetrics
	 * approach to obtain screen dimensions, including system UI elements like navigation
	 * bars and status bars in the measurement (real metrics).
	 *
	 * @return [DisplayMetrics] object containing the physical display characteristics
	 *         including width, height, and density information for legacy devices.
	 * @throws ClassCastException if the window service cannot be cast to WindowManager
	 */
	@Suppress("DEPRECATION")
	private fun getLegacyDisplayMetrics(): DisplayMetrics {
		return DisplayMetrics().apply {
			// Retrieve window service and get default display for legacy devices
			val windowService = INSTANCE.getSystemService(WINDOW_SERVICE)
			val windowManager = windowService as WindowManager
			// Populate metrics with real display dimensions including system UI
			windowManager.defaultDisplay.getRealMetrics(this)
		}
	}

	/**
	 * Calculates the number of rows required to display all items in a [GridView] with proper pagination.
	 *
	 * This function determines the grid layout dimensions by first calculating the number of columns
	 * that fit the available width, then computes the row count based on total items and column count.
	 * Handles partial rows by rounding up, ensuring all items are displayed. Essential for dynamic
	 * grid layouts and height calculations in scrollable containers.
	 *
	 * @param gridView The [GridView] for which to calculate the row count. Can be null,
	 *                 in which case 0 is returned to indicate no rows needed.
	 * @return The number of rows required to display all grid items, or 0 if the gridView is null,
	 *         has no adapter, or has zero columns. Partial rows count as full rows for layout purposes.
	 */
	@JvmStatic
	fun getNumberOfGridRows(gridView: GridView?): Int {
		if (gridView == null) return 0
		// Get total number of items from adapter, default to 0 if adapter is null
		val totalItems = gridView.adapter?.count ?: 0
		// Calculate how many columns fit in the current grid layout
		val numberOfColumns = getNumberOfGridColumns(gridView)
		return if (numberOfColumns > 0) {
			// Calculate base rows and add extra row if items don't fill columns evenly
			val rows = totalItems / numberOfColumns
			if (totalItems % numberOfColumns == 0) rows else rows + 1
		} else 0
	}

	/**
	 * Determines the number of columns that can fit within the [GridView]'s available width.
	 *
	 * This function calculates the optimal column count by dividing the device screen width
	 * by a predefined column width dimension. Uses a fixed column width (150dp) to maintain
	 * consistent visual appearance across different screen sizes and orientations.
	 *
	 * @param gridView The [GridView] for which to determine the column count. Can be null,
	 *                 in which case 1 is returned as a safe default for single-column layout.
	 * @return The number of columns that fit within the device width, or 1 if the gridView is null,
	 *         column width is invalid (≤0), or calculation fails. Minimum 1 column guaranteed.
	 */
	@JvmStatic
	fun getNumberOfGridColumns(gridView: GridView?): Int {
		if (gridView == null) return 1
		// Get device screen width for column calculation
		val deviceWidth = getDeviceWidth()
		// Retrieve fixed column width from dimension resources (150dp)
		val columnWidth = gridView.resources.getDimensionPixelSize(R.dimen._150)
		// Calculate columns by dividing available width by column width
		return if (columnWidth > 0) deviceWidth / columnWidth else 1
	}

	/**
	 * Sets the height of a [GridView] dynamically based on its content to eliminate internal scrolling.
	 *
	 * This function calculates the exact height needed to display all grid items without
	 * requiring scrollbars within the GridView itself. Essential for embedding grids within
	 * ScrollView containers or when fixed-height layouts are required. Updates layout parameters
	 * and triggers re-layout to apply the new height immediately.
	 *
	 * @param gridView The [GridView] whose height needs adjustment to accommodate all child items.
	 *                 Must not be null as height calculation requires valid grid view instance.
	 */
	@JvmStatic
	fun setGridViewHeightBasedOnChildren(gridView: GridView) {
		// Calculate how many rows are needed to display all items
		val rowCount = getNumberOfGridRows(gridView)
		// Retrieve fixed item height from dimension resources (135dp)
		val itemHeight = gridView.resources.getDimensionPixelSize(R.dimen._135)
		// Calculate total height by multiplying rows by item height
		val totalHeight = itemHeight * rowCount
		// Update layout parameters with calculated height
		val params = gridView.layoutParams
		params.height = totalHeight
		gridView.layoutParams = params
		// Request layout pass to apply new height parameters
		gridView.requestLayout()
	}

	/**
	 * Unbinds drawables from a [View] and its children to prevent memory leaks and free resources.
	 *
	 * This function recursively traverses the view hierarchy and systematically releases
	 * drawable references that could cause memory leaks. It handles background drawables,
	 * ImageView bitmaps, and ViewGroup children with special consideration for AdapterView
	 * types to avoid disrupting list adapters. Essential for calling in onDestroy() or
	 * when complex view hierarchies are no longer needed to ensure proper garbage collection.
	 *
	 * @param view The [View] or [ViewGroup] to recursively unbind drawables from. Can be null,
	 *             in which case the function exits silently without any operation.
	 */
	@JvmStatic
	fun unbindDrawables(view: View?) {
		try {
			// Remove callback from background drawable to prevent memory leaks
			view?.background?.callback = null

			if (view is ImageView) {
				// Clear image bitmap from ImageView to release large bitmap memory
				view.setImageBitmap(null)
			} else if (view is ViewGroup) {
				// Recursively process all child views in the ViewGroup hierarchy
				for (index in 0 until view.childCount)
					unbindDrawables(view.getChildAt(index))

				// Remove all child views except for AdapterView which manages its own children
				if (view !is AdapterView<*>) view.removeAllViews()
			}
		} catch (error: Exception) {
			// Log any errors during unbinding but don't crash the application
			logger.e("Error while unbinding drawables from views:", error)
		}
	}

	/**
	 * Sets a common [View.OnClickListener] on multiple [View]s within an [Activity].
	 *
	 * This function provides efficient batch click listener assignment for multiple views
	 * identified by their resource IDs, eliminating repetitive findViewById calls and
	 * listener assignments. Particularly useful for setting up consistent click behavior
	 * across related UI elements like button groups, navigation items, or form controls
	 * in activity layouts.
	 *
	 * @param clickListener The [View.OnClickListener] to assign to all specified views.
	 *                      Can be null to clear existing click listeners from the views,
	 *                      useful for resetting UI state or during cleanup operations.
	 * @param activity The [Activity] context containing the target views.
	 * @param ids A vararg of integer resource IDs ([IdRes]) identifying the views to receive
	 *            the click listener. Invalid or non-existent IDs are safely ignored without
	 *            affecting other view assignments in the batch operation.
	 */
	@JvmStatic
	fun setViewOnClickListener(
		clickListener: View.OnClickListener?,
		activity: Activity?, @IdRes vararg ids: Int
	) {
		for (id in ids) {
			// Safely find each view by ID and set/clear the click listener
			activity?.findViewById<View>(id).apply {
				this?.setOnClickListener(clickListener)
			}
		}
	}

	/**
	 * Sets a common [View.OnClickListener] on multiple [View]s within a parent [View] hierarchy.
	 *
	 * This function provides batch click listener assignment for multiple views within a
	 * specific layout or view hierarchy. More efficient than activity-level assignment when
	 * working with fragment layouts, custom views, or recycled list items. Handles null
	 * views gracefully and applies the same listener behavior across all specified view IDs.
	 *
	 * @param onClickListener The [View.OnClickListener] to assign to all specified views.
	 *                        Can be null to clear existing click listeners from the views.
	 * @param layout The parent [View] containing the target views to receive click listeners.
	 *               All view IDs are searched within this layout's hierarchy.
	 * @param ids A vararg of integer resource IDs ([IdRes]) identifying the views to receive
	 *            the click listener. Views not found in the layout are safely ignored.
	 */
	@JvmStatic
	fun setViewOnClickListener(
		onClickListener: View.OnClickListener?,
		layout: View, @IdRes vararg ids: Int
	) {
		for (id in ids) {
			// Find each view within the parent layout and set/clear the click listener
			layout.findViewById<View>(id).apply {
				this?.setOnClickListener(onClickListener)
			}
		}
	}

	/**
	 * Retrieves a specific [View] by its ID from a parent [ViewGroup] with null safety.
	 *
	 * This utility function provides a clean, concise way to find views within layout hierarchies
	 * while handling null parent layouts gracefully. Simplifies view lookup code and prevents
	 * NullPointerExceptions when working with dynamically inflated layouts or conditional UI.
	 *
	 * @param parentLayout The parent [ViewGroup] to search within for the target view.
	 *                     Can be null for safe handling in conditional layouts.
	 * @param id The integer resource ID ([IdRes]) of the view to locate and retrieve.
	 *           Must be a valid view ID from the layout XML or generated resources.
	 * @return The [View] with the specified ID if found, or null if the parentLayout is null
	 *         or no view exists with the given ID in the hierarchy.
	 */
	@JvmStatic
	fun getView(parentLayout: View?, @IdRes id: Int): View? {
		return parentLayout?.findViewById(id)
	}

	/**
	 * Starts an infinite clockwise rotation animation on the given [view] for loading indicators.
	 *
	 * This function applies a continuous rotating animation to create spinning loader effects,
	 * commonly used for progress indicators or loading states. The animation runs indefinitely
	 * until explicitly stopped.
	 *
	 * @param activity The [Activity] context used to load animation resources.
	 *
	 * @param view The [View] to animate with continuous clockwise rotation.
	 *             If null, no animation is started.
	 */
	@JvmStatic
	fun animateInfiniteRotation(activity: Activity?, view: View?) {
		if (activity == null) return // Early exit for null activity safely
		// Load clockwise rotation animation from XML resources
		val animResId = R.anim.anim_rotate_clockwise
		val animation = loadAnimation(activity, animResId)
		// Start infinite rotation animation on the target view
		view?.startAnimation(animation)
	}

	/**
	 * Converts Density-independent Pixels (DP) to device-specific Pixels (PX) for responsive layouts.
	 *
	 * This utility function calculates pixel values based on screen density, ensuring consistent
	 * visual sizing across different devices and screen resolutions. Essential for creating
	 * responsive UIs that maintain proportional dimensions regardless of display density.
	 *
	 * @param context The [Context] used to retrieve display density metrics.
	 *
	 * @param dp The value in Density-independent Pixels (DP) to convert to physical pixels.
	 *           Represents consistent visual size across different screen densities.
	 * @return The equivalent value in device-specific Pixels (PX) as integer, or -1 if
	 *         context is null or display metrics are unavailable for calculation.
	 */
	@JvmStatic
	fun dpToPx(context: Context?, dp: Float): Int {
		val metrics = context?.resources?.displayMetrics
		return if (metrics != null) {
			// Calculate pixels using density scaling factor (160dpi baseline)
			(dp * (metrics.densityDpi / 160f)).roundToInt()
		} else -1
	}

	/**
	 * Converts device-specific Pixels (PX) to Density-independent Pixels (DP) for density-aware design.
	 *
	 * This utility function converts physical pixel measurements back to density-independent
	 * units, useful for analyzing existing layouts or working with pixel-based design assets.
	 * Helps maintain consistent spacing and sizing across different screen densities.
	 *
	 * @param context The [Context] used to retrieve display density metrics.
	 *
	 * @param px The value in device-specific Pixels (PX) to convert to density-independent units.
	 *           Represents actual physical pixels on the device screen.
	 * @return The equivalent value in Density-independent Pixels (DP) as float, or -1.0f if
	 *         context is null or display metrics are unavailable for calculation.
	 */
	@JvmStatic
	fun pxToDp(context: Context?, px: Int): Float {
		val metrics = context?.resources?.displayMetrics
		return if (metrics != null) {
			// Calculate DP using density scaling factor (160dpi baseline)
			px / (metrics.densityDpi / 160f)
		} else -1.0f
	}

	/**
	 * Shows the on-screen keyboard and requests focus for the given [focusedView] for text input.
	 *
	 * This function programmatically triggers the soft keyboard to appear and assigns focus
	 * to the specified view, enabling immediate text input without user interaction.
	 * Essential for improving user experience in forms, search interfaces, and input-heavy
	 * applications.
	 *
	 * @param activity The [Activity] context used to access system input method services.
	 *
	 * @param focusedView The [View] that should receive focus and trigger keyboard display.
	 *             Typically an EditText or other input-capable view. If null,
	 *             keyboard cannot be shown as no target view is available.
	 */
	@JvmStatic
	fun showOnScreenKeyboard(activity: Activity?, focusedView: View?) {
		if (activity == null) return // Early exit for null activity safely

		// Retrieve the input method manager system service
		val inputService = activity.getSystemService(INPUT_METHOD_SERVICE)
		val inputMethodManager = inputService as InputMethodManager
		// Request focus on target view and show soft keyboard
		focusedView?.requestFocus()
		inputMethodManager.showSoftInput(focusedView, 0)
	}

	/**
	 * Hides the on-screen keyboard by dismissing the current input method session.
	 *
	 * This function uses the InputMethodManager to programmatically close the soft keyboard
	 * that is currently associated with the focused view's window token. Essential for
	 * improving user experience when keyboard dismissal is required after text input
	 * completion or during screen transitions.
	 *
	 * @param activity The [Activity] context used to access system input method services.
	 *
	 * @param focusedView The [View] that currently has input focus, providing the window token
	 *                    needed to identify which keyboard session to dismiss. If null,
	 *                    function cannot hide keyboard as token is unavailable.
	 */
	@JvmStatic
	fun hideOnScreenKeyboard(activity: Activity?, focusedView: View?) {
		if (activity == null) return // Early exit for null activity safely

		if (focusedView != null) {
			// Retrieve the input method manager system service
			val service = activity.getSystemService(INPUT_METHOD_SERVICE)
			val inputMethodManager = service as InputMethodManager
			// Dismiss keyboard using the focused view's window token
			inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
		}
	}

	@JvmStatic
	fun TextView.normalizeTallSymbols(
		reductionFactor: Float = 0.8f
	) {
		val fullText = this.text.toString()
		if (fullText.isEmpty()) return

		// Use SpannableStringBuilder to prepare for styling
		val spannable = SpannableStringBuilder(fullText)

		// Use BreakIterator to correctly identify the boundaries of visible characters (grapheme clusters)
		// The default locale is used as the breaking logic is mostly independent of language.
		val boundaryIterator = BreakIterator.getCharacterInstance(Locale.getDefault())
		boundaryIterator.setText(fullText)

		var currentStart = boundaryIterator.first()
		var currentEnd = boundaryIterator.next()

		// 1. Iterate through the text one grapheme cluster (visible character) at a time
		while (currentEnd != BreakIterator.DONE) {
			val cluster = fullText.substring(currentStart, currentEnd)

			// 2. Check if the cluster contains any non-Latin characters.
			// If the cluster has any character that is NOT Latin, we span the whole cluster.
			// We exclude spaces explicitly so they are not spanned if they appear within the cluster.
			// We use .any() to check if ANY code point in the cluster is non-Latin.
			val shouldReduce = cluster.any { char ->
				// FIX: Use char.code to get the Int Unicode code point, which is what UnicodeScript.of() expects.
				!Character.isSpaceChar(char) && !Character.UnicodeScript.of(char.code).isLatin()
			}

			if (shouldReduce) {
				// 3. Apply the span to the entire grapheme cluster
				try {
					spannable.setSpan(
						RelativeSizeSpan(reductionFactor),
						currentStart,
						currentEnd,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
					)
				} catch (e: Exception) {
					println("Error applying span to cluster: $e")
				}
			}

			currentStart = currentEnd
			currentEnd = boundaryIterator.next()
		}

		// 4. Set the newly generated text back to the TextView
		this.text = spannable
	}

	/**
	 * Helper extension function to check if a Unicode Script is Latin.
	 */
	private fun Character.UnicodeScript?.isLatin(): Boolean {
		return this == Character.UnicodeScript.LATIN
	}


	/**
	 * Tints the given [Drawable] with the application's primary brand color.
	 *
	 * This function applies color tinting to drawables using the primary color defined
	 * in the application's theme resources. Useful for creating consistent branded
	 * icons and graphics that match the app's color scheme. Uses DrawableCompat for
	 * backward compatibility across Android versions.
	 *
	 * @param targetDrawable The [Drawable] to apply primary color tinting to. If null,
	 *                       function exits silently without any modification.
	 */
	@JvmStatic
	fun tintDrawableWithPrimaryColor(targetDrawable: Drawable?) {
		if (targetDrawable == null) return
		// Retrieve primary color from application resources
		val tintColor = getColor(INSTANCE, R.color.color_primary)
		// Apply tint using DrawableCompat for version compatibility
		DrawableCompat.setTint(targetDrawable, tintColor)
	}

	/**
	 * Tints the given [Drawable] with the application's secondary accent color.
	 *
	 * This function applies color tinting to drawables using the secondary color defined
	 * in the application's theme resources. Ideal for creating visual hierarchy and
	 * accent elements that complement the primary color scheme. Uses DrawableCompat
	 * for consistent tinting behavior across different Android versions.
	 *
	 * @param targetDrawable The [Drawable] to apply secondary color tinting to. If null,
	 *                       function exits silently without any modification.
	 */
	@JvmStatic
	fun tintDrawableWithSecondaryColor(targetDrawable: Drawable?) {
		if (targetDrawable == null) return
		// Retrieve secondary color from application resources
		val tintColor = getColor(INSTANCE, R.color.color_secondary)
		// Apply tint using DrawableCompat for version compatibility
		DrawableCompat.setTint(targetDrawable, tintColor)
	}

	/**
	 * Tints the given [Drawable] with a custom color specified by resource ID.
	 *
	 * This function provides flexible color tinting for drawables using any color
	 * resource from the application's palette. Enables dynamic theming and state-based
	 * color changes for icons and graphics. Uses DrawableCompat for reliable tinting
	 * across all supported Android versions.
	 *
	 * @param targetDrawable The [Drawable] to apply custom color tinting to. If null,
	 *                       function exits silently without any modification.
	 * @param colorResId The resource ID of the color to use for tinting. Must be a
	 *                   valid color resource identifier from R.color namespace.
	 */
	@JvmStatic
	fun tintDrawableWithProvidedColor(targetDrawable: Drawable?, colorResId: Int) {
		if (targetDrawable == null) return
		// Retrieve specified color from application resources
		val tintColor = getColor(INSTANCE, colorResId)
		// Apply tint using DrawableCompat for version compatibility
		DrawableCompat.setTint(targetDrawable, tintColor)
	}

	/**
	 * Checks if the on-screen keyboard is currently visible by analyzing display frame dimensions.
	 *
	 * This function detects keyboard visibility by comparing the visible display frame height
	 * with the total screen height. When the keyboard appears, it reduces the available visible
	 * area, creating a measurable difference. Uses a threshold of 100 pixels to distinguish
	 * between keyboard presence and normal UI variations.
	 *
	 * @param activity The [Activity] context used to access window and view hierarchy.
	 *
	 * @return `true` if the on-screen keyboard is currently visible and occupying screen space,
	 *         `false` if keyboard is hidden, activity is null, or measurement fails.
	 */
	@JvmStatic
	fun isOnScreenKeyboardVisible(activity: Activity?): Boolean {
		if (activity == null) return false // Early exit for null activity safely

		// Get the root content view of the activity
		val rootView = activity.findViewById<View>(android.R.id.content)
		// Calculate the currently visible display frame
		val rect = Rect()
		rootView?.getWindowVisibleDisplayFrame(rect)
		// Get total screen height including off-screen areas
		val screenHeight = rootView?.rootView?.height
		// Calculate keyboard height as difference between total and visible height
		val keypadHeight = screenHeight?.minus(rect.bottom)
		// Return true if keyboard height exceeds threshold (typically >100px for most keyboards)
		return if (keypadHeight != null) keypadHeight > 100 else false
	}

	/**
	 * Sets the visibility of multiple [View]s to either [VISIBLE] or [GONE] with batch operation.
	 *
	 * This utility function provides efficient batch control over view visibility states,
	 * converting a boolean parameter to appropriate visibility flags. Useful for showing/hiding
	 * related UI elements simultaneously, such as form sections, loading states, or conditional
	 * layouts. Handles null views gracefully without affecting other views in the batch.
	 *
	 * @param isVisible `true` to set all views to [VISIBLE] state, `false` to set to [GONE] state.
	 *                  GONE completely removes views from layout, unlike INVISIBLE which preserves space.
	 * @param views A vararg of [View]s whose visibility needs synchronized updating.
	 *              Null views are safely ignored without interrupting batch processing.
	 */
	@JvmStatic
	fun setViewsVisibility(isVisible: Boolean, vararg views: View?) {
		// Convert boolean to appropriate Android visibility constant
		val visibility = if (isVisible) VISIBLE else GONE
		// Apply visibility to all non-null views in the batch
		for (view in views) view?.visibility = visibility
	}

	/**
	 * Sets the visibility of multiple [View]s to a specific visibility state with precise control.
	 *
	 * This function provides fine-grained batch visibility control allowing specification of
	 * exact visibility states (VISIBLE, GONE, or INVISIBLE). Useful for complex UI state
	 * management where different visibility behaviors are required. Handles null views safely
	 * and applies the same visibility state to all specified views.
	 *
	 * @param visibility The desired visibility state ([VISIBLE], [GONE], or [INVISIBLE]).
	 *                   VISIBLE shows view, GONE hides and removes from layout, INVISIBLE hides but preserves space.
	 * @param views A vararg of [View]s whose visibility needs synchronized updating.
	 *              Null views are safely ignored without affecting other views in the operation.
	 */
	@JvmStatic
	fun setViewsVisibility(visibility: Int, vararg views: View?) {
		// Apply specified visibility constant to all non-null views
		for (view in views) view?.visibility = visibility
	}

	/**
	 * Toggles the visibility of a [targetView] with optional fade animation in both directions.
	 *
	 * This function provides intelligent visibility toggling with smooth animated transitions
	 * when enabled. For showing, it uses fade-in from transparent to opaque. For hiding, it uses
	 * fade-out from opaque to transparent with proper visibility state management. Includes
	 * immediate toggle option without animation for performance-critical scenarios.
	 *
	 * @param targetView The [View] whose visibility needs to be toggled between VISIBLE and GONE.
	 * @param shouldAnimate If `true`, uses smooth fade animations for pleasant visual transitions.
	 *                      If `false`, toggles visibility immediately without animation.
	 * @param animTimeout The duration of fade animations in milliseconds (default: 300ms).
	 *                    Applies to both fade-in and fade-out transitions.
	 */
	@JvmStatic
	fun toggleViewVisibility(targetView: View, shouldAnimate: Boolean = false, animTimeout: Long = 300) {
		if (shouldAnimate) {
			if (targetView.isVisible) {
				// Animate fade-out and set GONE after animation completes
				targetView.animate().alpha(0f)
					.setDuration(animTimeout)
					.withEndAction { targetView.visibility = GONE }

			} else {
				// Set initial transparent state and animate fade-in to visible
				targetView.alpha = 0f
				targetView.visibility = VISIBLE
				targetView.animate().alpha(1f).setDuration(animTimeout)
			}
		} else {
			// Immediate toggle without animation for instant visibility change
			targetView.visibility = if (targetView.isVisible) GONE
			else VISIBLE
		}
	}

	/**
	 * Animates a view with a pop effect by scaling up slightly while fading out for dramatic disappearance.
	 *
	 * This function creates an attention-grabbing exit animation that combines scale enlargement
	 * with opacity reduction. The view briefly expands to 120% size while becoming transparent,
	 * creating a "pop" effect that draws attention before disappearance. Uses synchronized
	 * property animations for smooth, coordinated visual effects.
	 *
	 * @param targetView The [View] to animate with pop and fade-out effect. Can be null for safe handling.
	 * @param duration The duration of the complete animation sequence in milliseconds.
	 *                 Controls the speed of both scaling and fading transitions.
	 */
	@JvmStatic
	fun animatePopAndFadeOut(targetView: View?, duration: Long) {
		// Create horizontal scale animation from normal size to 120% enlarged
		val scaleUpX = ofFloat(targetView, "scaleX", 1f, 1.2f)
		// Create vertical scale animation from normal size to 120% enlarged
		val scaleUpY = ofFloat(targetView, "scaleY", 1f, 1.2f)
		// Create opacity animation from fully opaque to completely transparent
		val fadeOut = ofFloat(targetView, "alpha", 1f, 0f)

		// Combine all animations to run simultaneously
		val animatorSet = AnimatorSet()
		animatorSet.playTogether(scaleUpX, scaleUpY, fadeOut)
		animatorSet.duration = duration
		animatorSet.start()
	}

	/**
	 * Animates a view by fading it out and sliding it to the left for smooth horizontal exit.
	 *
	 * This function creates a combined animation that reduces opacity while translating the view
	 * leftward by its full width. The view becomes INVISIBLE after animation completion, making
	 * it suitable for swipe-to-dismiss effects or horizontal navigation transitions.
	 *
	 * @param targetView The [View] to animate with fade-out and left slide effect. If null,
	 *                   function returns immediately without animation.
	 * @param duration The duration of the combined animation in milliseconds (default: 300ms).
	 *                 Controls the speed of both fade and slide transitions.
	 */
	@JvmStatic
	fun fadeOutAndSlideLeft(targetView: View?, duration: Long = 300) {
		if (targetView == null) return  // Early exit for null safety

		// Create opacity animation from fully opaque to completely transparent
		val fadeOut = ofFloat(targetView, "alpha", 1f, 0f)
		// Create horizontal translation animation moving view leftward by its width
		val slideLeft = ofFloat(targetView, "translationX", 0f, -targetView.width.toFloat())

		// Combine both animations to run simultaneously
		val animatorSet = AnimatorSet()
		animatorSet.playTogether(fadeOut, slideLeft)
		animatorSet.duration = duration
		animatorSet.start()
		// Set view to INVISIBLE after animation completes
		animatorSet.addListener(onEnd = { targetView.visibility = INVISIBLE })
	}

	/**
	 * Animates a view by sliding it in from the left while fading in for elegant horizontal entrance.
	 *
	 * This function creates a combined animation that translates the view from left outside the
	 * screen to its final position while increasing opacity from transparent to opaque. The view
	 * is set to VISIBLE before animation starts. Ideal for slide-in menus, navigation drawers,
	 * or horizontal content transitions.
	 *
	 * @param targetView The [View] to animate with left slide-in and fade-in effect. If null,
	 *                   function returns immediately without animation.
	 * @param duration The duration of the combined animation in milliseconds (default: 300ms).
	 *                 Controls the speed of both slide and fade transitions.
	 */
	@JvmStatic
	fun slideInFromLeftAndFadeIn(targetView: View?, duration: Long = 300) {
		if (targetView == null) return  // Early exit for null safety

		// Set initial state: positioned left outside view area and completely transparent
		targetView.translationX = -targetView.width.toFloat()
		targetView.alpha = 0f
		targetView.visibility = VISIBLE  // Make visible before animation starts

		// Create horizontal translation animation from left to final position
		val slideIn = ofFloat(targetView, "translationX", -targetView.width.toFloat(), 0f)
		// Create opacity animation from transparent to fully opaque
		val fadeIn = ofFloat(targetView, "alpha", 0f, 1f)

		// Combine both animations to run simultaneously
		val animatorSet = AnimatorSet()
		animatorSet.playTogether(slideIn, fadeIn)
		animatorSet.duration = duration
		animatorSet.start()
	}

	/**
	 * Animates a view by simultaneously fading it out and sliding it upward for smooth disappearance.
	 *
	 * This function creates a combined animation that reduces opacity to transparent while
	 * translating the view upward by its own height. The view becomes INVISIBLE after animation
	 * completion, preserving layout space while hiding content. Uses AnimatorSet for synchronized
	 * property animations with precise timing control.
	 *
	 * @param targetView The [View] to animate with fade-out and slide-up effect. If null,
	 *                   function returns immediately without any animation.
	 * @param duration The duration of the combined animation in milliseconds (default: 300ms).
	 *                 Controls the speed of both fade and slide transitions simultaneously.
	 */
	@JvmStatic
	fun fadeOutAndSlideUp(targetView: View?, duration: Long = 300) {
		if (targetView == null) return  // Early exit for null safety

		// Create alpha animation from fully opaque to completely transparent
		val fadeOut = ofFloat(targetView, "alpha", 1f, 0f)
		// Create vertical translation animation moving view upward by its height
		val slideUp = ofFloat(targetView, "translationY", 0f, -targetView.height.toFloat())

		// Combine both animations to run simultaneously
		AnimatorSet().apply {
			playTogether(fadeOut, slideUp)
			this.duration = duration
			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					// Set view to INVISIBLE after animation to preserve layout space
					targetView.visibility = INVISIBLE
				}
			})
			start()
		}
	}

	/**
	 * Animates a view by simultaneously fading it in and sliding it downward for elegant appearance.
	 *
	 * This function creates a combined animation that increases opacity from transparent to opaque
	 * while translating the view downward from above its position to its final location. The view
	 * is set to VISIBLE before animation starts and uses synchronized property animations for
	 * smooth, coordinated entrance effects.
	 *
	 * @param targetView The [View] to animate with fade-in and slide-down effect. If null,
	 *                   function returns immediately without any animation.
	 * @param duration The duration of the combined animation in milliseconds (default: 300ms).
	 *                 Controls the speed of both fade and slide transitions simultaneously.
	 */
	@JvmStatic
	fun fadeInAndSlideDown(targetView: View?, duration: Long = 300) {
		if (targetView == null) return  // Early exit for null safety

		// Set initial state: positioned above view area and completely transparent
		targetView.translationY = -targetView.height.toFloat()
		targetView.alpha = 0f
		targetView.visibility = VISIBLE  // Make visible before animation starts

		// Create alpha animation from transparent to fully opaque
		val fadeIn = ofFloat(targetView, "alpha", 0f, 1f)
		// Create vertical translation animation moving view downward to final position
		val slideDown = ofFloat(targetView, "translationY", -targetView.height.toFloat(), 0f)

		// Combine both animations to run simultaneously
		AnimatorSet().apply {
			playTogether(fadeIn, slideDown)
			this.duration = duration
			start()
		}
	}

	/**
	 * Hides a [targetView] with optional fade-out animation and duplicate state prevention.
	 *
	 * This function efficiently manages view hiding with intelligent state checking to
	 * avoid redundant operations. When animation is enabled, it creates a smooth fade-out
	 * effect before setting visibility to GONE. Includes automatic early return if the
	 * view is already hidden to optimize performance and prevent unnecessary animations.
	 *
	 * @param targetView The [View] to hide with optional transition effect.
	 * @param shouldAnimate If `true`, applies smooth fade-out animation for pleasant visual transition.
	 *                      If `false`, immediately sets visibility to GONE without animation.
	 * @param animTimeout The duration of the fade-out animation in milliseconds (default: 500ms).
	 *                    Longer durations create more gradual, noticeable fade effects.
	 */
	@JvmStatic
	fun hideView(targetView: View?, shouldAnimate: Boolean = false, animTimeout: Long = 500) {
		//Early return if null target view is passed
		if (targetView == null) return

		// Early return if view is already hidden to prevent redundant operations
		if (!targetView.isVisible) return

		if (shouldAnimate) {
			// Animate to transparent state with specified duration
			targetView.animate().alpha(0f)
				.setDuration(animTimeout)
			// Set to GONE after animation starts (consider using withEndAction for better timing)
			targetView.visibility = GONE
		} else {
			// Immediate hiding without animation for instant disappearance
			targetView.visibility = GONE
		}
	}

	@JvmStatic
	fun hideView(targetView: View?, visibility: Int = GONE, shouldAnimate: Boolean = false, animTimeout: Long = 500) {
		//Early return if null target view is passed
		if (targetView == null) return

		// Early return if view is already hidden to prevent redundant operations
		if (!targetView.isVisible) return

		if (shouldAnimate) {
			// Animate to transparent state with specified duration
			targetView.animate().alpha(0f)
				.setDuration(animTimeout)
			// Set to GONE after animation starts (consider using withEndAction for better timing)
			targetView.visibility = visibility
		} else {
			// Immediate hiding without animation for instant disappearance
			targetView.visibility = visibility
		}
	}

	/**
	 * Shows a [targetView] with optional fade-in animation and duplicate visibility prevention.
	 *
	 * This function efficiently manages view visibility with intelligent state checking
	 * to avoid redundant operations. When animation is enabled, it creates a smooth
	 * fade-in effect from completely transparent to fully opaque. Includes automatic
	 * early return if the view is already visible to optimize performance.
	 *
	 * @param targetView The [View] to make visible with optional transition effect.
	 * @param shouldAnimate If `true`, applies smooth fade-in animation for pleasant visual transition.
	 *                      If `false`, immediately sets visibility without animation.
	 * @param animTimeout The duration of the fade-in animation in milliseconds (default: 500ms).
	 *                    Longer durations create more gradual, noticeable fade effects.
	 */
	@JvmStatic
	fun showView(targetView: View?, shouldAnimate: Boolean = false, animTimeout: Long = 500) {
		//Early return if null target view is passed
		if (targetView == null) return

		// Early return if view is already visible to prevent redundant operations
		if (targetView.isVisible) return

		if (shouldAnimate) {
			// Set initial transparent state and make view visible before animation
			targetView.alpha = 0f
			targetView.visibility = VISIBLE
			// Animate to fully opaque state with specified duration
			targetView.animate().alpha(1f).setDuration(animTimeout)
		} else {
			// Immediate visibility without animation for instant display
			targetView.visibility = VISIBLE
		}
	}

	/**
	 * Gets the height of the top display cutout (notch) for safe area calculation.
	 *
	 * This function retrieves the safe inset measurement from the device's display cutout
	 * to determine the vertical space occupied by notches, camera holes, or other display
	 * obstructions. Essential for creating notch-aware layouts that prevent content
	 * from being obscured by modern screen designs.
	 *
	 * @param activity The [Activity] context used to access window insets and display metrics.
	 *
	 * @return The height of the top cutout area in pixels, or 0 if no cutout exists,
	 *         activity is null, or device doesn't support display cutout API.
	 */
	@JvmStatic
	fun getTopCutoutHeight(activity: Activity?): Int {
		if (activity == null) return 0 // Early exit for null activity safely

		// Access window insets to retrieve display cutout information
		val windowInsets = activity.window?.decorView?.rootWindowInsets
		val displayCutout = windowInsets?.displayCutout
		// Return safe inset top which represents the cutout height
		return displayCutout?.safeInsetTop ?: 0
	}

	/**
	 * Sets the top margin of a [view] to accommodate display cutout (notch) safe area.
	 *
	 * This function automatically calculates the required top margin by measuring the
	 * device's display cutout height and applies it to the view's layout parameters.
	 * Ensures content appears below the cutout area for proper visibility and prevents
	 * content clipping or obstruction by notches and camera cutouts.
	 *
	 * @param view The [View] to adjust top margin for cutout accommodation. If null,
	 *             function exits silently without any layout modifications.
	 * @param activity The [Activity] context used to determine cutout dimensions.
	 */
	@JvmStatic
	fun setTopMarginWithCutout(view: View?, activity: Activity?) {
		if (view == null) return  // Early exit for null view safety
		if (activity == null) return // Early exit for null activity safely

		// Retrieve and modify the view's layout parameters
		val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
		// Calculate required top margin based on cutout height
		val topCutoutHeight = getTopCutoutHeight(activity)
		layoutParams.topMargin = topCutoutHeight
		// Apply modified layout parameters to the view
		view.layoutParams = layoutParams
	}

	/**
	 * Enables or disables multiple [View]s with comprehensive null safety.
	 *
	 * This utility function provides batch control over view interactivity states,
	 * efficiently handling null views to prevent NullPointerExceptions. Useful for
	 * form validation, loading states, or conditional UI control scenarios where
	 * multiple views need synchronized enabled/disabled states.
	 *
	 * @param enabled `true` to enable interactive state for all views, `false` to disable them.
	 *                Disabled views appear grayed out and don't respond to user input.
	 * @param views A vararg of [View]s to modify enabled state. Null views are safely ignored
	 *              without affecting other views in the batch operation.
	 */
	@JvmStatic
	fun setViewsEnabled(enabled: Boolean, vararg views: View?) {
		for (view in views) if (view != null) view.isEnabled = enabled
	}

	/**
	 * Sets the transparency (alpha) of multiple [View]s with batch operation efficiency.
	 *
	 * This function allows synchronized alpha adjustments across multiple views for
	 * consistent visual effects. Handles null views gracefully and applies the same
	 * transparency level to all specified views. Useful for creating disabled states,
	 * overlay effects, or gradual appearance transitions.
	 *
	 * @param alpha The alpha value to set (0.0f for fully transparent, 1.0f for fully opaque).
	 *              Values outside 0-1 range are automatically clamped by Android system.
	 * @param views A vararg of [View]s to apply transparency to. Null views are safely
	 *              skipped without affecting transparency of other views.
	 */
	@JvmStatic
	fun setViewsTransparency(alpha: Float, vararg views: View?) {
		for (view in views) if (view != null) view.alpha = alpha
	}

	/**
	 * Checks if a [targetView] is currently visible within its parent layout.
	 *
	 * This utility wrapper provides direct access to view visibility state with
	 * clear intent. Handles the combined check of VISIBLE visibility flag and
	 * non-zero dimensions to determine if view is actually visible to users.
	 *
	 * @param targetView The [View] to check for current visibility state.
	 * @return `true` if the view has VISIBLE visibility flag and non-zero dimensions,
	 *         `false` if view is GONE, INVISIBLE, or has zero size.
	 */
	@JvmStatic
	fun isViewVisible(targetView: View): Boolean = targetView.isVisible

	/**
	 * Recursively finds a [View] within a view hierarchy by its tag using depth-first search.
	 *
	 * This function traverses the entire view tree starting from the root view, checking
	 * each view's tag against the target value. Returns the first matching view found.
	 * Useful for locating specific views in complex layouts when view IDs are not available.
	 *
	 * @param rootView The root [View] of the hierarchy to search within. Can be null for safe handling.
	 * @param tag The tag object to search for in the view hierarchy. Uses standard equality comparison.
	 * @return The first [View] found with matching tag, or null if no match found or rootView is null.
	 */
	@JvmStatic
	fun findViewByTag(rootView: View?, tag: Any): View? {
		if (rootView == null) return null
		if (tag == rootView.tag) return rootView
		if (rootView is ViewGroup) {
			for (index in 0 until rootView.childCount) {
				val child = findViewByTag(rootView.getChildAt(index), tag)
				if (child != null) {
					return child
				}
			}
		}
		return null
	}

	/**
	 * Measures the intrinsic size (width and height) of a [view] without layout constraints.
	 *
	 * This function performs measurement with UNSPECIFIED spec to determine the view's
	 * natural dimensions based on its content and layout parameters. Useful for calculating
	 * required space before actual layout, or for custom layout implementations.
	 *
	 * @param view The [View] to measure for intrinsic width and height dimensions.
	 * @return An [IntArray] containing measured width at index 0 and height at index 1 in pixels,
	 *         or the view's current dimensions if measurement cannot be performed.
	 */
	@JvmStatic
	fun measureViewSize(view: View): IntArray {
		view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
		return intArrayOf(view.measuredWidth, view.measuredHeight)
	}

	/**
	 * Animates the visibility of a [targetView] with smooth fade-in or fade-out transition.
	 *
	 * This function uses ViewPropertyAnimator for hardware-accelerated alpha transitions
	 * with proper visibility state management. Automatically handles visibility flag changes
	 * at appropriate animation phases to ensure smooth visual transitions without flickering.
	 *
	 * @param targetView The [View] whose visibility needs animated transition.
	 * @param visibility The target visibility state ([VISIBLE], [GONE], or [INVISIBLE]).
	 *                   VISIBLE triggers fade-in, other values trigger fade-out.
	 * @param duration The duration of the alpha animation in milliseconds. Longer durations
	 *                 create more gradual, smoother fade transitions.
	 */
	@JvmStatic
	fun animateViewVisibility(targetView: View, visibility: Int, duration: Int) {
		val alpha = if (visibility == VISIBLE) 1f else 0f
		targetView
			.animate()
			.alpha(alpha)
			.setDuration(duration.toLong())
			.withStartAction { if (visibility == VISIBLE) targetView.visibility = VISIBLE }
			.withEndAction { if (visibility != VISIBLE) targetView.visibility = visibility }
	}

	/**
	 * Applies a horizontal shake animation to a [targetView] with configurable timing parameters.
	 *
	 * This function creates a realistic shake effect by rapidly translating the view along the X-axis
	 * in a decreasing amplitude pattern (10px → -10px → 5px → -5px → 0px). Uses ObjectAnimator
	 * for smooth property animation and Handler for precise timing control of the animation sequence.
	 * Automatically stops after the specified total duration to prevent infinite animation loops.
	 *
	 * @param targetView The [View] to apply the horizontal shake animation effect to.
	 * @param durationOfShake The duration of each individual shake movement in milliseconds.
	 *                        Controls the speed of each back-and-forth motion.
	 * @param durationOfAnim The total duration of the entire shake animation sequence in milliseconds.
	 *                       Defines how long the complete shaking effect will continue.
	 */
	@JvmStatic
	fun shakeAnimationOnView(targetView: View, durationOfShake: Long, durationOfAnim: Long) {
		// Create horizontal translation animation with decreasing amplitude for natural shake effect
		val shakeX = ofFloat(
			targetView, "translationX",
			0f, 10f, -10f, 10f, -10f, 5f, -5f, 0f
		)
		shakeX.duration = durationOfShake

		// Set up animator set to manage the shake animation sequence
		val animatorSet = AnimatorSet()
		animatorSet.play(shakeX)

		// Create timing control for the complete animation duration
		val handler = Handler(Looper.getMainLooper())
		val endTime = System.currentTimeMillis() + durationOfAnim

		/**
		 * Recursive function that starts shake animations until total duration is reached
		 */
		fun startShaking() {
			if (System.currentTimeMillis() < endTime) {
				animatorSet.start()
				handler.postDelayed({ startShaking() }, shakeX.duration)
			}
		}

		startShaking()
	}

	/**
	 * Animates a view with a continuous fade-in and fade-out pulsing effect.
	 *
	 * This function creates an infinite alpha animation that cycles between transparent and opaque,
	 * creating a subtle pulsing effect to draw attention to the view. Includes duplicate animation
	 * prevention by checking for existing running animations. Uses the legacy AlphaAnimation API
	 * for broad compatibility across Android versions.
	 *
	 * @param targetView The [View] to animate with fade pulsing effect. If null, function exits silently.
	 */
	@JvmStatic
	fun animateFadInOutAnim(targetView: View?) {
		if (targetView == null) return

		// Check if animation already running to prevent duplicate animations
		val current = targetView.animation
		if (current != null && !current.hasEnded()) {
			return   // already animating
		}

		// Create infinite alpha animation with reverse repeat mode for smooth pulsing
		val anim = AlphaAnimation(0f, 1f).apply {
			duration = 500
			repeatCount = Animation.INFINITE
			repeatMode = Animation.REVERSE
		}

		targetView.startAnimation(anim)
	}

	/**
	 * Stops any running animation on the given [view] and clears animation state.
	 *
	 * This function uses the legacy View.clearAnimation() method to immediately halt
	 * any active animations and reset the view's transformation state. For modern
	 * ViewPropertyAnimator or ObjectAnimator instances, consider also calling
	 * view.animate().cancel() for complete animation termination.
	 *
	 * @param view The [View] to stop all animations on and reset to default state.
	 */
	@JvmStatic
	fun closeAnyAnimation(view: View?) = view?.clearAnimation()

	/**
	 * Fades out a view with smooth alpha transition and optional completion callback.
	 *
	 * This function creates a fade-out animation that gradually reduces the view's opacity
	 * from fully visible to completely transparent. Uses the legacy AlphaAnimation API
	 * with fillAfter enabled to maintain the faded state after animation completion.
	 * Includes animation listener for precise callback timing when fade completes.
	 *
	 * @param view The [View] to fade out from fully visible to completely transparent.
	 * @param duration The duration of the fade-out animation in milliseconds (default: 300ms).
	 *                 Longer durations create slower, more gradual fade effects.
	 * @param onAnimationEnd Optional callback function executed when the fade animation
	 *                       completes and the view is fully transparent.
	 */
	@JvmStatic
	fun fadeOutView(
		view: View?, duration: Long = 300L,
		onAnimationEnd: (() -> Unit)? = null
	) {
		//Early return for null view provided
		if (view == null) return
		// Create alpha animation from fully opaque to fully transparent
		val fadeOut = AlphaAnimation(1.0f, 0.0f).apply {
			this.duration = duration
			fillAfter = true  // Maintain transparent state after animation
			setAnimationListener(object : Animation.AnimationListener {
				override fun onAnimationStart(animation: Animation?) {}
				override fun onAnimationEnd(animation: Animation?) {
					onAnimationEnd?.invoke()  // Execute callback when fade completes
				}

				override fun onAnimationRepeat(animation: Animation?) {}
			})
		}
		view.startAnimation(fadeOut)
	}

	/**
	 * Safely updates the text of a [TextView] inside an [Activity].
	 *
	 * This method checks for a valid Activity and TextView before applying the text,
	 * preventing crashes when the Activity is null, finishing, or the view is not found.
	 * No extra references are stored, so it avoids memory-related issues.
	 *
	 * @param activity The Activity that contains the target TextView. If null, the call is ignored.
	 * @param id The view ID of the TextView to update.
	 * @param text The text value to set on the TextView.
	 */
	@JvmStatic
	fun setTextViewText(activity: Activity?, @IdRes id: Int, text: String) {
		// Safely find TextView and update text only if activity and view exist
		activity?.findViewById<TextView>(id)?.text = text
	}

	/**
	 * Sets the drawable of an [ImageView] within an [Activity] with comprehensive safety checks.
	 *
	 * This method safely updates ImageView content while handling null activities,
	 * missing views, and null drawables. Prevents crashes when activities are destroyed
	 * or during configuration changes by using null-safe navigation.
	 *
	 * @param activity The [Activity] context containing the target ImageView.
	 *                 Null-safe handling prevents memory leaks from destroyed activities.
	 * @param id The resource ID of the [ImageView] to update with the new drawable.
	 * @param drawable The [Drawable] to display in the ImageView. Can be null to clear the image.
	 */
	@JvmStatic
	fun setImageViewDrawable(activity: Activity?, @IdRes id: Int, drawable: Drawable?) {
		// Safely update ImageView drawable with null handling for all parameters
		activity?.findViewById<ImageView>(id)?.setImageDrawable(drawable)
	}

	/**
	 * Gets the text content of a [TextView] within an [Activity] with safe fallback.
	 *
	 * This utility method retrieves TextView content while gracefully handling null
	 * activities, missing views, and null text values. Returns empty string as default
	 * to prevent null pointer exceptions in calling code.
	 *
	 * @param activity The [Activity] context where the TextView is located.
	 *                 Null-safe access prevents crashes from destroyed activities.
	 * @param id The resource ID of the [TextView] to retrieve text content from.
	 * @return The text content of the specified TextView as a [String], or empty string
	 *         if the Activity, TextView, or text content is null or unavailable.
	 */
	@JvmStatic
	fun getTextViewText(activity: Activity?, @IdRes id: Int): String? {
		// Safely retrieve text with null-to-string conversion for consistent return type
		return activity?.findViewById<TextView>(id)?.text.toString()
	}

	/**
	 * Gets the bitmap from an [ImageView] within an [Activity] with type safety.
	 *
	 * This method extracts the underlying bitmap from an ImageView's drawable while
	 * performing comprehensive type checking. Only returns bitmaps from BitmapDrawable
	 * instances, ensuring type safety and preventing class cast exceptions.
	 *
	 * @param activity The [Activity] context containing the target ImageView.
	 *                 Uses null safety to handle destroyed or unavailable activities.
	 * @param id The resource ID of the [ImageView] to extract the bitmap from.
	 * @return The [Bitmap] from the ImageView if the drawable is a BitmapDrawable,
	 *         or null if the Activity is null, ImageView not found, drawable is null,
	 *         or drawable is not a BitmapDrawable instance.
	 */
	@JvmStatic
	fun getImageViewBitmap(activity: Activity?, @IdRes id: Int): Bitmap? {
		// Safely find ImageView and extract bitmap only from BitmapDrawable instances
		val imageView = activity?.findViewById<ImageView>(id)
		if (imageView != null) {
			val drawable = imageView.drawable
			if (drawable is BitmapDrawable) return drawable.bitmap
		}
		return null
	}

	/**
	 * Gets the ID of a [View] with null safety and concise syntax.
	 *
	 * This utility function provides a clean way to access view IDs while handling
	 * null views gracefully. Useful for conditional view operations and ID-based
	 * view management where null safety is required.
	 *
	 * @param view The [View] to get the ID from. Can be null for safe handling.
	 * @return The ID of the [View] as nullable Int, or null if the view is null.
	 */
	@JvmStatic
	fun getId(view: View?): Int? = view?.id

	/**
	 * Loads a thumbnail image from a URL and sets it to an ImageView with intelligent orientation handling.
	 *
	 * This method performs network operations in background threads to prevent UI blocking.
	 * Automatically detects portrait-oriented images and rotates them 90 degrees for proper display.
	 * Includes comprehensive error handling with placeholder fallback and proper resource management.
	 * Uses Glide for efficient image loading and caching after initial bitmap processing.
	 *
	 * @param thumbnailUrl The URL of the thumbnail image to load and display.
	 * @param targetImageView The ImageView where the processed thumbnail will be displayed.
	 * @param placeHolderDrawableId Optional placeholder drawable resource ID to display
	 *                              if image loading fails or URL is inaccessible.
	 */
	@JvmStatic
	fun loadThumbnailFromUrl(
		thumbnailUrl: String,
		targetImageView: ImageView,
		placeHolderDrawableId: Int? = null
	) {
		// Execute the image loading in a background thread to prevent UI freezing
		ThreadsUtility.executeInBackground(codeBlock = {
			try {
				// Create a connection to the image URL with proper configuration
				val url = URL(thumbnailUrl)
				val connection = url.openConnection() as HttpURLConnection
				connection.doInput = true
				connection.connectTimeout = 5000
				connection.readTimeout = 5000
				connection.connect()

				// Get the input stream and decode the image into a bitmap
				val input: InputStream = connection.inputStream
				val bitmap = decodeStream(input) ?: return@executeInBackground

				// Check if the image is in portrait orientation (height > width)
				val isPortrait = bitmap.height > bitmap.width

				// Rotate the bitmap if it is portrait to ensure proper display orientation
				val rotatedBitmap = if (isPortrait) {
					rotateBitmap(bitmap, 90f)
				} else bitmap

				// Once the image is processed, update the UI on the main thread
				ThreadsUtility.executeOnMain {
					// Use Glide for efficient image loading and caching
					Glide.with(targetImageView.context)
						.load(rotatedBitmap).into(targetImageView)
				}
				input.close()
				connection.disconnect()
			} catch (error: Exception) {
				logger.e("Error while loading thumbnail from a remote url:", error)
				// Set placeholder image if provided, or leave it unchanged
				if (placeHolderDrawableId != null) {
					targetImageView.setImageResource(placeHolderDrawableId)
				}
			}
		})
	}

	/**
	 * Rotates a bitmap by a specified angle with proper memory management.
	 *
	 * This method applies matrix transformation to create a rotated version of the bitmap.
	 * It automatically recycles the original bitmap to free memory, making it suitable for
	 * processing large images or working in memory-constrained environments. The rotation
	 * is performed around the bitmap's center point.
	 *
	 * @param bitmap The original bitmap to rotate. Will be recycled after rotation.
	 * @param angle The angle in degrees to rotate the bitmap (positive for clockwise).
	 * @return A new bitmap instance containing the rotated image with identical dimensions
	 *         and configuration as the original.
	 */
	@JvmStatic
	fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
		// Create a matrix for rotation with the specified angle around center point
		val matrix = Matrix().apply { postRotate(angle) }

		// Create a rotated bitmap with the same configuration as the original
		val rotatedBitmap = Bitmap.createBitmap(
			bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
		)

		// Recycle the original bitmap to free up memory if it's no longer needed
		if (!bitmap.isRecycled) {
			bitmap.recycle()
		}

		return rotatedBitmap
	}

	/**
	 * Retrieves a thumbnail image for a given file, either from the file itself or a provided URL.
	 *
	 * The method handles different file types, including audio, image, APK, and video files.
	 * It uses the file's name to determine the type and attempts to extract a corresponding thumbnail:
	 * - For audio files, it attempts to extract the album art.
	 * - For image files, it scales the image to the required width.
	 * - For APK files, it attempts to extract the app's icon.
	 * - For video files, it attempts to retrieve a frame from the video file or uses a provided URL.
	 *
	 * If no thumbnail is available from these sources, the method returns null.
	 *
	 * @param targetFile The file for which the thumbnail is being requested.
	 * @param thumbnailUrl An optional URL to an external thumbnail to be used if available.
	 * @param requiredThumbWidth The required width for the thumbnail. The height will be adjusted
	 *                           to maintain the aspect ratio.
	 * @return A Bitmap representing the thumbnail image, or null if no thumbnail is found.
	 */
	@JvmStatic
	fun getThumbnailFromFile(
		targetFile: File,
		thumbnailUrl: String? = null,
		requiredThumbWidth: Int
	): Bitmap? {
		// Check if the file is audio and attempt to extract album art
		if (FileSystemUtility.isAudioByName(targetFile.name)) {
			extractAudioAlbumArt(targetFile)?.let { return it }
		}

		// Check if the file is an image and retrieve the bitmap, scaling it to the required width
		else if (FileSystemUtility.isImageByName(targetFile.name)) {
			getBitmapFromFile(imageFile = targetFile)?.let {
				return scaleBitmap(it, requiredThumbWidth)
			}
		}

		// If the file is an APK, retrieve its icon/thumbnail
		else if (targetFile.name.endsWith(".apk", true)) {
			var apkBitmap: Bitmap? = null
			getApkThumbnail(targetFile, onApkIconFound = { bmp -> apkBitmap = bmp; bmp })
			if (apkBitmap != null) return apkBitmap
		}

		// For video files or unknown types, attempt to extract a frame as the thumbnail
		val retriever = MediaMetadataRetriever()
		try {
			var originalBitmap: Bitmap? = null
			// Attempt to use the provided thumbnail URL
			if (!thumbnailUrl.isNullOrEmpty()) {
				originalBitmap = getBitmapFromThumbnailUrl(thumbnailUrl)
			}

			// If no URL, try extracting a frame from the video file itself
			if (originalBitmap == null) {
				retriever.setDataSource(targetFile.absolutePath)
				originalBitmap = retriever
					.getFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
					?: retriever.frameAtTime
			}

			// Scale the bitmap to the required width, maintaining the aspect ratio
			originalBitmap?.let {
				if (it.width <= 0) return null
				val aspectRatio = it.height.toFloat() / it.width
				val targetHeight = (requiredThumbWidth * aspectRatio).toInt()
				return it.scale(requiredThumbWidth, targetHeight, false)
			}
		} catch (error: Exception) {
			logger.e("Error while retrieving thumbnail from a file:", error)
		} finally {
			retriever.release()
		}

		return null  // Return null if no thumbnail could be found
	}

	/**
	 * Attempts to extract and display the application icon from an APK file with comprehensive fallback handling.
	 *
	 * This method uses the Android package manager to parse APK files and extract embedded application icons.
	 * It handles various edge cases including missing package info, corrupted APKs, and extraction failures.
	 * Provides multiple output options including direct ImageView updates and bitmap callbacks for flexibility.
	 * Includes proper scaling and padding configuration for optimal icon display in thumbnail contexts.
	 *
	 * @param apkFile The APK file object from which to extract the application icon.
	 *                Must exist and have .apk extension; falls back to default if invalid.
	 * @param imageViewHolder The ImageView to display the extracted icon in. Optional - can be null.
	 * @param defaultThumbDrawable Fallback drawable to use if icon extraction fails or APK is invalid.
	 * @param onApkIconFound Optional callback that receives the extracted bitmap for custom processing.
	 * @return True if the application icon was successfully extracted and applied, false otherwise.
	 */
	@JvmStatic
	fun getApkThumbnail(
		apkFile: File,
		imageViewHolder: ImageView? = null,
		defaultThumbDrawable: Drawable? = null,
		onApkIconFound: ((Bitmap) -> Bitmap)? = null
	): Boolean {
		// Validate file existence and extension before processing
		val apkExtension = ".apk".lowercase(Locale.ROOT)
		if (!apkFile.exists() || !apkFile.name.endsWith(apkExtension)) {
			// Apply fallback drawable and return failure for invalid APK files
			imageViewHolder?.apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
			return false
		}

		val packageManager = INSTANCE.packageManager
		return try {
			val apkPath = apkFile.absolutePath

			// Extract package information from APK file to access application metadata
			val packageInfo = packageManager.getPackageArchiveInfo(
				apkPath, PackageManager.GET_META_DATA
			)

			// Process application info if package was successfully parsed
			packageInfo?.applicationInfo?.let { appInfo ->
				// Set source directories to enable icon loading from APK context
				appInfo.sourceDir = apkPath
				appInfo.publicSourceDir = apkPath

				// Load the application icon using package manager
				val drawableIcon = appInfo.loadIcon(packageManager)

				// Apply the extracted icon to the ImageView with optimal display settings
				imageViewHolder?.setImageDrawable(drawableIcon)
				imageViewHolder?.scaleType = ImageView.ScaleType.CENTER_INSIDE
				imageViewHolder?.setPadding(0, 0, 0, 0)

				// Convert drawable to bitmap and invoke callback for additional processing
				val bitmap = drawableToBitmap(drawableIcon)
				if (bitmap != null) {
					onApkIconFound?.invoke(bitmap)
				}

				return true  // Successfully extracted and applied APK icon
			}

			// Fallback: No package info found, apply default drawable
			imageViewHolder?.setImageDrawable(defaultThumbDrawable)
			false  // Failed to extract icon from APK

		} catch (error: Exception) {
			// Log extraction error and apply graceful fallback with adjusted display settings
			logger.e("Error found while extracting app icon thumbnail from an apk file:", error)
			imageViewHolder?.apply {
				scaleType = ImageView.ScaleType.FIT_CENTER
				setPadding(0, 0, 0, 0)
				setImageDrawable(defaultThumbDrawable)
			}
			false  // Exception occurred during extraction process
		}
	}

	/**
	 * Converts a [Drawable] to a [Bitmap] with intelligent handling of different drawable types.
	 *
	 * This method efficiently handles BitmapDrawable instances by returning their existing bitmap
	 * without unnecessary conversion. For other drawable types, it creates a new bitmap using
	 * the drawable's intrinsic dimensions or provides safe defaults for dimension-less drawables.
	 * Includes comprehensive error handling to prevent crashes during drawable rendering and
	 * bitmap creation operations.
	 *
	 * @param drawable The [Drawable] to be converted into a bitmap representation. Can be any
	 *                 drawable type including VectorDrawable, ShapeDrawable, or BitmapDrawable.
	 * @return A [Bitmap] representation of the drawable, or null if conversion fails due to
	 *         invalid dimensions, rendering errors, or insufficient memory.
	 */
	@JvmStatic
	fun drawableToBitmap(drawable: Drawable): Bitmap? {
		// Check if the drawable is already a BitmapDrawable (no need to convert)
		if (drawable is BitmapDrawable) return drawable.bitmap

		// Set a default size if the drawable has no intrinsic size
		val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
		val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1

		return try {
			// Create a bitmap with the drawable's intrinsic size
			val bitmap = createBitmap(width, height)
			val canvas = Canvas(bitmap)

			// Set bounds and draw the drawable onto the canvas
			drawable.setBounds(0, 0, canvas.width, canvas.height)
			drawable.draw(canvas)

			// Return the created bitmap
			bitmap
		} catch (error: Exception) {
			logger.e("Error found while converting drawable to a bitmap:", error)
			null
		}
	}

	/**
	 * Scales the given [targetBitmap] to the specified width while maintaining aspect ratio.
	 *
	 * This method uses a memory-efficient scaling approach by disabling bitmap filtering
	 * to reduce computational overhead and memory usage. It calculates the target height
	 * automatically based on the original aspect ratio to prevent image distortion.
	 * Includes optimization to return the original bitmap unchanged when dimensions
	 * already match the required size, avoiding unnecessary bitmap recreation.
	 *
	 * @param targetBitmap The original Bitmap to be scaled to thumbnail dimensions.
	 * @param requiredThumbWidth The target width for the scaled thumbnail in pixels.
	 *                           Must be positive; returns original if invalid.
	 * @return A new scaled Bitmap with preserved aspect ratio, or the original bitmap
	 *         if scaling is not required or parameters are invalid.
	 */
	@JvmStatic
	fun scaleBitmap(targetBitmap: Bitmap, requiredThumbWidth: Int): Bitmap {
		if (requiredThumbWidth <= 0 || targetBitmap.width <= 0) return targetBitmap

		val aspectRatio = targetBitmap.height.toFloat() / targetBitmap.width
		val targetHeight = (requiredThumbWidth * aspectRatio).toInt()

		// Avoid unnecessary scaling if dimensions are same
		if (targetBitmap.width == requiredThumbWidth && targetBitmap.height == targetHeight) {
			return targetBitmap
		}

		// Use createScaledBitmap directly with "filter = false" to reduce memory overhead
		return targetBitmap.scale(requiredThumbWidth, targetHeight, false)
	}

	/**
	 * Extracts embedded album art from the specified audio file using MediaMetadataRetriever.
	 *
	 * This method efficiently retrieves album artwork embedded in audio files (MP3, FLAC, etc.)
	 * while minimizing memory usage through intelligent bitmap sampling. It first analyzes
	 * the embedded image dimensions without full decoding, then applies appropriate scaling
	 * to prevent loading excessively large images into memory. Ensures proper resource
	 * cleanup of the MediaMetadataRetriever to prevent memory leaks.
	 *
	 * @param audioFile The audio file object from which to extract embedded album artwork.
	 *                  Must exist and be accessible; returns null if file doesn't exist.
	 * @return A decoded Bitmap of the embedded album art scaled to reasonable dimensions,
	 *         or null if no artwork is present, file is inaccessible, or extraction fails.
	 */
	@JvmStatic
	fun extractAudioAlbumArt(audioFile: File): Bitmap? {
		if (!audioFile.exists()) return null

		val retriever = MediaMetadataRetriever()
		return try {
			retriever.setDataSource(audioFile.absolutePath)
			val embeddedPicture = retriever.embeddedPicture ?: return null

			// Use inJustDecodeBounds to avoid decoding large images unnecessarily
			val optionsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			decodeByteArray(embeddedPicture, 0, embeddedPicture.size, optionsBounds)

			// Define a reasonable max dimension for album art (e.g., 412x412)
			val maxSize = 412
			val scale = maxOf(1, maxOf(optionsBounds.outWidth, optionsBounds.outHeight) / maxSize)

			val decodeOptions = BitmapFactory.Options().apply {
				inSampleSize = scale
			}

			decodeByteArray(embeddedPicture, 0, embeddedPicture.size, decodeOptions)
		} catch (error: Exception) {
			logger.e("Error found while extracting audio album-art frm an audio file", error)
			null
		} finally {
			retriever.release()
		}
	}

	/**
	 * Downloads and decodes a Bitmap from a given image URL with comprehensive error handling.
	 *
	 * This function establishes a secure network connection, validates the HTTP response code
	 * and content type, then decodes the image stream into a Bitmap. It implements proper
	 * resource management by ensuring all streams and connections are closed in finally blocks,
	 * preventing memory leaks and resource exhaustion. Includes timeout protection and
	 * content validation to handle malformed responses gracefully.
	 *
	 * @param thumbnailUrl The URL string pointing to the remote thumbnail image resource.
	 *                     Returns null if the URL is null, empty, or inaccessible.
	 * @return The decoded Bitmap object if successful, null if network failure, invalid
	 *         response, or image decoding error occurs.
	 */
	@JvmStatic
	fun getBitmapFromThumbnailUrl(thumbnailUrl: String?): Bitmap? {
		if (thumbnailUrl.isNullOrEmpty()) return null

		var connection: HttpURLConnection? = null
		var inputStream: InputStream? = null

		return try {
			val url = URL(thumbnailUrl)
			connection = url.openConnection() as? HttpURLConnection
			connection?.apply {
				connectTimeout = 5000
				readTimeout = 5000
				doInput = true
				connect()
			}

			if (connection?.responseCode == HTTP_OK &&
				connection.contentType?.contains("image", true) == true) {
				inputStream = BufferedInputStream(connection.inputStream)
				decodeStream(inputStream)
			} else null
		} catch (error: Exception) {
			logger.e("Error found while getting thumbnail from a remote url:", error)
			null
		} finally {
			try {
				inputStream?.close()
			} catch (_: IOException) {
			}
			connection?.disconnect()
		}
	}

	/**
	 * Saves the given [Bitmap] to the app's internal storage with configurable format and quality.
	 *
	 * This method writes the bitmap to a private file within the application's internal storage
	 * directory, ensuring data isolation and security. It handles the complete file output
	 * stream lifecycle and provides compression control to balance image quality and storage
	 * efficiency. The resulting file path can be used to reference the saved image later.
	 *
	 * @param bitmapToSave The Bitmap image data to be persisted to internal storage.
	 * @param fileName The name of the file (without path) to save the bitmap as.
	 *                 Should include appropriate file extension matching the format.
	 * @param format The image compression format to use when encoding the bitmap.
	 *               Defaults to [Bitmap.CompressFormat.JPEG] for photographic content.
	 * @param quality The compression quality level ranging from 0 (lowest) to 100 (highest).
	 *                Defaults to 60 for reasonable quality with moderate file size.
	 * @return The absolute file system path to the saved image file, or null if saving
	 *         failed due to I/O errors or compression failures.
	 */
	@JvmStatic
	fun saveBitmapToFile(
		bitmapToSave: Bitmap,
		fileName: String,
		format: CompressFormat = CompressFormat.JPEG,
		quality: Int = 60
	): String? {
		return try {
			val modePrivate = Context.MODE_PRIVATE
			val appContext = INSTANCE
			appContext.openFileOutput(fileName, modePrivate).use { outputStream ->
				// Compress and write bitmap to output stream
				if (!bitmapToSave.compress(format, quality, outputStream)) return null
			}

			"${appContext.filesDir}/$fileName"
		} catch (error: Throwable) {
			logger.e("Error found while saving bitmap to a file:", error)
			null
		}
	}

	/**
	 * Loads a [Bitmap] from a given image [File] with existence validation and error handling.
	 *
	 * This method performs file system checks to ensure the target file exists and is readable
	 * before attempting bitmap decoding. It leverages Android's built-in bitmap decoding
	 * capabilities with automatic format detection for common image types (JPEG, PNG, WEBP).
	 * Provides graceful failure handling for corrupted, unreadable, or missing image files.
	 *
	 * @param imageFile The image file object from which the bitmap should be decoded and loaded.
	 *                  Must represent a valid, accessible image file in supported format.
	 * @return A decoded [Bitmap] object if successful, null if the file doesn't exist,
	 *         is unreadable, contains unsupported format, or decoding fails.
	 */
	@JvmStatic
	fun getBitmapFromFile(imageFile: File): Bitmap? {
		return try {
			if (imageFile.exists() && imageFile.isFile) {
				decodeFile(imageFile.absolutePath)
			} else {
				null
			}
		} catch (error: Exception) {
			logger.e("Error found while decoding bitmap from a image file:", error)
			null
		}
	}

	/**
	 * Checks whether a given image file is entirely black by analyzing its pixel content.
	 *
	 * This method employs a memory-efficient approach by first loading only the image bounds
	 * to calculate an optimal downscaling factor, then analyzing a scaled-down version of the
	 * image. This prevents excessive memory usage while maintaining accurate detection of
	 * completely black images. The algorithm iterates through all pixels and returns early
	 * upon finding any non-black pixel for optimal performance.
	 *
	 * @param targetImageFile The image file to be analyzed for complete blackness.
	 *                       Returns false if the file is null, doesn't exist, or cannot be decoded.
	 * @return True if every pixel in the image is completely black (RGB 0,0,0),
	 *         false if any non-black pixel is found or if image loading fails.
	 */
	@JvmStatic
	fun isBlackThumbnail(targetImageFile: File?): Boolean {
		if (targetImageFile == null || !targetImageFile.exists()) return false

		// Step 1: Decode image bounds only (no memory allocation for pixels)
		val options = BitmapFactory.Options().apply {
			inJustDecodeBounds = true
		}
		decodeFile(targetImageFile.absolutePath, options)

		// Step 2: Calculate downscale factor to reduce memory footprint
		val maxSize = 64
		val scale = maxOf(1, maxOf(options.outWidth, options.outHeight) / maxSize)

		// Step 3: Decode scaled-down bitmap for analysis
		val decodeOptions = BitmapFactory.Options().apply {
			inSampleSize = scale
		}

		val bitmap = decodeFile(targetImageFile.absolutePath, decodeOptions)
			?: return false

		// Step 4: Check if all pixels are black with early termination
		for (x in 0 until bitmap.width) {
			for (y in 0 until bitmap.height) {
				if (bitmap[x, y] != Color.BLACK) {
					bitmap.recycle() // Free memory immediately upon detection
					return false
				}
			}
		}

		bitmap.recycle() // Always recycle bitmap to avoid memory leaks
		return true
	}

	/**
	 * Applies a Gaussian blur effect to the given [Bitmap] using RenderScript for hardware acceleration.
	 *
	 * This implementation uses the deprecated [RenderScript] API, which remains the most efficient
	 * solution for devices below API 31 (Android 12). For Android 12 and above, consider migrating
	 * to [RenderEffect] with [Paint.setRenderEffect] for future compatibility. The blur radius is
	 * automatically clamped to the supported range to ensure stable operation across all devices.
	 *
	 * @param bitmap The input [Bitmap] to be processed with Gaussian blur effect.
	 * @param radius The blur radius controlling effect intensity, clamped between `0f` and `25f`.
	 *               Default is `20f` for moderate blur. Larger values increase blur strength but
	 *               also processing time. Values outside range are automatically constrained.
	 *
	 * @return A new blurred [Bitmap] with identical dimensions and configuration as the input,
	 *         preserving original image properties while applying the visual blur effect.
	 *
	 * @see RenderScript
	 * @see ScriptIntrinsicBlur
	 */
	@Suppress("DEPRECATION")
	fun blurBitmap(bitmap: Bitmap, radius: Float = 20f): Bitmap {
		val safeConfig = bitmap.config ?: Bitmap.Config.ARGB_8888
		val rs = RenderScript.create(INSTANCE)

		// Create input allocation from the bitmap for RenderScript processing
		val input = Allocation.createFromBitmap(rs, bitmap)

		// Prepare output allocation to receive blurred result
		val output = Allocation.createTyped(rs, input.type)

		// Initialize the intrinsic blur script with optimized parameters
		val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
		script.setRadius(radius.coerceIn(0f, 25f)) // Radius must be in [0..25]
		script.setInput(input)
		script.forEach(output)

		// Copy result into a new bitmap with original configuration
		val blurred = createBitmap(bitmap.width, bitmap.height, safeConfig)
		output.copyTo(blurred)

		// Clean up resources to prevent memory leaks in RenderScript context
		rs.destroy()

		return blurred
	}

	/**
	 * Sets a drawable on the left side of a [TextView] using a specified drawable resource ID.
	 *
	 * This extension method provides a convenient way to add left-aligned icons or indicators
	 * to text views without manual drawable management. It automatically handles bounds calculation
	 * and proper drawable positioning within the text view's compound drawables array.
	 *
	 * @receiver TextView on which the left-side drawable will be positioned and displayed.
	 * @param drawableResIdRes The resource ID of the drawable to be displayed on the left side.
	 *                         The drawable is automatically scaled to its intrinsic dimensions.
	 */
	@JvmStatic
	fun TextView.setLeftSideDrawable(drawableResIdRes: Int) {
		val drawable = getDrawable(INSTANCE, drawableResIdRes)
		drawable?.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
		this.setCompoundDrawables(drawable, null, null, null)
	}

	/**
	 * Matches the height of a [View] to the top display cutout (notch) area if present on the device.
	 *
	 * This extension function should be used when designing adaptive layouts that need to accommodate
	 * modern phones with notches, punch-hole cameras, or other display cutouts. It ensures proper
	 * spacing and prevents content from being obscured by the cutout area. The height matching
	 * is performed after the view's layout is complete to ensure accurate measurement.
	 */
	@JvmStatic
	fun View.matchHeightToTopCutout() {
		doOnLayout { updateCutoutHeight() }
	}

	/**
	 * Updates the height of a [View] to match the height of the top display cutout area if present.
	 *
	 * This method examines the window insets to detect display cutouts and automatically adjusts
	 * the view's layout parameters to align with the cutout dimensions. It handles multiple cutout
	 * scenarios and selects the appropriate bounding rectangle that starts from the top edge (y=0).
	 * Essential for creating notch-aware layouts that maintain visual consistency across devices.
	 */
	@JvmStatic
	fun View.updateCutoutHeight() {
		val rootWindowInsets = rootWindowInsets
		val cutout = rootWindowInsets?.displayCutout

		if (cutout != null) {
			val cutoutHeight = cutout.boundingRects
				.firstOrNull { it.top == 0 }
				?.height() ?: 0

			val params = layoutParams
			params.height = cutoutHeight
			layoutParams = params
		}
	}

	/**
	 * Sets the text color of a [TextView] using a color resource ID with type safety.
	 *
	 * This extension method simplifies text color assignment by handling resource resolution
	 * internally and providing a more Kotlin-idiomatic API compared to the native setTextColor
	 * method. It automatically retrieves the color from the application resources and applies it.
	 *
	 * @receiver TextView whose text color will be modified and updated.
	 * @param colorResId The color resource ID to be resolved and applied to the text.
	 */
	@JvmStatic
	fun TextView.setTextColorKT(colorResId: Int) {
		this.setTextColor(INSTANCE.getColor(colorResId))
	}

	/**
	 * Converts an integer value in density-independent pixels (dp) to physical pixels (px).
	 *
	 * This extension function provides a convenient way to convert dp values to pixel values
	 * using the system's display density metrics. Essential for creating responsive layouts
	 * that maintain consistent sizing across different screen densities and resolutions.
	 *
	 * @receiver The dp value to be converted to pixels for actual screen rendering.
	 * @return The corresponding pixel value as an integer, rounded from density calculation.
	 */
	@JvmStatic
	fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

	/**
	 * Changes the app's theme to dark or light mode based on persistent user preference settings.
	 *
	 * This method forcefully applies the selected theme regardless of system default settings,
	 * ensuring consistent visual experience across the application. It reads the theme preference
	 * from a dedicated settings file and applies the corresponding night mode configuration.
	 * Additionally updates the system navigation and status bars to match the chosen theme
	 * for complete visual harmony.
	 *
	 * @param activity The current activity where the theme should be applied and rendered.
	 */
	@JvmStatic
	fun changesSystemTheme(activity: BaseActivity) {
		val tempFile = File(INSTANCE.filesDir, AIO_SETTING_DARK_MODE_FILE_NAME)
		when (tempFile.exists()) {
			true -> {
				setDefaultNightMode(MODE_NIGHT_YES)
				activity.setDarkSystemBarTheme()
			}

			false -> {
				setDefaultNightMode(MODE_NIGHT_NO)
				activity.setLightSystemBarTheme()
			}
		}
	}

	/**
	 * Detects and returns the current screen orientation of the given Activity as a descriptive string.
	 *
	 * Analyzes the device's configuration to determine whether the user interface is currently
	 * displayed in landscape or portrait mode. This is essential for responsive layout adjustments,
	 * orientation-specific optimizations, and conditional UI behavior based on screen aspect ratio.
	 *
	 * @param activity The Activity instance whose current screen orientation is to be determined
	 *                 and analyzed for layout and behavior decisions.
	 * @return A string representing the current orientation state with possible values:
	 *         - "landscape" when the device width exceeds height (horizontal layout)
	 *         - "portrait" when the device height exceeds width (vertical layout)
	 *         - "undefined" when the orientation cannot be determined or is square aspect
	 */
	@JvmStatic
	fun getCurrentOrientation(activity: Activity): String {
		return when (activity.resources.configuration.orientation) {
			Configuration.ORIENTATION_LANDSCAPE -> "landscape"
			Configuration.ORIENTATION_PORTRAIT -> "portrait"
			else -> "undefined"
		}
	}

	/**
	 * Dynamically shrinks text content to fit within the available width of a TextView while
	 * preserving important endings. Intelligently trims text from the end, prioritizing removal of
	 * specified end patterns before general character removal. Handles measurement failures gracefully
	 * and ensures text remains readable with minimum length constraints.
	 *
	 * @param textView The TextView component whose text needs to be fitted within its bounds
	 * @param text The original text content that may exceed available display width
	 * @param endMatch The suffix pattern to prioritize for removal during text shrinking operations
	 */
	@JvmStatic
	fun shrinkTextToFitView(textView: TextView?, text: String, endMatch: String) {
		// Safely return if null conditions are matched
		if (textView == null) return

		// Calculate available width accounting for padding to get true display area
		val availableWidth = textView.width - textView.paddingStart - textView.paddingEnd
		logger.d("Fit text: \"$text\" endMatch=\"$endMatch\"")

		// If view width isn't available yet, retry after layout pass when dimensions are known
		if (availableWidth <= 0) {
			textView.doOnLayout { shrinkTextToFitView(textView, text, endMatch) }
			return
		}

		var newText = text
		// Create a copy of the TextView's paint for accurate text measurement without side effects
		val paint = Paint(textView.paint)

		try {
			// Only attempt to trim if the text ends with the specified pattern for intelligent shortening
			if (newText.endsWith(endMatch, ignoreCase = true)) {
				logger.d("Trimming text end \"$endMatch\" if needed")
				// Gradually remove characters until text fits or becomes too short for readability
				while (paint.measureText(newText) > availableWidth && newText.length > 4) {
					newText = if (newText.endsWith(endMatch, ignoreCase = true)) {
						// Preferentially remove the endMatch pattern first for semantic preservation
						newText.dropLast(endMatch.length)
					} else {
						// Fall back to removing single characters when pattern is exhausted
						newText.dropLast(1)
					}
				}
				logger.d("Trimmed text: \"$newText\"")
			}
			// Apply the potentially modified text to the TextView for display
			textView.text = newText
		} catch (error: Exception) {
			// Fall back to original text if measurement fails to prevent display issues
			logger.e("ShrinkText : Font measurement failed for text=$text", error)
			textView.text = text
		}
	}
}