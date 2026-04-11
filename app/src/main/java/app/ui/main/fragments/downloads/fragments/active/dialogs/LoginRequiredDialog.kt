package app.ui.main.fragments.downloads.fragments.active.dialogs

import android.view.View
import android.widget.ImageView
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.BaseActivity
import app.core.engines.caches.LoginSessionCache.markUserLoggedInForHost
import app.core.engines.downloader.AIODownload
import app.ui.main.MotherActivity
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.networks.URLUtilityKT.getHostFromUrl
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.WeakReference

/**
 * A dialog class that prompts the user to log in when a download requires authentication.
 *
 * This dialog typically displays an animation and provides a call-to-action that redirects
 * the user to the [MotherActivity]'s browser fragment to complete the login process
 * for the specific [dataModel] site referrer.
 *
 * @property baseActivity The [BaseActivity] context used to build the dialog and access navigation.
 * @property dataModel The [AIODownload] containing the download information and site referrer.
 */
class LoginRequiredDialog(val baseActivity: BaseActivity, val dataModel: AIODownload) {

	/**
	 * Logger instance used for tracking errors and diagnostic information within this dialog.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A weak reference to the [baseActivity] used to prevent memory leaks while
	 * providing access to the activity context for dialog operations.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)

	/**
	 * Provides a memory-safe access to the [BaseActivity] instance via a [WeakReference].
	 * Returns the activity if it is still in memory, or `null` if it has been reclaimed by the GC.
	 */
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()

	/**
	 * A lazy-initialized [DialogBuilder] instance used to construct and manage
	 * the visual representation of the login required dialog.
	 */
	private val dialogBuilder by lazy { DialogBuilder(safeBaseActivity) }

	/**
	 * The root view of the dialog, retrieved from [dialogBuilder].
	 * This layout is used to initialize and interact with the UI components within the dialog.
	 */
	private val dialogLayout by lazy { dialogBuilder.view }

	init {
		initialize()
	}

	/**
	 * Initializes the dialog by setting the content view and calling [initializeViews]
	 * to set up the interactive elements.
	 */
	fun initialize() {
		dialogBuilder.apply {
			setView(R.layout.dialog_login_required_1)
			initializeViews(view)
		}
	}

	/**
	 * Initializes the UI components of the login required dialog.
	 *
	 * This function sets up the Lottie animation by attempting to load a pre-cached composition
	 * or falling back to a raw resource. It also configures the click listener for the positive
	 * button, which triggers a navigation flow to a new browsing tab using the site referrer
	 * from the [dataModel] and resets the error state of the download model.
	 *
	 * @param dialogLayout The root view of the dialog containing the UI elements to be initialized.
	 */
	fun initializeViews(dialogLayout: View) {
		if (safeBaseActivity == null) return
		dialogLayout.findViewById<LottieAnimationView>(R.id.img_loading_anim)?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			val composition = aioRawFiles.getLoginRequiredComposition()
			if (composition != null) {
				setComposition(composition)
				playAnimation()
				showView(this, true, 100)
			} else {
				setAnimation(R.raw.animation_login_required)
				showView(this, true, 100)
			}
		}

		dialogLayout.findViewById<View>(R.id.btn_dialog_positive_container).setOnClickListener {
			try {
				if (safeBaseActivity is MotherActivity) {
					val motherActivityRef = safeBaseActivity as MotherActivity
					val browserFragment = motherActivityRef.browserFragment
					val webviewEngine = browserFragment?.getBrowserWebEngine()
						?: return@setOnClickListener

					val sideNavigation = motherActivityRef.sideNavigation
					sideNavigation?.addNewBrowsingTab(dataModel.siteReferrer, webviewEngine)
					motherActivityRef.openBrowserFragment()
					markUserLoggedInForHost(getHostFromUrl(dataModel.siteReferrer))

					dataModel.isYtdlpErrorFound = false
					dataModel.ytdlpErrorMessage = ""
					close()
				}
			} catch (error: Exception) {
				logger.e("Error while processing login required download:", error)
				showToast(safeBaseActivity, R.string.title_something_went_wrong)
				close()
			}
		}
	}

	/**
	 * Displays the login required dialog if it is not already being shown.
	 */
	fun show() {
		if (dialogBuilder.isShowing == false) {
			dialogBuilder.show()
		}
	}

	/**
	 * Dismisses the login required dialog if it is currently visible and clears
	 * the weak reference to the base activity to prevent memory leaks.
	 */
	fun close() {
		if (dialogBuilder.isShowing) {
			dialogBuilder.close()
			weakReferenceOfBaseActivity.clear()
		}
	}

}