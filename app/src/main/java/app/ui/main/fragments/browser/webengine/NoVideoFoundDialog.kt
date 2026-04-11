package app.ui.main.fragments.browser.webengine

import android.view.View
import android.widget.ImageView
import app.core.AIOApp.Companion.IS_ULTIMATE_VERSION_UNLOCKED
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.BaseActivity
import app.core.engines.video_parser.parsers.SupportedURLs.isYouTubeUrl
import app.ui.main.guides.GuidePlatformPicker
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder
import java.lang.ref.WeakReference

/**
 * A dialog shown when no downloadable video is detected on a web page.
 *
 * This dialog provides users with options when no video content could be parsed or recognized
 * by the `WebViewEngine`. It optionally guides users to a manual platform selection guide.
 *
 * Behavior:
 * - If the app is not in the ultimate version and the URL is a YouTube URL, the guide is not shown.
 * - Otherwise, the dialog presents a button to open the `GuidePlatformPicker` for manual help.
 *
 * @property baseActivity The activity context used for showing the dialog.
 * @property webviewEngine The current instance of [WebViewEngine] tied to the web session.
 */
class NoVideoFoundDialog(
	val baseActivity: BaseActivity,
	val webviewEngine: WebViewEngine
) {
	
	/** A weak reference to avoid leaking the activity context. */
	private val safeBaseActivity = WeakReference(baseActivity).get()
	
	/** Builder instance for constructing and controlling the dialog. */
	private val dialogBuilder: DialogBuilder = DialogBuilder(safeBaseActivity)
	
	init {
		dialogBuilder.setView(R.layout.dialog_no_video_found_1)
		
		// Set up the dialog view logic
		dialogBuilder.view.apply {
			// When user clicks the positive action button, open guide
			findViewById<View>(R.id.btn_dialog_positive_container).setOnClickListener {
				close()
				if (safeBaseActivity == null) return@setOnClickListener
				GuidePlatformPicker(safeBaseActivity).show()
			}


			findViewById<LottieAnimationView>(R.id.img_empty_downloads).apply {
				clipToCompositionBounds = false
				setScaleType(ImageView.ScaleType.FIT_XY)

				aioRawFiles.getNoResultEmptyComposition()?.let {
					setComposition(it)
					playAnimation()
				} ?: setAnimation(R.raw.animation_no_result)

				showView(this, true, 100)
			}

			// If it's a YouTube URL and the ultimate version isn't unlocked, skip interaction
			webviewEngine.currentWebView?.url?.let { webpageUrl ->
				if (!IS_ULTIMATE_VERSION_UNLOCKED && isYouTubeUrl(webpageUrl)) return@apply
			}
		}
	}
	
	/**
	 * Displays the dialog to the user.
	 */
	fun show() {
		dialogBuilder.show()
	}
	
	/**
	 * Closes the dialog if it's currently shown.
	 */
	fun close() {
		dialogBuilder.close()
	}
}
