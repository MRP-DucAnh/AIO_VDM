@file:Suppress("DEPRECATION")

package app.core.bases

import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
import android.view.MotionEvent
import android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
import android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
import android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioAdblocker
import app.core.AIOApp.Companion.aioLanguage
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.downloadSystem
import app.core.CrashHandler
import app.core.bases.dialogs.UpdaterDialog
import app.core.bases.interfaces.BaseActivityInf
import app.core.bases.interfaces.PermissionsResult
import app.core.bases.language.LanguageAwareActivity
import app.core.engines.backend.AIOSelfDestruct.shouldSelfDestructApplication
import app.core.engines.services.AIOForegroundService
import app.core.engines.updater.AIOUpdater
import app.ui.main.MotherActivity
import app.ui.others.startup.OpeningActivity
import com.aio.R
import com.anggrayudi.storage.SimpleStorageHelper
import com.permissionx.guolindev.PermissionX
import com.permissionx.guolindev.PermissionX.isGranted
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import lib.files.FileSystemUtility.getFileExtension
import lib.files.FileSystemUtility.getFileSha256
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.process.CommonTimeUtils.delay
import lib.process.LogHelperUtils
import lib.process.ThreadsUtility
import lib.ui.ActivityAnimator.animActivityFade
import lib.ui.ActivityAnimator.animActivitySwipeRight
import lib.ui.MsgDialogUtils
import lib.ui.MsgDialogUtils.showMessageDialog
import lib.ui.ViewUtility
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.builders.ToastView.Companion.showToast
import java.io.File
import java.io.IOException
import java.lang.Thread.setDefaultUncaughtExceptionHandler
import java.lang.ref.WeakReference
import java.util.TimeZone
import kotlin.system.exitProcess

/**
 * Base activity class that provides common functionality and infrastructure for all activities in the application.
 *
 * This abstract class serves as the foundation for all activities in the app, implementing
 * common patterns and functionality to reduce code duplication and ensure consistent behavior
 * across the application. It extends LanguageAwareActivity for localization support and
 * implements BaseActivityInf interface to provide standardized activity operations.
 *
 * Core features and responsibilities:
 * - Comprehensive lifecycle management with proper resource cleanup
 * - Runtime permission handling with user-friendly dialogs and fallbacks
 * - Smooth activity transitions and animations for better user experience
 * - System UI customization (status bar, navigation bar theming)
 * - Haptic feedback integration through vibration services
 * - Global crash handling and exception management
 * - Multi-language support with dynamic language switching
 * - Advertisement integration and management
 * - Storage management with scoped storage compatibility
 * - Memory leak prevention through weak reference patterns
 * - Back press handling with double-tap confirmation
 *
 * Subclasses should override the template methods to provide specific functionality
 * while inheriting the common infrastructure and behavior.
 *
 * @see LanguageAwareActivity For localization and language switching capabilities
 * @see BaseActivityInf For the interface defining common activity operations
 */
abstract class BaseActivity : LanguageAwareActivity(), BaseActivityInf {

	/**
	 * Logger instance for debugging, tracing lifecycle events, and monitoring application behavior.
	 *
	 * This logger provides structured logging throughout the activity lifecycle, helping with
	 * debugging, performance monitoring, and issue diagnosis. It automatically uses the
	 * concrete activity class name for clear log identification.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference to the activity instance for safe context access in callbacks and background operations.
	 *
	 * Using weak references prevents memory leaks that can occur when activities are referenced
	 * from long-lived objects like background threads, handlers, or static fields. The weak
	 * reference allows the activity to be garbage collected when it's no longer needed, while
	 * still providing access when the activity is alive.
	 *
	 * @see WeakReference For the Java weak reference mechanism used
	 */
	private var weakReferenceOfActivity: WeakReference<BaseActivity>? = null

	/**
	 * Flag to track whether a user permission check is currently in progress.
	 *
	 * This prevents duplicate permission requests from being shown to the user simultaneously,
	 * which can cause confusion and poor user experience. The flag is set when a permission
	 * request is initiated and cleared when the request completes or times out.
	 *
	 * When true: New permission requests should be skipped or queued
	 * When false: New permission requests can be processed normally
	 */
	private var isUserPermissionCheckingActive = false

	/**
	 * Flag indicating whether the activity is currently running and visible to the user.
	 *
	 * This flag tracks the activity's visibility state and is updated in lifecycle methods:
	 * - Set to true in onStart() and onResume()
	 * - Set to false in onPause() and onStop()
	 *
	 * Use this flag to prevent UI operations when the activity is not in the foreground,
	 * such as showing toasts, updating views, or starting animations that would be wasted
	 * when the user can't see them.
	 */
	private var isActivityRunning = false

	/**
	 * Counter for handling double-back-press behavior and similar multi-tap interactions.
	 *
	 * This implements common UX patterns where users must press back twice within a short
	 * timeframe to confirm actions like exiting the app. The counter tracks the state:
	 * - 0: No back press recorded (initial state)
	 * - 1: First back press recorded, waiting for confirmation
	 * - Reset to 0 after timeout or successful second press
	 *
	 * This pattern prevents accidental exits and provides clear user confirmation for
	 * important navigation actions.
	 */
	private var isBackButtonEventFired = 0

	/**
	 * Helper for managing scoped storage permissions and file operations on modern Android versions.
	 *
	 * This component abstracts the complexity of scoped storage introduced in Android 10 (API 29),
	 * providing a simplified API for file access while handling permission requests and
	 * storage framework compatibility. It ensures the app works correctly across all Android
	 * versions while following modern storage best practices.
	 *
	 * @see SimpleStorageHelper For the specific implementation details
	 */
	open var scopedStorageHelper: SimpleStorageHelper? = null

	/**
	 * Listener interface for receiving permission check results and propagating them to subclasses.
	 *
	 * This allows subclasses to be notified when permission requests complete, enabling them
	 * to update their UI state, enable/disable features, or handle permission denials appropriately.
	 * The listener pattern decouples the permission handling logic from specific activity implementations.
	 *
	 * Usage:
	 * ```kotlin
	 * permissionCheckListener = object : PermissionsResult {
	 *     override fun onPermissionResultFound(isGranted: Boolean,
	 *     grantedList: List<String>, deniedList: List<String>) {
	 *         // Handle permission results
	 *     }
	 * }
	 * ```
	 *
	 * @see PermissionsResult For the callback interface definition
	 */
	open var permissionCheckListener: PermissionsResult? = null

	/**
	 * Vibrator instance for providing haptic feedback to users.
	 *
	 * This property uses lazy initialization to optimize resource usage by only creating
	 * the Vibrator instance when it's actually needed. The implementation automatically
	 * selects the appropriate Vibrator service based on the Android version:
	 * - Android 12 (S) and above: Uses VibratorManager for enhanced vibration control
	 * - Android 11 and below: Uses legacy Vibrator service for backward compatibility
	 *
	 * The lazy initialization ensures that vibration resources are only allocated
	 * when haptic feedback is actually used in the application, reducing memory
	 * footprint for users who don't interact with vibration-enabled features.
	 */
	private val vibrator: Vibrator? by lazy {
		logger.d("Initializing Vibrator instance with lazy loading")
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			logger.d("Using VibratorManager for Android 12+ with enhanced vibration capabilities")
			val vmClass = VibratorManager::class.java
			val vibratorManager = getSystemService(vmClass)
			vibratorManager.defaultVibrator
		} else {
			logger.d("Using legacy Vibrator service for Android 11 and below")
			getSystemService(Vibrator::class.java)
		}
	}

	/**
	 * Called when the activity becomes visible to the user and is about to start interacting.
	 *
	 * This method marks the activity as running, indicating that it's now in the foreground
	 * and ready to handle user interactions. It's called after onCreate() and before onResume(),
	 * making it the appropriate place to initialize components that should be visible to users
	 * but don't require the activity to be in the foreground.
	 *
	 * Unlike onResume(), this method is called when the activity is first created and also
	 * when returning from a background state, making it reliable for basic visibility tracking.
	 */
	override fun onStart() {
		super.onStart()
		isActivityRunning = true
		logger.d("onStart() called — activity is now visible and marked as running")
	}

	/**
	 * Initializes the activity during creation phase, setting up core components and UI foundation.
	 *
	 * This method performs the essential initialization required for the activity to function
	 * properly. It establishes activity references, configures system UI, sets up error handling,
	 * and prepares the layout infrastructure. The method follows a specific initialization sequence
	 * to ensure dependencies are available when needed.
	 *
	 * Initialization sequence:
	 * 1. Establish weak activity references for memory-safe context access
	 * 2. Set up global crash handler for uncaught exceptions
	 * 3. Configure theme and system UI appearance
	 * 4. Initialize storage helpers for file access
	 * 5. Apply language localization settings
	 * 6. Configure device orientation and navigation
	 * 7. Inflate activity layout from subclass specification
	 *
	 * @param savedInstanceState Previously saved activity state, or null for fresh creation.
	 *        Used to restore activity state after configuration changes or process death.
	 */
	@SuppressLint("SourceLockedOrientationActivity")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		logger.d("onCreate() called — performing core activity initialization")

		// Initialize weak reference to prevent memory leaks when activity is destroyed
		// Weak references allow garbage collection while providing temporary access
		logger.d("Activity references initialized with weak reference strategy")
		weakReferenceOfActivity = WeakReference(this)

		getActivity()?.let { activity ->
			logger.d("Safe activity reference acquired — proceeding with full initialization")

			// Set global crash handler to capture uncaught exceptions and prevent app crashes
			// This provides better error reporting and user experience during failures
			logger.d("Setting default uncaught exception handler for crash prevention")
			val weakReferenceOfCrashHandler = WeakReference(CrashHandler())
			setDefaultUncaughtExceptionHandler(weakReferenceOfCrashHandler.get())

			// Configure theme appearance based on user preferences or system settings
			// Ensures consistent visual experience across the application
			logger.d("Applying theme appearance from user preferences")
			setThemeAppearance()

			// Initialize scoped storage helper for modern file access on Android 10+
			// Handles permissions and provides abstraction for storage operations
			logger.d("Initializing ScopedStorageHelper for file system access")
			scopedStorageHelper = SimpleStorageHelper(activity)

			// Apply user-selected language for localization and internationalization
			// Overrides system language if user has specified a preference
			logger.d("Applying user-selected language for localization")
			aioLanguage.applyUserSelectedLanguage()

			// Lock activity orientation to portrait for consistent user experience
			// Prevents layout recalculations and provides predictable UI behavior
			logger.d("Locking orientation to portrait mode for consistency")
			requestedOrientation = SCREEN_ORIENTATION_PORTRAIT

			// Set up custom back-press handling to override default behavior
			// Allows for double-press confirmation or custom navigation flows
			logger.d("Configuring custom back press handler for enhanced navigation")
			WeakReference(object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() = onBackPressActivity()
			}).get()?.let { onBackPressedDispatcher.addCallback(activity, it) }

			// Inflate layout if provided by subclass through template method
			// Allows subclasses to specify their own UI while maintaining base initialization
			val layoutId = onRenderingLayout()
			if (layoutId > -1) {
				logger.d("Setting content view with layoutId=$layoutId from subclass")
				setContentView(layoutId)
			} else {
				logger.d("No layout provided by subclass — activity will have no UI")
			}
		} ?: logger.d("Failed to acquire safe activity reference — critical initialization skipped")
	}

	/**
	 * Called after onCreate() completion, typically used for final UI setup and initialization.
	 *
	 * This method provides an additional initialization point after the activity's layout
	 * has been fully inflated and the basic UI hierarchy has been established. It's the
	 * ideal place to perform operations that require the layout to be fully rendered,
	 * such as finding views, setting up adapters, or initializing UI components.
	 *
	 * Common use cases:
	 * - Setting up RecyclerView adapters and layout managers
	 * - Configuring ViewPagers and TabLayouts
	 * - Initializing custom views and their state
	 * - Setting click listeners and other UI interactions
	 * - Performing initial data loading for UI display
	 *
	 * @param savedInstanceState Previously saved activity state, same as in onCreate().
	 *        Can be used to restore UI state after configuration changes.
	 */
	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)
		logger.d("onPostCreate() called — performing post-layout initialization")

		// Allow subclasses to set up UI components after layout inflation is complete
		// This ensures all views are available and properly initialized
		logger.d("Calling onAfterLayoutRender() for subclass-specific UI setup")
		onAfterLayoutRender()

		// Check for app updates after the UI is fully set up to avoid blocking rendering
		// This provides a smooth user experience while ensuring update availability is known
		logger.d("Checking for latest available app update in background")
		checkForLatestUpdate()
	}

	/**
	 * Called when the activity moves to the foreground and becomes interactive to the user.
	 *
	 * This method performs comprehensive reinitialization and state restoration to ensure
	 * the activity is fully prepared for user interaction. It handles everything from
	 * reference management to service updates and permission verification, creating a
	 * seamless experience when users return to the app after backgrounding or interruption.
	 *
	 * Key responsibilities:
	 * - Re-establish activity references for UI operations
	 * - Verify and request necessary permissions
	 * - Update service states and background operations
	 * - Validate user configurations and settings
	 * - Initialize essential components and libraries
	 * - Handle localization and language changes
	 * - Manage security and self-destruct features
	 */
	override fun onResume() {
		super.onResume()
		logger.d("onResume() called — preparing activity for interaction")

		// Reinitialize weak activity reference if it was cleared during backgrounding
		// This ensures safe access to activity context for UI operations
		if (weakReferenceOfActivity == null) {
			logger.d("Re-initializing safe weak activity reference after background state")
			weakReferenceOfActivity = WeakReference(this)
		}

		getActivity()?.let { activity ->
			isActivityRunning = true
			logger.d("Activity marked as running and ready for user interaction")

			// Ensure permissions are granted or request if needed
			// Critical for features that require runtime permissions to function properly
			logger.d("Checking and requesting required permissions for app functionality")
			requestForPermissionIfRequired()

			// Request user to disable battery optimization for reliable background operations
			// This ensures downloads and other background tasks aren't interrupted by the system
			logger.d("Requesting user to disable battery optimization for uninterrupted service")
			requestForDisablingBatteryOptimization()

			// Update the state of foreground services to ensure they're running correctly
			// Important for ongoing downloads and other persistent operations
			logger.d("Updating foreground service state for background tasks")
			AIOForegroundService.updateService()

			// Validate user-selected folders to ensure they're still accessible
			// Prevents issues with storage permissions changes or folder deletion
			logger.d("Validating user-selected download folder accessibility")
			aioSettings.validateUserSelectedFolder()

			// Initialize YouTube-DLP engine for video downloading capabilities
			// Ensures the video download functionality is ready when needed
			logger.d("Initializing YtDLP engine for video download operations")
			INSTANCE.initializeYtDLP()

			// Invoke any subclass-specific resume logic for specialized behavior
			// Allows child activities to perform their own initialization
			logger.d("Calling subclass onResumeActivity() for custom initialization")
			onResumeActivity()

			// Handle language changes and restart if necessary for localization
			// Ensures UI is displayed in the correct language based on user preference
			logger.d("Checking for language changes that require activity restart")
			aioLanguage.closeActivityIfLanguageChanged(activity)

			// Refresh ad-blocking filters to ensure up-to-date protection
			// Maintains effective ad-blocking with latest filter definitions
			logger.d("Fetching latest ad-blocker filters for updated protection")
			aioAdblocker.fetchAdFilters()

			// Register base-activity at download UI manager for proper UI updates
			// Ensures download progress and status are properly displayed
			logger.d("Registering base-activity at download ui manager for UI coordination")
			(activity as? MotherActivity)?.let { motherActivity ->
				downloadSystem.downloadsUIManager.safeMotherActivity = motherActivity
			}

			// Handle self-destruct mode if enabled for security purposes
			// Provides automatic cleanup for sensitive applications
			logger.d("Checking self-destruct activation status for security")
			shouldSelfDestructApplication()
		} ?: logger.d("safeBaseActivityRef is null — skipping onResume tasks due to invalid context")
	}

	/**
	 * Called when the activity is about to move to the background and lose user focus.
	 *
	 * This method performs essential cleanup and state preservation to ensure the
	 * activity can properly resume later. It updates internal flags and allows
	 * subclasses to implement their own pause logic for specialized behavior.
	 *
	 * Important actions:
	 * - Updates activity running state to prevent UI operations in background
	 * - Invokes subclass pause logic for custom cleanup
	 * - Prepares the activity for potential destruction or background state
	 */
	override fun onPause() {
		super.onPause()
		isActivityRunning = false
		logger.d("onPause() called — activity moved to background, UI operations disabled")

		// Allow subclass to handle pause-specific behavior and cleanup
		// This includes saving transient state, pausing animations, or stopping sensors
		logger.d("Calling subclass onPauseActivity() for custom background preparation")
		onPauseActivity()
	}

	/**
	 * Called when the activity is being permanently destroyed and removed from memory.
	 *
	 * This method performs comprehensive cleanup of resources to prevent memory leaks
	 * and ensure proper app behavior. It releases system resources, cancels ongoing
	 * operations, and clears references to allow garbage collection.
	 *
	 * Cleanup responsibilities:
	 * - Release system resources like vibration hardware
	 * - Cancel background operations and network calls
	 * - Clear activity references to prevent memory leaks
	 * - Reset static fields that hold context references
	 * - Stop any ongoing services or background tasks
	 */
	override fun onDestroy() {
		super.onDestroy()
		logger.d("onDestroy() called — performing comprehensive resource cleanup")

		isActivityRunning = false

		// Releasing objects that are no longer needed to free memory
		// These objects contain context references that could cause memory leaks
		scopedStorageHelper = null
		permissionCheckListener = null
		logger.d("Released scopedStorageHelper and permissionCheckListener references")

		// Cancel ongoing vibrations to release hardware resources
		// Prevents vibrations from continuing after activity destruction
		if (vibrator?.hasVibrator() == true) {
			logger.d("Cancelling active vibration to release hardware resources")
			vibrator?.cancel()
		}

		// Cancel ongoing background version update check to prevent memory leaks
		// Stops coroutines and network operations associated with update checking
		logger.d("Cancelling background version update check operations")
		cancelUpdateCheck()

		// Clear the MotherActivity reference from download UI manager
		// Prevents attempts to update UI from a destroyed activity
		(getActivity() as? MotherActivity)?.let { motherActivity ->
			downloadSystem.downloadsUIManager.safeMotherActivity = null
			logger.d("Cleared MotherActivity reference from download UI manager")
		}
	}

	/**
	 * Called when the activity is paused, providing a hook for subclass-specific pause logic.
	 *
	 * Subclasses can override this method to implement custom behavior when the activity
	 * moves to the background. This is ideal for pausing animations, stopping sensors,
	 * saving transient UI state, or performing other cleanup specific to the subclass.
	 *
	 * The default implementation is intentionally empty, allowing subclasses to
	 * override only when they need specialized pause behavior without being forced
	 * to call super.onPauseActivity().
	 */
	override fun onPauseActivity() {
		logger.d("onPauseActivity() called — default implementation does nothing")
		// Default implementation intentionally left blank for subclass customization
		// Subclasses can override to add their own pause logic without calling super
	}

	/**
	 * Called when the activity is resumed, providing a hook for subclass-specific resume logic.
	 *
	 * Subclasses can override this method to implement custom behavior when the activity
	 * returns to the foreground. This is ideal for restarting animations, initializing
	 * sensors, restoring UI state, or performing other setup specific to the subclass.
	 *
	 * The default implementation is intentionally empty, allowing subclasses to
	 * override only when they need specialized resume behavior without being forced
	 * to call super.onResumeActivity().
	 */
	override fun onResumeActivity() {
		logger.d("onResumeActivity() called — default implementation does nothing")
		// Default implementation intentionally left blank for subclass customization
		// Subclasses can override to add their own resume logic without calling super
	}

	/**
	 * Configures the appearance of system bars (status bar and navigation bar) with comprehensive customization.
	 *
	 * This method provides complete control over the visual appearance of both the status bar
	 * (top bar showing time, battery, etc.) and navigation bar (bottom bar with back/home/recent buttons).
	 * It automatically handles the different implementation approaches required for various Android
	 * versions, ensuring consistent behavior across the entire device ecosystem.
	 *
	 * Key capabilities:
	 * - Custom colors for both status and navigation bars
	 * - Light/dark icon theming for optimal contrast and readability
	 * - Automatic API-level detection for using modern or legacy approaches
	 * - Seamless integration with Material Design guidelines
	 *
	 * @param statusBarColorResId Resource ID for the status bar background color.
	 *        Use transparent colors for edge-to-edge designs or opaque colors for traditional layouts.
	 * @param navigationBarColorResId Resource ID for the navigation bar background color.
	 *        Should coordinate with status bar color for visual harmony.
	 * @param isLightStatusBar Whether to use light-colored icons (dark icons) on the status bar.
	 *        Set to true for light backgrounds (improves dark icon visibility),
	 *        false for dark backgrounds (uses light icons).
	 * @param isLightNavigationBar Whether to use light-colored icons (dark icons) on the navigation bar.
	 *        Follows the same logic as status bar for consistent theming.
	 */
	override fun setSystemBarsColors(
		statusBarColorResId: Int,
		navigationBarColorResId: Int,
		isLightStatusBar: Boolean,
		isLightNavigationBar: Boolean,
	) {
		logger.d(
			"setSystemBarsColors() called with " +
					"statusBarColorResId=$statusBarColorResId, " +
					"navigationBarColorResId=$navigationBarColorResId, " +
					"isLightStatusBar=$isLightStatusBar, " +
					"isLightNavigationBar=$isLightNavigationBar"
		)

		val activityWindow = window

		// Apply colors to system bars - this sets the background colors for both bars
		activityWindow.statusBarColor = getColor(this, statusBarColorResId)
		activityWindow.navigationBarColor = getColor(this, navigationBarColorResId)
		logger.d("Applied status and navigation bar colors from resources")

		val decorView = activityWindow.decorView

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			logger.d("Using modern WindowInsetsController API for Android R (API 30) and above")

			val insetsController = activityWindow.insetsController

			// Configure status bar icon appearance using modern API
			// This controls whether status bar icons are light or dark based on background
			insetsController?.setSystemBarsAppearance(
				if (isLightStatusBar) APPEARANCE_LIGHT_STATUS_BARS else 0,
				APPEARANCE_LIGHT_STATUS_BARS
			)

			// Configure navigation bar icon appearance using modern API
			// This controls whether navigation bar buttons are light or dark
			insetsController?.setSystemBarsAppearance(
				if (isLightNavigationBar) APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
				APPEARANCE_LIGHT_NAVIGATION_BARS
			)

			logger.d("Applied light/dark appearance for system bars using modern API (R+)")
		} else {
			logger.d("Using legacy systemUiVisibility flags for pre-R devices (API < 30)")

			// Legacy approach for status bar icon theming
			// Uses bitwise operations to set or clear the light status bar flag
			if (isLightStatusBar) {
				decorView.systemUiVisibility =
					decorView.systemUiVisibility or
							SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
			} else {
				decorView.systemUiVisibility =
					decorView.systemUiVisibility and
							SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
			}

			// Legacy approach for navigation bar icon theming
			// Uses bitwise operations to set or clear the light navigation bar flag
			if (isLightNavigationBar) {
				decorView.systemUiVisibility =
					decorView.systemUiVisibility or
							SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
			} else {
				decorView.systemUiVisibility =
					decorView.systemUiVisibility and
							SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
			}

			logger.d("Applied light/dark appearance for system bars using legacy flags (pre-R)")
		}
	}

	/**
	 * Intercepts all touch events to provide enhanced user experience by automatically dismissing
	 * the software keyboard when users tap outside of text input fields.
	 *
	 * This method improves usability by preventing the keyboard from staying open when users
	 * are done with text input, which is a common UX expectation in mobile applications.
	 * It detects taps outside of focused EditText views and automatically hides the keyboard
	 * while clearing focus from the input field.
	 *
	 * The implementation:
	 * - Detects ACTION_DOWN events to identify user taps
	 * - Checks if the current focus is an EditText (or subclass)
	 * - Calculates whether the tap occurred outside the EditText bounds
	 * - Dismisses keyboard and clears focus when appropriate
	 * - Maintains normal event dispatch for all other interactions
	 *
	 * @param motionEvent The motion event describing the user's touch interaction, containing
	 *        coordinates, action type, and other touch metadata from the Android input system.
	 * @return `true` if the event was handled by this interceptor (keyboard dismissal),
	 *         otherwise passes the event to the superclass for normal processing.
	 *         This ensures all touch events are properly handled while adding the keyboard
	 *         dismissal functionality.
	 */
	override fun dispatchTouchEvent(motionEvent: MotionEvent): Boolean {
		if (motionEvent.action == MotionEvent.ACTION_DOWN) {
			val focusedView = currentFocus

			if (focusedView is EditText) {
				logger.d("dispatchTouchEvent(): User touched outside EditText — checking keyboard dismissal")

				// Get the global visible rectangle of the focused EditText
				val outRect = Rect()
				focusedView.getGlobalVisibleRect(outRect)

				// Check if the touch occurred outside the EditText bounds
				if (!outRect.contains(motionEvent.rawX.toInt(), motionEvent.rawY.toInt())) {
					logger.d("Tapped outside EditText — hiding keyboard and clearing focus")

					// Clear focus from the EditText to indicate input completion
					focusedView.clearFocus()

					// Hide the soft keyboard to free up screen space
					val service = getSystemService(INPUT_METHOD_SERVICE)
					val imm = service as InputMethodManager
					imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
				}
			}
		}

		// Always call super to maintain normal event dispatch chain
		return super.dispatchTouchEvent(motionEvent)
	}

	/**
	 * Launches a comprehensive permission request flow for the specified permissions.
	 *
	 * This method orchestrates the entire permission request process using PermissionX library,
	 * handling various user response scenarios with appropriate dialogs and fallbacks. It
	 * manages the complete lifecycle from initial request to final result handling.
	 *
	 * The flow includes three main stages:
	 * 1. Initial permission request with system dialog
	 * 2. Explanation dialog when permissions are initially denied (educates user)
	 * 3. Settings redirect dialog when permissions are permanently denied (user checked "Don't ask again")
	 *
	 * @param permissions The list of permissions to request from the user. Typically includes
	 *        storage, notification, or other runtime permissions required by the app.
	 *        The list should be generated based on SDK version requirements.
	 */
	override fun launchPermissionRequest(permissions: ArrayList<String>) {
		logger.d("launchPermissionRequest() called with permissions=$permissions")

		getActivity()?.let { activity ->
			logger.d("Starting permission request flow with ${permissions.size} permission(s)")

			PermissionX.init(activity)
				.permissions(permissions)

				// Show explanation dialog when permissions are initially denied
				// This educates the user about why the permissions are needed
				.onExplainRequestReason { callback, deniedList ->
					logger.d("Showing explanation dialog for denied permissions: $deniedList")
					callback.showRequestReasonDialog(
						permissions = deniedList,
						message = getString(R.string.title_allow_the_storage_permissions),
						positiveText = getString(R.string.title_allow_now_in_settings)
					)
				}

				// Show settings redirect dialog when permissions are permanently denied
				// This occurs when user selects "Don't ask again" in system dialog
				.onForwardToSettings { scope, deniedList ->
					logger.d("Permissions permanently denied — forwarding to settings: $deniedList")
					scope.showForwardToSettingsDialog(
						permissions = deniedList,
						message = getString(R.string.text_allow_permission_in_setting),
						positiveText = getString(R.string.title_allow_now_in_settings)
					)
				}

				// Handle final permission result after user completes the flow
				.request { allGranted, grantedList, deniedList ->
					logger.d(
						"Permission request completed — " +
								"allGranted=$allGranted, granted=$grantedList, denied=$deniedList"
					)

					// Reset the active permission checking state
					isUserPermissionCheckingActive = false

					// Notify listener with the comprehensive result
					permissionCheckListener?.onPermissionResultFound(
						isGranted = allGranted,
						grantedList = grantedList,
						deniedList = deniedList
					)
				}

			// Mark that permission checking is now active to prevent duplicate requests
			isUserPermissionCheckingActive = true
			logger.d("Permission request initiated — waiting for user response")
		} ?: logger.d("launchPermissionRequest() skipped — safeBaseActivityRef is null")
	}

	/**
	 * Opens another activity with proper intent configuration and optional transition animation.
	 *
	 * This method provides a standardized way to navigate between activities while
	 * maintaining consistent behavior and user experience. It uses CLEAR_TOP and
	 * SINGLE_TOP flags to ensure proper back stack management and prevent duplicate
	 * activity instances.
	 *
	 * The fade animation provides smooth visual transition that helps users understand
	 * the navigation flow and maintains the app's polished feel.
	 *
	 * @param targetActivity The target activity class to open. This should be a valid
	 *        Activity class that is declared in the AndroidManifest.xml.
	 * @param shouldAnimate Whether to apply the fade transition animation after
	 *        launching the activity. Set to true for user-initiated navigation
	 *        where smooth transitions enhance UX, false for programmatic navigation
	 *        where animation might be unnecessary.
	 */
	override fun openActivity(targetActivity: Class<*>, shouldAnimate: Boolean) {
		logger.d("openActivity() called — activity=${targetActivity.simpleName}, shouldAnimate=$shouldAnimate")

		getActivity()?.let { activity ->
			logger.d("Launching activity: ${targetActivity.simpleName}")

			// Prepare intent with flags to manage activity stack and prevent duplicates
			val intent = Intent(activity, targetActivity).apply {
				flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
			}

			startActivity(intent)
			logger.d("Activity ${targetActivity.simpleName} started successfully")

			// Apply fade animation if requested for smooth visual transition
			if (shouldAnimate) {
				logger.d("Applying fade transition animation for polished navigation experience")
				animActivityFade(activity)
			}
		} ?: logger.d("openActivity() skipped — safeBaseActivityRef is null")
	}

	/**
	 * Closes the current activity with swipe-right animation for natural navigation feel.
	 *
	 * This method provides an intuitive way to close activities that mimics the natural
	 * back navigation gesture on Android. The swipe-right animation creates the visual
	 * impression of sliding the activity off-screen to the right, which aligns with
	 * users' mental model of going "back" in the navigation flow.
	 *
	 * The animation is particularly effective for activities that feel like "pages"
	 * in a book or steps in a workflow, reinforcing the hierarchical navigation structure.
	 *
	 * @param shouldAnimate Whether to apply the swipe-right exit animation.
	 *        Set to true for user-initiated back actions where the animation provides
	 *        valuable visual feedback, false for programmatic closures where immediate
	 *        response is more important than animation.
	 */
	override fun closeActivityWithSwipeAnimation(shouldAnimate: Boolean) {
		logger.d("closeActivityWithSwipeAnimation() called — shouldAnimate=$shouldAnimate")

		getActivity()?.apply {
			logger.d("Finishing current activity: ${this::class.java.simpleName}")
			finish()

			// Apply swipe-right animation if requested for natural navigation feel
			if (shouldAnimate) {
				logger.d("Applying swipe-right exit animation mimicking natural back gesture")
				animActivitySwipeRight(this)
			}
		} ?: logger.d("closeActivityWithSwipeAnimation() skipped — safeBaseActivityRef is null")
	}

	/**
	 * Closes the current activity with optional fade-out animation for smooth visual transition.
	 *
	 * This method provides a controlled way to finish the current activity while
	 * maintaining a polished user experience through optional animations. The fade
	 * animation creates a subtle visual cue that helps users understand the
	 * navigation flow and provides feedback that the activity is closing.
	 *
	 * @param shouldAnimate Whether to apply the fade-out transition animation.
	 *        Set to true for normal user-initiated closures, false for programmatic
	 *        closures where animation might be disruptive (e.g., during error handling
	 *        or rapid sequential navigation).
	 */
	override fun closeActivityWithFadeAnimation(shouldAnimate: Boolean) {
		logger.d("closeActivityWithFadeAnimation() called — shouldAnimate=$shouldAnimate")

		getActivity()?.apply {
			logger.d("Finishing current activity: ${this::class.java.simpleName}")
			finish()

			// Apply fade-out animation if requested for smooth visual transition
			if (shouldAnimate) {
				logger.d("Applying fade exit animation for better user experience")
				animActivityFade(this)
			}
		} ?: logger.d("closeActivityWithFadeAnimation() skipped — safeBaseActivityRef is null")
	}

	/**
	 * Handles double-back-press logic to prevent accidental activity exits.
	 *
	 * This method implements the common UX pattern where users must press the back
	 * button twice within a short timeframe to confirm they want to exit. This
	 * prevents accidental closures while still providing an easy exit path.
	 *
	 * Behavior flow:
	 * - First press: Shows a toast message prompting user to press again to exit
	 * - Second press (within 2 seconds): Closes activity with swipe animation
	 * - Timeout (after 2 seconds): Resets the counter if user doesn't press again
	 *
	 * The 2-second timeout provides a reasonable window for user confirmation while
	 * not being overly restrictive. The swipe animation provides clear visual feedback
	 * that the exit action was successful.
	 */
	override fun exitActivityOnDoubleBackPress() {
		logger.d("exitActivityOnDoubleBackPress() called — currentState=$isBackButtonEventFired")

		if (isBackButtonEventFired == 0) {
			logger.d("First back press detected — showing toast prompt to user")

			// Show exit prompt toast to educate user about double-press requirement
			showToast(
				activityInf = getActivity(),
				msgId = R.string.title_press_back_button_to_exit
			)

			// Set flag to indicate back button pressed once (pending confirmation)
			isBackButtonEventFired = 1

			// Reset flag after 2 seconds if user doesn't press again to confirm
			delay(2000, object : OnTaskFinishListener {
				override fun afterDelay() {
					logger.d("Resetting back press flag after timeout - user did not confirm exit")
					isBackButtonEventFired = 0
				}
			})
		} else if (isBackButtonEventFired == 1) {
			logger.d("Second back press detected within timeout — exiting activity with animation")
			isBackButtonEventFired = 0
			closeActivityWithSwipeAnimation(true)
		}
	}

	/**
	 * Force quits the entire application process immediately.
	 *
	 * This method performs an immediate termination of the application process,
	 * bypassing normal Android activity lifecycle callbacks (onPause, onStop, onDestroy).
	 * It should be used sparingly and only in specific scenarios where graceful
	 * shutdown is not possible or necessary.
	 *
	 * Use cases:
	 * - Critical errors that make the app unstable
	 * - Security concerns requiring immediate termination
	 * - User-initiated force quit from emergency situations
	 *
	 * WARNING: This method does not save state, persist data, or perform cleanup.
	 * Use normal activity finishing methods for routine navigation and closures.
	 */
	override fun forceQuitApplication() {
		logger.d("forceQuitApplication() called — terminating the process immediately")

		// Kill the current process to ensure complete application termination
		Process.killProcess(Process.myPid())
		logger.d("Process killed successfully - all activities and services terminated")

		// Exit the JVM to release all resources and complete shutdown
		exitProcess(0)
	}

	/**
	 * Opens the application's App Info screen in device system settings.
	 *
	 * This method launches the system settings page specifically for this application,
	 * allowing users to manage various app-level configurations without navigating
	 * through the general settings menu. This is particularly useful for helping
	 * users access settings that might be needed for troubleshooting or configuration.
	 *
	 * Users can typically perform these actions from the App Info screen:
	 * - Grant or revoke runtime permissions
	 * - Clear app cache and data
	 * - Force stop the application
	 * - Manage battery optimization settings
	 * - Review storage usage and notifications
	 * - Uninstall the application
	 *
	 * This provides a direct path for users to manage app settings that might
	 * affect functionality or resolve issues they're experiencing.
	 */
	override fun openAppInfoSetting() {
		logger.d("openAppInfoSetting() called — launching system app settings")

		val packageName = this.packageName
		logger.d("Target package for app info: $packageName")

		// Prepare intent to open the specific App Info screen for this application
		val uri = "package:$packageName".toUri()
		val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS, uri)

		startActivity(intent)
		logger.d("App Info settings screen opened successfully -" +
				" user can now manage app permissions and settings")
	}

	/**
	 * Opens the official application website or Play Store page in an external browser.
	 *
	 * This method attempts to launch the app's official online presence, which could be
	 * the application website, Play Store listing, or other promotional page. It uses
	 * the standard Android intent system to delegate to the user's preferred browser.
	 *
	 * If no browser application is available on the device, it gracefully falls back
	 * to showing a toast message prompting the user to install a web browser. This
	 * ensures the app doesn't crash and provides helpful guidance to the user.
	 *
	 * @throws Exception if the intent cannot be resolved or launched, which is
	 *         caught internally and handled by showing the fallback toast message.
	 */
	override fun openApplicationOfficialSite() {
		logger.d("openApplicationOfficialSite() called")

		try {
			// Get the official site URL from string resources for easy maintenance
			val uri = getText(R.string.text_aio_official_page_url).toString()
			logger.d("Opening official site URL: $uri")

			// Use ACTION_VIEW intent to open in user's preferred browser or Play Store
			startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
			logger.d("Official site opened successfully in browser or Play Store")
		} catch (error: Exception) {
			logger.d("Failed to open official site: ${error.message}")
			error.printStackTrace()

			// Show fallback message when no browser is available
			showToast(
				activityInf = getActivity(),
				msgId = R.string.title_please_install_web_browser
			)
			logger.d("Displayed toast: Please install a web browser")
		}
	}

	/**
	 * Retrieves the device's current time zone identifier.
	 *
	 * This method returns the IANA time zone ID (e.g., "America/New_York", "Europe/London")
	 * that represents the device's configured time zone. This is useful for timestamp
	 * synchronization, scheduling, and displaying time-sensitive information in the
	 * user's local time context.
	 *
	 * @return The device's default time zone ID as a string in IANA format.
	 *         Examples: "Asia/Kolkata", "America/Los_Angeles", "Europe/Paris"
	 */
	override fun getTimeZoneId(): String {
		val timeZoneId = TimeZone.getDefault().id
		logger.d("getTimeZoneId() called — current time zone: $timeZoneId")
		return timeZoneId
	}

	/**
	 * Returns the currently active BaseActivity instance for UI operations.
	 *
	 * This method provides access to the current activity context, which is essential
	 * for performing UI operations, showing dialogs, or starting new activities.
	 * The reference is managed through weak references to prevent memory leaks
	 * and is automatically cleared during activity destruction.
	 *
	 * @return The current BaseActivity instance if available, or null if the activity
	 *         is not currently active (destroyed or not initialized). Callers should
	 *         always check for null before using the returned activity.
	 */
	override fun getActivity(): BaseActivity? {
		logger.d("getActivity() called — returning current activity reference")
		return weakReferenceOfActivity?.get()
	}

	/**
	 * Clears the weak reference to the current activity to prevent memory leaks.
	 *
	 * This method is typically called by the application lifecycle manager during
	 * activity destruction or configuration changes. It ensures that stale activity
	 * references don't prevent garbage collection, which could lead to memory leaks
	 * and increased memory usage over time.
	 *
	 * The method clears both the weak reference container and the safe activity
	 * reference, providing a clean slate for the next activity instance.
	 */
	open fun clearWeakActivityReference() {
		logger.d("clearWeakActivityReference() called — clearing activity references")

		// Clear the weak reference to allow garbage collection
		weakReferenceOfActivity?.clear()
		logger.d("Weak reference cleared")

		// Clear the safe reference to prevent accidental usage of destroyed activity
		weakReferenceOfActivity = null
		logger.d("Safe weak activity reference set to null")
	}

	/**
	 * Triggers a short vibration on the device for haptic feedback.
	 *
	 * This method provides tactile feedback to users for various interactions
	 * such as button presses, notifications, or confirmation events. It first
	 * checks if the device has vibrator capability before attempting to vibrate.
	 *
	 * The vibration uses the default amplitude setting and creates a one-shot
	 * vibration effect for the specified duration.
	 *
	 * @param timeInMillis Duration of the vibration in milliseconds. Typical values
	 *        range from 50ms for subtle feedback to 500ms for prominent notifications.
	 *        Values outside reasonable ranges may be ignored by the system.
	 */
	override fun doSomeVibration(timeInMillis: Int) {
		logger.d("doSomeVibration() called — duration=${timeInMillis}ms")

		if (vibrator?.hasVibrator() == true) {
			logger.d("Device supports vibration — triggering vibration")

			// Create a one-shot vibration effect with default amplitude
			vibrator?.vibrate(
				VibrationEffect.createOneShot(
					/* milliseconds = */ timeInMillis.toLong(),
					/* amplitude = */ VibrationEffect.DEFAULT_AMPLITUDE
				)
			)

			logger.d("Vibration triggered successfully for ${timeInMillis}ms")
		} else {
			logger.d("Device does not support vibration — skipping haptic feedback")
		}
	}

	/**
	 * Provides standard intent flags for launching activities in single-top mode.
	 *
	 * These flags are commonly used when you want to ensure only one instance of
	 * an activity exists in the task stack. If an instance already exists, it will
	 * be brought to the front instead of creating a new instance.
	 *
	 * The combined flags provide the following behavior:
	 * - FLAG_ACTIVITY_CLEAR_TOP: Removes intermediate activities from the stack
	 * - FLAG_ACTIVITY_SINGLE_TOP: Prevents multiple instances of the same activity
	 *
	 * This is particularly useful for main activities, launcher activities, or
	 * activities that should maintain a single instance in the back stack.
	 *
	 * @return Combined intent flags for single-top launch mode configuration.
	 */
	override fun getSingleTopIntentFlags(): Int {
		val flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
		logger.d("getSingleTopIntentFlags() called — returning flags=$flags")
		return flags
	}

	/**
	 * Displays a user-friendly dialog to inform users that a selected feature
	 * is not yet implemented or currently unavailable.
	 *
	 * This method provides a consistent user experience when users attempt to
	 * access upcoming or in-development features. It includes haptic feedback
	 * for better user acknowledgment and uses custom styling to maintain
	 * the app's visual identity while delivering the message.
	 *
	 * The dialog features:
	 * - A green-colored title for positive visual association
	 * - An icon-enhanced "Okay" button for clear call-to-action
	 * - Brief vibration feedback to confirm user interaction
	 * - Safe activity reference checking to prevent crashes
	 */
	fun showUpcomingFeatures() {
		logger.d("showUpcomingFeatures() called — displaying upcoming feature dialog")

		// Trigger short vibration for haptic feedback to acknowledge user interaction
		doSomeVibration(20)

		getActivity()?.let { safeActivityRef ->
			logger.d("Safe activity reference found — preparing dialog")

			showMessageDialog(
				baseActivityInf = safeActivityRef,
				isTitleVisible = true,
				titleText = getString(R.string.title_feature_isnt_implemented),
				isNegativeButtonVisible = false, // Single action flow - only "Okay" option
				positiveButtonText = getString(R.string.title_okay),

				// Customize message text view with the upcoming feature explanation
				messageTextViewCustomize = { messageTextView ->
					logger.d("Setting message text for upcoming features")
					messageTextView.setText(R.string.text_feature_isnt_available_yet)
				},

				// Customize title text view with green color for positive visual indication
				titleTextViewCustomize = { titleTextView ->
					val colorResId = R.color.color_green
					val color = safeActivityRef.resources.getColor(colorResId, null)
					titleTextView.setTextColor(color)
					logger.d("Title text color set to green - indicating informational message")
				},

				// Customize positive button with an icon for enhanced visual appeal
				positiveButtonTextCustomize = { positiveButton: TextView ->
					val drawable = getDrawable(applicationContext, R.drawable.ic_okay_done)
					drawable?.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
					positiveButton.setCompoundDrawables(drawable, null, null, null)
					logger.d("Positive button customized with drawable icon for better UX")
				}
			)

			logger.d("Upcoming feature dialog displayed successfully")
		} ?: logger.d("showUpcomingFeatures() skipped — safeBaseActivityRef is null")
	}

	/**
	 * Requests runtime permissions if required by the app and not yet granted.
	 *
	 * This method orchestrates the permission request flow with careful timing
	 * and state management. It uses a delayed approach to ensure the activity
	 * is fully initialized before showing permission dialogs, which prevents
	 * UI conflicts and improves user experience.
	 *
	 * Key features:
	 * - 1-second delay to avoid overlapping with activity startup animations
	 * - Automatic skipping for OpeningActivity to prevent permission fatigue
	 * - Comprehensive permission checking across different Android versions
	 * - State tracking to prevent duplicate permission requests
	 * - Delegates result handling to permissionCheckListener for modularity
	 *
	 * The method checks all required permissions based on SDK version and
	 * only requests those that haven't been granted yet.
	 */
	private fun requestForPermissionIfRequired() {
		logger.d("requestForPermissionIfRequired() called")

		getActivity()?.let { activity ->
			if (!isUserPermissionCheckingActive) {
				logger.d("Permission check not active — scheduling delayed permission request")

				// Add a delay to ensure activity UI is fully loaded and visible
				// This prevents permission dialogs from appearing during activity transitions
				delay(timeInMile = 1000, listener = object : OnTaskFinishListener {
					override fun afterDelay() {
						logger.d("Delayed permission check triggered after 1000ms")

						// Skip permission check for OpeningActivity to avoid overwhelming new users
						if (activity is OpeningActivity) {
							logger.d("Activity is OpeningActivity — " +
									"skipping permission request to improve first-run experience")
							return
						}

						val permissions = getRequiredPermissionsBySDKVersion()
						logger.d("Permissions required by SDK version: $permissions")

						// Check if any required permissions are not granted
						// This includes notification and storage permissions based on Android version
						if (permissions.isNotEmpty() ||
							!isGranted(activity, POST_NOTIFICATIONS) ||
							!isGranted(activity, MANAGE_EXTERNAL_STORAGE) ||
							!isGranted(activity, WRITE_EXTERNAL_STORAGE)) {
							logger.d("Required permissions not granted — launching permission request dialog")
							launchPermissionRequest(permissions)
						} else {
							logger.d("All required permissions are already granted — notifying listener")
							// Notify listener that permissions are already available
							permissionCheckListener?.onPermissionResultFound(
								isGranted = true,
								grantedList = permissions,
								deniedList = null
							)
						}
					}
				})
			} else {
				logger.d("Permission check already active — " +
						"skipping duplicate request to avoid conflicts")
			}
		} ?: logger.d("Activity reference is null — " +
				"cannot request permissions without valid context")
	}

	/**
	 * Indicates whether the activity is currently running and in the foreground.
	 *
	 * This method provides the current lifecycle state of the activity, which is
	 * essential for preventing UI operations when the activity is not active.
	 * The state is automatically managed by the activity's lifecycle callbacks
	 * (onResume and onPause) to ensure accurate tracking.
	 *
	 * @return true if the activity is running in the foreground and able to
	 *         process user interactions, false if the activity is paused,
	 *         stopped, or destroyed. Useful for conditional UI updates and
	 *         preventing background operations from affecting the UI.
	 */
	fun isActivityRunning(): Boolean {
		logger.d("isActivityRunning() called — result=$isActivityRunning")
		return isActivityRunning
	}

	/**
	 * Determines which runtime permissions are required based on the current Android SDK version.
	 *
	 * This method handles the dynamic permission requirements that have changed across
	 * Android versions, particularly around storage and notifications. It ensures the
	 * app requests the appropriate permissions for the device's API level, adapting to
	 * Google's scoped storage changes and new permission models.
	 *
	 * Permission Strategy:
	 * - Android 13+ (Tiramisu): POST_NOTIFICATIONS for notification access
	 * - Android 10+ (R): MANAGE_EXTERNAL_STORAGE for broad file access (with Play Store restrictions)
	 * - Android < 13: WRITE_EXTERNAL_STORAGE for legacy storage access
	 *
	 * @return A list of required permission strings that should be requested using
	 *         ActivityResultContracts.RequestMultiplePermissions. The list varies
	 *         based on the device's Android version and feature requirements.
	 */
	fun getRequiredPermissionsBySDKVersion(): ArrayList<String> {
		logger.d("getRequiredPermissionsBySDKVersion() called")

		val permissions = ArrayList<String>()

		// Handle notification permissions for Android 13+ where they became runtime permissions
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			logger.d("Android version >= 13 — adding POST_NOTIFICATIONS permission")
			permissions.add(POST_NOTIFICATIONS)
		} else {
			// Legacy storage permission for devices before Android 13
			logger.d("Android version < 13 — adding WRITE_EXTERNAL_STORAGE permission")
			permissions.add(WRITE_EXTERNAL_STORAGE)
		}

		// Broad file access permission for Android 10+ (requires special Play Store approval)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			logger.d("Android version >= 11 — adding MANAGE_EXTERNAL_STORAGE permission")
			permissions.add(MANAGE_EXTERNAL_STORAGE)
		}

		logger.d("Permissions determined: $permissions")
		return permissions
	}

	/**
	 * Configures the system bars (status bar and navigation bar) for light theme compatibility.
	 *
	 * This method applies a light-themed appearance to the system bars, ensuring they
	 * use dark-colored icons (black/dark gray) on light backgrounds. This creates optimal
	 * visual contrast and follows Material Design guidelines for light theme implementation.
	 *
	 * The colors are typically set to light surface colors, and both status bar and
	 * navigation bar are configured to use dark-content appearance for better readability
	 * against light backgrounds. This is the standard appearance for light-themed apps.
	 */
	fun setLightSystemBarTheme() {
		logger.d("setLightSystemBarTheme() called — applying light system bar appearance")

		setSystemBarsColors(
			statusBarColorResId = R.color.color_surface,     // Light background color for status bar
			navigationBarColorResId = R.color.color_surface, // Light background color for navigation bar
			isLightStatusBar = true,     // Use light status bar with dark icons
			isLightNavigationBar = true  // Use light navigation bar with dark icons
		)

		logger.d("Light system bar theme applied successfully")
	}

	/**
	 * Applies the app's theme appearance based on user preferences or system defaults.
	 *
	 * This method coordinates the visual theme of the application by delegating to
	 * ViewUtility.changesSystemTheme() which handles the actual theme application.
	 * It supports three theme modes that can be configured in app settings or
	 * follow the system-wide dark mode setting.
	 *
	 * Theme Mode Options:
	 * - -1: Follow system (auto) - automatically switches between light/dark based on system setting
	 * -  1: Force Dark mode - always uses dark theme regardless of system setting
	 * -  2: Force Light mode - always uses light theme regardless of system setting
	 *
	 * The method safely checks for an active activity reference before applying
	 * the theme to prevent crashes when called from background or destroyed contexts.
	 */
	fun setThemeAppearance() {
		logger.d("setThemeAppearance() called — applying user-selected or system theme")

		getActivity()?.let { activity ->
			ViewUtility.changesSystemTheme(activity)
			logger.d("Theme applied via ViewUtility.changesSystemTheme()")
		} ?: logger.d("No active activity reference — theme appearance not applied")
	}

	/**
	 * Checks whether the system's dark mode is currently active.
	 *
	 * This method examines the device's UI configuration to determine if dark mode
	 * is enabled at the system level. It does not consider app-specific theme settings,
	 * but rather the actual system-wide dark mode state. This is useful for coordinating
	 * app behavior with system preferences or for analytics tracking.
	 *
	 * @return true if dark mode is enabled system-wide, false if light mode is active.
	 *         Returns based on the actual Configuration.uiMode rather than app theme.
	 */
	fun isDarkModeActive(): Boolean {
		val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
		val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
		logger.d("isDarkModeActive() called — result=$isDark")
		return isDark
	}

	/**
	 * Configures the system bars (status bar and navigation bar) for dark theme compatibility.
	 *
	 * This method applies a dark-themed appearance to the system bars, ensuring they
	 * use light-colored icons (white) on dark backgrounds. This creates optimal visual
	 * contrast and follows Material Design guidelines for dark theme implementation.
	 *
	 * The colors are typically set to the app's primary dark color, and both status bar
	 * and navigation bar are configured to use light-content appearance for better
	 * readability against dark backgrounds.
	 */
	fun setDarkSystemBarTheme() {
		logger.d("setDarkSystemBarTheme() called — applying dark system bar appearance")

		setSystemBarsColors(
			statusBarColorResId = R.color.color_primary,
			navigationBarColorResId = R.color.color_primary,
			isLightStatusBar = false,    // Use dark status bar with light icons
			isLightNavigationBar = false // Use dark navigation bar with light icons
		)

		logger.d("Dark system bar theme applied successfully")
	}

	/**
	 * Enables immersive edge-to-edge fullscreen mode by hiding system bars.
	 *
	 * This method configures the app to use the entire screen real estate, hiding
	 * both status bar and navigation bar for a completely immersive experience.
	 * System bars can be temporarily revealed by swiping from the edges of the screen.
	 *
	 * The implementation uses different approaches based on Android version:
	 * - Android R (API 30+) and above: Uses modern WindowInsetsController API
	 * - Pre-Android R: Uses legacy system UI visibility flags with immersive sticky mode
	 *
	 * This is ideal for media consumption, gaming, or reading experiences where
	 * maximum screen space is desired without permanently losing system navigation.
	 */
	fun setEdgeToEdgeFullscreen() {
		logger.d("setEdgeToEdgeFullscreen() called — enabling immersive fullscreen mode")

		// Disable default window fitting to enable edge-to-edge layout
		// This allows content to draw behind system bars when they are hidden
		WindowCompat.setDecorFitsSystemWindows(window, false)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			logger.d("Android version >= R — using InsetsController API for fullscreen")

			window.insetsController?.let {
				// Hide both status bar and navigation bar
				it.hide(WindowInsets.Type.systemBars())
				// System bars appear transiently when user swipes from edges
				it.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			}
		} else {
			logger.d("Android version < R — using legacy system UI flags")

			window.decorView.systemUiVisibility = (SYSTEM_UI_FLAG_LAYOUT_STABLE
					or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					or SYSTEM_UI_FLAG_HIDE_NAVIGATION
					or SYSTEM_UI_FLAG_FULLSCREEN
					or SYSTEM_UI_FLAG_IMMERSIVE_STICKY) // Sticky immersive mode for better UX
		}

		// Apply edge-to-edge behavior using compatibility controller for consistent behavior
		WindowCompat.getInsetsController(window, window.decorView).let { controller ->
			controller.hide(WindowInsetsCompat.Type.systemBars())
			controller.systemBarsBehavior =
				WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			logger.d("Fullscreen mode activated with swipe-to-show system bars")
		}

		logger.d("Edge-to-edge fullscreen setup completed successfully")
	}

	/**
	 * Disables edge-to-edge fullscreen mode and restores standard system UI layout.
	 *
	 * This method reverses edge-to-edge display configuration, returning the app to
	 * standard Android system UI behavior. It ensures system bars (status bar and
	 * navigation bar) become visible and interactive again, with content properly
	 * inset below these system elements.
	 *
	 * The implementation handles both modern (API 30+) and legacy (pre-API 30)
	 * Android versions appropriately, using the recommended approaches for each
	 * platform to guarantee consistent behavior across devices.
	 */
	fun disableEdgeToEdge() {
		logger.d("disableEdgeToEdge() called — restoring default system UI layout")

		// Re-enable window fitting for system windows - content will no longer draw behind system bars
		WindowCompat.setDecorFitsSystemWindows(window, true)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			// Modern approach for Android 11+ using WindowInsetsController
			window.insetsController?.let {
				// Make system bars permanently visible
				it.show(WindowInsets.Type.systemBars())
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					// Reset to default behavior (system bars hide when user swipes)
					it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
				}
				logger.d("InsetsController used to show system bars and reset behavior")
			}
		} else {
			// Legacy approach for Android versions before API 30
			val flags = (SYSTEM_UI_FLAG_LAYOUT_STABLE
					or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
			window.decorView.systemUiVisibility = flags
			logger.d("Legacy system UI flags applied for pre-R devices")
		}

		// Apply compatibility controller for consistent behavior across all API levels
		WindowCompat.getInsetsController(window, window.decorView).let { controller ->
			controller.show(WindowInsetsCompat.Type.systemBars())
			controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
			logger.d("InsetsControllerCompat used to show system bars with default behavior")
		}
	}

	/**
	 * Enables edge-to-edge fullscreen mode with a custom cutout (notch) color.
	 *
	 * This method configures the app to use the entire screen, including areas
	 * around display cutouts (notches). Content will extend behind system bars,
	 * creating an immersive experience. The specified color is applied to the
	 * status bar and navigation bar backgrounds to ensure visual consistency.
	 *
	 * @param color The color to apply to status bar and navigation bar cutout areas.
	 *              This should typically match your app's primary background color
	 *              to create a seamless visual transition between content and system areas.
	 */
	fun setEdgeToEdgeCustomCutoutColor(@ColorInt color: Int) {
		logger.d("setEdgeToEdgeCustomCutoutColor() called — color=${color}")

		// Allow content to draw behind system bars for edge-to-edge effect
		WindowCompat.setDecorFitsSystemWindows(window, false)

		// Set transparent system bars so content can show through
		window.statusBarColor = color
		window.navigationBarColor = color

		// Allow content to extend into display cutouts on short edges (notched devices)
		window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			// Set window background to match cutout color for visual consistency
			window.setBackgroundDrawable(color.toDrawable())
			logger.d("Applied custom cutout color to window background (API >= R)")
		}
	}

	/**
	 * Checks if the application is ignoring battery optimizations.
	 *
	 * Battery optimization is a system feature that can restrict background activity
	 * to conserve battery. Some apps (like download managers or media players) need
	 * to be excluded from these restrictions to function properly in the background.
	 *
	 * This method checks whether the user has granted this app permission to bypass
	 * battery optimization restrictions. If not, you may want to prompt the user
	 * to disable optimization for reliable background operation.
	 *
	 * @return `true` if the app is excluded from battery optimization (can run freely
	 *         in background), `false` if subject to system battery restrictions.
	 */
	fun isBatteryOptimizationIgnored(): Boolean {
		val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
		// Check if this app is in the battery optimization whitelist
		val isIgnored = powerManager?.isIgnoringBatteryOptimizations(packageName) == true
		logger.d("isBatteryOptimizationIgnored() called — result=$isIgnored")
		return isIgnored
	}

	// Tracks whether the battery optimization dialog is currently being displayed to the user
	private var isBatteryOptimizationDialogShowing = false

	/**
	 * Prompts the user to disable battery optimization for the app to ensure reliable background operations.
	 *
	 * This function displays a persuasive dialog explaining why disabling battery optimization is beneficial
	 * for maintaining background download functionality. The dialog only shows under specific conditions
	 * to avoid annoying users and appears at appropriate times.
	 *
	 * @see isBatteryOptimizationIgnored For checking current battery optimization status
	 * @see MotherActivity The main activity context required for showing the dialog
	 */
	fun requestForDisablingBatteryOptimization() {
		logger.d("requestForDisablingBatteryOptimization() called")

		// Guard clause: Only show if user has experienced successful downloads

		// Only proceed further if user has not explicitly mentioned to skip it
		if (aioSettings.hasUserSkipBatteryOptimization) {
			logger.d("Skipping — no successful downloads yet")
			return
		}

		// This proves the app's value before asking for special permissions
		if (aioSettings.totalNumberOfSuccessfulDownloads < 1) {
			logger.d("Skipping — no successful downloads yet")
			return
		}

		// Guard clause: Only show in main activity context for proper UI presentation
		if (getActivity() !is MotherActivity) {
			logger.d("Skipping — current activity is not MotherActivity")
			return
		}

		// Guard clause: Prevent multiple simultaneous dialogs
		if (isBatteryOptimizationDialogShowing) {
			logger.d("Skipping battery optimization prompt — already showing to user")
			return
		}

		// Guard clause: Don't bother user if they've already configured this setting
		if (isBatteryOptimizationIgnored()) {
			logger.d("Skipping battery optimization prompt —  already ignored by user")
			return
		}

		logger.d("All conditions met — proceeding to show battery optimization dialog")

		// Create and configure the battery optimization explanation dialog
		MsgDialogUtils.getMessageDialog(
			baseActivityInf = getActivity(),
			isTitleVisible = true,
			messageTextViewCustomize = { it.setText(R.string.text_battery_optimization_msg) },
			titleTextViewCustomize = { it.setText(R.string.title_turn_off_battery_optimization) },
			positiveButtonTextCustomize = {
				it.setText(R.string.title_disable_now)
				it.setLeftSideDrawable(R.drawable.ic_button_arrow_next)
			},
			negativeButtonTextCustomize = {
				it.setText(R.string.title_not_now)
				it.setLeftSideDrawable(R.drawable.ic_button_cancel)
			}
		)?.apply {
			// Set up dialog lifecycle listeners to properly manage the showing state
			dialog.setOnDismissListener {
				logger.d("Battery optimization dialog dismissed - resetting showing state")
				isBatteryOptimizationDialogShowing = false
			}

			dialog.setOnCancelListener {
				logger.d("Battery optimization dialog cancelled - resetting showing state")
				isBatteryOptimizationDialogShowing = false
			}

			// Handle user cancellation explicitly (negative button)
			setOnClickForNegativeButton {
				logger.d("User chose to skip battery optimization prompt — updating settings and dismissing dialog")
				dialog.cancel()
				aioSettings.hasUserSkipBatteryOptimization = true
				aioSettings.updateInStorage()
				logger.d("User skip preference persisted successfully")
			}

			// Handle user acceptance - launch system settings for battery optimization
			setOnClickForPositiveButton {
				logger.d("User accepted battery optimization prompt — launching system settings intent")
				dialog.cancel()
				try {
					// Intent to open battery optimization settings where user can exclude this app
					val intent = Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
					startActivity(intent)
					logger.d("Battery optimization settings intent launched successfully")
				} catch (error: Exception) {
					logger.e("Failed to launch battery optimization settings intent", error)
				}
			}

		}?.show().let {
			// This prevents duplicate dialogs while the current one is visible
			isBatteryOptimizationDialogShowing = true
		}

		logger.d("Battery optimization dialog display process completed")
	}

	/**
	 * Tracks the background coroutine job for update checks to prevent multiple concurrent checks.
	 *
	 * This property manages the lifecycle of the update check operation, allowing it to be
	 * cancelled when no longer needed (e.g., when the activity is destroyed or the user
	 * navigates away). Using a Job reference enables proper coroutine cancellation and
	 * prevents memory leaks from orphaned background tasks.
	 */
	private var updateCheckJob: Job? = null

	/**
	 * Initiates a check for the latest application update with robust error handling and retry logic.
	 *
	 * This method coordinates the entire update checking process, starting with cancellation
	 * of any ongoing checks to prevent duplicates, then executing a new check with intelligent
	 * retry mechanisms for transient network failures. It ensures only one update check runs
	 * at a time and gracefully handles cases where the activity context is unavailable.
	 *
	 * The retry logic specifically targets network-related issues (IOExceptions and timeouts)
	 * while allowing logical errors to fail fast, providing optimal user experience by
	 * automatically recovering from temporary network problems without bothering users.
	 */
	fun checkForLatestUpdate() {
		getActivity()?.let { activity ->
			// Cancel previous update check to prevent duplicates and resource conflicts
			updateCheckJob?.cancel()

			logger.d("Starting optimized checkForLatestUpdate() with retry mechanism")

			// Execute update check with retry mechanism for transient failures
			updateCheckJob = ThreadsUtility.executeWithRetry(
				retryCount = 2,
				retryDelay = 2000L,
				shouldRetry = { error ->
					// Retry only on network/timeout errors, not logical errors
					// This prevents endless retries for permanent failures while
					// automatically recovering from temporary network issues
					error is IOException || error is TimeoutCancellationException
				},
				codeBlock = { performUpdateCheck(activity) },
				onSuccess = { logger.d("Update check completed successfully") },
				onFinalError = { error -> logger.e("Update check failed after retries:", error) }
			)
		} ?: logger.d("safeBaseActivityRef is null — cannot perform update check without activity context")
	}

	/**
	 * Performs the actual update check workflow by coordinating with the AIOUpdater service.
	 *
	 * This method handles the core update checking logic, starting with a quick availability
	 * check to avoid unnecessary processing, then proceeding to download and validate the
	 * update package if available. It efficiently uses background threads for CPU-intensive
	 * operations like hash calculation while maintaining responsive UI performance.
	 *
	 * @param baseActivity The activity context used for UI operations and dialog presentation.
	 *        Required for showing the update dialog to users when a valid update is found.
	 */
	private suspend fun performUpdateCheck(baseActivity: BaseActivity) {
		val updater = AIOUpdater().apply { logger.d("AIOUpdater initialized") }

		// Early return if no update available to avoid unnecessary processing
		// This quick check saves bandwidth and processing time for users
		if (!updater.isNewUpdateAvailable()) {
			logger.d("No new update available — skipping detailed update check")
			return
		}

		logger.d("New update available — proceeding with download and validation")

		// Use executeOnDefault for CPU-intensive hash calculations to avoid blocking UI thread
		val updateResult = ThreadsUtility.executeOnDefault {
			fetchAndValidateUpdate(updater)
		}

		// If valid update found, show the update dialog to user on main thread
		updateResult?.let { (apkFile, updateInfo) ->
			showUpdateDialog(baseActivity, apkFile, updateInfo)
		}
	}

	/**
	 * Fetches and validates an application update by downloading the APK and verifying its integrity.
	 *
	 * This method implements a comprehensive validation pipeline that ensures downloaded updates
	 * are genuine, complete, and safe to install. It performs multiple security checks including
	 * file format validation, size verification, and cryptographic hash matching to prevent
	 * tampering and corruption.
	 *
	 * The validation process follows these steps:
	 * 1. Retrieve the latest APK download URL from the update server
	 * 2. Fetch update metadata (version, changelog, hash) for verification
	 * 3. Download the APK file silently without user interruption
	 * 4. Validate the file structure and APK format integrity
	 * 5. Compute SHA256 hash and compare with server-provided hash
	 * 6. Return validated update package or null if any check fails
	 *
	 * @param updater The updater instance used to fetch update information and download files.
	 * @return A pair containing the downloaded APK file and update info if validation passes,
	 *         or null if any validation step fails, ensuring only safe updates are presented to users.
	 */
	private suspend fun fetchAndValidateUpdate(
		updater: AIOUpdater): Pair<File, AIOUpdater.UpdateInfo>? {
		// Step 1: Get the latest APK download URL from the update server
		val latestAPKUrl = updater.getLatestApkUrl()
		if (latestAPKUrl.isNullOrEmpty()) {
			logger.d("Latest APK URL is null or empty — aborting update check")
			return null
		}
		logger.d("Latest APK URL retrieved: $latestAPKUrl")

		// Step 2: Fetch update metadata (version, hash, changelog, etc.)
		val updateInfo = updater.fetchUpdateInfo()
		if (updateInfo == null) {
			logger.d("UpdateInfo is null — aborting update check due to missing metadata")
			return null
		}
		logger.d("Fetched update info: version=" +
				"${updateInfo.latestVersion}, hash=${updateInfo.versionHash}")

		// Step 3: Download the APK file silently (without user interaction)
		val latestAPKFile = updater.downloadUpdateApkSilently(
			url = latestAPKUrl,
			version = updateInfo.latestVersion.toString()
		) ?: run {
			logger.d("Failed to download latest APK — network or storage issue")
			return null
		}

		// Step 4: Validate the downloaded file is a proper APK with valid structure
		if (!isValidApkFile(latestAPKFile)) {
			logger.d("Downloaded file is not a valid APK — deleting corrupted file")
			latestAPKFile.delete()
			return null
		}

		// Step 5: Verify file integrity using SHA256 hash on background thread
		// This prevents UI freezing during computationally intensive hash calculation
		val fileHash = ThreadsUtility.executeOnDefault {
			getFileSha256(latestAPKFile)
		}

		// Step 6: Compare computed hash with expected hash from server to prevent tampering
		if (fileHash != updateInfo.versionHash) {
			logger.d("SHA256 mismatch! Expected=" +
					"${updateInfo.versionHash}, Got=$fileHash — " +
					"deleting potentially tampered APK")
			latestAPKFile.delete()
			return null
		}

		logger.d("APK hash verified successfully — update is genuine and intact")
		return Pair(latestAPKFile, updateInfo)
	}

	/**
	 * Validates that a file is a legitimate APK file with proper structure and format.
	 *
	 * This method performs basic sanity checks to ensure the downloaded file is actually
	 * an Android application package before proceeding with installation. It verifies:
	 * - File existence and accessibility
	 * - Non-zero file size to detect incomplete downloads
	 * - Correct file extension to filter out non-APK files
	 *
	 * @param file The file to validate as a potential APK package.
	 * @return true if the file exists, is non-empty, has valid file permissions, and
	 *         has an APK extension, indicating it's likely a valid Android package.
	 */
	private fun isValidApkFile(file: File): Boolean {
		return file.exists() &&
				file.isFile &&
				file.length() > 0 &&
				getFileExtension(file.name)?.contains("apk", true) == true
	}

	/**
	 * Displays the update dialog to the user on the main UI thread with proper lifecycle safety.
	 *
	 * This method ensures the update dialog is only shown when the activity is in a valid
	 * state and can properly handle user interactions. It performs comprehensive lifecycle
	 * checks to prevent WindowManager crashes and automatically cleans up downloaded files
	 * when the activity is no longer available to present the dialog.
	 *
	 * The dialog is restricted to MotherActivity (the main activity) to ensure consistent
	 * user experience and proper navigation flow after update installation.
	 *
	 * @param activity The activity context for showing the dialog and handling user input.
	 * @param apkFile The downloaded APK file ready for installation, which will be
	 *        automatically cleaned up if the dialog cannot be shown.
	 * @param updateInfo The update metadata containing version information, release notes,
	 *        and other details to present to the user for update decision.
	 */
	private suspend fun showUpdateDialog(activity: BaseActivity,
		apkFile: File, updateInfo: AIOUpdater.UpdateInfo) {
		ThreadsUtility.executeOnMain(codeBlock = {
			// Safety check: ensure activity is still valid and active to prevent crashes
			if (!isActivityRunning || activity.isFinishing || activity.isDestroyed) {
				logger.d("Activity not running — cleaning up downloaded APK to free storage")
				apkFile.delete()
				return@executeOnMain
			}

			// Only show dialog on MotherActivity (main activity) for consistent UX
			if (activity is MotherActivity) {
				UpdaterDialog(
					weakReferenceOfActivity = WeakReference(activity),
					latestVersionApkFile = apkFile,
					versionInfo = updateInfo
				).show()
				logger.d("UpdaterDialog launched for version=${updateInfo.latestVersion}")
			} else {
				logger.d("Activity is not MotherActivity — cleaning up downloaded APK")
				apkFile.delete()
			}
		})
	}

	/**
	 * Cancels any ongoing update check operation to free resources and prevent unnecessary processing.
	 *
	 * This method is essential for proper resource management when the update check is no longer
	 * needed, such as when the user navigates away from the app, the activity is destroyed, or
	 * the app is backgrounded. It ensures coroutines are properly cancelled to prevent memory
	 * leaks and unnecessary battery/network usage.
	 *
	 * Call this method during activity destruction or when update checks should be interrupted
	 * due to changing application state or user preferences.
	 */
	private fun cancelUpdateCheck() {
		updateCheckJob?.cancel()
		updateCheckJob = null
		logger.d("Update check cancelled and resources released")
	}

	// Timestamp tracking for debouncing update checks to prevent excessive network usage
	private var lastUpdateCheckTime = 0L

	// Minimum interval between update checks (5 minutes) to avoid excessive network calls and battery drain
	private val minUpdateCheckInterval = 5 * 60 * 1000L // 5 minutes in milliseconds

	/**
	 * Performs a debounced update check to prevent excessive checking and conserve resources.
	 *
	 * This method implements a throttling mechanism that prevents update checks from occurring
	 * too frequently, which could waste network bandwidth, drain battery, and annoy users with
	 * constant update notifications. It maintains a 5-minute minimum interval between checks
	 * while still allowing manual update checks when specifically requested by users.
	 *
	 * The debouncing is particularly important for automatic checks triggered by app startup
	 * or resume events, ensuring the app respects user resources and network conditions.
	 */
	private fun checkForLatestUpdateDebounced() {
		val currentTime = System.currentTimeMillis()

		// Check if minimum interval has elapsed since last check to prevent spam
		if (currentTime - lastUpdateCheckTime < minUpdateCheckInterval) {
			logger.d("Skipping update check - too soon since last check (throttled)")
			return
		}

		// Update timestamp and proceed with check to reset the throttle window
		lastUpdateCheckTime = currentTime
		checkForLatestUpdate()
	}
}
