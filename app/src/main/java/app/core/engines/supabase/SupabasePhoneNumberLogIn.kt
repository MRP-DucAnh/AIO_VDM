package app.core.engines.supabase

import android.view.*
import android.widget.*
import app.core.*
import app.core.AIOTimer.*
import app.core.bases.*
import app.core.engines.supabase.SupabaseCloudServer.supabaseClient
import com.aio.*
import io.github.jan.supabase.auth.*
import io.github.jan.supabase.auth.providers.builtin.*
import kotlinx.coroutines.*
import lib.device.DeviceUtility.normalizeIndianNumber
import lib.process.*
import lib.process.CommonTimeUtils.OnTaskFinishListener
import lib.texts.CommonTextUtils.getText
import lib.ui.*
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.lang.ref.*

class SupabasePhoneNumberLogIn(
	private val baseActivity: BaseActivity,
	private val onAccountSuccessfullyRegistered: () -> Unit = {},
	private val onAccountRegistrationFailed: () -> Unit = {}
) : AIOTimerListener {
	
	private val logger = LogHelperUtils.from(javaClass)
	
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()
	
	private val dialogBuilder by lazy { DialogBuilder(safeBaseActivity) }
	
	private var editTextPhoneNumber: EditText? = null
	private var editTextPassword: EditText? = null
	private var containerEditTextPhoneNumber: View? = null
	private var containerEditTextPassword: View? = null
	private var btnRegisterAccount: View? = null
	private var btnTextRegisterAccount: TextView? = null
	private var txtResendOptTimerCount: TextView? = null
	
	private var hasOptBeenSent = false
	
	private val totalSeconds = 3 * 60
	private var lastShownSecond = -1
	private var otpStartTimeMillis = 0L
	
	init {
		dialogBuilder.setView(R.layout.dialog_user_phone_registration_1)
		dialogBuilder.setCancelable(true)
		
		dialogBuilder.view.apply {
			editTextPhoneNumber = findViewById(R.id.edit_field_phone_number)
			editTextPassword = findViewById(R.id.edit_field_password)
			containerEditTextPhoneNumber = findViewById(R.id.container_edit_field_phone_number)
			containerEditTextPassword = findViewById(R.id.container_edit_field_password)
			btnRegisterAccount = findViewById(R.id.btn_register_account)
			btnTextRegisterAccount = findViewById(R.id.txt_register_account)
			txtResendOptTimerCount = findViewById(R.id.txt_resend_opt_timer_info)
			
			containerEditTextPhoneNumber?.setOnClickListener { focusKeyboardOnPhoneNumberField() }
			containerEditTextPassword?.setOnClickListener { focusKeyboardOnPasswordField() }
			btnTextRegisterAccount?.text = getText(R.string.title_send_verification_code)
			
			containerEditTextPassword?.visibility = View.GONE
			txtResendOptTimerCount?.visibility = View.GONE
			
			btnRegisterAccount?.setOnClickListener {
				val rawNumber = editTextPhoneNumber?.text.toString()
				val phone = normalizeIndianNumber(rawNumber)
				
				if (!hasOptBeenSent) sendUserOTP(phone)
				else verifyUserOTP(phone, editTextPassword?.text.toString())
			}
		}
	}
	
	fun show() {
		if (!dialogBuilder.isShowing) {
			dialogBuilder.show()
			AIOApp.aioTimer.register(this)
			
			CommonTimeUtils.delay(500, object : OnTaskFinishListener {
				override fun afterDelay() {
					focusKeyboardOnPhoneNumberField()
				}
			})
		}
	}
	
	fun close() {
		if (dialogBuilder.isShowing) {
			dialogBuilder.close()
			AIOApp.aioTimer.unregister(this)
		}
	}
	
	private fun focusKeyboardOnPhoneNumberField() {
		editTextPhoneNumber?.selectAll()
		ViewUtility.showOnScreenKeyboard(safeBaseActivity, editTextPhoneNumber)
	}
	
	private fun focusKeyboardOnPasswordField() {
		editTextPassword?.selectAll()
		ViewUtility.showOnScreenKeyboard(safeBaseActivity, editTextPassword)
	}
	
	private fun resetOtpTimer() {
		otpStartTimeMillis = System.currentTimeMillis()
		lastShownSecond = -1
	}
	
	override fun onAIOTimerTick(loopCount: Double) {
		if (otpStartTimeMillis == 0L) return
		if (txtResendOptTimerCount?.visibility != View.VISIBLE) return
		
		val elapsedSeconds =
			((System.currentTimeMillis() - otpStartTimeMillis) / 1000).toInt()
		
		val remainingSeconds = totalSeconds - elapsedSeconds
		if (remainingSeconds < 0 || remainingSeconds == lastShownSecond) return
		
		lastShownSecond = remainingSeconds
		
		val minutes = remainingSeconds / 60
		val seconds = remainingSeconds % 60
		
		safeBaseActivity
			?.getAttachedCoroutineScope()
			?.launch(Dispatchers.Main) {
				
				txtResendOptTimerCount?.text =
					"${getText(R.string.title_you_can_resend_otp_after)} $minutes:$seconds Mins"
				
				if (remainingSeconds == 0) {
					containerEditTextPassword?.visibility = View.GONE
					txtResendOptTimerCount?.visibility = View.GONE
					hasOptBeenSent = false
					btnTextRegisterAccount?.text =
						getText(R.string.title_resend_verification_code)
				}
			}
	}
	
	private fun sendUserOTP(userPhoneNumber: String) {
		if (userPhoneNumber.length != 10) {
			safeBaseActivity?.doSomeVibration()
			showToast(safeBaseActivity, R.string.title_enter_valid_phone_number)
			return
		}
		
		safeBaseActivity
			?.getAttachedCoroutineScope()
			?.launch(Dispatchers.IO) {
				withContext(Dispatchers.Main) {
					resetOtpTimer()
					
					containerEditTextPassword?.visibility = View.VISIBLE
					txtResendOptTimerCount?.visibility = View.VISIBLE
					btnTextRegisterAccount?.text =
						getText(R.string.title_verify_and_login)
					
					hasOptBeenSent = true
					focusKeyboardOnPasswordField()
				}
				supabaseClient.auth.signInWith(OTP) {
					phone = "91$userPhoneNumber"
				}
			}
	}
	
	private fun verifyUserOTP(userPhoneNumber: String, otp: String) {
		if (otp.length < 6) {
			safeBaseActivity?.doSomeVibration()
			showToast(safeBaseActivity, R.string.title_enter_valid_otp_number)
			return
		}
		
		safeBaseActivity
			?.getAttachedCoroutineScope()
			?.launch(Dispatchers.IO) {
				logger.d("Verifying OTP")
				supabaseClient.auth.verifyPhoneOtp(
					type = OtpType.Phone.SMS,
					phone = "91$userPhoneNumber",
					token = otp
				)
				
				withContext(Dispatchers.Main) {
					safeBaseActivity?.doSomeVibration()
					showToast(safeBaseActivity, R.string.title_login_successful)
					close()
				}
			}
	}
}
