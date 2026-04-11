package app.ui.others.startup

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.view.View.INVISIBLE
import android.widget.TextView
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.BaseActivity
import app.ui.main.MotherActivity
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lib.device.AppVersionUtility
import lib.process.LogHelperUtils
import lib.ui.ActivityAnimator.animActivityFade
import lib.ui.ViewUtility.hideView

class OpeningActivity : BaseActivity() {

	private val logger = LogHelperUtils.from(javaClass)

	override fun onRenderingLayout(): Int {
		logger.d("Rendering layout")
		return R.layout.activity_opening_1
	}

	override fun onAfterLayoutRender() {
		logger.d("After layout render")
		showApkVersionInfo()
		getAttachedCoroutineScope().launch {
			logger.d("Starting Lottie lazy load")
			aioRawFiles.getLayoutLoadComposition()
			aioRawFiles.getNoResultEmptyComposition()
			aioRawFiles.getWaitingLoadingComposition()
			logger.d("Finished Lottie lazy load, waiting 1.5s")
			delay(1500)
			stopLoadingLottieAnimation()
			launchMotherActivity()
		}
	}

	override fun onBackPressActivity() = exitActivityOnDoubleBackPress()

	private fun showApkVersionInfo() {
		val versionName = AppVersionUtility.versionName
		"${getString(R.string.title_version)} : $versionName".apply {
			findViewById<TextView>(R.id.txt_version_info).text = this
		}
		logger.d("Displayed APK version: $versionName")
	}

	private fun stopLoadingLottieAnimation() {
		getActivity()?.let { activity ->
			val id = R.id.img_loading_lottie_anim
			val lottieView = activity.findViewById<LottieAnimationView>(id)
			hideView(lottieView, INVISIBLE, true, 300)
			lottieView.pauseAnimation()
			logger.d("Stopped Lottie animation")
		}
	}

	private fun launchMotherActivity() {
		getActivity()?.let { activity ->
			Intent(activity, MotherActivity::class.java).apply {
				flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
				startActivity(this)
				animActivityFade(activity)
				finish()
				logger.d("Launched MotherActivity")
			}
		}
	}
}