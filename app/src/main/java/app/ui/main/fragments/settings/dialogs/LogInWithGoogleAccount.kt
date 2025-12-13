package app.ui.main.fragments.settings.dialogs

import app.core.bases.*
import lib.process.*
import java.lang.ref.*

/**
 * Manages the process of logging in with a Google account.
 *
 * This class encapsulates the logic required to initiate and handle the Google login flow.
 * It interacts with a [BaseActivity] to display UI elements, such as upcoming feature dialogs,
 * and to provide haptic feedback during the process.
 *
 * To prevent memory leaks, it holds a [WeakReference] to the provided activity,
 * ensuring that the activity can be garbage collected even if this class instance persists.
 *
 * @param baseActivity The activity context required to show UI dialogs and perform actions.
 *                     It is stored as a [WeakReference] to avoid memory leaks.
 */
class LogInWithGoogleAccount(
	baseActivity: BaseActivity,
	onSuccess: () -> Unit = {}, onFailed: () -> Unit = {}
) {
	
	/**
	 * Logger for this class, used for logging various events and debugging information.
	 * It's initialized using `LogHelperUtils` with the current class context.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	/**
	 * A [WeakReference] to the [BaseActivity] instance.
	 *
	 * This is used to avoid memory leaks. The activity can be garbage collected
	 * if it's destroyed, and this class won't hold a strong reference preventing it.
	 * Access to the activity should be done through the [safeBaseActivity] property,
	 * which safely retrieves the reference.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)
	
	/**
	 * Safely retrieves the [BaseActivity] instance from the weak reference.
	 *
	 * Using a `WeakReference` helps prevent memory leaks by allowing the `BaseActivity`
	 * to be garbage collected if it's no longer in use elsewhere. This property provides
	 * a convenient and null-safe way to access the activity when needed.
	 *
	 * @return The [BaseActivity] instance if it's still available, or `null` otherwise.
	 */
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()
	
	fun tryLogin() {
		safeBaseActivity?.let { activity ->
			activity.doSomeVibration()
			activity.showUpcomingFeatures()
		}
	}
}