package app.ui.main.fragments.downloads.poups

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import app.core.AIOApp
import app.core.bases.BaseActivity
import app.core.engines.settings.AIOSettings
import com.aio.R
import lib.process.LogHelperUtils
import lib.ui.builders.PopupBuilder
import java.lang.ref.WeakReference

/**
 * Sorting Downloads Popup
 *
 * A specialized popup dialog that provides a comprehensive sorting interface for download items.
 * This class manages a radio button-based selection menu allowing users to sort downloads by
 * multiple criteria including date, name, size, and file type categories.
 *
 * @param baseActivity The parent activity context (uses WeakReference for memory safety)
 * @param anchorView The view to anchor the popup to (typically a sort button)
 * @param onSortOptionSelected Callback invoked with the selected sort ID (from AIOSettings constants)
 * @see AIOSettings For sort option constant definitions
 * @see PopupBuilder For the underlying popup rendering implementation
 */
class SortingDownloads(
	val baseActivity: BaseActivity?,
	val anchorView: View?,
	val onSortOptionSelected: (Int) -> Unit
) {
	/**
	 * Logger instance for tracking popup lifecycle events and user interactions.
	 * Provides detailed debug information for troubleshooting and analytics.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference to the base activity preventing memory leaks.
	 *
	 * This is a critical design choice that allows the activity to be garbage collected
	 * even if this popup instance is still referenced elsewhere. Without this, the popup
	 * could cause memory leaks by holding a strong reference to a destroyed activity.
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)

	/**
	 * Safe accessor for the base activity with null safety.
	 *
	 * Returns null if the activity has been garbage collected, preventing
	 * null pointer exceptions in asynchronous operations. Always check
	 * the result before using it.
	 */
	private val safeBaseActivity get() = weakReferenceOfBaseActivity.get()

	/**
	 * PopupBuilder instance responsible for rendering and managing the popup UI.
	 *
	 * Nullable to gracefully handle cases where initialization fails due to
	 * missing dependencies (null activity or anchor view). The popup will
	 * simply not show in such cases rather than crashing.
	 */
	private var popupBuilder: PopupBuilder? = null

	/**
	 * Initializes the popup builder and sets up the UI components.
	 *
	 * This initialization process follows a fail-soft approach:
	 * 1. Validates that both baseActivity and anchorView are non-null
	 * 2. If validation passes, creates and configures the PopupBuilder
	 * 3. Sets popup position to LEFT alignment for optimal UX
	 * 4. Sets up radio button click listeners for all sort options
	 *
	 * If either dependency is null, initialization is silently skipped
	 * and subsequent show() calls will have no effect.
	 */
	init {
		initializeViews()
	}

	/**
	 * Displays the sorting options popup anchored to the provided view.
	 *
	 * This method is safe to call even if:
	 * - The popup wasn't properly initialized (does nothing)
	 * - The popup is already showing (prevents duplicates)
	 * - The activity is no longer valid (checks internally)
	 *
	 * The popup appears with left alignment relative to the anchor view
	 * for consistent positioning across different screen sizes.
	 */
	fun show() {
		// Access the popup view if the popup builder is available
		popupBuilder?.getPopupView()?.apply {

			// Fetch the currently applied sorting option from settings
			val currentSort = AIOApp.aioSettings.downloadsDefaultSortOrder

			// RadioGroup that contains all sorting options
			val radioGroup = findViewById<RadioGroup>(R.id.sort_options_group)

			// Map saved sort value to the corresponding RadioButton ID
			val checkedId = when (currentSort) {
				AIOSettings.SORT_DATE_NEWEST_FIRST -> R.id.button_sort_by_date_desc
				AIOSettings.SORT_DATE_OLDEST_FIRST -> R.id.button_sort_by_date_asc
				AIOSettings.SORT_NAME_A_TO_Z -> R.id.button_sort_by_name_asc
				AIOSettings.SORT_NAME_Z_TO_A -> R.id.button_sort_by_name_desc
				AIOSettings.SORT_SIZE_SMALLEST_FIRST -> R.id.button_sort_by_size_asc
				AIOSettings.SORT_SIZE_LARGEST_FIRST -> R.id.button_sort_by_size_desc
				AIOSettings.SORT_TYPE_VIDEOS_FIRST -> R.id.button_sort_by_videos
				AIOSettings.SORT_TYPE_MUSIC_FIRST -> R.id.button_sort_by_music
				else -> View.NO_ID // Fallback for unknown or unsupported values
			}

			// Pre-select the previously applied sorting option
			if (checkedId != View.NO_ID) {
				radioGroup.check(checkedId)
			}
		}

		// Finally display the popup
		popupBuilder?.show()
	}

	/**
	 * Closes the sorting options popup if it's currently visible.
	 *
	 * This method handles proper cleanup of the popup window and is
	 * safe to call even if:
	 * - The popup isn't showing (does nothing)
	 * - The popup wasn't properly initialized (does nothing)
	 * - The activity is no longer valid (checks internally)
	 */
	fun close() {
		popupBuilder?.close()
	}

	/**
	 * Initializes the popup views and configures all UI components.
	 *
	 * This method performs a complete setup of the sorting popup:
	 * 1. **Dependency Validation**: Checks for null activity and anchor view
	 * 2. **Popup Builder Creation**: Instantiates PopupBuilder with the sorting layout
	 * 3. **Position Configuration**: Sets popup to LEFT alignment for optimal placement
	 * 4. **Listener Setup**: Configures click listeners for all radio buttons
	 *
	 * The method uses comprehensive null safety to prevent crashes and
	 * logs detailed information for debugging initialization issues.
	 */
	private fun initializeViews() {
		// Validate required dependencies
		if (baseActivity == null) {
			logger.d("Cannot initialize popup - baseActivity is null")
			return
		}
		if (anchorView == null) {
			logger.d("Cannot initialize popup - anchorView is null")
			return
		}

		// Create and configure PopupBuilder
		popupBuilder = PopupBuilder(
			activityInf = safeBaseActivity,
			popupLayoutId = R.layout.frag_down_1_main_sorting_1,
			popupAnchorView = anchorView
		).apply {
			// Set popup position to LEFT alignment for better UX
			// This ensures the popup doesn't extend off-screen on smaller devices
			setPosition(PopupBuilder.PopupPosition.LEFT)
		}

		// Set up radio button click listeners
		setupRadioButtonListeners()
	}

	/**
	 * Sets up click listeners for all radio buttons in the sorting popup.
	 *
	 * This method establishes the core interaction logic:
	 * 1. Retrieves the RadioGroup container from the inflated layout
	 * 2. Creates a mapping between button IDs and sort option constants
	 * 3. Attaches click listeners to each radio button
	 * 4. Handles selection by invoking the callback and closing the popup
	 *
	 * ## Button-to-Option Mapping:
	 * - button_sort_by_date_desc → AIOSettings.SORT_DATE_NEWEST_FIRST
	 * - button_sort_by_date_asc → AIOSettings.SORT_DATE_OLDEST_FIRST
	 * - button_sort_by_name_asc → AIOSettings.SORT_NAME_A_TO_Z
	 * - button_sort_by_name_desc → AIOSettings.SORT_NAME_Z_TO_A
	 * - button_sort_by_size_asc → AIOSettings.SORT_SIZE_SMALLEST_FIRST
	 * - button_sort_by_size_desc → AIOSettings.SORT_SIZE_LARGEST_FIRST
	 * - button_sort_by_videos → AIOSettings.SORT_TYPE_VIDEOS_FIRST
	 * - button_sort_by_music → AIOSettings.SORT_TYPE_MUSIC_FIRST
	 *
	 * The method is null-safe and will log warnings if any expected UI
	 * components cannot be found, preventing crashes from layout changes.
	 */
	private fun setupRadioButtonListeners() {
		val popupView = popupBuilder?.getPopupView() ?: run {
			logger.d("Cannot setup listeners - popupView is null")
			return
		}

		// Map button IDs to their corresponding sort option integer constants
		// This mapping centralizes the relationship between UI elements and business logic
		val buttonIdToSortOption = mapOf(
			R.id.button_sort_by_date_desc to AIOSettings.SORT_DATE_NEWEST_FIRST,
			R.id.button_sort_by_date_asc to AIOSettings.SORT_DATE_OLDEST_FIRST,
			R.id.button_sort_by_name_asc to AIOSettings.SORT_NAME_A_TO_Z,
			R.id.button_sort_by_name_desc to AIOSettings.SORT_NAME_Z_TO_A,
			R.id.button_sort_by_size_asc to AIOSettings.SORT_SIZE_SMALLEST_FIRST,
			R.id.button_sort_by_size_desc to AIOSettings.SORT_SIZE_LARGEST_FIRST,
			R.id.button_sort_by_videos to AIOSettings.SORT_TYPE_VIDEOS_FIRST,
			R.id.button_sort_by_music to AIOSettings.SORT_TYPE_MUSIC_FIRST
		)

		// Set click listeners for each radio button
		buttonIdToSortOption.forEach { (buttonId, sortOption) ->
			val radioButton = popupView.findViewById<RadioButton>(buttonId)
			radioButton?.setOnClickListener {
				logger.d("Sort option selected: $sortOption")
				onSortOptionSelected(sortOption)
				close() // Automatically close popup after selection for better UX
			} ?: logger.d("RadioButton with ID $buttonId not found in layout")
		}

		logger.d("Radio button listeners setup complete with ${buttonIdToSortOption.size} options")
	}
}