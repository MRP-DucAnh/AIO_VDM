@file:Suppress("DEPRECATION")

package app.core.bases.language

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.*
import androidx.core.view.*
import com.aio.*

class LanguagePrepareActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_locale_change_prepare)
		setUpStatusBarTransparent()
	}

	private fun setUpStatusBarTransparent() {
		findViewById<RelativeLayout>(R.id.rootView)?.let { rootView ->
			ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
				insets.consumeSystemWindowInsets()
			}
		}
		window?.apply {
			clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
			addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
			decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
				View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
			statusBarColor = Color.TRANSPARENT
		}
	}

	override fun onResume() {
		super.onResume()
		Handler(Looper.getMainLooper())
			.postDelayed(
				{ finishWithTransition() },
				LocalizeConstant.FINISH_DELAY
			)
	}

	private fun finishWithTransition() {
		finish()
		overridePendingTransition(
			android.R.anim.fade_in,
			android.R.anim.fade_out
		)
	}

	companion object {
		fun navigate(activity: Activity) {
			val clazz = LanguagePrepareActivity::class.java
			val intent = Intent(activity, clazz)
			activity.apply {
				startActivity(intent)
				overridePendingTransition(
					android.R.anim.fade_in,
					android.R.anim.fade_out
				)
			}
		}
	}
}