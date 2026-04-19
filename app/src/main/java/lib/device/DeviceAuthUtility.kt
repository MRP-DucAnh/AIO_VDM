package lib.device

import android.os.*
import androidx.biometric.*
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.core.content.*
import app.core.bases.*
import com.aio.R
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.ToastView.Companion.showToast

object DeviceAuthUtility {

	@JvmStatic
	suspend fun authenticate(activity: BaseActivity, onResult: (Boolean) -> Unit
	) {
		withMainContext {
			val biometricManager = BiometricManager.from(activity)
			val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				BIOMETRIC_STRONG or DEVICE_CREDENTIAL
			} else BIOMETRIC_STRONG

			when (biometricManager.canAuthenticate(authenticators)) {
				BiometricManager.BIOMETRIC_SUCCESS -> {
					executePrompt(activity, authenticators, onResult)
				}
				else -> {
					showToast(activity, R.string.title_authentication_failed)
					onResult(false)
				}
			}
		}
	}

	private fun executePrompt(
		activity: BaseActivity, authenticators: Int, onResult: (Boolean) -> Unit
	) {
		val executor = ContextCompat.getMainExecutor(activity)

		val promptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle(getText(R.string.title_unlock_requires))
			.setAllowedAuthenticators(authenticators)
			.apply {
				if ((authenticators and DEVICE_CREDENTIAL) == 0) {
					setNegativeButtonText(getText(R.string.title_cancel))
				}
			}
			.build()

		val biometricPrompt = BiometricPrompt(
			activity, executor,
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(
					result: BiometricPrompt.AuthenticationResult) {
					super.onAuthenticationSucceeded(result)
					onResult(true)
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					super.onAuthenticationError(errorCode, errString)
					if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
						errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
						showToast(activity, R.string.title_authentication_failed)
					}
					onResult(false)
				}
			})

		biometricPrompt.authenticate(promptInfo)
	}
}