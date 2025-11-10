package app.core.bases

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import java.lang.ref.WeakReference

/**
 * A comprehensive base fragment class that provides common infrastructure and lifecycle management
 * for all fragments within the application.
 *
 * This abstract class serves as the foundation for fragment implementations, offering:
 * - Memory-safe activity and view references using weak references
 * - Standardized lifecycle management with template methods
 * - Consistent layout inflation and initialization patterns
 * - Fragment visibility state tracking
 * - Prevention of common fragment-related memory leaks
 *
 * Subclasses must implement the abstract methods to provide fragment-specific behavior
 * while inheriting robust lifecycle management and memory safety features.
 *
 * @see Fragment For the base Android Fragment class being extended
 */
abstract class BaseFragment : Fragment() {

	/**
	 * A memory-safe weak reference to the parent BaseActivity, retrieved lazily when first accessed.
	 *
	 * Using weak references prevents memory leaks that can occur when fragments retain strong
	 * references to activities after they are destroyed. The lazy initialization ensures the
	 * reference is only created when actually needed, optimizing resource usage.
	 *
	 * Usage pattern:
	 * ```kotlin
	 * safeBaseActivityRef?.let { activity ->
	 *     // Perform operations with the safe activity reference
	 * }
	 * ```
	 *
	 * @return The parent BaseActivity instance if available and not destroyed, or null otherwise.
	 */
	open val safeBaseActivityRef: BaseActivity? by lazy {
		WeakReference(activity).get() as BaseActivity
	}

	/**
	 * Backing field for the fragment's layout view with controlled access through weak reference.
	 *
	 * This private field holds the strong reference to the fragment's view during its lifecycle,
	 * while the public property provides access through a weak reference to prevent memory leaks
	 * when the fragment is destroyed but references to the view might still exist.
	 */
	private var _fragmentLayout: View? = null

	/**
	 * A memory-safe weak reference to the fragment's layout view for safe access.
	 *
	 * This property provides access to the fragment's root view while preventing memory leaks
	 * that can occur when views are referenced after fragment destruction. The weak reference
	 * ensures that if the fragment is destroyed, the view can be garbage collected properly.
	 *
	 * Always check for null before using this property, as it may return null if the fragment's
	 * view has been destroyed or if the weak reference was cleared.
	 */
	open val safeFragmentLayoutRef: View?
		get() = WeakReference(_fragmentLayout).get()

	/**
	 * Flag indicating whether the fragment is currently visible and actively running.
	 *
	 * This state tracking is essential for managing fragment-specific operations that should
	 * only occur when the fragment is visible to users. The flag is automatically managed
	 * through the fragment lifecycle:
	 * - Set to true in onResume() when fragment becomes visible
	 * - Set to false in onPause() when fragment loses visibility
	 * - Reset to false in onDestroyView() during cleanup
	 *
	 * Use this flag to conditionally execute code that should only run when the fragment
	 * is in the foreground, such as starting animations, updating live data observers,
	 * or performing network requests that directly affect the UI.
	 */
	open var isFragmentRunning: Boolean = false

	/**
	 * Abstract method to define the layout resource ID for the fragment's user interface.
	 *
	 * Subclasses must implement this method to specify which layout resource should be
	 * inflated for the fragment. This follows the composition pattern where subclasses
	 * define their specific UI while the base class handles common inflation logic.
	 *
	 * @return The layout resource ID (e.g., R.layout.fragment_example) that defines
	 *         the fragment's visual interface and view hierarchy.
	 */
	protected abstract fun getLayoutResId(): Int

	/**
	 * Abstract method called after the fragment's layout has been inflated and created.
	 *
	 * This template method provides subclasses with an opportunity to perform initialization
	 * that requires access to the fully inflated view hierarchy. It is called from onViewCreated(),
	 * ensuring all views are available and properly initialized.
	 *
	 * Typical use cases include:
	 * - Finding and configuring view references with findViewById()
	 * - Setting up RecyclerView adapters and layout managers
	 * - Configuring click listeners and other user interactions
	 * - Initializing data binding or view model connections
	 * - Restoring UI state from saved instance state
	 *
	 * @param layoutView The root view of the fragment's inflated layout, providing access
	 *        to the complete view hierarchy for initialization.
	 * @param state The saved instance state Bundle containing previous UI state, or null
	 *        if no saved state exists (fresh creation).
	 */
	protected abstract fun onAfterLayoutLoad(layoutView: View, state: Bundle?)

	/**
	 * Abstract method to handle fragment-specific logic when the fragment resumes and becomes visible.
	 *
	 * This method is called when the fragment enters the resumed state and becomes interactive
	 * to users. Subclasses should implement this to perform operations that should occur when
	 * the fragment returns to the foreground, such as:
	 * - Starting or resuming animations
	 * - Refreshing data from ViewModels or repositories
	 * - Registering broadcast receivers or event listeners
	 * - Updating UI based on latest data state
	 * - Resuming video playback or other media
	 *
	 * This method is called after the base class sets isFragmentRunning to true, ensuring
	 * the fragment is properly marked as active before subclass-specific resume logic executes.
	 */
	protected abstract fun onResumeFragment()

	/**
	 * Abstract method to handle fragment-specific logic when the fragment pauses and loses visibility.
	 *
	 * This method is called when the fragment is about to lose visibility and enter the paused state.
	 * Subclasses should implement this to perform cleanup operations that should occur when the
	 * fragment moves to the background, such as:
	 * - Pausing or stopping animations to save resources
	 * - Unregistering broadcast receivers or event listeners
	 * - Saving transient UI state or user progress
	 * - Pausing video playback or other media
	 * - Persisting data that might be lost during background state
	 *
	 * This method is called before the base class sets isFragmentRunning to false, allowing
	 * subclasses to perform cleanup while the fragment is still considered active.
	 */
	protected abstract fun onPauseFragment()

	/**
	 * Called by the system to inflate and return the fragment's layout hierarchy.
	 *
	 * This method handles the standard layout inflation process using the resource ID
	 * provided by getLayoutResId(). It automatically stores the inflated view in the
	 * _fragmentLayout backing field for memory-safe access through safeFragmentLayoutRef.
	 *
	 * The method uses the provided LayoutInflater to create the view hierarchy and
	 * attaches it to the container if specified. The attachToRoot parameter is set to
	 * false to allow the system to manage view attachment timing.
	 *
	 * @param inflater The LayoutInflater service used to inflate the layout XML into
	 *        actual View objects and handle view hierarchy creation.
	 * @param container The parent view that the fragment's UI should be attached to,
	 *        or null if the fragment is not being attached to a parent view.
	 * @param savedInstanceState The saved instance state from a previous instance of
	 *        this fragment, or null if this is a fresh creation.
	 * @return The root View of the inflated layout hierarchy, which is also stored
	 *         in _fragmentLayout for safe access throughout the fragment lifecycle.
	 */
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? = inflater
		.inflate(getLayoutResId(), container, false).also { _fragmentLayout = it }

	/**
	 * Called immediately after onCreateView() when the fragment's view hierarchy has been created.
	 *
	 * This method provides the final initialization point after the view hierarchy is fully
	 * created but before the fragment becomes visible. It invokes the onAfterLayoutLoad()
	 * template method to allow subclasses to perform view-specific initialization with
	 * guaranteed access to the complete view hierarchy.
	 *
	 * @param view The root View of the fragment's newly created layout hierarchy,
	 *        providing access to all child views for initialization.
	 * @param bundle The same saved instance state Bundle passed to onCreateView(),
	 *        containing any previously saved UI state for restoration.
	 */
	override fun onViewCreated(view: View, bundle: Bundle?) {
		super.onViewCreated(view, bundle)
		onAfterLayoutLoad(view, bundle)
	}

	/**
	 * Called when the fragment becomes visible and starts interacting with the user.
	 *
	 * This method marks the fragment as running and invokes the subclass-specific resume
	 * logic. It is called when the fragment enters the foreground and should prepare for
	 * user interaction. The method follows the lifecycle sequence: super.onResume() first
	 * to ensure proper system initialization, then state update, then subclass logic.
	 */
	override fun onResume() {
		super.onResume()
		isFragmentRunning = true
		onResumeFragment()
	}

	/**
	 * Called when the fragment is no longer in the foreground and loses user interaction.
	 *
	 * This method marks the fragment as not running and invokes the subclass-specific pause
	 * logic. It is called when the fragment is being placed in the background or is being
	 * destroyed. The method follows the lifecycle sequence: subclass cleanup first, then
	 * state update, then super.onPause() for proper system cleanup.
	 */
	override fun onPause() {
		super.onPause()
		isFragmentRunning = false
		onPauseFragment()
	}

	/**
	 * Called when the fragment's view hierarchy is being destroyed and cleaned up.
	 *
	 * This method performs essential cleanup to prevent memory leaks by clearing the
	 * fragment's layout reference and resetting the running state. It ensures that
	 * any strong references to views are released, allowing proper garbage collection
	 * of the view hierarchy when the fragment is destroyed or recreated.
	 */
	override fun onDestroyView() {
		super.onDestroyView()
		isFragmentRunning = false
		_fragmentLayout = null
	}
}