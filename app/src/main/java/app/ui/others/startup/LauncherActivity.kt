package app.ui.others.startup

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.core.AIOApp.Companion.aioSettings
import app.ui.main.MotherActivity
import app.ui.others.information.UserFeedbackActivity
import app.ui.others.information.UserFeedbackActivity.FROM_CRASH_HANDLER
import app.ui.others.information.UserFeedbackActivity.WHERE_DIS_YOU_COME_FROM
import lib.process.LogHelperUtils
import lib.ui.ActivityAnimator.animActivityFade
import java.lang.ref.WeakReference

class LauncherActivity : AppCompatActivity() {

	private val logger = LogHelperUtils.from(javaClass)
	private val weakReferenceOfActivity = WeakReference(this)
	private val safeActivityRef get() = weakReferenceOfActivity.get()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		logger.d("LauncherActivity created")
		if (aioSettings.hasAppCrashedRecently) {
			logger.d("App crashed recently, launching feedback activity")
			launchFeedbackActivity()
		} else {
			logger.d("No recent crash, launching opening activity")
			launchOpeningActivity()
		}
	}

	private fun getActivity(): LauncherActivity? = safeActivityRef

	private fun launchFeedbackActivity() {
		getActivity()?.let { activity ->
			aioSettings.hasAppCrashedRecently = false
			aioSettings.updateInDB()
			logger.d("Reset crash flag")

			Intent(activity, UserFeedbackActivity::class.java).apply {
				flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
				putExtra(WHERE_DIS_YOU_COME_FROM, FROM_CRASH_HANDLER)
				startActivity(this)
				finish()
				animActivityFade(activity)
				logger.d("Feedback activity launched")
			}
		}
	}

	private fun launchMotherActivity() {
		getActivity()?.let { activity ->
			Intent(activity, MotherActivity::class.java).apply {
				flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
				startActivity(this)
				finish()
				animActivityFade(activity)
				logger.d("MotherActivity launched")
			}
		}
	}

	private fun launchOpeningActivity() {
		getActivity()?.let { activity ->
			Intent(activity, OpeningActivity::class.java).apply {
				flags = FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP
				startActivity(this)
				finish()
				animActivityFade(activity)
				logger.d("OpeningActivity launched successfully from LauncherActivity")
			}
		}
	}
}