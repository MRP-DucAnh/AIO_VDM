package lib.ui.builders

import android.graphics.drawable.Drawable
import android.view.Gravity.NO_GRAVITY
import android.view.LayoutInflater
import android.view.MotionEvent.ACTION_OUTSIDE
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.view.View.*
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.content.res.ResourcesCompat
import app.core.bases.interfaces.BaseActivityInf
import com.aio.R
import lib.process.LogHelperUtils
import lib.ui.builders.PopupBuilder.PopupPosition.LEFT
import lib.ui.builders.PopupBuilder.PopupPosition.RIGHT
import java.lang.ref.WeakReference

/**
 * Popup Builder
 *
 * A fluent builder utility class that simplifies the creation, configuration, and display
 * of [PopupWindow] instances anchored to specific views. This class handles all the
 * boilerplate code associated with popup windows while providing a clean, declarative API.
 *
 * ## Key Features:
 * - Simplified popup creation with either layout resource or custom view
 * - Automatic positioning relative to anchor view with smart screen boundary calculation
 * - Memory leak prevention through weak activity references
 * - Comprehensive touch handling (inside clicks, outside dismissal)
 * - Optional immersive mode for full-screen experiences
 * - Transparent background management for proper outside-click detection
 *
 * ## Architecture:
 * - Uses builder pattern for flexible popup configuration
 * - Implements weak references to prevent activity memory leaks
 * - Provides comprehensive error handling and logging
 * - Supports both layout inflation and custom view assignment
 *
 * ## Usage Examples:
 * ```kotlin
 * // Create popup from layout resource
 * val popupBuilder = PopupBuilder(
 *     activityInf = this,
 *     popupLayoutId = R.layout.custom_popup_menu,
 *     popupAnchorView = anchorButton
 * )
 * popupBuilder.show()
 *
 * // Create popup with custom view
 * val customView = layoutInflater.inflate(R.layout.custom_view, null)
 * val popupBuilder = PopupBuilder(
 *     activityInf = this,
 *     popupContentView = customView,
 *     popupAnchorView = anchorButton
 * )
 * popupBuilder.show(shouldHideStatusAndNavbar = true)
 * ```
 *
 * @param activityInf Reference to the activity interface for context and validation
 * @param popupLayoutId Resource ID of the layout to inflate (optional if popupContentView provided)
 * @param popupContentView Pre-constructed view to use as popup content (optional if popupLayoutId provided)
 * @param popupAnchorView The view to which the popup will be anchored on screen
 * @throws IllegalArgumentException if neither popupLayoutId nor popupContentView is provided
 * @see PopupWindow For the underlying Android popup implementation
 * @since Version 1.0.0
 */
class PopupBuilder(
	private val activityInf: BaseActivityInf?,
	private val popupLayoutId: Int = -1,
	private val popupContentView: View? = null,
	private val popupAnchorView: View
) {
	/**
	 * Logger instance for tracking popup lifecycle and debugging issues.
	 * Provides detailed logs for popup creation, display, and dismissal events.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference to the activity to prevent memory leaks.
	 *
	 * This is crucial for preventing Context-related memory leaks that commonly occur
	 * when popups hold strong references to their parent activities. The weak reference
	 * allows the activity to be garbage collected even if the popup is still referenced.
	 */
	private val weakReferenceOfActivityInf = WeakReference(activityInf?.getActivity())

	/**
	 * The PopupWindow instance that will be displayed on screen.
	 *
	 * This is the core Android component that handles the actual popup display
	 * and window management. It's initialized with the activity context and
	 * configured during the builder's setup phase.
	 */
	private val popupWindow = PopupWindow(weakReferenceOfActivityInf.get())

	/**
	 * The main content view displayed inside the popup.
	 *
	 * This view is either inflated from the provided layout resource or set directly
	 * from the popupContentView parameter. It's initialized during builder construction
	 * and becomes the contentView of the popupWindow.
	 */
	private lateinit var popupLayout: View

	/**
	 * Defines the preferred horizontal alignment of the popup relative to the screen or anchor.
	 *
	 * This enum value determines whether the positioning logic should favor the
	 * [PopupPosition.LEFT] or [PopupPosition.RIGHT] side of the display area.
	 *
	 * @see PopupPosition for available alignment options.
	 */
	private var popupPosition: PopupPosition = RIGHT

	/**
	 * Initializes the popup builder with the provided configuration.
	 *
	 * This initialization block performs three critical steps:
	 * 1. Sets up the popup content from either layout resource or custom view
	 * 2. Validates that valid content was provided
	 * 3. Configures the PopupWindow with appropriate properties
	 *
	 * If any step fails, the error is logged and rethrown to ensure the caller
	 * is aware of the failure condition.
	 *
	 * @throws IllegalArgumentException if no valid content source is provided
	 * @throws Exception for other initialization failures (logged and rethrown)
	 */
	init {
		try {
			initializePopupContent() // Inflate or assign the content view
			validateContentView()    // Ensure valid content is provided
			setupPopupWindow()       // Setup dimensions, background, interaction handlers
		} catch (error: Exception) {
			logger.e("Error found while initializing the Popup Builder:", error)
			throw error
		}
	}

	/**
	 * Displays the popup window at a calculated position relative to the anchor view.
	 *
	 * This method orchestrates the complete popup display workflow:
	 * 1. Checks if popup is already showing to prevent duplicates
	2. Optionally enables immersive mode to hide system UI
	3. Calculates optimal position relative to anchor view
	4. Displays the popup with proper positioning
	 *
	 * The popup position is automatically calculated to stay within screen boundaries,
	 * with a margin from the screen edge defined by R.dimen._10.
	 *
	 * @param shouldHideStatusAndNavbar If true, enables immersive mode to hide status and navigation bars
	 * @see enableImmersiveMode For immersive mode implementation
	 * @see showPopupWindow For positioning logic
	 */
	fun show(shouldHideStatusAndNavbar: Boolean = false) {
		try {
			if (popupWindow.isShowing) return
			if (shouldHideStatusAndNavbar) enableImmersiveMode()
			showPopupWindow()
		} catch (error: Exception) {
			logger.e("Error found while showing popup-view:", error)
		}
	}

	/**
	 * Closes the popup if it's currently showing and the activity is valid.
	 *
	 * This method ensures safe popup dismissal by:
	 * 1. Validating that the activity is still in a valid state
	 * 2. Checking if the popup is currently showing
	 * 3. Dismissing the popup only if both conditions are met
	 *
	 * The activity validation prevents crashes when trying to dismiss a popup
	 * after the parent activity has been destroyed or finished.
	 */
	fun close() {
		try {
			val activity = weakReferenceOfActivityInf.get() ?: return
			if (activity.isValidForWindowManagement() && popupWindow.isShowing) {
				popupWindow.dismiss()
			}
		} catch (error: Exception) {
			logger.e("Error found while closing popup-view:", error)
		}
	}

	/**
	 * Returns the content view displayed inside the popup.
	 *
	 * This view can be used to modify popup content after creation or to
	 * find specific child views within the popup layout.
	 *
	 * @return The View object that serves as the popup's content
	 */
	fun getPopupView(): View = popupWindow.contentView

	/**
	 * Returns the underlying PopupWindow instance.
	 *
	 * Provides direct access to the PopupWindow for advanced configuration
	 * that might not be covered by the builder's API.
	 *
	 * @return The configured PopupWindow instance
	 */
	fun getPopupWindow(): PopupWindow = popupWindow

	/**
	 * Initializes the popup content from the provided source.
	 *
	 * This method handles two content sources:
	 * 1. Layout resource ID: Inflates the layout using the activity's LayoutInflater
	 * 2. Custom view: Directly assigns the provided view
	 *
	 * The method does nothing if both sources are invalid - validation occurs
	 * in validateContentView().
	 */
	private fun initializePopupContent() {
		when {
			popupLayoutId != -1 -> {
				val inflater = LayoutInflater.from(weakReferenceOfActivityInf.get())
				popupLayout = inflater.inflate(popupLayoutId, null, false)
			}

			popupContentView != null -> popupLayout = popupContentView
		}
	}

	/**
	 * Validates that popup content was properly initialized.
	 *
	 * This method checks if popupLayout was assigned during initialization.
	 * If not, it throws an IllegalArgumentException indicating that a valid
	 * content source must be provided.
	 *
	 * @throws IllegalArgumentException if popupLayout was not initialized
	 */
	private fun validateContentView() {
		if (!::popupLayout.isInitialized) {
			throw IllegalArgumentException(
				"Must provide valid content via popupLayoutId or popupContentView"
			)
		}
	}

	/**
	 * Sets the preferred horizontal alignment position for the popup.
	 *
	 * This configuration determines whether the popup should be positioned relative
	 * to the [PopupPosition.LEFT] or [PopupPosition.RIGHT] side of the anchor or screen.
	 * The default value is [PopupPosition.RIGHT].
	 *
	 * @param position The desired [PopupPosition] alignment.
	 * @return This [PopupBuilder] instance for method chaining.
	 */
	fun setPosition(position: PopupPosition): PopupBuilder {
		this.popupPosition = position
		return this
	}

	/**
	 * Configures the PopupWindow with all required properties.
	 *
	 * This comprehensive setup method configures:
	 * - Basic properties (touchable, focusable, outside touchable)
	 * - Background drawable for proper outside-click detection
	 * - Touch handling behavior for inside/outside interactions
	 * - Width and height as WRAP_CONTENT
	 * - Content view assignment
	 *
	 * Each configuration step is logged for debugging purposes.
	 */
	private fun setupPopupWindow() {
		logger.d("Setting up popup window properties")

		popupWindow.apply {
			isTouchable = true
			isFocusable = true
			isOutsideTouchable = true

			logger.d("Applying transparent background to popup window")
			setBackgroundDrawable(createTransparentBackground())

			logger.d("Configuring touch handling for popup window")
			configureTouchHandling()

			logger.d("Setting popup window width and height to WRAP_CONTENT")
			width = WindowManager.LayoutParams.WRAP_CONTENT
			height = WindowManager.LayoutParams.WRAP_CONTENT

			logger.d("Assigning content view to popup window")
			contentView = popupLayout
		}

		logger.d("Popup window setup complete")
	}

	/**
	 * Creates a transparent background drawable for the popup window.
	 *
	 * A transparent background is essential for proper outside-click detection.
	 * Without it, touches on areas outside the popup might not be recognized
	 * as outside events.
	 *
	 * @return A transparent Drawable, or null if the activity context is unavailable
	 */
	private fun createTransparentBackground(): Drawable? {
		logger.d("Creating transparent background for popup window")
		return weakReferenceOfActivityInf.get()?.let { ctx ->
			ResourcesCompat.getDrawable(
				ctx.resources,
				R.drawable.bg_image_transparent,
				ctx.theme
			)
		}
	}

	/**
	 * Configures custom touch handling for the popup window.
	 *
	 * This method sets up a touch interceptor that provides two behaviors:
	 * 1. ACTION_UP: Triggers click action when user lifts finger inside popup
	 * 2. ACTION_OUTSIDE: Dismisses popup when user touches outside its bounds
	 *
	 * The interceptor returns appropriate boolean values to indicate whether
	 * the touch event was consumed.
	 */
	private fun configureTouchHandling() {
		logger.d("Configuring touch behavior for popup window")
		popupWindow.setTouchInterceptor { view, event ->
			when (event.action) {
				ACTION_UP -> view.performClick().let { false }
				ACTION_OUTSIDE -> popupWindow.dismiss().let { true }
				else -> false
			}
		}
	}

	/**
	 * Enables immersive mode to hide system status and navigation bars.
	 *
	 * Uses deprecated system flags for backward compatibility with older Android versions.
	 * The combination of flags provides:
	 * - SYSTEM_UI_FLAG_FULLSCREEN: Hides status bar
	 * - SYSTEM_UI_FLAG_HIDE_NAVIGATION: Hides navigation bar
	 * - SYSTEM_UI_FLAG_IMMERSIVE_STICKY: Makes bars reappear temporarily on edge swipes
	 *
	 * @suppress DEPRECATION warning as this approach maintains compatibility
	 */
	@Suppress("DEPRECATION")
	private fun enableImmersiveMode() {
		logger.d("Enabling immersive mode for popup window")
		val s1 = SYSTEM_UI_FLAG_FULLSCREEN
		val s2 = SYSTEM_UI_FLAG_HIDE_NAVIGATION
		val s3 = SYSTEM_UI_FLAG_IMMERSIVE_STICKY
		popupWindow.contentView.systemUiVisibility = (s1 or s2 or s3)
	}

	/**
	 * Positions and displays the popup window relative to the anchor view.
	 *
	 * This method implements intelligent positioning:
	 * 1. Gets the anchor view's screen coordinates
	 * 2. Measures the popup content to determine its dimensions
	3. Calculates X offset to align the popup with the right edge of the screen
	4. Maintains a margin from the screen edge (R.dimen._10)
	5. Displays the popup at the calculated position
	 *
	 * The positioning ensures the popup doesn't go off-screen and maintains
	 * consistent spacing from the screen edges.
	 */
	private fun showPopupWindow() {
		logger.d("Measuring and positioning popup window on screen")
		val anchorLocation = IntArray(2)
		popupAnchorView.getLocationOnScreen(anchorLocation)

		val anchorX = anchorLocation[0]
		val anchorY = anchorLocation[1]

		val margin = popupLayout.resources.getDimensionPixelSize(R.dimen._10)

		popupLayout.measure(UNSPECIFIED, UNSPECIFIED)
		val popupWidth = popupLayout.measuredWidth

		val xOffset = when (popupPosition) {
			RIGHT -> {
				val screenWidth = popupLayout.resources.displayMetrics.widthPixels
				screenWidth - popupWidth - margin
			}

			LEFT -> {
				(anchorX - popupWidth - margin).coerceAtLeast(margin)
			}
		}

		popupWindow.showAtLocation(popupAnchorView, NO_GRAVITY, xOffset, anchorY)
		logger.d("Popup window shown at X=$xOffset, Y=$anchorY")
	}

	/**
	 * Extension function to check if an activity is valid for window operations.
	 *
	 * This validation ensures that the activity:
	 * 1. Is not null
	 * 2. Is not in the process of finishing
	 * 3. Is not destroyed
	 *
	 * These checks prevent common crashes when attempting window operations
	 * on invalid activity states.
	 *
	 * @return True if the activity is safe for window management operations
	 */
	private fun BaseActivityInf?.isValidForWindowManagement(): Boolean {
		val valid = this?.getActivity()?.let { activity ->
			!activity.isFinishing && !activity.isDestroyed
		} ?: false
		logger.d("Activity valid for window management: $valid")
		return valid
	}

	/**
	 * Defines the horizontal alignment options for the popup relative to its anchor view.
	 *
	 * Use [LEFT] to align the popup towards the start/left side of the anchor,
	 * and [RIGHT] to align it towards the end/right side or the screen edge.
	 */
	enum class PopupPosition { LEFT, RIGHT }
}