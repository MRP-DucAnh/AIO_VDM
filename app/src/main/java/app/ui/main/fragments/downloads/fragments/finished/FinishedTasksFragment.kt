package app.ui.main.fragments.downloads.fragments.finished

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.isVisible
import app.core.AIOApp
import app.core.AIOApp.Companion.aioRawFiles
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOTimer.AIOTimerListener
import app.core.bases.BaseFragment
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.settings.AIOSettings.Companion.SYSTEM_GALLERY
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.DownloadsFragment
import app.ui.main.guides.GuidePlatformPicker
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.hideView
import lib.ui.ViewUtility.showView

class FinishedTasksFragment : BaseFragment(), FinishedTasksClickEvents, AIOTimerListener {

	private val logger = LogHelperUtils.from(javaClass)

	private var emptyDownloadContainer: View? = null
	private var buttonOpenActiveTasks: View? = null
	private var openActiveTasksAnim: LottieAnimationView? = null
	private var buttonHowToDownload: View? = null
	private var downloadsListView: ListView? = null

	private var lastCheckedFinishedTasks = 0

	var finishedTasksListAdapter: FinishedTasksListAdapter? = null

	val safeMotherActivityRef: MotherActivity? get() = safeBaseActivityRef as MotherActivity
	val safeFinishTasksFragment: FinishedTasksFragment? get() = safeBaseFragmentRef as FinishedTasksFragment

	override fun getLayoutResId() = R.layout.frag_down_4_finish_1

	override fun onAfterLayoutLoad(layoutView: View, state: Bundle?) {
		logger.d("onAfterLayoutLoad() → initializing UI")
		initializeViewsAndListAdapter(layoutView)
	}

	override fun onResumeFragment() {
		logger.d("onResumeFragment() → registering UI + timer")
		registerIntoDownloadSystem()
		registerToDownloadFragment()
		safeFinishTasksFragment?.let { AIOApp.aioTimer.register(it) }
	}

	override fun onPauseFragment() {
		logger.d("onPauseFragment() → unregistering UI + timer")
		safeFinishTasksFragment?.let { AIOApp.aioTimer.unregister(it) }
	}

	override fun onDestroyView() {
		logger.d("onDestroyView() → cleaning resources")

		finishedTasksListAdapter?.let { adapter ->
			logger.d("Cleaning adapter resources… count=${adapter.count}")
			adapter.clearResources()
		}

		downloadsListView?.adapter = null
		finishedTasksListAdapter = null

		downloadsListView = null
		emptyDownloadContainer = null
		buttonOpenActiveTasks = null
		buttonHowToDownload = null
		openActiveTasksAnim = null

		safeFinishTasksFragment?.let { AIOApp.aioTimer.unregister(it) }
		unregisterIntoDownloadSystem()
		unregisterToDownloadFragment()

		logger.d("onDestroyView() → completed cleanup")
		super.onDestroyView()
	}

	override fun onAIOTimerTick(loopCount: Double) {
		if (!isAdded || isDetached) {
			logger.d("onAIOTimerTick() skipped → fragment not active")
			return
		}

		logger.d("onAIOTimerTick() → UI update")
		safeFinishTasksFragment?.let {
			if (!isFragmentRunning) return
			updateDownloadFragmentTitle(parentFragment as? DownloadsFragment)
			updateDownloadFragmentPrivateButtonText(parentFragment as? DownloadsFragment)
			toggleEmptyListVisibility(emptyDownloadContainer, downloadsListView)
			toggleOpenActiveTasksButtonVisibility(buttonOpenActiveTasks)
		}
	}

	override fun onFinishedDownloadClick(downloadModel: DownloadDataModel) {
		logger.d("Finished item clicked: id=${downloadModel.id}")

		safeMotherActivityRef?.let { activity ->
			downloadModel.hasUserOpenedTheFile = true
			downloadModel.updateInStorage()

			val opts = FinishedDownloadOptions(safeFinishTasksFragment)
			opts.initialize()
			if (aioSettings.openDownloadedFileOnSingleClick) {
				opts.setDownloadModel(downloadModel)
				opts.playTheMedia()
			} else {
				opts.show(downloadModel)
			}
		}
	}

	override fun onFinishedDownloadLongClick(downloadModel: DownloadDataModel) {
		logger.d("Finished item long-clicked: id=${downloadModel.id}")

		safeMotherActivityRef?.let { activity ->
			downloadModel.hasUserOpenedTheFile = true
			downloadModel.updateInStorage()
			val opts = FinishedDownloadOptions(safeFinishTasksFragment)
			activity.doSomeVibration()
			opts.show(downloadModel)
		}
	}

	private fun initializeViewsAndListAdapter(layout: View) {
		logger.d("Initializing views + adapter…")

		safeFinishTasksFragment?.let { fragment ->
			val activityRef = fragment.safeMotherActivityRef

			emptyDownloadContainer = layout.findViewById(R.id.container_empty_downloads)

			buttonHowToDownload = layout.findViewById(R.id.btn_how_to_download)
			buttonHowToDownload?.setOnClickListener {
				logger.d("How-to-download clicked")
				GuidePlatformPicker(activityRef).show()
			}

			buttonOpenActiveTasks = layout.findViewById(R.id.btn_open_active_downloads)
			buttonOpenActiveTasks?.setOnClickListener {
				logger.d("Open active tasks clicked")
				openActiveTasksFragment()
			}

			openActiveTasksAnim = layout.findViewById(R.id.img_open_active_downloads)
			loadOpenActiveTasksAnimation()

			downloadsListView = layout.findViewById(R.id.container_download_tasks_finished)
			finishedTasksListAdapter = FinishedTasksListAdapter(fragment)
			finishedTasksListAdapter?.setFilter { it.globalSettings.defaultDownloadLocation == SYSTEM_GALLERY }
			downloadsListView?.adapter = finishedTasksListAdapter

			logger.d("Views + adapter initialization complete")

			toggleEmptyListVisibility(emptyDownloadContainer, downloadsListView)
			toggleOpenActiveTasksButtonVisibility(buttonOpenActiveTasks)
		}
	}

	private fun openActiveTasksFragment() {
		logger.d("Switching to active-tasks tab")
		(parentFragment as? DownloadsFragment)?.openActiveTab()
	}

	private fun toggleOpenActiveTasksButtonVisibility(button: View?) {
		button ?: return
		val show = downloadSystem.activeDownloadDataModels.isNotEmpty()
		if (show != button.isVisible) {
			if (show) showView(button, true, 300)
			else hideView(button, true, 300)
		}
	}

	private fun toggleEmptyListVisibility(emptyView: View?, listView: View?) {
		if (downloadSystem.isInitializing) return
		if (!isFragmentRunning) return
		if (emptyView == null || listView == null) return

		val empty = getFinishedDownloadModels().isEmpty()
		logger.d("Toggle empty-state UI → empty=$empty")

		if (empty) {
			hideView(listView, true, 100)
			showView(emptyView, true, 300)
		} else {
			hideView(emptyView, true, 100)
			showView(listView, true, 300)
		}

		finishedTasksListAdapter?.notifyDataSetChangedOnSort(false)
	}

	private fun registerToDownloadFragment() {
		logger.d("Registering this fragment to parent DownloadsFragment")
		(parentFragment as? DownloadsFragment)?.let {
			it.finishedTasksFragment = this
			updateDownloadFragmentTitle(it)
		}
	}

	private fun unregisterToDownloadFragment() {
		logger.d("Unregistering fragment from parent DownloadsFragment")
		(parentFragment as? DownloadsFragment)?.finishedTasksFragment = null
	}

	private fun registerIntoDownloadSystem() {
		logger.d("Registering into DownloadSystem")
		downloadSystem.downloadsUIManager.finishedTasksFragment = safeFinishTasksFragment
	}

	private fun unregisterIntoDownloadSystem() {
		logger.d("Unregistering from DownloadSystem")
		downloadSystem.downloadsUIManager.finishedTasksFragment = null
	}

	fun getFinishedDownloadModels(): ArrayList<DownloadDataModel> {
		logger.d("Returning all finished download data models via array")
		return downloadSystem.finishedDownloadDataModels
	}

	fun updateDownloadFragmentTitle(downloadsFragment: DownloadsFragment?) {
		val container = downloadsFragment?.safeFragmentLayoutRef ?: return
		if (!isFragmentRunning) return

		val total = downloadSystem.finishedDownloadDataModels.size

		if (total == lastCheckedFinishedTasks && total > 0) return

		logger.d("Updating fragment title → total=$total")

		val title = container.findViewById<TextView>(R.id.txt_current_frag_name)
		val fixedName = getText(R.string.title_total_downloads)
		val text = "$fixedName ($total)"
		title?.text = text

		lastCheckedFinishedTasks = total
	}

	fun updateDownloadFragmentPrivateButtonText(downloadsFragment: DownloadsFragment?) {
		if (!isFragmentRunning) return
		downloadsFragment?.togglePrivateFilesButtonUI()
	}

	private fun loadOpenActiveTasksAnimation() {
		logger.d("Loading open-active-tasks animation")

		openActiveTasksAnim?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			aioRawFiles.getDownloadFoundAnimationComposition()?.let {
				setComposition(it)
				playAnimation()
			} ?: setAnimation(R.raw.animation_videos_found)

			showView(this, true, 100)
		}
	}
}
