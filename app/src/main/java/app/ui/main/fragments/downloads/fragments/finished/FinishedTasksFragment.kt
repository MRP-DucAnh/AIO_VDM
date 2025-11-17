package app.ui.main.fragments.downloads.fragments.finished

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.core.AIOApp
import app.core.AIOApp.Companion.aioRawFiles
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOTimer.AIOTimerListener
import app.core.bases.BaseFragment
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.settings.AIOSettings
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.DownloadsFragment
import app.ui.main.guides.GuidePlatformPicker
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.device.SecureFileUtil.authenticate
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.hideView
import lib.ui.ViewUtility.showView
import java.lang.ref.WeakReference

/**
 * Fragment displaying completed download tasks with comprehensive management capabilities.
 *
 * This fragment serves as the main interface for viewing, interacting with, and managing
 * finished downloads within the AIO application. It provides a complete ecosystem for
 * handling completed downloads including visual presentation, user interactions, and
 * system integration. Key features include:
 *
 * - Interactive list of completed downloads with rich thumbnails and detailed metadata
 * - Intuitive click and long-click actions for file operations and management
 * - Smart empty state management with contextual guides and navigation suggestions
 * - Deep integration with download system for real-time state synchronization
 * - Secure access control with biometric authentication for private folder downloads
 * - Timer-based UI synchronization ensuring consistent state representation
 * - Comprehensive lifecycle management preventing memory leaks and resource waste
 *
 * The fragment employs weak references for callback safety, proper adapter cleanup,
 * and systematic unregistration from all external systems during destruction.
 */
class FinishedTasksFragment : BaseFragment(), FinishedTasksClickEvents, AIOTimerListener {

	// Logger instance for debugging, error tracking, and operational monitoring
	private val logger = LogHelperUtils.from(javaClass)

	// Weak self-reference to prevent memory leaks in timer callbacks and async operations
	private val weakSelfReference = WeakReference(this)

	// UI component references for dynamic visibility control and user interaction
	private var emptyDownloadContainer: View? = null
	private var buttonOpenActiveTasks: View? = null
	private var openActiveTasksAnim: LottieAnimationView? = null
	private var buttonHowToDownload: View? = null
	private var downloadsListView: RecyclerView? = null

	// State tracking variables for optimization and performance monitoring
	private var lastCheckedFinishedTasks = 0

	// Adapter for managing the list of finished downloads with data binding and view recycling
	var finishedTasksListAdapter: FinishedTasksListAdapter? = null

	/**
	 * Safe reference to the parent MotherActivity with proper type casting and null safety.
	 * Provides access to activity-level functionality while gracefully handling cases where
	 * the activity is not available, not of the expected type, or during configuration changes.
	 * Returns null if the activity context is unavailable or incompatible.
	 */
	val safeMotherActivityRef: MotherActivity? get() = safeBaseActivityRef as MotherActivity

	/**
	 * Safe reference to this fragment instance accessed through weak reference protection.
	 * Prevents memory leaks in timer callbacks, background operations, and delayed executions
	 * by allowing garbage collection even if external systems retain references. Returns null
	 * when the fragment has been garbage collected or is no longer valid.
	 */
	val safeFinishTasksFragment: FinishedTasksFragment? get() = weakSelfReference.get()

	/**
	 * Returns the layout resource ID that defines the visual structure and component arrangement.
	 * This XML resource contains the complete UI hierarchy including list views, empty states,
	 * navigation elements, and interactive controls that compose the finished downloads interface.
	 */
	override fun getLayoutResId() = R.layout.frag_down_4_finish_1

	/**
	 * Called after the fragment layout has been inflated and all views are available for manipulation.
	 * Initializes the complete user interface including UI component references, event listeners,
	 * and the list adapter for displaying finished downloads. This is the primary setup point
	 * where the fragment transitions from layout definition to functional user interface.
	 *
	 * @param layoutView The inflated layout view containing all fragment UI components and hierarchies
	 * @param state Saved instance state bundle for restoration, null for fresh fragment initialization
	 */
	override fun onAfterLayoutLoad(layoutView: View, state: Bundle?) {
		logger.d("onAfterLayoutLoad() → initializing UI")
		initializeViewsAndListAdapter(layoutView)
	}

	/**
	 * Called when the fragment becomes visible and actively interacting with the user.
	 * Registers the fragment with the download system to receive state updates and notifications.
	 * Starts timer-based UI refresh cycles to maintain real-time synchronization with download
	 * progress and system changes. Ensures the UI reflects current data upon becoming visible.
	 */
	override fun onResumeFragment() {
		logger.d("onResumeFragment() → registering UI + timer")
		registerIntoDownloadSystem()
		registerToDownloadFragment()
		safeFinishTasksFragment?.let { AIOApp.aioTimer.register(it) }
	}

	/**
	 * Called when the fragment is no longer visible to the user but remains in the back stack.
	 * Unregisters from timer updates to conserve system resources and prevent unnecessary
	 * callback processing when the fragment is not actively displayed. Maintains fragment
	 * state while minimizing background processing overhead.
	 */
	override fun onPauseFragment() {
		logger.d("onPauseFragment() → unregistering UI + timer")
		safeFinishTasksFragment?.let { AIOApp.aioTimer.unregister(it) }
	}

	/**
	 * Called when the fragment's view hierarchy is being destroyed during fragment removal
	 * or configuration changes. Performs comprehensive cleanup of all resources, adapters,
	 * and system registrations to prevent memory leaks and ensure proper garbage collection.
	 * This is the critical cleanup point before the fragment is permanently destroyed.
	 */
	override fun onDestroyView() {
		logger.d("onDestroyView() → cleaning resources")

		// Clean up adapter and view holders to prevent memory leaks
		finishedTasksListAdapter?.let { adapter ->
			logger.d("Cleaning adapter resources… count=${adapter.itemCount}")
			adapter.clearResources(downloadsListView)
		}

		// Clear all references to prevent memory leaks
		downloadsListView?.recycledViewPool?.clear()
		downloadsListView?.adapter = null
		finishedTasksListAdapter = null

		// Nullify UI component references
		downloadsListView = null
		emptyDownloadContainer = null
		buttonOpenActiveTasks = null
		buttonHowToDownload = null
		openActiveTasksAnim = null

		// Ensure unregistration from all systems
		safeFinishTasksFragment?.let { AIOApp.aioTimer.unregister(it) }
		unregisterIntoDownloadSystem()
		unregisterToDownloadFragment()

		weakSelfReference.clear()
		logger.d("onDestroyView() → completed cleanup")
		super.onDestroyView()
	}

	/**
	 * Timer callback executed at regular intervals for real-time UI synchronization with download system.
	 * Updates critical UI elements including fragment title, empty state visibility, and navigation buttons
	 * to reflect current download states. Includes safety checks to prevent updates when fragment is
	 * not actively attached to the activity or user interface.
	 *
	 * @param loopCount The current iteration count of the timer for performance tracking and debugging
	 */
	override fun onAIOTimerTick(loopCount: Double) {
		if (!isAdded || isDetached) {
			logger.d("onAIOTimerTick() skipped → fragment not active")
			return
		}

		logger.d("onAIOTimerTick() → UI update")
		safeFinishTasksFragment?.let {
			updateDownloadFragmentTitle(parentFragment as? DownloadsFragment)
			toggleEmptyListVisibility(emptyDownloadContainer, downloadsListView)
			toggleOpenActiveTasksButtonVisibility(buttonOpenActiveTasks)
		}
	}

	/**
	 * Handles click events on finished download items in the list view.
	 * Opens the downloaded file directly or shows action options based on user preferences.
	 * For files stored in private folders, requires biometric or device authentication
	 * before granting access. Automatically marks files as opened for user behavior tracking.
	 *
	 * @param downloadModel The download data model representing the clicked finished download item
	 */
	override fun onFinishedDownloadClick(downloadModel: DownloadDataModel) {
		logger.d("Finished item clicked: id=${downloadModel.id}")

		safeMotherActivityRef?.let { activity ->
			val globalSettings = downloadModel.globalSettings
			val downloadLocation = globalSettings.defaultDownloadLocation

			// Mark file as opened and persist the change
			downloadModel.hasUserOpenedTheFile = true
			downloadModel.updateInStorage()

			val opts = FinishedDownloadOptions(safeFinishTasksFragment)

			/**
			 * Opens file options or directly plays media based on user preferences
			 */
			fun openOptions() {
				if (aioSettings.openDownloadedFileOnSingleClick) {
					// Directly open/play the file
					opts.setDownloadModel(downloadModel)
					opts.playTheMedia()
				} else {
					// Show options dialog for user choice
					opts.show(downloadModel)
				}
			}

			// Handle private folder authentication
			if (downloadLocation == AIOSettings.PRIVATE_FOLDER) {
				logger.d("Authenticating private file access…")
				authenticate(activity = activity, onResult = { ok ->
					if (ok) openOptions()
				})
			} else {
				openOptions()
			}
		}
	}

	/**
	 * Handles long-click events on finished download items for advanced operations.
	 * Always displays the options dialog regardless of user single-click preferences,
	 * providing access to file management actions like share, delete, or properties.
	 * Includes haptic feedback and requires authentication for private folder access.
	 *
	 * @param downloadModel The download data model representing the long-clicked finished download item
	 */
	override fun onFinishedDownloadLongClick(downloadModel: DownloadDataModel) {
		logger.d("Finished item long-clicked: id=${downloadModel.id}")

		safeMotherActivityRef?.let { activity ->
			// Mark file as opened and persist the change
			downloadModel.hasUserOpenedTheFile = true
			downloadModel.updateInStorage()

			/**
			 * Shows options dialog with haptic feedback
			 */
			fun openOptions() {
				val opts = FinishedDownloadOptions(safeFinishTasksFragment)
				activity.doSomeVibration() // Provide haptic feedback
				opts.show(downloadModel)
			}

			// Handle private folder authentication
			val loc = downloadModel.globalSettings.defaultDownloadLocation
			if (loc == AIOSettings.PRIVATE_FOLDER) {
				logger.d("Authenticating private file access for long-click…")
				authenticate(activity = activity, onResult = { ok ->
					if (ok) openOptions()
				})
			} else {
				openOptions()
			}
		}
	}

	/**
	 * Initializes all UI components, sets up event listeners, and configures the list adapter.
	 * Called during fragment view creation to prepare the complete user interface. Establishes
	 * references to all major UI elements, configures click handlers for navigation and guidance,
	 * and initializes the list adapter with data. Sets initial UI states based on current download system status.
	 *
	 * @param layout The root layout view containing all fragment UI components and sub-views
	 */
	private fun initializeViewsAndListAdapter(layout: View) {
		logger.d("Initializing views + adapter…")

		safeFinishTasksFragment?.let { fragment ->
			val activityRef = fragment.safeMotherActivityRef

			// Initialize UI component references
			emptyDownloadContainer = layout.findViewById(R.id.container_empty_downloads)

			// Set up how-to-download guide button with platform picker
			buttonHowToDownload = layout.findViewById(R.id.btn_how_to_download)
			buttonHowToDownload?.setOnClickListener {
				logger.d("How-to-download clicked")
				GuidePlatformPicker(activityRef).show()
			}

			// Set up active tasks navigation button with tab switching
			buttonOpenActiveTasks = layout.findViewById(R.id.btn_open_active_downloads)
			buttonOpenActiveTasks?.setOnClickListener {
				logger.d("Open active tasks clicked")
				openActiveTasksFragment()
			}

			// Initialize and load animation for active tasks button
			openActiveTasksAnim = layout.findViewById(R.id.img_open_active_downloads)
			loadOpenActiveTasksAnimation()

			// Set up list view and adapter for finished downloads display
			downloadsListView = layout.findViewById(R.id.container_download_tasks_finished)
			downloadsListView?.layoutManager = LinearLayoutManager(fragment.safeBaseActivityRef)
			finishedTasksListAdapter = FinishedTasksListAdapter(fragment)
			downloadsListView?.adapter = finishedTasksListAdapter
			
			logger.d("Views + adapter initialization complete")

			// Set initial UI states based on current download data
			toggleEmptyListVisibility(emptyDownloadContainer, downloadsListView)
			toggleOpenActiveTasksButtonVisibility(buttonOpenActiveTasks)
		}
	}

	/**
	 * Navigates to the active downloads tab within the parent DownloadsFragment.
	 * Provides seamless transition between finished and active download views by invoking
	 * the parent fragment's tab switching mechanism. Enables quick access to ongoing downloads
	 * from the finished downloads interface.
	 */
	private fun openActiveTasksFragment() {
		logger.d("Switching to active-tasks tab")
		(parentFragment as? DownloadsFragment)?.openActiveTab()
	}

	/**
	 * Controls the visibility of the "Open Active Tasks" button based on download system state.
	 * Shows the navigation button only when there are active downloads currently in progress,
	 * providing contextual access to ongoing operations. Uses smooth animations for visibility
	 * transitions to maintain polished user experience.
	 *
	 * @param button The button view to show or hide based on presence of active downloads
	 */
	private fun toggleOpenActiveTasksButtonVisibility(button: View?) {
		val active = downloadSystem.activeDownloadDataModels
		if (active.isNotEmpty()) {
			if (button?.isVisible == false) showView(button, true, 300)
		} else {
			if (button?.isVisible == true) hideView(button, true, 300)
		}
	}

	/**
	 * Manages the visibility transition between empty state and download list based on content availability.
	 * Shows the empty state container when no finished downloads exist, otherwise displays the list view.
	 * Includes smooth animation transitions and ensures the list adapter is synchronized with current data.
	 * Waits for download system initialization to complete before making visibility decisions.
	 *
	 * @param emptyView The container view displaying empty state message, graphics, and guidance
	 * @param listView The RecycleView containing the scrollable collection of finished download items
	 */
	private fun toggleEmptyListVisibility(emptyView: View?, listView: RecyclerView?) {
		// Skip if download system is still initializing
		if (downloadSystem.isInitializing) return
		if (emptyView == null || listView == null) return

		val empty = getFinishedDownloadModels().isEmpty()
		logger.d("Toggle empty-state UI → empty=$empty")

		if (empty) {
			// Show empty state, hide list with staggered animation timing
			hideView(listView, true, 100)
			showView(emptyView, true, 300)
		} else {
			// Show list, hide empty state with staggered animation timing
			hideView(emptyView, true, 100)
			showView(listView, true, 300)
		}

		// Ensure adapter reflects current data state without triggering full sort
		finishedTasksListAdapter?.notifyDataSetChangedOnSort(false)
	}

	/**
	 * Registers this fragment with the parent DownloadsFragment to establish bidirectional communication.
	 * Sets up the reference in the parent fragment for coordination, method calls, and state synchronization.
	 * Immediately updates the fragment title to reflect the current download count after registration.
	 */
	private fun registerToDownloadFragment() {
		logger.d("Registering this fragment to parent DownloadsFragment")
		(parentFragment as? DownloadsFragment)?.let {
			it.finishedTasksFragment = this
			updateDownloadFragmentTitle(it)
		}
	}

	/**
	 * Unregisters this fragment from the parent DownloadsFragment during cleanup operations.
	 * Clears the bidirectional reference to prevent memory leaks, stale callbacks, and ensure proper
	 * garbage collection when the fragment is destroyed or removed from the navigation stack.
	 */
	private fun unregisterToDownloadFragment() {
		logger.d("Unregistering fragment from parent DownloadsFragment")
		(parentFragment as? DownloadsFragment)?.finishedTasksFragment = null
	}

	/**
	 * Registers this fragment with the download system to receive state updates and notifications.
	 * Establishes the connection that allows the download system to push updates about finished
	 * download changes, completion events, and other relevant state modifications to this UI component.
	 */
	private fun registerIntoDownloadSystem() {
		logger.d("Registering into DownloadSystem")
		downloadSystem.downloadsUIManager.finishedTasksFragment = safeFinishTasksFragment
	}

	/**
	 * Unregisters this fragment from the download system to prevent memory leaks and stale callbacks.
	 * Clears the fragment reference in the downloads UI manager, ensuring no further update notifications
	 * are sent after fragment destruction. Critical for preventing callback exceptions and memory retention
	 * when the fragment is removed from the navigation stack or during configuration changes.
	 */
	private fun unregisterIntoDownloadSystem() {
		logger.d("Unregistering from DownloadSystem")
		downloadSystem.downloadsUIManager.finishedTasksFragment = null
	}

	/**
	 * Retrieves the complete collection of finished download models from the download system.
	 * Provides access to all successfully completed downloads for display in the list adapter.
	 * Returns a live ArrayList that reflects the current state of finished downloads in the system.
	 *
	 * @return ArrayList of DownloadDataModel instances representing all completed downloads
	 */
	fun getFinishedDownloadModels(): ArrayList<DownloadDataModel> {
		logger.d("Returning all finished download data models via array")
		return downloadSystem.finishedDownloadDataModels
	}

	/**
	 * Updates the fragment title to display the current count of finished downloads with optimization.
	 * Only refreshes the title text when the download count actually changes to minimize unnecessary
	 * UI updates and improve performance. Formats the title as "Total Downloads (X)" where X is the
	 * current count. Maintains previous count tracking to prevent redundant rendering operations.
	 *
	 * @param downloadsFragment The parent DownloadsFragment containing the title TextView to update
	 */
	fun updateDownloadFragmentTitle(downloadsFragment: DownloadsFragment?) {
		val container = downloadsFragment?.safeFragmentLayoutRef ?: return
		if (!isFragmentRunning) return

		val total = downloadSystem.finishedDownloadDataModels.size

		// Skip update if count hasn't changed and we have existing downloads
		if (total == lastCheckedFinishedTasks && total > 0) return

		logger.d("Updating fragment title → total=$total")

		val title = container.findViewById<TextView>(R.id.txt_current_frag_name)
		val fixedName = getText(R.string.title_total_downloads)
		val text = "$fixedName ($total)"
		title?.text = text

		lastCheckedFinishedTasks = total
	}

	/**
	 * Loads and configures the Lottie animation for the active tasks navigation button.
	 * Attempts to load a custom animation composition from app resources first, then falls back
	 * to the default animation if custom composition is unavailable. Configures animation
	 * properties for optimal display and automatically starts playback once loaded.
	 *
	 * Animation provides visual feedback and enhances user engagement for the active
	 * downloads navigation feature. Uses non-clipping bounds and FIT_XY scaling to
	 * ensure proper rendering across different device sizes and resolutions.
	 */
	private fun loadOpenActiveTasksAnimation() {
		logger.d("Loading open-active-tasks animation")

		openActiveTasksAnim?.apply {
			// Configure animation properties for optimal rendering
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			// Try to load custom animation, fall back to default
			aioRawFiles.getDownloadFoundAnimationComposition()?.let {
				setComposition(it)
				playAnimation()
			} ?: setAnimation(R.raw.animation_videos_found)

			showView(this, true, 100)
		}
	}
}
