package lib.device

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.core.content.ContextCompat
import app.core.bases.BaseActivity
import com.aio.R
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.ToastView.Companion.showToast

/**
 * Utility object for managing security-sensitive file operations and user authentication.
 *
 * This object provides a standardized interface for verifying user identity using the system's
 * biometric authentication framework (fingerprint, face recognition) or device credentials
 * (pattern, PIN, password). It ensures that protected actions, such as accessing sensitive
 * files or data, are performed only after verifying the user's identity.
 *
 * Usage of this utility is recommended before performing any high-security operation
 * within the application to prevent unauthorized access.
 */
object SecureFileUtil {

	/**
	 * Cryptographic key alias registered in Android's secure hardware-backed KeyStore.
	 * This unique identifier references the symmetric encryption key used for file protection.
	 * The key material remains secured within the Trusted Execution Environment (TEE)
	 * or Secure Element, preventing extraction even from rooted devices.
	 */
	private const val KEY_ALIAS = "PrivateFileKey"

	/**
	 * Official Android KeyStore cryptographic service provider identifier.
	 * Specifies the hardware-backed keystore implementation that provides tamper-resistant
	 * key storage with key usage restrictions. This provider ensures keys never leave
	 * secure hardware boundaries and supports biometric authentication binding.
	 */
	private const val ANDROID_KEYSTORE = "AndroidKeyStore"

	/**
	 * Prompts the user to authenticate using biometric credentials (fingerprint, face, etc.)
	 * or the device lock screen credentials (PIN, pattern, password).
	 *
	 * This method handles the setup of the [BiometricPrompt] and dynamically determines the
	 * allowed authenticators based on the Android version. On Android 11 (API 30) and above,
	 * it allows both biometric strong authentication and device credentials as a fallback.
	 * On older versions, it restricts the request to strong biometric authentication only.
	 *
	 * The authentication process is asynchronous. Results are delivered via the [onResult]
	 * callback. If authentication fails or an error occurs (e.g., no hardware available),
	 * a toast message is displayed to the user.
	 *
	 * @param activity The currently active [BaseActivity] context used to launch the prompt.
	 *                 If null, the method returns immediately without performing any action.
	 * @param onResult A callback lambda that receives a Boolean value.
	 *                 - `true`: Authentication was successful.
	 *                 - `false`: Authentication failed, was cancelled, or an error occurred.
	 */
	fun authenticate(activity: BaseActivity?, onResult: (Boolean) -> Unit) {
		if (activity == null) return

		// Determine which authenticators are allowed based on the Android API level.
		// Android R+ allows falling back to device credentials (PIN/Pattern/Password) if biometrics fail.
		val allowedAuthenticators =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				BIOMETRIC_STRONG or DEVICE_CREDENTIAL
			} else {
				BIOMETRIC_STRONG
			}

		// Configure the visual and behavioral aspects of the authentication prompt.
		val promptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle(getText(R.string.title_unlock_requires))
			.setAllowedAuthenticators(allowedAuthenticators)
			.build()

		// Executor that runs callbacks on the main thread.
		val executor = ContextCompat.getMainExecutor(activity)

		// Initialize the BiometricPrompt with the context, executor, and callback handlers.
		val biometricPrompt = BiometricPrompt(
			activity,
			executor,
			object : AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: AuthenticationResult) {
					// Notify the caller that the user has successfully authenticated.
					onResult(true)
				}

				override fun onAuthenticationError(
					errorCode: Int,
					errString: CharSequence
				) {
					// Notify the user of the failure via a toast message.
					showToast(activity, R.string.title_authentication_failed)
					// Notify the caller that authentication failed.
					onResult(false)
				}
			}
		)

		// Launch the system authentication UI.
		biometricPrompt.authenticate(promptInfo)
	}
}