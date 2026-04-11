package app.ui.main.fragments.downloads.fragments.finished

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.isVisible
import app.core.AIOApp.Companion.aioRawFiles
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.aioTimer
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOTimer.AIOTimerListener
import app.core.bases.BaseFragment
import app.core.engines.downloader.AIODownload
import app.core.engines.settings.AIOSettings.Companion.SYSTEM_GALLERY
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.DownloadsFragment
import app.ui.main.guides.GuidePlatformPicker
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.hideView
import lib.ui.ViewUtility.showView

/**
 * A fragment responsible for displaying and managing the list of completed download tasks.
 *
 * This fragment provides the user interface for viewing finished downloads, allowing users to
 * open files, manage private downloads, and view download history. It integrates with the
 * [downloadSystem] to fetch task data and utilizes [AIOTimerListener] for periodic UI
 * synchronization, such as updating task counts and toggling visibility of empty state views.
 *
 * Key functionalities include:
 * - Displaying a list of completed downloads via [FinishedTasksListAdapter].
 * - Handling click and long-click events on finished tasks to open files or show options.
 * - Dynamic UI updates for empty states and navigation to active downloads.
 * - Integration with [DownloadsFragment] to manage private folder visibility and titles.
 *
 * @see BaseFragment
 * @see FinishedTasksClickEvents
 * @see AIOTimerListener
 */
class FinishedTasksFragment : BaseFragment(), FinishedTasksClickEvents, AIOTimerListener {

	/**
	 * Helper for logging diagnostic messages and events specific to this fragment.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * The view container displayed when there are no completed downloads to show.
	 * It typically includes an empty state animation and action buttons to guide the user.
	 */
	private var containerEmptyDownloads: View? = null

	/**
	 * A button view that allows the user to navigate back to the active downloads tab.
	 * This view is automatically toggled visible when there are ongoing download tasks
	 * and hidden when the active download list is empty.
	 */
	private var btnOpenActiveDownloads: View? = null

	/**
	 * A reference to the view that, when clicked, opens the tutorial or guide
	 * (typically [GuidePlatformPicker]) to help users understand how to download files.
	 */
	private var btnHowToDownload: View? = null

	/**
	 * Button that allows the user to exit the private files viewing mode and return
	 * to the standard finished downloads list.
	 */
	private var btnClosePrivateFiles: View? = null

	/**
	 * Lottie animation view displayed within the button that navigates to active downloads.
	 * This animation is triggered to provide visual feedback when there are ongoing download tasks.
	 */
	private var animOpenActiveDownloads: LottieAnimationView? = null

	/**
	 * Lottie animation view displayed when there are no finished downloads to show.
	 * This provides a visual cue (e.g., an empty state animation) to the user
	 * when the list of completed tasks is empty.
	 */
	private var animEmptyDownloads: LottieAnimationView? = null

	/**
	 * The [ListView] responsible for displaying the list of successfully completed download tasks.
	 */
	var listViewDownloads: ListView? = null

	/**
	 * Tracks the number of finished tasks during the last UI update to prevent redundant
	 * layout operations or text updates. A value of -2 indicates a reset state (e.g., when paused).
	 */
	private var lastCheckedFinishedTasks = 0

	/**
	 * The adapter responsible for managing and displaying the list of successfully completed download tasks.
	 * It handles the data binding between the [AIODownload] items and the [listViewDownloads].
	 */
	var finishedTasksListAdapter: FinishedTasksListAdapter? = null

	/**
	 * Provides a type-safe reference to the hosting [MotherActivity].
	 *
	 * This property attempts to cast [safeBaseActivityRef] to [MotherActivity],
	 * returning null if the activity is not yet attached, has been destroyed,
	 * or is of a different activity type.
	 */
	val safeMotherActivityRef: MotherActivity?
		get() = safeBaseActivityRef as? MotherActivity

	/**
	 * Provides a type-safe reference to the current fragment as [FinishedTasksFragment].
	 * This is used to ensure that callbacks and background tasks (like timers or download updates)
	 * interact with the fragment only when it is in a valid state and correctly cast.
	 *
	 * @return The current [FinishedTasksFragment] instance if the base fragment reference
	 * is still valid and of the correct type, null otherwise.
	 */
	val safeFinishTasksFragment: FinishedTasksFragment?
		get() = safeBaseFragmentRef as? FinishedTasksFragment

	/**
	 * Returns the layout resource ID for the finished tasks fragment.
	 *
	 * @return The layout resource [R.layout.frag_down_4_finish_1].
	 */
	override fun getLayoutResId() = R.layout.frag_down_4_finish_1

	/**
	 * Called after the fragment's layout has been inflated and added to the view hierarchy.
	 * This method initiates the binding of UI components and sets up the list adapter
	 * for displaying completed download tasks.
	 *
	 * @param layoutView The root view of the fragment's inflated layout.
	 * @param state If non-null, this fragment is being re-constructed from a previous saved state.
	 */
	override fun onAfterLayoutLoad(layoutView: View, state: Bundle?) {
		initializeViewsAndListAdapter(layoutView)
	}

	/**
	 * Called when the fragment is visible to the user and actively running.
	 *
	 * This implementation handles the registration of the fragment into the download system
	 * and parent [DownloadsFragment], and attaches the fragment to the global [aioTimer]
	 * to begin receiving periodic UI updates.
	 */
	override fun onResumeFragment() {
		registerIntoDownloadSystem()
		registerToDownloadFragment()
		safeFinishTasksFragment?.let {
			aioTimer.register(it)
		}
	}

	/**
	 * Resets the finished tasks check counter and unregisters the fragment from the
	 * global timer to stop periodic UI updates when the fragment is no longer visible.
	 */
	override fun onPauseFragment() {
		lastCheckedFinishedTasks = -2
		safeFinishTasksFragment?.let {
			aioTimer.unregister(it)
		}
	}

	/**
	 * Cleans up the view resources and unregisters listeners when the fragment's view is being destroyed.
	 *
	 * This method ensures that:
	 * - The [finishedTasksListAdapter] is cleared and nullified to prevent memory leaks.
	 * - References to UI components (ListView, buttons, animations) are nullified.
	 * - The fragment is unregistered from the global [aioTimer].
	 * - Connections to the internal download system and the parent [DownloadsFragment] are severed.
	 */
	override fun onDestroyView() {
		finishedTasksListAdapter?.clearResources()
		listViewDownloads?.adapter = null
		finishedTasksListAdapter = null

		listViewDownloads = null
		containerEmptyDownloads = null
		btnOpenActiveDownloads = null
		btnHowToDownload = null
		animOpenActiveDownloads = null

		safeFinishTasksFragment?.let { aioTimer.unregister(it) }
		unregisterIntoDownloadSystem()
		unregisterToDownloadFragment()

		super.onDestroyView()
	}

	/**
	 * Called periodically by the AIO timer to refresh the UI state of the finished tasks fragment.
	 *
	 * This method updates the parent fragment's title, refreshes private mode button text,
	 * and toggles the visibility of the empty list state, private file controls,
	 * and active downloads shortcut based on the current download status.
	 *
	 * @param loopCount The current tick count provided by the global timer.
	 */
	override fun onAIOTimerTick(loopCount: Double) {
		if (!isAdded || isDetached) return
		if (safeFinishTasksFragment == null) return
		if (!isFragmentRunning) return

		val downloadsFragment = parentFragment as? DownloadsFragment
		updateDownloadFragmentTitle(downloadsFragment)
		updateDownloadFragmentPrivateButtonText(downloadsFragment)
		toggleEmptyListVisibility(containerEmptyDownloads, listViewDownloads)
		toggleClosePrivateButtonVisibility(btnClosePrivateFiles)
		toggleOpenActiveTasksButtonVisibility(btnOpenActiveDownloads)
	}

	/**
	 * Handles the click event on a completed download item.
	 *
	 * This function updates the [downloadModel] to mark the file as opened and persists this state.
	 * Depending on the user's settings, it will either immediately play/open the media file
	 * (if single-click to open is enabled) or display an options menu for the finished download.
	 *
	 * @param downloadModel The data model representing the finished download task being clicked.
	 */
	override fun onFinishedDownloadClick(downloadModel: AIODownload) {
		val activityRef = safeMotherActivityRef
		val fragment = safeFinishTasksFragment


		if (activityRef == null) return
		if (fragment == null) return

		downloadModel.hasUserOpenedTheFile = true
		downloadModel.updateInDB()

		val isSingleClickEnabled = aioSettings.openDownloadedFileOnSingleClick
		val finishedDownloadOptions = FinishedDownloadOptions(fragment)

		if (isSingleClickEnabled) {
			finishedDownloadOptions.setDownloadModel(downloadModel)
			finishedDownloadOptions.playTheMedia()
		} else {
			finishedDownloadOptions.show(downloadModel)
		}
	}

	/**
	 * Handles the long-click event on a finished download item.
	 *
	 * This method updates the file's "opened" status in the storage, displays a
	 * bottom sheet or dialog containing management options (such as share, delete, or rename),
	 * and triggers a haptic feedback (vibration) to acknowledge the long-press action.
	 *
	 * @param downloadModel The data model representing the completed download.
	 */
	override fun onFinishedDownloadLongClick(downloadModel: AIODownload) {
		val activityRef = safeMotherActivityRef
		val fragment = safeFinishTasksFragment

		if (activityRef == null) return
		if (fragment == null) return

		downloadModel.hasUserOpenedTheFile = true
		downloadModel.updateInDB()

		val finishedDownloadOptions = FinishedDownloadOptions(fragment)
		finishedDownloadOptions.show(downloadModel)

		activityRef.doSomeVibration()
	}

	/**
	 * Initializes the UI components, click listeners, and the list adapter for the finished tasks.
	 *
	 * This function performs the following setup:
	 * - Resolves references to the mother activity and fragment context.
	 * - Binds view elements from the provided [layout] to class members.
	 * - Attaches click listeners for guiding, switching to active tasks, and toggling private files.
	 * - Loads Lottie animations for empty states and active task indicators.
	 * - Configures the [FinishedTasksListAdapter] with a filter specifically for items
	 *   stored in the system gallery.
	 * - Refreshes the initial visibility state of the empty list container and active task button.
	 *
	 * @param layout The root view of the fragment used to find child views.
	 */
	private fun initializeViewsAndListAdapter(layout: View) {
		val motherActivity = safeMotherActivityRef
		val fragment = safeFinishTasksFragment

		if (motherActivity == null) return
		if (fragment == null) return

		containerEmptyDownloads = layout.findViewById(R.id.container_empty_downloads)
		btnHowToDownload = layout.findViewById(R.id.btn_how_to_download)
		btnClosePrivateFiles = layout.findViewById(R.id.btn_close_private_files)
		btnOpenActiveDownloads = layout.findViewById(R.id.btn_open_active_downloads)
		animOpenActiveDownloads = layout.findViewById(R.id.img_open_active_downloads)
		animEmptyDownloads = layout.findViewById(R.id.img_empty_downloads)
		listViewDownloads = layout.findViewById(R.id.container_download_tasks_finished)

		btnHowToDownload?.setOnClickListener { GuidePlatformPicker(motherActivity).show() }
		btnOpenActiveDownloads?.setOnClickListener { openActiveTasksFragment() }
		btnClosePrivateFiles?.setOnClickListener { triggerTogglingPrivateFiles() }

		loadOpenActiveTasksAnimation()
		loadEmptyDownloadsAnimation()

		finishedTasksListAdapter = FinishedTasksListAdapter(fragment)
		listViewDownloads?.adapter = finishedTasksListAdapter
		finishedTasksListAdapter?.setFilter { downloadModel ->
			val downloadConfigs = downloadModel.config
			downloadConfigs.defaultDownloadLocationType == SYSTEM_GALLERY
		}

		toggleEmptyListVisibility(containerEmptyDownloads, listViewDownloads)
		toggleOpenActiveTasksButtonVisibility(btnOpenActiveDownloads)
	}

	/**
	 * Navigates the user to the active downloads tab within the parent [DownloadsFragment].
	 * This is typically triggered by a shortcut button when there are ongoing tasks.
	 */
	private fun openActiveTasksFragment() {
		(parentFragment as? DownloadsFragment)?.openActiveTab()
	}

	/**
	 * Toggles the visibility of the "Open Active Tasks" button based on whether there
	 * are currently any ongoing downloads.
	 *
	 * If there are active tasks, the button is shown with an animation. If the active
	 * task list is empty, the button is hidden.
	 *
	 * @param button The view representing the active tasks button to be toggled.
	 */
	private fun toggleOpenActiveTasksButtonVisibility(button: View?) {
		button ?: return
		val activeModels = downloadSystem.activeDownloadDataModels
		val shouldVisible = activeModels.isNotEmpty()
		if (shouldVisible != button.isVisible) {
			if (shouldVisible) showView(button, true, 300)
			else hideView(button, true, 300)
		}
	}

	/**
	 * Triggers the process to hide or exit the private files view mode.
	 *
	 * This function communicates with the parent [DownloadsFragment] to toggle the state
	 * of private files. It includes safety checks to ensure the fragment is currently
	 * running and that the private folder mode is indeed active before attempting to toggle.
	 */
	private fun triggerTogglingPrivateFiles() {
		val fragment = safeFinishTasksFragment
		val motherActivity = safeMotherActivityRef

		if (!isFragmentRunning) return
		if (fragment == null) return
		if (motherActivity == null) return

		val downloadsFragment = parentFragment as? DownloadsFragment ?: return
		val isPrivateFolderActive = (downloadsFragment).isShowingPrivateFiles
		if (!isPrivateFolderActive) return
		downloadsFragment.togglePrivateFiles()
	}

	/**
	 * Toggles the visibility of the "Close Private Files" button based on the current
	 * viewing state of the downloads fragment.
	 *
	 * The button is displayed only when the user is currently viewing the private
	 * files directory, allowing them to exit that view and return to public files.
	 *
	 * @param btnView The view element to be shown or hidden.
	 */
	private fun toggleClosePrivateButtonVisibility(btnView: View?) {
		val fragment = safeFinishTasksFragment
		val motherActivity = safeMotherActivityRef

		if (!isFragmentRunning) return
		if (fragment == null) return
		if (motherActivity == null) return
		if (btnView == null) return

		val downloadsFragment = parentFragment as? DownloadsFragment ?: return
		val isPrivateFolderActive = (downloadsFragment).isShowingPrivateFiles
		val visibility = if (isPrivateFolderActive) View.VISIBLE else View.GONE
		btnView.visibility = visibility
	}

	/**
	 * Toggles the visibility between the empty state placeholder and the list of finished downloads.
	 *
	 * This function checks if there are any completed downloads. If the list is empty, it displays
	 * the [emptyView] (typically containing an illustration or message) and hides the [listView].
	 * If downloads are present, it performs the reverse. It also triggers a UI refresh for the
	 * adapter to ensure the data is synchronized.
	 *
	 * @param emptyView The [View] to be displayed when no finished downloads are found.
	 * @param listView The [View] (usually a [ListView]) that displays the finished download items.
	 */
	private fun toggleEmptyListVisibility(emptyView: View?, listView: View?) {
		if (downloadSystem.isInitializing) return
		if (!isFragmentRunning) return
		if (emptyView == null || listView == null) return

		finishedTasksListAdapter?.count?.let {
			if (it < 1) {
				hideView(listView, true, 100)
				showView(emptyView, true, 300)
			} else {
				hideView(emptyView, true, 100)
				showView(listView, true, 300)
			}
		}

		finishedTasksListAdapter?.notifyDataSetChangedOnSort(false)
	}

	/**
	 * Registers this fragment instance within the parent [DownloadsFragment].
	 *
	 * This establishes a back-reference that allows the parent fragment to coordinate
	 * UI updates, such as refreshing the title with the current count of finished downloads.
	 */
	private fun registerToDownloadFragment() {
		(parentFragment as? DownloadsFragment)?.let {
			it.finishedTasksFragment = this
			updateDownloadFragmentTitle(it)
		}
	}

	/**
	 * Detaches this fragment from the parent [DownloadsFragment] by clearing its reference
	 * to this instance, ensuring that no further communication occurs after the view is destroyed.
	 */
	private fun unregisterToDownloadFragment() {
		(parentFragment as? DownloadsFragment)?.finishedTasksFragment = null
	}

	/**
	 * Registers this fragment instance into the global download system's UI manager.
	 * This allows the download system to communicate updates directly to this fragment,
	 * ensuring the UI stays synchronized with the state of completed tasks.
	 */
	private fun registerIntoDownloadSystem() {
		val downloadsUIManager = downloadSystem.downloadsUIManager
		downloadsUIManager.finishedTasksFragment = safeFinishTasksFragment
	}

	/**
	 * Unregisters this fragment from the global download system's UI manager.
	 *
	 * This clears the reference to this fragment in the download system to prevent
	 * memory leaks and ensure that UI updates are no longer dispatched to this
	 * instance after it has been destroyed.
	 */
	private fun unregisterIntoDownloadSystem() {
		val downloadsUIManager = downloadSystem.downloadsUIManager
		downloadsUIManager.finishedTasksFragment = null
	}

	/**
	 * Retrieves the list of download tasks that have successfully completed.
	 *
	 * This function queries the [downloadSystem] to provide a collection of [AIODownload]
	 * objects representing all finished downloads.
	 *
	 * @return An [ArrayList] of [AIODownload] containing the records of completed downloads.
	 */
	fun getFinishedDownloadModels(): ArrayList<AIODownload> {
		return downloadSystem.finishedDownloadDataModels
	}

	/**
	 * Updates the title in the parent [DownloadsFragment] to display the current count of finished tasks.
	 *
	 * This function retrieves the total number of finished downloads and updates the UI text to
	 * follow the format: "Title (Count)". It includes an optimization check to prevent redundant
	 * UI updates if the task count has not changed since the last update.
	 *
	 * @param fragment The parent [DownloadsFragment] instance whose title needs to be updated.
	 */
	fun updateDownloadFragmentTitle(fragment: DownloadsFragment?) {
		val container = fragment?.safeFragmentLayoutRef ?: return
		if (!isFragmentRunning) return

		val total = getFinishedDownloadModels().size
		if (total == lastCheckedFinishedTasks && total > 0) return

		val title = container.findViewById<TextView>(R.id.txt_current_frag_name)
		val fixedName = getText(R.string.title_total_files)
		val text = "$fixedName ($total)"
		title?.text = text

		lastCheckedFinishedTasks = total
	}

	/**
	 * Updates the text or appearance of the private files button within the parent [DownloadsFragment].
	 *
	 * This method ensures the button UI accurately reflects the current state of the private
	 * folder (e.g., whether it is locked, unlocked, or showing private content).
	 *
	 * @param fragment The parent [DownloadsFragment] containing the UI elements to be updated.
	 */
	fun updateDownloadFragmentPrivateButtonText(fragment: DownloadsFragment?) {
		if (!isFragmentRunning) return
		fragment?.togglePrivateFilesButtonUI()
	}

	/**
	 * Configures and starts the Lottie animation for the "Open Active Tasks" button.
	 *
	 * This method initializes the [animOpenActiveDownloads] view by setting its scale type,
	 * loading the "download found" animation composition from raw resources, and triggering
	 * the animation playback.
	 */
	private fun loadOpenActiveTasksAnimation() {
		animOpenActiveDownloads?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			aioRawFiles.getDownloadFoundAnimationComposition()?.let {
				setComposition(it)
				playAnimation()
			} ?: setAnimation(R.raw.animation_videos_found)

			showView(this, true, 100)
		}
	}

	/**
	 * Configures and plays the Lottie animation displayed when the finished downloads list is empty.
	 *
	 * This method sets up the [animEmptyDownloads] view by adjusting its scaling, loading the
	 * "no results" animation composition from raw resources, and initiating playback.
	 */
	private fun loadEmptyDownloadsAnimation() {
		animEmptyDownloads?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			aioRawFiles.getNoResultEmptyComposition()?.let {
				setComposition(it)
				playAnimation()
			} ?: setAnimation(R.raw.animation_no_result)

			showView(this, true, 100)
		}
	}
}