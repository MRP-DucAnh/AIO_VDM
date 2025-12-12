package app.ui.main.fragments.settings.dialogs

import android.view.*
import android.widget.*
import app.core.bases.*
import com.aio.*
import lib.process.*
import lib.ui.builders.*
import java.lang.ref.*

class AccountRegistration(baseActivity: BaseActivity) {
	
	private val logger = LogHelperUtils.from(javaClass)
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()
	private val dialogBuilder by lazy { DialogBuilder(safeBaseActivity) }
	private var editTextPassword: EditText? = null
	private var editTextUsername: EditText? = null
	private var containerEditTextUsername: View? = null
	private var containerEditTextPassword: View? = null
	private var btnTogglePasswordVisibility: ImageView? = null
	private var btnForgetPassword: View? = null
	private var btnSignInAccount: View? = null
	private var btnGoogleLogin: View? = null
	private var btnFacebookLogin: View? = null
	private var btnGithubLogin: View? = null
	
	init {
		initializeDialogViews()
	}
	
	fun show() {
		if (!dialogBuilder.isShowing) {
			dialogBuilder.show()
		}
	}
	
	fun close() {
		if (dialogBuilder.isShowing) {
			dialogBuilder.close()
			clearResources()
		}
	}
	
	fun clearResources() {
	
	}
	
	private fun initializeDialogViews() {
		dialogBuilder.setView(R.layout.dialog_user_registration_1)
		dialogBuilder.setCancelable(true)
	}
}
