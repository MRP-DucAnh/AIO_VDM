package app.core.bases.interfaces

import app.core.bases.BaseActivity

/**
 * Defines a comprehensive contract for base activities to implement standardized behaviors
 * across the application's activity hierarchy.
 *
 * This interface establishes a consistent API for common Android activity operations,
 * ensuring that all activities in the application follow the same patterns for lifecycle
 * management, navigation, permissions, system UI customization, and user interactions.
 * By implementing this interface, activities gain access to a standardized set of
 * functionality while maintaining the flexibility to override specific behaviors.
 *
 * The interface covers five main categories of functionality:
 * 1. Lifecycle Management - Template methods for activity lifecycle events
 * 2. Navigation & Transitions - Activity launching and closing with animations
 * 3. System Integration - Permissions, system UI, and device features
 * 4. User Experience - Haptic feedback, back press handling, and exit flows
 * 5. Utility Operations - Common helpers for time zones, app info, and intent flags
 *
 * Implementing classes should use BaseActivity as the foundation, which provides
 * default implementations for most of these methods while allowing customization
 * through the template method pattern.
 */
interface BaseActivityInf {

	/**
	 * Called during activity creation to provide the layout resource ID for content rendering.
	 *
	 * This template method allows implementing activities to specify their visual structure
	 * without overriding onCreate(). The returned layout resource will be automatically
	 * inflated and set as the activity's content view during the base activity initialization.
	 *
	 * @return The layout resource ID (e.g., R.layout.activity_main) that defines the
	 *         activity's user interface structure and visual components.
	 */
	fun onRenderingLayout(): Int

	/**
	 * Called immediately after the activity's layout has been inflated and rendered.
	 *
	 * This lifecycle hook provides the ideal point for view initialization, binding,
	 * and event listener setup. Unlike onCreate(), this method guarantees that all
	 * views in the layout hierarchy are fully inflated and accessible via findViewById().
	 *
	 * Typical use cases include:
	 * - Initializing RecyclerView adapters and setting layout managers
	 * - Configuring ViewPagers, TabLayouts, and other complex UI components
	 * - Setting click listeners, text watchers, and other view interactions
	 * - Restoring UI state from saved instance state or ViewModels
	 * - Setting up data binding and observing LiveData objects
	 */
	fun onAfterLayoutRender()

	/**
	 * Called when the activity enters the resumed state and becomes interactive.
	 *
	 * This template method is invoked from the activity's onResume() lifecycle callback,
	 * providing a dedicated hook for resume-specific logic. It's ideal for operations
	 * that should occur every time the activity returns to the foreground, such as:
	 * - Refreshing data from repositories or ViewModels
	 * - Starting or resuming animations and media playback
	 * - Registering broadcast receivers or event listeners
	 * - Updating UI elements based on latest application state
	 * - Requesting location updates or sensor data
	 */
	fun onResumeActivity()

	/**
	 * Called when the activity enters the paused state and loses user interaction.
	 *
	 * This template method is invoked from the activity's onPause() lifecycle callback,
	 * providing a dedicated hook for pause-specific cleanup. It's ideal for operations
	 * that should occur every time the activity moves to the background, such as:
	 * - Pausing or stopping animations to conserve resources
	 * - Unregistering broadcast receivers or event listeners
	 * - Persisting transient UI state or user progress
	 * - Releasing camera, sensor, or other system resources
	 * - Stopping media playback or background operations
	 */
	fun onPauseActivity()

	/**
	 * Called when the system back button is pressed, allowing custom back navigation.
	 *
	 * This method enables activities to override the default back button behavior
	 * to implement custom navigation flows, confirmation dialogs, or special handling.
	 * The implementation should either handle the back press completely or delegate
	 * to the default behavior by calling super.onBackPressed() when appropriate.
	 *
	 * Common use cases include:
	 * - Showing confirmation dialogs for destructive actions
	 * - Implementing custom fragment back stack management
	 * - Saving draft content before exiting
	 * - Navigating to a different activity instead of finishing
	 */
	fun onBackPressActivity()

	/**
	 * Launches a runtime permission request dialog for the specified permissions.
	 *
	 * This method handles the complete permission request flow including explanation
	 * dialogs, system permission prompts, and result handling. It should implement
	 * appropriate fallback behavior for when permissions are denied and provide
	 * clear user education about why permissions are needed.
	 *
	 * @param permissions A list of permission strings to request from the user,
	 *                    typically from Manifest.permission class (e.g., CAMERA,
	 *                    READ_EXTERNAL_STORAGE, ACCESS_FINE_LOCATION). The list
	 *                    should be generated based on feature requirements and
	 *                    Android version compatibility.
	 */
	fun launchPermissionRequest(permissions: ArrayList<String>)

	/**
	 * Configures the appearance of system bars (status bar and navigation bar).
	 *
	 * This method provides comprehensive control over system bar theming to match
	 * the application's design language. It handles both color customization and
	 * icon appearance (light/dark) across different Android versions, ensuring
	 * consistent visual presentation while following platform guidelines.
	 *
	 * @param statusBarColorResId Resource ID of the desired status bar background color.
	 *                            Use transparent colors for edge-to-edge designs or
	 *                            opaque colors for traditional app bar integration.
	 * @param navigationBarColorResId Resource ID of the desired navigation bar background
	 *                                color. Should coordinate with status bar for visual
	 *                                harmony and brand consistency.
	 * @param isLightStatusBar Whether to use light-colored content (dark icons) on the
	 *                         status bar. Set to true for light backgrounds, false for
	 *                         dark backgrounds to ensure proper contrast and readability.
	 * @param isLightNavigationBar Whether to use light-colored content (dark icons) on
	 *                             the navigation bar. Follows the same contrast principles
	 *                             as the status bar parameter for consistent theming.
	 */
	fun setSystemBarsColors(
		statusBarColorResId: Int,
		navigationBarColorResId: Int,
		isLightStatusBar: Boolean = false,
		isLightNavigationBar: Boolean = false
	)

	/**
	 * Navigates to another activity with configurable transition animations.
	 *
	 * This method provides a standardized approach to activity navigation with
	 * proper intent configuration and optional visual transitions. It should
	 * handle common intent flags and ensure consistent navigation behavior
	 * throughout the application.
	 *
	 * @param activity The class of the target activity to launch (e.g.,
	 *                 SettingsActivity::class.java). The activity must be
	 *                 declared in the AndroidManifest.xml with proper intent filters.
	 * @param shouldAnimate Whether to apply a transition animation when opening
	 *                      the activity. Set to true for user-initiated navigation
	 *                      where smooth transitions enhance UX, false for
	 *                      programmatic navigation where immediate response is
	 *                      preferred over visual polish.
	 */
	fun openActivity(activity: Class<*>, shouldAnimate: Boolean = true)

	/**
	 * Closes the current activity with an intuitive swipe-right animation.
	 *
	 * This method provides a natural closing experience that mimics the system
	 * back gesture, reinforcing the user's mental model of navigation. The
	 * swipe animation creates the visual impression of sliding the activity
	 * off-screen to the right, which aligns with typical back navigation patterns.
	 *
	 * @param shouldAnimate Whether to apply the swipe-right closing animation.
	 *                      Set to true for user-initiated back actions where
	 *                      the animation provides valuable visual feedback,
	 *                      false for programmatic closures where immediate
	 *                      response is more important than animation.
	 */
	fun closeActivityWithSwipeAnimation(shouldAnimate: Boolean = false)

	/**
	 * Closes the current activity with a subtle fade-out animation.
	 *
	 * This method provides a smooth, polished closing experience suitable for
	 * modal interactions or when the swipe animation would feel inappropriate.
	 * The fade animation creates a gentle visual transition that doesn't imply
	 * directional navigation, making it versatile for various closure scenarios.
	 *
	 * @param shouldAnimate Whether to apply the fade-out closing animation.
	 *                      Set to true for graceful exits where visual continuity
	 *                      is important, false for immediate programmatic
	 *                      closures during error handling or rapid navigation.
	 */
	fun closeActivityWithFadeAnimation(shouldAnimate: Boolean = false)

	/**
	 * Implements double-back-press detection to prevent accidental activity exits.
	 *
	 * This method provides a common UX pattern where users must press the back
	 * button twice within a short timeframe to confirm exit intentions. It
	 * typically shows a toast message on the first press and closes the activity
	 * on the second press, preventing accidental closures while maintaining
	 * easy exit access for intentional actions.
	 */
	fun exitActivityOnDoubleBackPress()

	/**
	 * Forcefully terminates the entire application process immediately.
	 *
	 * This method should be used sparingly and only in specific scenarios where
	 * graceful shutdown is not possible or appropriate. It bypasses normal
	 * activity lifecycle callbacks and immediately kills the process, which
	 * can be useful for:
	 * - Critical security concerns requiring immediate termination
	 * - Unrecoverable application errors or corrupted state
	 * - User-initiated emergency exit from sensitive contexts
	 * - Testing and development scenarios
	 *
	 * WARNING: This method does not save state or perform cleanup operations.
	 * Use normal activity finishing methods for routine navigation and closures.
	 */
	fun forceQuitApplication()

	/**
	 * Opens the application's information screen in system settings.
	 *
	 * This method provides direct access to the system app info screen where
	 * users can manage permissions, clear data, check storage usage, force stop
	 * the application, or uninstall it. This is particularly useful for helping
	 * users troubleshoot permission issues or storage problems without navigating
	 * through the complex settings hierarchy.
	 */
	fun openAppInfoSetting()

	/**
	 * Opens the application's official page in the Google Play Store or website.
	 *
	 * This method launches the app's presence in the Play Store for updates,
	 * ratings, reviews, or additional information. If the Play Store is not
	 * available, it should fall back to opening the official website in a
	 * browser. This is commonly used for:
	 * - Directing users to update the application
	 * - Encouraging ratings and reviews
	 * - Showing additional app information or support
	 * - Promoting other applications from the same developer
	 */
	fun openApplicationOfficialSite()

	/**
	 * Retrieves the device's current time zone identifier for localization.
	 *
	 * This method returns the IANA time zone ID (e.g., "America/New_York",
	 * "Europe/London") that represents the device's configured time zone.
	 * This is essential for proper timestamp handling, scheduling operations,
	 * and displaying time-sensitive information in the user's local context.
	 *
	 * @return The time zone ID string in IANA format, suitable for use with
	 *         Java Time API or other date/time handling libraries.
	 */
	fun getTimeZoneId(): String

	/**
	 * Returns a reference to the BaseActivity implementing this interface.
	 *
	 * This method provides type-safe access to the activity context for
	 * operations that require specific BaseActivity functionality. It's
	 * particularly useful in scenarios where the activity reference might
	 * be needed from fragments, adapters, or other components that have
	 * access to the interface but not the concrete activity instance.
	 *
	 * @return The current BaseActivity instance if available, or null if
	 *         the activity has been destroyed or is not in a valid state.
	 *         Callers should always check for null before using the returned value.
	 */
	fun getActivity(): BaseActivity?

	/**
	 * Triggers haptic feedback through device vibration.
	 *
	 * This method provides tactile feedback for user interactions, enhancing
	 * the user experience by confirming actions through physical sensation.
	 * It should check device vibration capability before attempting to vibrate
	 * and use appropriate vibration patterns for different interaction types.
	 *
	 * @param timeInMillis Duration of the vibration in milliseconds. Typical
	 *                     values range from 10-50ms for subtle feedback to
	 *                     100-500ms for prominent notifications. The system
	 *                     may ignore values outside reasonable ranges or
	 *                     based on device capabilities and user settings.
	 */
	fun doSomeVibration(timeInMillis: Int)

	/**
	 * Provides standardized intent flags for single-top activity launch mode.
	 *
	 * This method returns the appropriate combination of intent flags to ensure
	 * an activity launches in single-top mode, which reuses an existing instance
	 * if one exists at the top of the back stack rather than creating a new instance.
	 * This is particularly useful for main activities, launcher activities, or
	 * activities that should maintain a single instance in the navigation flow.
	 *
	 * @return The combined intent flags (typically FLAG_ACTIVITY_CLEAR_TOP |
	 *         FLAG_ACTIVITY_SINGLE_TOP) that configure single-top launch behavior.
	 */
	fun getSingleTopIntentFlags(): Int
}