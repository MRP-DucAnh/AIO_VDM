@file:Suppress("DEPRECATION")

package app.core.bases

import android.Manifest.permission.*
import android.annotation.*
import android.content.*
import android.content.Intent.*
import android.content.pm.ActivityInfo.*
import android.content.res.*
import android.graphics.*
import android.os.*
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
import android.view.*
import android.view.View.*
import android.view.WindowInsetsController.*
import android.view.WindowManager.LayoutParams.*
import android.view.inputmethod.*
import android.widget.*
import androidx.activity.*
import androidx.annotation.*
import androidx.core.content.ContextCompat.*
import androidx.core.graphics.drawable.*
import androidx.core.net.*
import androidx.core.view.*
import androidx.lifecycle.*
import app.core.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioAdblocker
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.aioTimer
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOTimer.*
import app.core.bases.dialogs.*
import app.core.bases.interfaces.*
import app.core.bases.language.*
import app.core.engines.services.*
import app.core.engines.updater.*
import app.core.engines.updater.AIOSelfDestruct.shouldSelfDestructApplication
import app.ui.main.*
import app.ui.others.startup.*
import com.aio.R
import com.anggrayudi.storage.*
import com.permissionx.guolindev.PermissionX.*
import kotlinx.coroutines.*
import lib.device.DateTimeUtils.calculateTime
import lib.files.FileSystemUtility.getFileExtension
import lib.files.FileSystemUtility.getFileSha256
import lib.process.*
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.process.CommonTimeUtils.delay
import lib.ui.*
import lib.ui.ActivityAnimator.animActivityFade
import lib.ui.ActivityAnimator.animActivitySwipeRight
import lib.ui.MsgDialogUtils.showMessageDialog
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.builders.ToastView.Companion.showToast
import java.io.*
import java.lang.System
import java.lang.Thread.*
import java.lang.ref.*
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.Boolean
import kotlin.Double
import kotlin.Exception
import kotlin.Int
import kotlin.Pair
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.apply
import kotlin.collections.ArrayList
import kotlin.collections.isNotEmpty
import kotlin.getValue
import kotlin.lazy
import kotlin.let
import kotlin.system.*
import kotlin.toString

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
abstract class BaseActivity : LocaleActivityImpl(), BaseActivityInf, AIOTimerListener {

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
	 * Coroutine scope tied to the activity's lifecycle for managing concurrent operations.
	 *
	 * This scope automatically cancels all launched coroutines when the activity is destroyed,
	 * preventing memory leaks and ensuring proper cleanup of background operations. It provides
	 * a structured concurrency model for asynchronous tasks within the activity.
	 */
	val activityCoroutineScope get() = lifecycleScope

	/**
	 * Local loop counter for tracking iteration limits and preventing infinite loops.
	 *
	 * This counter is typically used in recursive algorithms, polling mechanisms, or iterative
	 * operations where a safety mechanism is needed to prevent runaway execution. It helps
	 * ensure operations terminate within reasonable bounds.
	 */
	private var localLoopCounter = AtomicInteger(0)

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

	private var lastUpdateCheckTime = 0L
	private val minUpdateCheckInterval = 5 * 60 * 1000L

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
	 * Handles periodic timer ticks to track and persist the application's total foreground usage time.
	 *
	 * This method implements a batching strategy to minimize storage I/O overhead. It accumulates
	 * local timer ticks and only updates persistent storage once a threshold (50 ticks, representing
	 * approximately 10 seconds) is reached. When the threshold is met, it calculates the elapsed time,
	 * updates the formatted time string in settings, persists the data, and resets the local counter.
	 *
	 * This approach balances accurate time tracking with performance by avoiding frequent disk writes.
	 *
	 * @param loopCount The number of times the global timer has looped (passed but not strictly used in logic here).
	 */
	override fun onAIOTimerTick(loopCount: Double) {
		try {
			localLoopCounter.incrementAndGet()
			if (localLoopCounter.get() >= 50) {
				aioSettings.foregroundUsageDurationMs += ((localLoopCounter.get() * 200)).toFloat()
				aioSettings.foregroundUsageDurationFormatted =
					calculateTime(aioSettings.foregroundUsageDurationMs)
				localLoopCounter.set(0)
			}
		} catch (error: Exception) {
			logger.e("onAIOTimerTick() error: ${error.message}", error)
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
			val weakReferenceOfCrashHandler = WeakReference(AIOCrashHandler())
			setDefaultUncaughtExceptionHandler(weakReferenceOfCrashHandler.get())

			// Configure theme appearance based on user preferences or system settings
			// Ensures consistent visual experience across the application
			logger.d("Applying theme appearance from user preferences")
			getAttachedCoroutineScope().launch {
				setThemeAppearance()
			}

			// Initialize scoped storage helper for modern file access on Android 10+
			// Handles permissions and provides abstraction for storage operations
			logger.d("Initializing ScopedStorageHelper for file system access")
			scopedStorageHelper = SimpleStorageHelper(activity)

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

	override fun onPostCreate(savedInstanceState: Bundle?) {
		super.onPostCreate(savedInstanceState)
		onAfterLayoutRender()
		if ((onRenderingLayout() > -1) == false) return
		getAttachedCoroutineScope().launch {
			checkForLatestUpdateDebounced()
		}
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
			aioTimer.register(activity)
			logger.d("Activity marked as running and ready for user interaction")

			// Ensure permissions are granted or request if needed
			// Critical for features that require runtime permissions to function properly
			logger.d("Checking and requesting required permissions for app functionality")
			requestForPermissionIfRequired()

			// Request user to disable battery optimization for reliable background operations
			// This ensures downloads and other background tasks aren't interrupted by the system
			logger.d("Requesting user to disable battery optimization for uninterrupted service")
			getAttachedCoroutineScope().launch {
				requestForDisablingBatteryOptimization()
			}

			// Update the state of foreground services to ensure they're running correctly
			// Important for ongoing downloads and other persistent operations
			logger.d("Updating foreground service state for background tasks")
			AIOApp.applicationScope.launch(Dispatchers.IO) {
				AIOForegroundService.updateService()
			}

			// Validate user-selected folders to ensure they're still accessible
			// Prevents issues with storage permissions changes or folder deletion
			logger.d("Validating user-selected download folder accessibility")
			getAttachedCoroutineScope().launch {
				aioSettings.validateUserSelectedFolder()
			}

			// Initialize YouTube-DLP engine for video downloading capabilities
			// Ensures the video download functionality is ready when needed
			logger.d("Initializing YtDLP engine for video download operations")
			INSTANCE.initializeYtDLP()

			// Invoke any subclass-specific resume logic for specialized behavior
			// Allows child activities to perform their own initialization
			logger.d("Calling subclass onResumeActivity() for custom initialization")
			onResumeActivity()

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
	 * Called when the activity is being placed in the background but has not yet been stopped.
	 *
	 * This method is the counterpart to `onResume()` and is typically called when the user
	 * navigates away from the activity, a new activity starts on top of it, or the
	 * screen is turned off. It marks the activity as no longer running in the foreground
	 * and provides a hook for subclasses to perform any necessary cleanup or state saving.
	 *
	 * Key actions performed:
	 * - Sets `isActivityRunning` to `false` to indicate the activity is no longer interactive.
	 * - Calls the `onPauseActivity()` template method, allowing subclasses to implement
	 *   custom logic for pausing operations, such as stopping animations or saving draft data.
	 *
	 * After this method completes, the system may either resume the activity (by calling `onResume()`)
	 * or stop it completely (by calling `onStop()`).
	 *
	 * @see onResume
	 * @see onPauseActivity
	 */
	override fun onPause() {
		super.onPause()
		isActivityRunning = false
		getActivity()?.let { aioTimer.unregister(it) }
		onPauseActivity()
	}

	/**
	 * Performs final cleanup when the activity is being destroyed.
	 *
	 * This method is called by the system when the activity is permanently removed from memory,
	 * either due to the user finishing it, a configuration change, or the system reclaiming
	 * resources. It is crucial for releasing all resources and references to prevent memory leaks
	 * and ensure a clean shutdown.
	 *
	 * Cleanup actions performed:
	 * - Marks the activity as not running.
	 * - Nullifies references to helpers and listeners (`scopedStorageHelper`, `permissionCheckListener`)
	 *   to allow garbage collection.
	 * - Cancels the background coroutine scope (`activityJob`) to stop all associated jobs.
	 * - Cancels any ongoing update checks to prevent orphaned background tasks.
	 * - Releases the `vibrator` service if it was initialized.
	 * - Unregisters the activity from the download UI manager to prevent callbacks to a destroyed
	 *   context.
	 *
	 * This ensures that no long-lived references or background tasks tied to the activity
	 * persist after its destruction.
	 */
	override fun onDestroy() {
		isActivityRunning = false
		scopedStorageHelper = null
		permissionCheckListener = null

		getAttachedCoroutineScope().launch { cancelUpdateCheck() }

		if (vibrator?.hasVibrator() == true) {
			vibrator?.cancel()
		}

		if (getActivity() as? MotherActivity != null) {
			val downloadUIManager = downloadSystem.downloadsUIManager
			downloadUIManager.safeMotherActivity = null
		}
		super.onDestroy()
	}

	/**
	 * Template method called during the `onPause` lifecycle event of the activity.
	 *
	 * Subclasses can override this method to perform specific actions when the activity
	 * is about to enter a paused state, such as saving transient UI state, stopping
	 * animations, or releasing resources that are not needed while the activity is
	 * not in the foreground.
	 *
	 * This method is called after the base `onPause` logic completes, ensuring that
	 * the activity's `isActivityRunning` flag has already been set to false.
	 *
	 * The default implementation is empty, providing an optional hook for subclasses.
	 */
	override fun onPauseActivity() = Unit

	/**
	 * A template method called from [onResume] to allow subclasses to perform custom
	 * initialization when the activity becomes interactive.
	 *
	 * Subclasses can override this method to execute specific logic after the core
	 * onResume tasks (like permission checks, service updates, and UI state restoration)
	 * have been completed in the base class. This provides a clean extension point
	 * for child activities to perform their own on-resume actions without overriding
	 * the entire `onResume` method and risking an incorrect implementation.
	 *
	 * The default implementation is empty.
	 *
	 * Example Usage:
	 * ```kotlin
	 * override fun onResumeActivity() {
	 *     // Refresh UI components with the latest data
	 *     viewModel.loadData()
	 * }
	 * ```
	 */
	override fun onResumeActivity() = Unit

	/**
	 * Customizes the colors and icon themes of the system bars (status bar and navigation bar).
	 *
	 * This method provides a unified API to theme the system bars across different Android
	 * versions, handling the complexities of modern `WindowInsetsController` (Android 11+) and
	 * legacy `systemUiVisibility` flags. It allows setting the background color and toggling
	 * the icon theme (light or dark) for both the status bar and the navigation bar.
	 *
	 * On Android 11 (API 30) and newer, it uses the `WindowInsetsController` API for a more
	 * reliable and modern approach to controlling system bar appearance. On older versions,
	 * it manipulates the `systemUiVisibility` bit-flags to achieve the same effect.
	 *
	 * @param statusBarColorResId The color resource ID (e.g., `R.color.my_color`) to set as the
	 *        status bar's background color.
	 * @param navigationBarColorResId The color resource ID for the navigation bar's background
	 *        color.
	 * @param isLightStatusBar If `true`, the status bar icons (like clock, battery, and
	 *        notifications) will be dark, suitable for a light-colored status bar background.
	 *        If `false`, they will be light.
	 * @param isLightNavigationBar If `true`, the navigation bar icons (like back, home, and
	 *        recents) will be dark, suitable for a light-colored navigation bar background.
	 *        If `false`, they will be light.
	 */
	override fun setSystemBarsColors(
		statusBarColorResId: Int,
		navigationBarColorResId: Int,
		isLightStatusBar: Boolean,
		isLightNavigationBar: Boolean,
	) {
		// Get the current window of the activity to modify system UI elements.
		val activityWindow = window
		// Set the background color of the status bar and navigation bar.
		activityWindow.statusBarColor = getColor(this, statusBarColorResId)
		activityWindow.navigationBarColor = getColor(this, navigationBarColorResId)

		// Get the DecorView, which is the root view of the window, to manipulate system UI flags.
		val decorView = activityWindow.decorView

		// For Android 11 (API 30) and above, use the modern WindowInsetsController API.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val insetsController = activityWindow.insetsController
			// Configure the appearance of the status bar icons (e.g., clock, battery).
			// If isLightStatusBar is true, icons become dark for light backgrounds.
			insetsController?.setSystemBarsAppearance(
				if (isLightStatusBar) APPEARANCE_LIGHT_STATUS_BARS else 0,
				APPEARANCE_LIGHT_STATUS_BARS
			)
			// Configure the appearance of the navigation bar icons (e.g., back, home).
			// If isLightNavigationBar is true, icons become dark for light backgrounds.
			insetsController?.setSystemBarsAppearance(
				if (isLightNavigationBar) APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
				APPEARANCE_LIGHT_NAVIGATION_BARS
			)
		} else {
			// For older versions (below Android 11), use the legacy systemUiVisibility flags.
			// This is deprecated but necessary for backward compatibility.
			if (isLightStatusBar) {
				// Add the flag to make status bar icons dark.
				decorView.systemUiVisibility =
					decorView.systemUiVisibility or
						SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
			} else {
				// Remove the flag to make status bar icons light (default).
				decorView.systemUiVisibility =
					decorView.systemUiVisibility and
						SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
			}

			if (isLightNavigationBar) {
				// Add the flag to make navigation bar icons dark.
				decorView.systemUiVisibility =
					decorView.systemUiVisibility or
						SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
			} else {
				// Remove the flag to make navigation bar icons light (default).
				decorView.systemUiVisibility =
					decorView.systemUiVisibility and
						SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
			}
		}
	}

	/**
	 * Handles configuration changes, such as screen rotation, keyboard availability, or locale changes.
	 *
	 * This method is called by the system when a device configuration changes while the activity is running.
	 * The primary responsibility of this override is to ensure that the application's localization is
	 * reapplied correctly after such a change. It calls `openPrepareLocalize()` to reload language-specific
	 * resources and update the UI accordingly.
	 *
	 * After handling localization, it calls the superclass implementation to allow the default system
	 * behavior for configuration changes to proceed.
	 *
	 * @param newConfiguration The new device configuration.
	 */
	override fun onConfigurationChanged(newConfiguration: Configuration) {
		if (skipConfigurationUpdates == false) openPrepareLocalize()
		super.onConfigurationChanged(newConfiguration)
	}

	/**
	 * Hides the soft keyboard when a touch event occurs outside of a focused `EditText`.
	 *
	 * This override intercepts all touch events at the activity level. When a user taps
	 * the screen (`ACTION_DOWN`), it checks if the currently focused view is an `EditText`.
	 * If it is, the method determines if the tap occurred outside the visible bounds of that
	 * `EditText`. If the tap is outside, it clears the focus from the `EditText` and
	 * explicitly hides the soft keyboard.
	 *
	 * This provides a common and intuitive user experience, allowing users to dismiss the
	 * keyboard by simply tapping anywhere else on the screen.
	 *
	 * @param motionEvent The motion event being dispatched.
	 * @return `true` to consume the event, or the result of the superclass implementation
	 *         to allow normal event propagation.
	 */
	override fun dispatchTouchEvent(motionEvent: MotionEvent): Boolean {
		// Check if the user has just tapped the screen.
		if (motionEvent.action == MotionEvent.ACTION_DOWN) {
			// Get the view that currently has focus.
			val focusedView = currentFocus

			// If the focused view is an EditText, check if the tap was outside of it.
			if (focusedView is EditText) {
				val outRect = Rect()
				// Get the visible screen coordinates of the EditText.
				focusedView.getGlobalVisibleRect(outRect)

				// Check if the tap's coordinates are outside the bounds of the EditText.
				if (!outRect.contains(motionEvent.rawX.toInt(), motionEvent.rawY.toInt())) {
					// If the tap was outside, clear the EditText's focus.
					focusedView.clearFocus()
					// And hide the soft keyboard.
					val service = getSystemService(INPUT_METHOD_SERVICE)
					val imm = service as InputMethodManager
					imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
				}
			}
		}
		// Call the superclass implementation to ensure normal touch event processing.
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

			init(activity)
				.permissions(permissions)

				// Show explanation dialog when permissions are initially denied
				// This educates the user about why the permissions are needed
				.onExplainRequestReason { callback, deniedList ->
					logger.d("Showing explanation dialog for denied permissions: $deniedList")
					callback.showRequestReasonDialog(
						permissions = deniedList,
						message = getString(R.string.title_storage_permissions),
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

	override fun openActivity(targetActivity: Class<*>, shouldAnimate: Boolean) {
		getActivity()?.let { activity ->
			val intent = Intent(activity, targetActivity).apply {
				flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
			}

			startActivity(intent)
			if (shouldAnimate) getAttachedCoroutineScope().launch {
				animActivityFade(activity)
			}
		}
	}

	override fun closeActivityWithSwipeAnimation(shouldAnimate: Boolean) {
		getActivity()?.apply {
			finish()
			if (shouldAnimate) {
				getAttachedCoroutineScope().launch {
					animActivitySwipeRight(getActivity())
				}
			}
		}
	}

	override fun closeActivityWithFadeAnimation(shouldAnimate: Boolean) {
		getActivity()?.apply {
			finish()
			if (shouldAnimate) {
				getAttachedCoroutineScope().launch {
					animActivityFade(getActivity())
				}
			}
		}
	}

	override fun exitActivityOnDoubleBackPress() {
		if (isBackButtonEventFired == 0) {
			showToast(getActivity(), R.string.title_press_back_button_to_exit)
			isBackButtonEventFired = 1

			delay(2000, object : OnTaskFinishListener {
				override fun afterDelay() {
					isBackButtonEventFired = 0
				}
			})
		} else if (isBackButtonEventFired == 1) {
			isBackButtonEventFired = 0
			closeActivityWithSwipeAnimation(true)
		}
	}

	override fun forceQuitApplication() {
		Process.killProcess(Process.myPid())
		exitProcess(0)
	}

	override fun openAppInfoSetting() {
		val packageName = this.packageName
		val uri = "package:$packageName".toUri()
		val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS, uri)
		startActivity(intent)
	}

	override fun openApplicationOfficialSite() {
		try {
			val uri = APP_OFFICIAL_SITE
			startActivity(Intent(ACTION_VIEW, uri.toUri()))
		} catch (error: Exception) {
			error.printStackTrace()
			showToast(getActivity(), R.string.title_please_install_web_browser)
		}
	}

	override fun getTimeZoneId(): String {
		val timeZoneId = TimeZone.getDefault().id
		return timeZoneId
	}

	override fun getActivity(): BaseActivity? {
		return weakReferenceOfActivity?.get()
	}

	override fun getAttachedCoroutineScope(): CoroutineScope {
		return activityCoroutineScope
	}

	override fun runCodeOnAttachedThread(isUIThread: Boolean, codeBlock: () -> Unit) {
		if (isUIThread) getAttachedCoroutineScope().launch { codeBlock.invoke() }
		else getAttachedCoroutineScope().launch {
			withContext(Dispatchers.IO) { codeBlock.invoke() }
		}
	}

	open fun clearWeakActivityReference() {
		weakReferenceOfActivity?.clear()
		weakReferenceOfActivity = null
	}

	override fun doSomeVibration(timeInMillis: Int) {
		if (vibrator?.hasVibrator() == true) {
			vibrator?.vibrate(
				VibrationEffect.createOneShot(
					timeInMillis.toLong(),
					VibrationEffect.DEFAULT_AMPLITUDE
				)
			)
		}
	}

	override fun getSingleTopIntentFlags(): Int {
		val flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
		return flags
	}

	fun showUpcomingFeatures() {
		doSomeVibration()
		getActivity()?.let { activityRef ->
			activityRef.getAttachedCoroutineScope().launch {
				showMessageDialog(
					baseActivityInf = activityRef,
					isTitleVisible = true,
					titleText = getString(R.string.title_feature_isnt_implemented),
					isNegativeButtonVisible = false,
					positiveButtonText = getString(R.string.title_okay),
					messageTextViewCustomize = { messageTextView ->
						messageTextView.setText(R.string.text_feature_isnt_available_yet)
					},

					titleTextViewCustomize = { titleTextView ->
						val colorResId = R.color.color_green
						val color = activityRef.resources.getColor(colorResId, null)
						titleTextView.setTextColor(color)
					},

					positiveButtonTextCustomize = { positiveButton: TextView ->
						val drawable = getDrawable(applicationContext, R.drawable.ic_okay_done)
						drawable?.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
						positiveButton.setCompoundDrawables(drawable, null, null, null)
					}
				)
			}
		}
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
							logger.d(
								"Activity is OpeningActivity — " +
									"skipping permission request to improve first-run experience"
							)
							return
						}

						val permissions = getRequiredPermissionsBySDKVersion()
						logger.d("Permissions required by SDK version: $permissions")

						// Check if any required permissions are not granted
						// This includes notification and storage permissions based on Android version
						if (permissions.isNotEmpty() ||
							!isGranted(activity, POST_NOTIFICATIONS) ||
							!isGranted(activity, MANAGE_EXTERNAL_STORAGE) ||
							!isGranted(activity, WRITE_EXTERNAL_STORAGE)
						) {
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
				logger.d(
					"Permission check already active — " +
						"skipping duplicate request to avoid conflicts"
				)
			}
		} ?: logger.d(
			"Activity reference is null — " +
				"cannot request permissions without valid context"
		)
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

	suspend fun setThemeAppearance() {
		getActivity()?.let { activity ->
			ViewUtility.changesSystemTheme(activity)
		}
	}

	fun isDarkModeActive(): Boolean {
		val uiMode = resources.configuration.uiMode
		val currentNightMode = uiMode and Configuration.UI_MODE_NIGHT_MASK
		val isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
		return isDark
	}

	fun setDarkSystemBarTheme() {
		setSystemBarsColors(
			statusBarColorResId = R.color.color_primary,
			navigationBarColorResId = R.color.color_primary,
			isLightStatusBar = false,
			isLightNavigationBar = false
		)
	}

	fun setEdgeToEdgeFullscreen() {
		WindowCompat.setDecorFitsSystemWindows(window, false)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			window.insetsController?.let {
				it.hide(WindowInsets.Type.systemBars())
				it.systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			}
		} else {
			window.decorView.systemUiVisibility = (SYSTEM_UI_FLAG_LAYOUT_STABLE
				or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				or SYSTEM_UI_FLAG_HIDE_NAVIGATION
				or SYSTEM_UI_FLAG_FULLSCREEN
				or SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
		}

		WindowCompat.getInsetsController(window, window.decorView).let { controller ->
			controller.hide(WindowInsetsCompat.Type.systemBars())
			controller.systemBarsBehavior =
				WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}
	}

	fun disableEdgeToEdge() {
		WindowCompat.setDecorFitsSystemWindows(window, true)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			window.insetsController?.let {
				it.show(WindowInsets.Type.systemBars())
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					it.systemBarsBehavior = BEHAVIOR_DEFAULT
				}
			}
		} else {
			val flags = (SYSTEM_UI_FLAG_LAYOUT_STABLE
				or SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				or SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
			window.decorView.systemUiVisibility = flags
		}

		WindowCompat.getInsetsController(window, window.decorView).let { controller ->
			controller.show(WindowInsetsCompat.Type.systemBars())
			controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
		}
	}

	fun setEdgeToEdgeCustomCutoutColor(@ColorInt color: Int) {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		window.statusBarColor = color
		window.navigationBarColor = color
		window.attributes.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			window.setBackgroundDrawable(color.toDrawable())
		}
	}

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
	suspend fun requestForDisablingBatteryOptimization() {
		logger.d("requestForDisablingBatteryOptimization() called")

		// Guard clause: Only show if user has experienced successful downloads

		// Only proceed further if user has not explicitly mentioned to skip it
		if (aioSettings.hasSkippedBatteryOptimization) {
			logger.d("Skipping — no successful downloads yet")
			return
		}

		// This proves the app's value before asking for special permissions
		if (aioSettings.successfulDownloadCount < 1) {
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
				getActivity()?.getAttachedCoroutineScope()?.launch {
					it.setText(R.string.title_disable_now)
					it.setLeftSideDrawable(R.drawable.ic_button_arrow_next)
				}
			},
			negativeButtonTextCustomize = {
				getActivity()?.activityCoroutineScope?.launch {
					it.setText(R.string.title_not_now)
					it.setLeftSideDrawable(R.drawable.ic_button_cancel)
				}
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
				aioSettings.hasSkippedBatteryOptimization = true
				aioSettings.updateInDB()
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
		}
			?: logger.d("safeBaseActivityRef is null — cannot perform update check without activity context")
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

	private suspend fun fetchAndValidateUpdate(updater: AIOUpdater):
		Pair<File, AIOUpdater.UpdateInfo>? {
		val latestAPKUrl = updater.getLatestApkUrl()
		if (latestAPKUrl.isNullOrEmpty()) return null
		logger.d("Latest APK URL retrieved: $latestAPKUrl")

		val updateInfo = updater.fetchUpdateInfo() ?: return null
		logger.d(
			"Fetched update info: version=" +
				"${updateInfo.latestVersion}, hash=${updateInfo.versionHash}"
		)

		val latestAPKFile = updater.downloadUpdateApkSilently(
			url = latestAPKUrl,
			version = updateInfo.latestVersion.toString(),
			serverHash = updateInfo.versionHash
		) ?: return null

		if (!isValidApkFile(latestAPKFile)) {
			latestAPKFile.delete()
			return null
		}

		val fileHash = ThreadsUtility.executeOnDefault {
			getFileSha256(latestAPKFile)
		}

		if (fileHash != updateInfo.versionHash) {
			logger.d(
				"SHA256 mismatch! Expected=" +
					"${updateInfo.versionHash}, Got=$fileHash — " +
					"deleting potentially tampered APK"
			)
			latestAPKFile.delete()
			return null
		}

		logger.d("APK hash verified successfully — update is genuine and intact")
		return Pair(latestAPKFile, updateInfo)
	}

	private suspend fun isValidApkFile(file: File): Boolean {
		return file.exists() &&
			file.isFile &&
			file.length() > 0 &&
			getFileExtension(file.name)
				?.contains("apk", true) == true
	}

	private suspend fun showUpdateDialog(
		activity: BaseActivity,
		apkFile: File,
		updateInfo: AIOUpdater.UpdateInfo
	) {
		withIOContext {
			if (!isActivityRunning ||
				activity.isFinishing ||
				activity.isDestroyed
			) {
				apkFile.delete()
				return@withIOContext
			}

			if (activity is MotherActivity) {
				UpdaterDialog(
					activityInf = activity,
					latestVersionApkFile = apkFile,
					versionInfo = updateInfo
				).initialize().show()
			} else {
				apkFile.delete()
			}
		}
	}

	private suspend fun cancelUpdateCheck() {
		updateCheckJob?.cancel()
		updateCheckJob = null
	}

	private suspend fun checkForLatestUpdateDebounced() {
		getActivity()?.let { activityRef ->
			if (activityRef is MotherActivity) {
				val currentTime = System.currentTimeMillis()
				if (currentTime - lastUpdateCheckTime < minUpdateCheckInterval) return
				lastUpdateCheckTime = currentTime
				checkForLatestUpdate()
			}
		}
	}
}
