package app.ui.main.fragments.settings.dialogs

import android.view.*
import android.view.View.*
import android.view.ViewGroup.*
import android.view.ViewGroup.LayoutParams.*
import android.widget.*
import app.core.AIOApp.Companion.aioSettings
import app.core.bases.*
import app.core.engines.youtube.ContentRegionsList.regionsList
import com.aio.*
import kotlinx.coroutines.*
import lib.process.*
import lib.ui.builders.*
import java.lang.ref.*

/**
 * A specialized dialog component for managing and selecting geographic content regions.
 *
 * This class orchestrates the lifecycle, UI rendering, and data persistence for region-specific
 * application settings. It is designed to handle large lists of regional data without
 * compromising UI fluidness through a batched inflation strategy.
 *
 * ### Key Features:
 * * **Performance Optimized**: Implements a chunked view inflation system that avoids
 * dropping frames when populating many region options.
 * * **Thread-Safe Logic**: Seamlessly transitions between `withMainContext` for UI
 * manipulation and `withIOContext` for database persistence.
 * * **Lifecycle Awareness**: Uses [WeakReference] to the host Activity to prevent memory
 * leaks during asynchronous operations or configuration changes.
 * * **State Management**: Automatically synchronizes the UI state with `aioSettings`
 * and notifies observers via [onApplyListener].
 */
class ContentRegionSelector(baseActivity: BaseActivity) {

	/**
	 * Utility for diagnostic logging and error tracking, specifically tagged with
	 * the current class name for streamlined debugging in Logcat.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] wrapping the [baseActivity] context.
	 * * This is used to prevent memory leaks by ensuring the Activity can be garbage
	 * collected if the dialog lifecycle outlives the Activity's destruction (e.g.,
	 * during rapid configuration changes).
	 */
	private val weakReferenceOfActivity = WeakReference(baseActivity)

	/**
	 * Provides a null-safe reference to the Activity.
	 * * It returns the [BaseActivity] instance if it is still valid, or null if the
	 * reference has been cleared. This is critical for safely launching coroutines
	 * and inflating UI components.
	 */
	private val safeBaseActivityRef get() = weakReferenceOfActivity.get()

	/**
	 * The builder instance for the region selection dialog, initialized lazily.
	 * * The [DialogBuilder] is only created upon the first call to [show] or init,
	 * at which point it inflates the [R.layout.dialog_content_regions_1] layout.
	 * This saves resources by delaying UI inflation until the dialog is actually needed.
	 */
	private val contentRegionSelectionDialog by lazy {
		DialogBuilder(safeBaseActivityRef).apply {
			setView(R.layout.dialog_content_regions_1)
		}
	}

	/**
	 * A callback function triggered after a new region has been successfully
	 * selected and persisted in the database.
	 * * This allows the calling component to refresh its own UI or trigger a
	 * data reload without the dialog having direct knowledge of the implementation.
	 */
	var onApplyListener: () -> Unit = {}

	/**
	 * Initializes the region selection component by configuring the dialog's behavior and UI.
	 * * The initialization is offloaded to the activityCoroutineScope to ensure that
	 * initial data population (like batch-loading the region list) and listener
	 * attachments do not block the instance creation on the Main thread.
	 */
	init {
		safeBaseActivityRef?.activityCoroutineScope?.launch {
			contentRegionSelectionDialog.setCancelable(false)
			contentRegionSelectionDialog.view.apply {
				setAvailableRegions(this)
				setButtonOnClickListeners(this)
			}
		}
	}

	/**
	 * Provides access to the underlying [DialogBuilder] instance.
	 * * @return The [contentRegionSelectionDialog] currently managed by this class,
	 * allowing for external configuration of its window properties or theme.
	 */
	fun getDialogBuilder(): DialogBuilder {
		return contentRegionSelectionDialog
	}

	/**
	 * Safely dismisses the region selection dialog and cleans up references.
	 * * This is a [withMainContext] operation that ensures the dialog is removed
	 * from the window manager correctly. It also clears the [weakReferenceOfActivity]
	 * to assist the Garbage Collector in reclaiming memory, preventing potential
	 * leaks after the dialog is finished.
	 */
	suspend fun close() {
		withMainContext {
			if (contentRegionSelectionDialog.isShowing) {
				contentRegionSelectionDialog.close()
				weakReferenceOfActivity.clear()
			}
		}
	}

	/**
	 * Displays the content region selection dialog to the user.
	 * * Wraps the display logic in [withMainContext] to ensure thread safety.
	 * It includes a check to prevent redundant show calls if the dialog is
	 * already visible on the screen.
	 */
	suspend fun show() {
		withMainContext {
			if (!contentRegionSelectionDialog.isShowing) {
				contentRegionSelectionDialog.show()
			}
		}
	}

	/**
	 * Checks the current visibility status of the dialog.
	 * * @return `true` if the dialog is currently attached to a window and visible
	 * to the user; `false` otherwise. This check is performed on the Main thread
	 * to ensure an accurate state reading.
	 */
	suspend fun isShowing(): Boolean {
		return withMainContext { contentRegionSelectionDialog.isShowing }
	}

	/**
	 * Populates the dialog with a list of available content regions using a non-blocking batch process.
	 *
	 * This method implements a "chunked inflation" strategy to maintain high UI performance:
	 * 1. **Batching**: Instead of inflating the entire region list at once (which could cause
	 * frame drops or "jank"), it processes items in small batches of 10 per frame.
	 * 2. **Recursive UI Posting**: Uses [View.post] to queue subsequent batches on the UI
	 * message loop, allowing the main thread to remain responsive to user input between frames.
	 * 3. **View Configuration**: Inflates custom [RadioButton] items, assigns them unique
	 * IDs based on their index, and applies consistent dimensions and padding from resources.
	 * 4. **State Restoration**: Once all items are added, it identifies the currently saved
	 * AIOSettings.selectedContentRegion and programmatically checks the corresponding button.
	 *
	 * @param dialogLayoutView The root view of the dialog where the region list will be injected.
	 */
	private suspend fun setAvailableRegions(dialogLayoutView: View) {
		withMainContext {
			safeBaseActivityRef?.let { safeActivityRef ->
				val radioGroup = getRegionsRadioGroupView(dialogLayoutView)
				removeAllRadioSelectionViews(dialogLayoutView)

				val batchSize = 10   // How many RadioButtons to add per frame
				var currentIndex = 0

				fun addBatch() {
					val end = (currentIndex + batchSize).coerceAtMost(regionsList.size)
					logger.d("Adding regions batch: $currentIndex to $end")

					for (i in currentIndex until end) {
						val (_, name) = regionsList[i]
						inflate(safeActivityRef, R.layout.dialog_content_regions_item_1, null).apply {
							(this as RadioButton).apply {
								id = i
								text = name

								val radioButtonHeight = resources.getDimensionPixelSize(R.dimen._40)
								layoutParams = LayoutParams(MATCH_PARENT, radioButtonHeight)

								val horizontalPadding = resources.getDimensionPixelSize(R.dimen._5)
								val verticalPadding = resources.getDimensionPixelSize(R.dimen._5)
								setPadding(
									horizontalPadding,
									verticalPadding,
									horizontalPadding,
									verticalPadding
								)
							}
							radioGroup.addView(this)
						}
					}

					currentIndex = end
					if (currentIndex < regionsList.size) {
						// Post next batch to the UI thread queue to prevent UI freezing
						radioGroup.post { addBatch() }
					} else {
						// After all items are added → highlight current saved region
						val currentRegionCode = aioSettings.selectedContentRegion
						val selectedIndex = regionsList.indexOfFirst { it.first == currentRegionCode }
						logger.d(
							"Finished populating regions. " +
								"Current region: $currentRegionCode (index: $selectedIndex)"
						)
						if (selectedIndex >= 0) {
							radioGroup.findViewById<RadioButton>(selectedIndex)?.isChecked = true
						}
					}
				}

				// Start initial batch processing
				addBatch()
			}
		}
	}

	/**
	 * Clears all dynamically generated radio buttons from the selection container.
	 *
	 * This method ensures the UI is reset before a fresh list of regions is populated,
	 * preventing duplicate entries. It is executed within [withMainContext] to
	 * safely modify the [RadioGroup] view hierarchy.
	 *
	 * @param dialogLayoutView The parent layout containing the region options container.
	 */
	private suspend fun removeAllRadioSelectionViews(dialogLayoutView: View) {
		withMainContext {
			getRegionsRadioGroupView(dialogLayoutView)
				.removeAllViews()
		}
	}

	/**
	 * Retrieves the specific [RadioGroup] used for hosting region selection options.
	 *
	 * This helper method provides thread-safe access to the container view using
	 * [withMainContext]. It encapsulates the findViewById logic to simplify
	 * UI manipulation across the class.
	 *
	 * @param view The parent view to search within.
	 * @return The [RadioGroup] associated with the content region options.
	 */
	private suspend fun getRegionsRadioGroupView(view: View): RadioGroup {
		return withMainContext {
			view.findViewById(R.id.content_region_options_container)
		}
	}

	/**
	 * Configures the interaction logic for the dialog's action buttons.
	 *
	 * This method binds the positive action container to the region application logic.
	 * It ensures that when the user clicks "Apply":
	 * 1. A bridge is created from the synchronous click listener to the asynchronous
	 * [applySelectedApplicationContentRegion] via the activityCoroutineScope.
	 * 2. The operation is context-aware, checking [safeBaseActivityRef] to ensure
	 * the activity is still active before launching the coroutine.
	 *
	 * @param dialogLayoutView The root view of the dialog containing the button containers.
	 */
	private suspend fun setButtonOnClickListeners(dialogLayoutView: View) {
		withMainContext {
			dialogLayoutView.findViewById<View>(R.id.btn_dialog_positive_container).apply {
				setOnClickListener {
					safeBaseActivityRef?.activityCoroutineScope?.launch {
						applySelectedApplicationContentRegion(dialogLayoutView)
					}
				}
			}
		}
	}

	/**
	 * Captures and persists the user-selected content region from the dialog interface.
	 *
	 * This method performs the following sequence within an asynchronous [withIOContext]:
	 * 1. **Selection Retrieval**: Identifies the checked radio button within the [RadioGroup]
	 * provided by [dialogLayoutView].
	 * 2. **Validation**: Aborts the operation if no selection is detected, preventing
	 * null or empty data from corrupting settings.
	 * 3. **Data Mapping**: Extracts the corresponding region code and name from the
	 * indexed [regionsList].
	 * 4. **Persistence**: Updates the [aioSettings] preference and commits the change
	 * to the database to ensure the app's content region reflects the new choice.
	 * 5. **Lifecycle Management**: Triggers [close] to dismiss the UI and notifies
	 * any registered [onApplyListener] that the update is complete.
	 *
	 * @param dialogLayoutView The parent view containing the region selection radio buttons.
	 */
	private suspend fun applySelectedApplicationContentRegion(dialogLayoutView: View) {
		withIOContext {
			val contentRegionRadioGroup = getRegionsRadioGroupView(dialogLayoutView)
			val selectedRegionId = contentRegionRadioGroup.checkedRadioButtonId

			if (selectedRegionId == -1) {
				logger.d("No region selected. Skipping apply.")
				return@withIOContext // No selection
			}

			val (selectedRegionCode, selectedRegionName) = regionsList[selectedRegionId]
			logger.d("Applying selected region: $selectedRegionCode ($selectedRegionName)")

			// Save the new content region preference and persist it
			aioSettings.selectedContentRegion = selectedRegionCode
			aioSettings.updateInDB()

			close()             // Close the dialog
			onApplyListener()   // Notify listener
		}
	}
}
