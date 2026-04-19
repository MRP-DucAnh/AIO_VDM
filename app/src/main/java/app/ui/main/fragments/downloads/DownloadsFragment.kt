package app.ui.main.fragments.downloads

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.biometric.BiometricManager
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import app.core.AIOApp.Companion.aioRawFiles
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.downloadSystem
import app.core.bases.BaseFragment
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import app.core.engines.settings.AIOSettings.Companion.SYSTEM_GALLERY
import app.core.engines.video_parser.dialogs.VideoLinkPasteEditor
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.fragments.active.ActiveTasksFragment
import app.ui.main.fragments.downloads.fragments.finished.FinishedTasksFragment
import app.ui.main.fragments.downloads.poups.SortingDownloads
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.device.DeviceAuthUtility.authenticate
import lib.device.StorageUtility.getFreeStorageSpace
import lib.device.StorageUtility.getTotalStorageSpace
import lib.networks.DownloaderUtils.getHumanReadableFormat
import lib.process.LogHelperUtils
import lib.ui.ViewUtility
import lib.ui.ViewUtility.closeAnyAnimation
import lib.ui.ViewUtility.hideView
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.showView
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.WeakReference

/**
 * A fragment that serves as the primary container for managing download tasks within the application.
 *
 * This fragment implements a tabbed interface using a [ViewPager] to organize download activities into
 * two main categories:
 * - **Finished Tasks:** View and manage completed downloads, including support for a secured "Private Folder"
 *   accessible via authentication.
 * - **Active Tasks:** Monitor and control ongoing download processes.
 *
 * Key functionalities include:
 * - Integration with [MotherActivity] for navigation and UI synchronization.
 * - Secure handling of private files using [authenticate] and filtered list adapters.
 * - Dynamic UI updates for file counts and storage status.
 * - Loading state management with [LottieAnimationView] to enhance user experience during initialization.
 * - Navigation shortcuts to add new download tasks via [VideoLinkPasteEditor].
 *
 * @see FinishedTasksFragment
 * @see ActiveTasksFragment
 * @see DownloadFragmentAdapter
 */
open class DownloadsFragment : BaseFragment() {

	/**
	 * Logger instance used for capturing and reporting errors and debug information
	 * specific to the [DownloadsFragment] lifecycle and user interactions.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] to this fragment instance, used to provide a safe reference for
	 * asynchronous operations, lifecycle-aware callbacks, and parent activity communication.
	 *
	 * Utilizing a weak reference prevents memory leaks by allowing the garbage collector
	 * to reclaim the fragment instance even if a reference is still held by a long-running
	 * task or a static context.
	 */
	private val weakReferenceOfDownloadsFragment = WeakReference(this)

	/**
	 * Provides a memory-safe access to the current [DownloadsFragment] instance.
	 *
	 * This property retrieves the fragment instance held by [weakReferenceOfDownloadsFragment].
	 * Using a [java.lang.ref.WeakReference] helps prevent memory leaks by allowing the
	 * fragment to be garbage collected when it is no longer in use, even if this
	 * reference is still held by asynchronous tasks or long-lived callbacks.
	 *
	 * @return The [DownloadsFragment] instance if it is still in memory, or `null` otherwise.
	 */
	private val safeDownloadFragmentRef get() = weakReferenceOfDownloadsFragment.get()

	/**
	 * Provides a type-safe reference to the parent [MotherActivity] by casting [safeBaseActivityRef].
	 * Returns null if the fragment is not attached to an activity or if the activity is not
	 * an instance of [MotherActivity].
	 */
	private val safeMotherActivityRef get() = safeBaseActivityRef as? MotherActivity

	/**
	 * Reference to the root [View] of the fragment's layout.
	 *
	 * This property is initialized during [initializeViewProperties] and provides
	 * access to the inflated layout hierarchy, allowing for the retrieval and
	 * manipulation of UI components within the fragment.
	 */
	private lateinit var fragmentLayoutView: View

	/**
	 * The [ViewPager] responsible for hosting and managing navigation between the
	 * different download state fragments (e.g., Active and Finished tasks).
	 *
	 * This component handles the horizontal swiping logic and tab transitions within
	 * the downloads section. It uses a [DownloadFragmentAdapter] to manage the
	 * lifecycle of its child fragments.
	 */
	open lateinit var fragmentViewPager: ViewPager

	/**
	 * Reference to the child fragment responsible for displaying and managing
	 * the list of successfully completed download tasks.
	 */
	open var finishedTasksFragment: FinishedTasksFragment? = null

	/**
	 * Reference to the child fragment responsible for displaying and managing active download tasks.
	 * This fragment is typically hosted within the [fragmentViewPager].
	 */
	open var activeTasksFragment: ActiveTasksFragment? = null

	/**
	 * Flag indicating whether the UI is currently displaying files from the private/hidden folder.
	 *
	 * When `true`, the downloads list is filtered to show only items stored in the private directory.
	 * When `false`, the list displays standard files from the system gallery.
	 */
	var isShowingPrivateFiles = false

	/**
	 * Returns the layout resource ID associated with this fragment.
	 *
	 * @return The integer resource ID of the layout ([R.layout.frag_down_1_main_1])
	 * to be inflated for this fragment's user interface.
	 */
	override fun getLayoutResId(): Int {
		// Return the specific layout resource ID to be used for inflating the fragment's view
		return R.layout.frag_down_1_main_1
	}

	/**
	 * Called immediately after the fragment's layout has been inflated and the [View] hierarchy
	 * is created. This method handles the orchestration of the UI setup process for the
	 * Downloads screen.
	 *
	 * The initialization sequence includes:
	 * - Starting the Lottie loading animation.
	 * - Hiding the main content layout to show the loading state.
	 * - Linking this fragment instance to the parent [MotherActivity].
	 * - Initializing view-specific properties and data bindings.
	 * - Setting up child fragments (Finished and Active tasks) within the [ViewPager].
	 * - Registering click listeners for action bar buttons and navigation elements.
	 *
	 * @param layoutView The root view of the fragment.
	 * @param state If non-null, this fragment is being re-constructed from a previous saved state.
	 */
	override fun onAfterLayoutLoad(layoutView: View, state: Bundle?) {
		// Step 1: Set up and start the loading animation visuals
		settingUpLoadingStateAnimation(layoutView)

		// Step 2: Hide the main layout to display the loading state
		hideActualLayout()

		// Step 3: Link this fragment instance to the parent MotherActivity
		registerSelfReferenceInMotherActivity()

		// Step 4: Initialize view properties and UI elements
		initializeViewProperties(layoutView)

		// Step 5: Set up the ViewPager with child fragments
		initializeChildFragments()

		// Step 6: Assign click listeners to interactive UI elements
		initializeOnClickEvents(layoutView)
	}

	/**
	 * Handles the fragment's resume lifecycle event to ensure proper UI state and activity synchronization.
	 *
	 * This method performs the following actions:
	 * 1. Re-registers the fragment reference within the [MotherActivity] to maintain communication.
	 * 2. Initiates a delayed UI transition within the [viewLifecycleOwner] scope. After a 1-second delay,
	 *    it checks if the fragment is still resumed and, if so, triggers [releaseActualLayout] to
	 *    transition from the loading state to the actual content layout.
	 *
	 * The delay is intentionally implemented to allow background initialization processes or
	 * animations to settle before revealing the primary user interface.
	 */
	override fun onResumeFragment() {
		// Ensure the fragment is registered with the activity on resume
		registerSelfReferenceInMotherActivity()

		try {
			// Safely access the fragment and layout references
			safeDownloadFragmentRef?.let { fragmentRef ->
				safeFragmentLayoutRef?.let { layoutRef ->
					// Launch a coroutine tied to the fragment's view lifecycle
					fragmentRef.viewLifecycleOwner.lifecycleScope.launch {
						// Update storage information in the background
						updateDeviceStorageInfo(layoutRef)

						// Wait for 700ms to allow the loading animation to play
						delay(700)

						// Check if the fragment is still in the resumed state before revealing content
						if (fragmentRef.isResumed) releaseActualLayout()
					}
				}
			}
		} catch (error: Exception) {
			logger.e("Exception while updating download fragments UI", error)
		}
	}

	/**
	 * Called when the fragment is no longer in the resumed state.
	 *
	 * This implementation currently performs no additional actions, but is
	 * overridden to provide a hook for future cleanup or state preservation
	 * specific to the downloads interface.
	 */
	override fun onPauseFragment() {
		// Do nothing
	}

	/**
	 * Called when the fragment's view hierarchy is being removed.
	 *
	 * This implementation performs essential cleanup to prevent memory leaks:
	 * - Unregisters the fragment reference from the hosting [MotherActivity].
	 * - Clears the [ViewPager] adapter to break references between the fragment manager and the UI.
	 * - Invokes the superclass cleanup to finalize the view destruction.
	 */
	override fun onDestroyView() {
		// Unregister this fragment from the activity to prevent stale references
		unregisterSelfReferenceInMotherActivity()

		// Clear the ViewPager adapter to release child fragments
		clearFragmentAdapterFromMemory()

		// Perform superclass cleanup
		super.onDestroyView()
	}

	/**
	 * Initializes the fragment's view properties and UI components.
	 *
	 * This method ensures that the fragment is attached to a valid [MotherActivity]
	 * before assigning the [layoutView] to the local [fragmentLayoutView] reference.
	 * It then proceeds to call [initializeViews] to set up specific UI elements
	 * within the provided layout.
	 *
	 * @param layoutView The root view of the fragment's layout.
	 */
	private fun initializeViewProperties(layoutView: View) {
		safeMotherActivityRef?.let {
			// Store the root layout view in the local reference
			fragmentLayoutView = layoutView

			// Initialize specific views contained within the layout
			initializeViews(layoutView)
		}
	}

	/**
	 * Registers a reference of this fragment within the [MotherActivity] and synchronizes the UI state.
	 *
	 * This method performs two primary actions:
	 * 1. Updates the [MotherActivity.downloadFragment] property with a safe reference to this fragment,
	 *    allowing the activity to communicate with the download UI.
	 * 2. Closes the side navigation drawer to ensure the user has an unobstructed view of the
	 *    download management interface.
	 *
	 * It is called during the fragment's lifecycle (e.g., [onAfterLayoutLoad] and [onResumeFragment])
	 * to ensure the activity always holds a valid reference while the fragment is active.
	 */
	private fun registerSelfReferenceInMotherActivity() {
		// Update the activity's reference to this fragment
		safeMotherActivityRef?.downloadFragment = safeDownloadFragmentRef

		// Close the navigation drawer to focus on the content
		safeMotherActivityRef?.sideNavigation?.closeDrawerNavigation()
	}

	/**
	 * Clears the reference to this fragment from the [MotherActivity] to prevent memory leaks.
	 *
	 * This method is called during [onDestroyView] to ensure that the hosting activity
	 * no longer holds a reference to a fragment instance whose view hierarchy has been destroyed.
	 */
	private fun unregisterSelfReferenceInMotherActivity() {
		// Nullify the activity's reference to this fragment
		safeMotherActivityRef?.downloadFragment = null
	}

	/**
	 * Clears the [fragmentViewPager] adapter to prevent memory leaks.
	 *
	 * By nullifying the adapter when the fragment's view is destroyed, we ensure that
	 * the ViewPager does not hold onto the [DownloadFragmentAdapter] or the child
	 * fragment manager, allowing them to be properly garbage collected.
	 */
	private fun clearFragmentAdapterFromMemory() {
		// Remove the adapter to release child fragment references
		fragmentViewPager.adapter = null
	}

	/**
	 * Initializes the primary UI components of the fragment.
	 *
	 * This method hooks the [ViewPager] from the provided layout and performs
	 * the initial UI setup for the private files toggle button.
	 *
	 * @param layoutView The root view of the fragment containing the UI elements.
	 */
	private fun initializeViews(layoutView: View) {
		// Find and assign the ViewPager from the layout
		fragmentViewPager = layoutView.findViewById(R.id.fragment_viewpager)

		// Initialize the UI state for the private files toggle button
		togglePrivateFilesButtonUI()
	}

	/**
	 * Initializes the child fragments within the [ViewPager] by setting up its adapter.
	 *
	 * This method configures the [fragmentViewPager] with a [DownloadFragmentAdapter],
	 * which manages the display of the active and finished download task fragments.
	 * It also sets the offscreen page limit to 1 to ensure that at least one adjacent
	 * fragment is kept in memory for smoother transitions while minimizing resource usage.
	 *
	 * The initialization only proceeds if both the fragment's self-reference and the
	 * parent [MotherActivity] reference are valid.
	 */
	private fun initializeChildFragments() {
		// Proceed only if the fragment and activity references are safe
		safeDownloadFragmentRef?.let {
			safeMotherActivityRef?.let { safeMotherActivityRef ->
				// Set the adapter managing the child fragments
				fragmentViewPager.adapter = DownloadFragmentAdapter(childFragmentManager)

				// Keep one offscreen fragment cached to ensure smoother swiping
				fragmentViewPager.offscreenPageLimit = 1
			}
		}
	}

	/**
	 * Configures click listeners for the UI elements within the fragment's action bar and main layout.
	 *
	 * This method maps specific view IDs to their corresponding functional actions, including:
	 * - Opening the download task editor dialog.
	 * - Toggling the visibility of private/secure files.
	 * - Handling back navigation logic based on the current ViewPager state.
	 *
	 * @param layoutView The root view of the fragment containing the interactive elements.
	 */
	private fun initializeOnClickEvents(layoutView: View) {
		mapOf(
			R.id.btn_actionbar_add_download to { showDownloadTaskEditorDialog() },
			R.id.btn_actionbar_options to { safeMotherActivityRef?.showUpcomingFeatures() },
			R.id.btn_sort_the_list to { showSortingDownloads(layoutView) },
			R.id.btn_toggle_private_files to { togglePrivateFiles() },
			R.id.btn_actionbar_back to { resolveTabNavigation() }
		).forEach { (id, action) ->
			layoutView.findViewById<View>(id).setOnClickListener { action() }
		}
	}

	/**
	 * Resolves the navigation logic when the back action is triggered within the downloads section.
	 *
	 * This method determines the appropriate navigation path based on the current state of
	 * the [fragmentViewPager]:
	 * - If the user is currently on the "Active Tasks" tab (index 1), it navigates them
	 *   back to the "Finished Tasks" tab (index 0) via [openFinishedTab].
	 * - If the user is already on the "Finished Tasks" tab (index 0), it exits the
	 *   downloads section and returns to the browser interface via [navigateToBrowserFragment].
	 */
	private fun resolveTabNavigation() {
		if (fragmentViewPager.currentItem == 1) openFinishedTab()
		else navigateToBrowserFragment()
	}

	/**
	 * Displays the sorting options popup for the downloads list.
	 *
	 * This method initializes and shows a [SortingDownloads] popup anchored to the
	 * sort button. When a user selects a sorting criteria, it triggers
	 * [updateDownloadSorting] to reorder the items in the download fragments.
	 *
	 * @param layoutView The root view of the fragment used to locate the anchor
	 * view ([R.id.btn_sort_the_list]) for the popup.
	 */
	private fun showSortingDownloads(layoutView: View) {
		SortingDownloads(
			baseActivity = safeBaseActivityRef,
			anchorView = layoutView.findViewById<View>(R.id.btn_sort_the_list),
			onSortOptionSelected = { selectionOption ->
				updateDownloadSorting(selectionOption)
			}
		).show()
	}

	/**
	 * Updates the sorting order of download tasks in both the active and finished lists.
	 *
	 * This method applies a specific sorting criteria (e.g., date, name, size, or file type)
	 * to the collections maintained by the [downloadSystem]. After reordering the data
	 * models, it ensures that the UI components (fragments and their adapters) reflect
	 * the new sequence.
	 *
	 * Supported sorting options defined in [AIOSettings] include:
	 * - [AIOSettings.SORT_DATE_NEWEST_FIRST] / [AIOSettings.SORT_DATE_OLDEST_FIRST]
	 * - [AIOSettings.SORT_NAME_A_TO_Z] / [AIOSettings.SORT_NAME_Z_TO_A]
	 * - [AIOSettings.SORT_SIZE_SMALLEST_FIRST] / [AIOSettings.SORT_SIZE_LARGEST_FIRST]
	 * - [AIOSettings.SORT_TYPE_VIDEOS_FIRST] / [AIOSettings.SORT_TYPE_MUSIC_FIRST]
	 *
	 * @param selectionOption The integer constant from [AIOSettings] representing the desired sort logic.
	 */
	private fun updateDownloadSorting(selectionOption: Int) {
		try {
			logger.d("Selected option: $selectionOption")
			aioSettings.downloadsDefaultSortOrder = selectionOption
			aioSettings.updateInDB()
			downloadSystem.updateDownloadSortOrder()
			finishedTasksFragment?.finishedTasksListAdapter?.notifyDataSetChangedOnSort(true)
			finishedTasksFragment?.finishedTasksListAdapter?.count?.let { totalItems ->
				if (totalItems > 0) {
					finishedTasksFragment?.listViewDownloads?.setSelection(0)
				}
			}
			safeMotherActivityRef?.let { it.homeFragment?.recentDownloadUIUpdateRequest = true }
		} catch (error: Exception) {
			logger.e("Error in applying sorting option in download modules:", error)
		}
	}

	/**
	 * Navigates the user back to the browser fragment.
	 *
	 * This method utilizes the [safeMotherActivityRef] to trigger the fragment
	 * transaction within the host activity, effectively switching the UI focus
	 * from the downloads section back to the browser interface.
	 */
	private fun navigateToBrowserFragment() {
		safeMotherActivityRef?.openBrowserFragment()
	}

	/**
	 * Displays the video link paste editor dialog to the user.
	 *
	 * This method retrieves a safe reference to the [MotherActivity] and initializes
	 * a [VideoLinkPasteEditor], which allows the user to manually input or paste
	 * a URL to initiate a new download task.
	 */
	private fun showDownloadTaskEditorDialog() {
		// Check if the safe reference to the MotherActivity is not null
		safeMotherActivityRef?.let { safeActivityRef ->
			// Create and show the video link paste editor dialog using the safe activity reference
			VideoLinkPasteEditor(safeActivityRef).show()
		}
	}

	/**
	 * Switches the [fragmentViewPager] to the first tab (index 0), which displays
	 * the [FinishedTasksFragment].
	 *
	 * This method ensures that the view pager transitions to the "Finished" downloads
	 * section if it is not already selected.
	 */
	fun openFinishedTab() {
		// Check if the currently selected tab is not the Finished tab (index 0)
		if (fragmentViewPager.currentItem != 0) {
			// Switch the ViewPager to the first tab
			fragmentViewPager.currentItem = 0
		}
	}

	/**
	 * Switches the [fragmentViewPager] to the active tasks tab (index 1).
	 *
	 * This method checks the current item index and updates it only if the
	 * active tab is not already selected, ensuring the user is navigated
	 * to the view containing ongoing downloads.
	 */
	fun openActiveTab() {
		// Check if the currently selected tab is not the Active tab (index 1)
		if (fragmentViewPager.currentItem != 1) {
			// Switch the ViewPager to the second tab
			fragmentViewPager.currentItem = 1
		}
	}

	/**
	 * Updates the visual state and functional labeling of the private files toggle button.
	 *
	 * This method synchronizes the UI components based on the current visibility state of
	 * private files ([isShowingPrivateFiles]). It performs the following actions:
	 * 1. Refreshes the numeric file count displayed next to the button via [updatePrivateFilesCountUI].
	 * 2. Updates the button's text and icon (lock vs. unlock) to reflect whether the
	 *    private folder is currently active or closed.
	 * 3. Handles edge cases where the [finishedTasksFragment] might not be initialized yet.
	 */
	fun togglePrivateFilesButtonUI() {
		try {
			// Step 1: Update the counter showing how many private files exist
			updatePrivateFilesCountUI()

			// Step 2: Locate the button text view; exit if the view is not available
			val txt = safeFragmentLayoutRef
				?.findViewById<TextView>(R.id.txt_private_toggle) ?: return

			// Step 3: Handle the case where the finished tasks fragment is not yet initialized
			if (finishedTasksFragment == null) {
				txt.text = getText(R.string.title_toggle_private_files)
				return
			}

			// Step 4: Update text and icon based on the current visibility state
			if (isShowingPrivateFiles) {
				// Case A: Private files are currently visible. Show "Close" action with Lock icon.
				txt.text = getText(R.string.title_closed_privates)
				txt.setLeftSideDrawable(R.drawable.ic_button_lock)
			} else {
				// Case B: Private files are hidden. Show "Open" action with Unlock icon.
				txt.text = getText(R.string.title_open_privates)
				txt.setLeftSideDrawable(R.drawable.ic_button_unlock_v1)
			}

		} catch (error: Exception) {
			logger.e("Error while toggling private files title", error)
		}
	}

	/**
	 * Updates the UI counter that displays the total number of files stored in the private folder.
	 *
	 * This function filters the list of finished downloads to count only those where the
	 * download location is set to [PRIVATE_FOLDER]. It then updates the text of the
	 * designated [TextView] and ensures the view is visible with a short fade-in animation.
	 *
	 * If the fragment's layout view is no longer available, the function returns early.
	 */
	fun updatePrivateFilesCountUI() {
		// Define the view ID for the counter text view
		val txtPrivateFileCounterId = R.id.txt_private_file_counter

		// Retrieve the text view; exit if the fragment layout is not available
		val tv = safeFragmentLayoutRef?.findViewById<TextView>(txtPrivateFileCounterId) ?: return

		// Filter the finished download list to count items specifically saved to the Private Folder
		val totalPrivateFiles = downloadSystem.finishedDownloadDataModels.count { model ->
			model.config.defaultDownloadLocationType == PRIVATE_FOLDER
		}

		// Update the text view with the calculated count
		tv.text = totalPrivateFiles.toString()

		// Make the view visible with a 300ms fade-in animation
		showView(tv, true, 300)
	}

	/**
	 * Toggles the visibility of private files within the finished tasks list.
	 *
	 * This method performs the following actions:
	 * 1. Switches the view to the "Finished" tab to ensure the user sees the filtered results.
	 * 2. If private files are currently shown, it reverts the filter to display only files
	 *    stored in the system gallery.
	 * 3. If private files are hidden, it initiates a security authentication challenge.
	 *    Upon successful authentication, it filters the list to show only files stored
	 *    in the private folder and updates the UI state.
	 * 4. Refreshes the toggle button's appearance and icon via [togglePrivateFilesButtonUI].
	 */
	fun togglePrivateFiles() {
		// Ensure the UI is on the correct tab before filtering
		openFinishedTab()

		// Retrieve the adapter to apply filters; exit if the fragment/view is not ready
		val adapter = finishedTasksFragment?.finishedTasksListAdapter ?: return

		// Case A: Private files are currently visible. Switch back to public/system gallery view.
		if (isShowingPrivateFiles) {
			// Filter to show only items saved to the system gallery
			adapter.setFilter {
				val globalSettings = it.config
				globalSettings.defaultDownloadLocationType == SYSTEM_GALLERY
			}

			// Update state to reflect that private files are no longer shown
			isShowingPrivateFiles = false

			// Update the button icon to match the new state (e.g., unlocked icon)
			togglePrivateFilesButtonUI()
			return
		}

		// Case B: Private files are hidden. Attempt to authenticate to show them.
		// Retrieve the activity context; exit if unavailable (e.g., activity is destroyed)
		val activity = safeBaseActivityRef ?: return

		// Initialize BiometricManager to check hardware capabilities and enrollment status
		val biometricManager = BiometricManager.from(activity)

		// Determine allowed authenticators based on Android version.
		// Android R (11+) allows fallback to device credentials (PIN/Pattern/Password).
		val authenticators =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				BiometricManager.Authenticators.BIOMETRIC_STRONG or
					BiometricManager.Authenticators.DEVICE_CREDENTIAL
			} else {
				BiometricManager.Authenticators.BIOMETRIC_STRONG
			}

		// Check if biometric authentication can be performed based on hardware state
		when (biometricManager.canAuthenticate(authenticators)) {

			// 1. Success: Hardware is available and credentials are enrolled. Prompt user.
			BiometricManager.BIOMETRIC_SUCCESS -> {
				authenticate(activity) { isSuccess ->
					if (isSuccess) {
						// Authentication passed: Filter to show only items in the private folder
						adapter.setFilter {
							val globalSettings = it.config
							globalSettings.defaultDownloadLocationType == PRIVATE_FOLDER
						}

						isShowingPrivateFiles = true
						togglePrivateFilesButtonUI()
					}
				}
			}

			// 2. No Credentials Enrolled: Hardware exists, but user hasn't set up a lock screen.
			// Guide the user to settings to enable security.
			BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
				// Provide haptic feedback to alert the user
				activity.doSomeVibration()
				// Inform user that a lock screen is required
				showToast(activity, R.string.title_setup_lock_screen_first)

				// Create an intent to open the biometric or security settings screen
				val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
						putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticators)
					}
				} else {
					Intent(Settings.ACTION_SECURITY_SETTINGS)
				}

				activity.startActivity(intent)
			}

			// 3. Hardware Unavailable: Device lacks biometric sensors or hardware is broken.
			BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
			BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
				// Provide haptic feedback and notify user
				activity.doSomeVibration()
				showToast(activity, R.string.title_biometric_not_available)
			}

			// 4. Other Errors: Catch-all for security updates or other unexpected states.
			else -> {
				// Provide haptic feedback and show generic error message
				activity.doSomeVibration()
				showToast(activity, R.string.title_authentication_failed)
			}
		}
	}

	/**
	 * Unbinds views and releases resources to prevent memory leaks when the fragment's
	 * view is destroyed. This method is part of the `BaseFragment` lifecycle and is
	 * called automatically to nullify references to the fragment's layout and its
	 * associated click logic handler.
	 *
	 * Key actions performed:
	 * - Nullifies the `settingsOnClickLogic` instance, breaking its reference to the
	 *   fragment and allowing it to be garbage-collected.
	 * - Calls the superclass implementation to perform standard `BaseFragment` cleanup,
	 *   which includes releasing the reference to the fragment's layout view.
	 *
	 * This cleanup is crucial for fragments with complex view hierarchies and helper
	 * classes to ensure that no context or view references are held beyond their
	 * intended lifecycle, which is a common source of memory leaks in Android.
	 */
	private fun releaseActualLayout() {
		// Retrieve the reference to the fragment's root layout view.
		// If the view is already destroyed (null), the operation is skipped.
		safeFragmentLayoutRef?.let { fragLayout ->

			// Hide the loading overlay to ensure it doesn't persist on the screen.
			hideView(fragLayout.findViewById<View>(R.id.container_layout_loading), true)

			// Reveal the main content layout, restoring the standard UI state.
			showView(fragLayout.findViewById<View>(R.id.container_main_layout), true)
		}
	}

	/**
	 * Hides the main content layout and displays the loading indicator.
	 *
	 * This method is intended to be called when the fragment needs to perform a
	 * background task and wants to provide visual feedback to the user that
	 * something is happening. It makes the main settings container invisible
	 * and shows a loading spinner in its place.
	 *
	 * It is the counterpart to [releaseActualLayout], which performs the opposite action.
	 */
	private fun hideActualLayout() {
		// Retrieve the reference to the fragment's root layout view.
		// If the view is not yet created or is destroyed, the operation is skipped.
		safeFragmentLayoutRef?.let { fragLayout ->

			// Display the loading indicator container.
			showView(fragLayout.findViewById<View>(R.id.container_layout_loading), true)

			// Hide the main content container to clear the UI while loading.
			hideView(fragLayout.findViewById<View>(R.id.container_main_layout), true)
		}
	}

	/**
	 * Sets up and starts the animation for the loading state view.
	 *
	 * This method initializes the visual feedback for the user while the fragment
	 * is preparing its data or layout. It targets the loading container and
	 * applies the necessary animation logic to indicate background progress.
	 */
	private fun settingUpLoadingStateAnimation(fragLayout: View) {
		// Locate the LottieAnimationView within the fragment's layout hierarchy
		fragLayout.findViewById<LottieAnimationView>(R.id.img_layout_loading_anim)?.apply {

			// Allow the animation to draw outside its composition bounds if necessary
			clipToCompositionBounds = false

			// Ensure the animation scales to fit the entire available area
			setScaleType(ImageView.ScaleType.FIT_XY)

			// Attempt to retrieve the pre-loaded Lottie composition from memory
			val composition = aioRawFiles.getLayoutLoadComposition()
			if (composition != null) {
				// Case 1: Composition is cached. Set it directly and start playback.
				setComposition(composition)
				playAnimation()

				// Make the view visible with a 100ms fade-in effect for smoothness
				showView(this, true, 100)
			} else {
				// Case 2: Composition is not cached. Ensure it is loaded for next time
				// and fall back to loading from the raw resource file directly.
				setAnimation(R.raw.animation_layout_load)

				// Make the view visible with a 100ms fade-in effect
				showView(this, true, 100)
			}
		}
	}

	/**
	 * Calculates and updates the UI with the current device storage statistics.
	 *
	 * This method performs storage space calculations (total, used, and free) on a background
	 * [Dispatchers.IO] thread to avoid blocking the UI. It retrieves system storage metrics
	 * using [getFreeStorageSpace] and [getTotalStorageSpace], then formats these values into
	 * a human-readable string (e.g., MB/GB).
	 *
	 * The UI is updated in two stages:
	 * 1. Immediately displays a "getting info" placeholder.
	 * 2. Displays the final formatted storage string once calculations are complete.
	 *
	 * @param fragLayout The root view of the fragment containing the storage info [TextView].
	 */
	private fun updateDeviceStorageInfo(fragLayout: View) {
		try {
			// Launch a coroutine in the IO context to perform disk operations off the main thread
			safeMotherActivityRef?.getAttachedCoroutineScope()?.launch(Dispatchers.IO) {
				// Locate the TextView responsible for displaying the storage information
				fragLayout.findViewById<TextView>(R.id.txt_device_storage_info).let { tv ->

					// Switch to Main thread to update UI visibility and text immediately
					withContext(Dispatchers.Main) {
						// Set temporary placeholder text while calculating
						tv.text = getText(R.string.title_getting_storage_info)

						// Apply a fade-in/out animation to the text view
						ViewUtility.animateFadInOutAnim(tv)

						// Ensure the container holding the storage info is visible
						showView(fragLayout.findViewById<View>(R.id.container_bottom_storage_info))
					}

					// Perform storage calculations in the background
					val freeStorage = getFreeStorageSpace()
					val totalStorage = getTotalStorageSpace()

					// Construct the final human-readable string showing used, total, and available space
					val infoTxt = """
						  ${getHumanReadableFormat(totalStorage - freeStorage)} ${getText(R.string.title_used)} / 
							${getHumanReadableFormat(totalStorage)}
							(${getText(R.string.title_available)} : ${getHumanReadableFormat(freeStorage)})
							""".trimIndent().replace("\n", " ")

					// Switch back to Main thread to update the UI with the calculated data
					withContext(Dispatchers.Main) {
						tv.text = infoTxt

						// Stop any running animations on the text view
						closeAnyAnimation(tv)
					}
				}
			}
		} catch (error: Exception) {
			// Log any exceptions that occur during the storage calculation or UI update process
			logger.e("Error while updating device storage info", error)

			// Cleanup: Stop animations on the text view to prevent leaks or visual glitches
			closeAnyAnimation(fragLayout.findViewById(R.id.txt_device_storage_info))

			// Hide the storage info container if the update fails
			hideView(fragLayout.findViewById<View>(R.id.container_bottom_storage_info))
		}
	}
}