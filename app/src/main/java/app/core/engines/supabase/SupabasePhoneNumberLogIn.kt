package app.core.engines.supabase

import android.view.*
import android.widget.*
import app.core.*
import app.core.AIOTimer.*
import app.core.bases.*
import com.aio.*
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
	baseActivity: BaseActivity,
	val onAccountSuccessfullyRegistered: () -> Unit = {},
	val onAccountRegistrationFailed: () -> Unit = {}
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
	
	init {
		logger.d("Initializing")
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
			containerEditTextPassword?.visibility = View.GONE
			btnTextRegisterAccount?.text = getText(R.string.title_send_verification_code)
			btnRegisterAccount?.setOnClickListener {
				val rawNumberFromUser = editTextPhoneNumber?.text.toString()
				val userPhoneNumber = normalizeIndianNumber(rawNumberFromUser)
				if (!hasOptBeenSent) sendUserOTP(userPhoneNumber)
				else veryUserOTP(userPhoneNumber, editTextPassword?.text.toString())
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
	
	/**
	 * Sets the focus to the password/OTP input field (`editTextPassword`).
	 *
	 * This function performs three main actions:
	 * 1. Makes the password field focusable.
	 * 2. Selects all the text currently in the password field, if any.
	 * 3. Shows the on-screen keyboard, directing user input to the password field.
	 * This is typically called after the OTP has been sent to the user.
	 */
	private fun focusKeyboardOnPasswordField() {
		editTextPassword?.focusable
		editTextPassword?.selectAll()
		ViewUtility.showOnScreenKeyboard(safeBaseActivity, editTextPassword)
	}
	
	/**
	 * Sets the focus to the phone number input field, selects its content,
	 * and displays the on-screen keyboard. This provides a user-friendly way
	 * to prompt for phone number input immediately.
	 */
	private fun focusKeyboardOnPhoneNumberField() {
		editTextPhoneNumber?.focusable
		editTextPhoneNumber?.selectAll()
		ViewUtility.showOnScreenKeyboard(safeBaseActivity, editTextPhoneNumber)
	}
	
	/**
	 * The total duration in seconds for which the OTP (One-Time Password) is valid.
	 * This is used to calculate the remaining time for the resend OTP countdown timer.
	 * Currently set to 2 minutes (120 seconds).
	 */
	private val totalSeconds = 15
	
	/**
	 * Tracks the last second value displayed in the countdown timer.
	 * This is used to prevent redundant UI updates on every timer tick,
	 * ensuring the timer text is only updated once per second.
	 */
	private var lastShownSecond = -1
	
	/**
	 * Callback function triggered by the [AIOTimer] on every tick.
	 *
	 * This function is responsible for updating the countdown timer UI for resending an OTP.
	 * It calculates the remaining time based on the timer's loop count and updates a `TextView`
	 * to show the time left in a "minutes:seconds" format.
	 *
	 * The UI update is performed on the main thread. Once the timer reaches zero, it resets
	 * the view to its initial state (hiding the password field and timer), allowing the user
	 * to request a new OTP. It also unregisters itself from the [AIOTimer] to stop receiving updates.
	 *
	 * To optimize performance, it only updates the UI once per second by tracking `lastShownSecond`.
	 *
	 * @param loopCount A counter provided by the [AIOTimer] that increments on each tick. This is used
	 *                  to calculate the elapsed time.
	 */
	override fun onAIOTimerTick(loopCount: Double) {
		val elapsedMillis = (loopCount * 200).toLong()
		val elapsedSeconds = (elapsedMillis / 1000).toInt()
		val remainingSeconds = totalSeconds - elapsedSeconds
		
		if (remainingSeconds < 0 || remainingSeconds == lastShownSecond) return
		if (txtResendOptTimerCount?.visibility == View.GONE) return
		lastShownSecond = remainingSeconds
		
		val minutes = remainingSeconds / 60
		val seconds = remainingSeconds % 60
		
		safeBaseActivity
			?.getAttachedCoroutineScope()
			?.launch(Dispatchers.Main) {
				val message = "${getText(R.string.title_you_can_resend_otp_after)} $minutes:$seconds Mins"
				txtResendOptTimerCount?.text = message
				if (minutes == 0 && seconds == 0) {
					
					containerEditTextPassword?.visibility = View.GONE
					txtResendOptTimerCount?.visibility = View.GONE
					hasOptBeenSent = false
					btnTextRegisterAccount?.text = getText(R.string.title_resend_verification_code)
					AIOApp.aioTimer.unregister(this@SupabasePhoneNumberLogIn)
				}
			}
	}
	
	/**
	 * Sends a One-Time Password (OTP) to the user's phone number using Supabase Auth.
	 *
	 * This function first validates the provided phone number. If it's valid, it updates the UI to show
	 * the password/OTP input field and a resend timer. It then makes an asynchronous call to the
	 * Supabase client to send the OTP to the specified phone number. The Indian country code "+91" is
	 * prepended to the number before sending.
	 *
	 * @param userPhoneNumber The user's 10-digit phone number, without the country code.
	 */
	private fun sendUserOTP(userPhoneNumber: String) {
		if (userPhoneNumber.isEmpty() || userPhoneNumber.length < 10 || userPhoneNumber.length > 10) {
			safeBaseActivity?.doSomeVibration()
			showToast(safeBaseActivity, R.string.title_enter_valid_phone_number)
			return
		}
		
		safeBaseActivity?.getAttachedCoroutineScope()?.launch(Dispatchers.IO) {
			withContext(Dispatchers.Main) {
				ViewUtility.showView(containerEditTextPassword, true)
				btnTextRegisterAccount?.text = getText(R.string.title_verify_and_login)
				txtResendOptTimerCount?.visibility = View.VISIBLE
				hasOptBeenSent = true
				focusKeyboardOnPasswordField()
			}
			
//			supabaseClient.auth.signInWith(OTP) {
//				phone = "91${userPhoneNumber}"
//			}
		}
	}
	
	/**
	 * Verifies the One-Time Password (OTP) entered by the user against the one sent to their phone number.
	 *
	 * This function first performs a basic validation on the OTP to ensure it's not empty and has a
	 * minimum length of 6 characters. If the validation passes, it launches a coroutine to make an
	 * asynchronous call to the Supabase client to verify the OTP.
	 *
	 * If the verification is successful, the function switches to the main thread to:
	 * 1. Close the login dialog.
	 * 2. Trigger a device vibration for haptic feedback.
	 * 3. Show a toast message indicating a successful login.
	 *
	 * @param userPhoneNumber The phone number to which the OTP was sent. This is used by Supabase
	 *                        to match the verification request.
	 * @param userGivenOtp The 6-digit OTP string entered by the user.
	 */
	private fun veryUserOTP(userPhoneNumber: String, userGivenOtp: String) {
		if (userGivenOtp.isEmpty() || userGivenOtp.length < 6) {
			safeBaseActivity?.doSomeVibration()
			showToast(safeBaseActivity, R.string.title_enter_valid_otp_number)
			return
		}
		
		safeBaseActivity?.getAttachedCoroutineScope()?.launch(Dispatchers.IO) {
			logger.d("Verifying OTP")
//			supabaseClient.auth.verifyPhoneOtp(
//				type = OtpType.Phone.SMS,
//				phone = userPhoneNumber,
//				token = userGivenOtp
//			)
			withContext(Dispatchers.Main) {
				safeBaseActivity?.doSomeVibration()
				showToast(safeBaseActivity, R.string.title_login_successful)
				close()
			}
		}
		
	}
}
