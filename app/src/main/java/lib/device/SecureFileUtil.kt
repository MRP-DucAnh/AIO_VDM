package lib.device

import android.os.*
import androidx.biometric.*
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt.*
import androidx.core.content.*
import app.core.bases.*
import com.aio.R
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.ToastView.Companion.showToast

object SecureFileUtil {

	private const val KEY_ALIAS = "PrivateFileKey"
	private const val ANDROID_KEYSTORE = "AndroidKeyStore"

	suspend fun authenticate(activity: BaseActivity?, onResult: (Boolean) -> Unit) {
		withMainContext {
			if (activity == null) return@withMainContext
			val allowedAuthenticators =
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					BIOMETRIC_STRONG or DEVICE_CREDENTIAL
				} else {
					BIOMETRIC_STRONG
				}

			val promptInfo = PromptInfo.Builder()
				.setTitle(getText(R.string.title_unlock_requires))
				.setAllowedAuthenticators(allowedAuthenticators)
				.build()

			val executor = ContextCompat.getMainExecutor(activity)
			val biometricPrompt = BiometricPrompt(
				activity, executor, object : AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: AuthenticationResult) {
					onResult(true)
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					showToast(activity, R.string.title_authentication_failed)
					onResult(false)
				}
			})

			biometricPrompt.authenticate(promptInfo)
		}
	}
}