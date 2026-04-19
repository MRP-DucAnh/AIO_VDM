package app.core.bases.dialogs

import android.text.Html
import android.text.Html.FROM_HTML_MODE_COMPACT
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.BaseActivity
import app.core.engines.updater.AIOUpdater.UpdateInfo
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.device.ShareUtility.openApkFile
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.setViewOnClickListener
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder
import lib.ui.builders.ToastView.Companion.showToast
import java.io.File
import java.lang.ref.WeakReference

class UpdaterDialog(
	private val baseActivity: BaseActivity,
	private val latestVersionApkFile: File,
	private val versionInfo: UpdateInfo
) {

	private val logger = LogHelperUtils.from(javaClass)

	private val weakReferenceOfActivity = WeakReference(baseActivity)

	private val safeBaseActivityRef get() = weakReferenceOfActivity.get()

	private val dialogBuilder = DialogBuilder(safeBaseActivityRef)

	init {
		safeBaseActivityRef?.let { activityRef ->
			dialogBuilder.setView(R.layout.dialog_new_version_updater_1)
			dialogBuilder.setCancelable(false)
			setupNewVersionUpdateAnimation()
			setupUpdateChangeLogMessage(activityRef)
			setupClickListeners(activityRef)
		}
	}

	fun show() {
		if (!dialogBuilder.isShowing) {
			dialogBuilder.show()
		}
	}

	fun close() {
		if (dialogBuilder.isShowing) {
			dialogBuilder.close()
		}
	}

	private fun setupClickListeners(activityRef: BaseActivity) {
		setViewOnClickListener(
			{ button: View -> this.setupClickEvents(button) },
			dialogBuilder.view, R.id.btn_dialog_positive_container
		)
	}

	private fun setupNewVersionUpdateAnimation() {
		safeBaseActivityRef?.let {
			with(dialogBuilder.view) {
				findViewById<LottieAnimationView>(R.id.img_version_animation).apply {
					clipToCompositionBounds = false
					setScaleType(ImageView.ScaleType.FIT_XY)

					aioRawFiles.getNewVersionUpdateComposition()?.let {
						setComposition(it)
						playAnimation()
					} ?: setAnimation(R.raw.animation_new_app_version)

					showView(this, true, 100)
				}
			}
		}
	}

	private fun setupUpdateChangeLogMessage(activityRef: BaseActivity) {
		dialogBuilder.view.findViewById<TextView>(R.id.txt_dialog_message)?.let { textView ->
			val htmlMsg = activityRef.getString(
				R.string.title_b_latest_version_b,
				versionInfo.latestVersion
			).trimIndent()

			textView.text = Html.fromHtml(htmlMsg, FROM_HTML_MODE_COMPACT)
			textView.movementMethod = LinkMovementMethod.getInstance()
		}
	}

	private fun setupClickEvents(button: View) {
		when (button.id) {
			R.id.btn_dialog_positive_container -> {
				close()

				safeBaseActivityRef?.let { activityRef ->
					val authority = "${activityRef.packageName}.provider"
					openApkFile(activityRef, latestVersionApkFile, authority)
				} ?: run {
					showToast(safeBaseActivityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}
}