package app.ui.others.information

import android.view.*
import app.core.bases.*
import com.aio.*
import lib.process.*

class UserAccountDetailsActivityVideo : BaseActivityVideo() {
	
	private val logger = LogHelperUtils.from(javaClass)
	private val safeAccountDetailsActivityRef get() = getActivity() as? UserAccountDetailsActivityVideo
	
	override fun onRenderingLayout(): Int {
		return R.layout.activity_user_account_profile_1
	}
	
	override fun onAfterLayoutRender() {
		safeAccountDetailsActivityRef?.let { safeActivityRef ->
			initializeViewsClickEvents(safeActivityRef)
		}
	}
	
	override fun onBackPressActivity() {
		finish()
	}
	
	private fun initializeViewsClickEvents(
		safeActivityRef: UserAccountDetailsActivityVideo?
	) {
		safeActivityRef?.apply {
			findViewById<View>(R.id.btn_left_actionbar).setOnClickListener { view ->
				onBackPressActivity()
			}
		}
	}
}