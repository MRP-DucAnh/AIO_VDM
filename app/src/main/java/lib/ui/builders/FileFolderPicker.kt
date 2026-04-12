package lib.ui.builders

import android.app.*
import android.os.*
import android.text.format.Formatter
import android.view.*
import android.widget.*
import androidx.lifecycle.*
import app.core.bases.*
import com.aio.*
import kotlinx.coroutines.*
import lib.process.*
import lib.process.AsyncJobUtils.*
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.normalizeTallSymbols
import java.io.*
import java.lang.ref.*
import java.text.*
import java.util.*

/**
 * A high-performance File and Folder picker that bypasses the Storage Access Framework (SAF)
 * in favor of direct filesystem access via `java.io.File`.
 *
 * This builder provides a responsive browsing experience with the following key features:
 * - **Optimized Performance**: Uses direct I/O and `FileFilter` to minimize memory overhead during directory scans.
 * - **Thread Safety**: Offloads all filesystem operations to background threads to ensure zero UI jank.
 * - **Flexible Modes**: Configurable for folder-only, file-only, or mixed-mode selection.
 * - **Multi-Selection**: Supports picking multiple items when [isMultiSelection] is enabled.
 * - **Breadcrumb Navigation**: Visual path tracking with an interactive breadcrumb trail for quick navigation.
 *
 * @property baseActivity The context used to instantiate the dialog and manage UI-thread synchronization.
 * @property initialPath The starting directory path. Defaults to external storage if null, empty, or inaccessible.
 * @property isCancellable Whether the dialog can be dismissed via back-press or clicking outside the window.
 * @property isFolderPickerOnly If true, filters the view to prioritize and allow the selection of directories.
 * @property isFilePickerOnly If true, limits selection capabilities strictly to files.
 * @property isMultiSelection Enables a checkbox-based selection mode for picking multiple items.
 * @property titleText The header text displayed at the top of the picker dialog.
 * @property positiveButtonText The label for the confirmation action button.
 * @property onUserAbortedProcess Callback invoked if the user closes the picker without confirming a selection.
 * @property onFileSelection Callback invoked with a list of absolute paths once the user confirms their selection.
 */
class FileFolderPicker(
	private val baseActivity: BaseActivity?,
	private val initialPath: String?,
	private val isCancellable: Boolean = true,
	private val isFolderPickerOnly: Boolean = true,
	private val isFilePickerOnly: Boolean = false,
	private val isMultiSelection: Boolean = false,
	private val titleText: String = getText(R.string.title_file_folder_picker),
	private val positiveButtonText: String = getText(R.string.title_select),
	private val onUserAbortedProcess: () -> Unit = {},
	private val onFileSelection: (List<String>) -> Unit = {}
) {

	/**
	 * Internal logger instance for tracking navigation flow and debugging filesystem I/O errors.
	 * Initialized with the class name to provide contextual logging within the utility.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A weak reference to the [BaseActivity] to prevent memory leaks while
	 * allowing UI updates and access to context-dependent resources.
	 */
	private val weakReferenceOfActivity = WeakReference(baseActivity)

	/**
	 * Stores the absolute paths of the files or folders currently selected by the user.
	 * This set is used to maintain selection state across directory navigation and
	 * to provide the final list of results when the selection is confirmed.
	 */
	private val selectedPaths = mutableSetOf<String>()

	/**
	 * The backing dialog instance used to render the picker interface.
	 * Managed internally to handle UI lifecycle, updates, and cleanup.
	 */
	private var dialogBuilder: DialogBuilder? = null

	/**
	 * The bridge between the [browserState] items and the [ListView].
	 * This adapter is lazily initialized upon the first directory load and
	 * updated via [BrowserAdapter.swapData] during navigation.
	 */
	private var browserAdapter: BrowserAdapter? = null

	/**
	 * Holds the current state of the file browser, including the directory being viewed,
	 * the navigation breadcrumbs (history), and the list of items to be displayed.
	 */
	private var browserState = BrowserState()

	/**
	 * Represents the internal state of the file browser at a specific point in time.
	 *
	 * This state object is immutable and used to track the user's current position within
	 * the filesystem, the breadcrumb history for navigation, and the prepared list of
	 * files and folders to be displayed in the UI.
	 *
	 * @property currentDir The directory currently being viewed by the user.
	 * @property history A persistent stack of directories visited, used to facilitate robust back-navigation.
	 * @property items The list of files and folders contained within the [currentDir] after filtering and sorting.
	 */
	private data class BrowserState(
		val currentDir: File? = null,
		val history: List<File> = emptyList(),
		val items: List<BrowserItem> = emptyList()
	)

	/**
	 * Represents a single entry in the file browser (either a file or a directory).
	 *
	 * @property file The underlying [File] object.
	 * @property name The display name of the file or folder.
	 * @property path The absolute filesystem path to the item.
	 * @property isDirectory True if the item is a folder, false if it is a file.
	 * @property info Formatted metadata string (e.g., "Size • Date" for files, "Date" for folders).
	 * @property isSelected Current selection state in the UI, used for multi-selection mode.
	 */
	private data class BrowserItem(
		val file: File,
		val name: String,
		val path: String,
		val isDirectory: Boolean,
		val info: String,
		var isSelected: Boolean = false
	)

	init {
		initializeUI()
	}

	/**
	 * Displays the picker dialog and starts the directory scanning process.
	 *
	 * This method triggers the UI to be shown to the user and initializes the
	 * filesystem crawler to load the starting directory.
	 */
	suspend fun show() {
		withMainContext {
			dialogBuilder?.show()
			initiateBrowsing()
		}
	}

	/**
	 * Closes the picker dialog and performs a full cleanup of the internal state.
	 *
	 * This method ensures that the dialog is dismissed, references to UI components
	 * (like the adapter and dialog builder) are nulled out to prevent memory leaks,
	 * and the navigation history and selection sets are cleared.
	 */
	suspend fun close() {
		withMainContext {
			try {
				dialogBuilder?.close()
				browserState = BrowserState()
				selectedPaths.clear()
				dialogBuilder = null
				browserAdapter = null
			} catch (error: Exception) {
				logger.e("Cleanup failed: ${error.message}")
			}
		}
	}

	/**
	 * Configures the dialog's visual components and user interface interactions.
	 *
	 * This method initializes the [DialogBuilder], sets the layout, applies the title,
	 * and attaches click listeners for navigation and selection actions. It ensures
	 * that the UI reflects the current configuration (e.g., cancellable state, button labels)
	 * before the dialog is displayed.
	 */
	private fun initializeUI() {
		val activity = weakReferenceOfActivity.get() ?: return
		dialogBuilder = DialogBuilder(activity).apply {
			setView(R.layout.dialog_file_folder_picker_1)
			setCancelable(isCancellable)

			dialog.setOnDismissListener { onUserAbortedProcess.invoke() }
			dialog.setOnCancelListener { onUserAbortedProcess.invoke() }

			view.findViewById<TextView>(R.id.txt_dialog_title)?.text = titleText
			updateActionButtons()

			setOnClickForNegativeButton {
				onUserAbortedProcess()
				close()
			}

			setOnClickForPositiveButton {
				activity.lifecycleScope.launch {
					handleConfirmSelection()
				}
			}

			view.findViewById<TextView>(R.id.btn_go_to_previous_dir)?.setOnClickListener {
				navigateUp()
			}
		}
	}

	/**
	 * Starts the initial filesystem exploration process on a background thread.
	 *
	 * This method determines the starting directory based on user input or system fallbacks,
	 * initializes the navigation history with the root directory, and triggers the
	 * first directory loading operation.
	 */
	private fun initiateBrowsing() {
		executeInBackground {
			val root = resolveStartingDirectory()
			browserState = browserState.copy(
				currentDir = root,
				history = listOf(root)
			)
			loadDirectory(root)
		}
	}

	/**
	 * Determines the initial directory to display when the picker is first opened.
	 *
	 * The resolution logic follows a three-step fallback priority:
	 * 1. **Initial Path**: Attempts to use the [initialPath] provided during construction.
	 *    The path must exist, be a directory, and be readable.
	 * 2. **External Storage**: If the initial path is invalid or null, it falls back to the
	 *    primary external storage directory (typically `/storage/emulated/0`).
	 * 3. **Root**: If external storage is inaccessible, it defaults to the filesystem root (`/`).
	 *
	 * @return A validated [File] object representing the starting point for navigation.
	 */
	private fun resolveStartingDirectory(): File {
		// 1. Try initial path
		if (!initialPath.isNullOrBlank()) {
			val file = File(initialPath)
			if (file.exists() && file.isDirectory && file.canRead()) {
				return file
			}
		}

		// 2. Fallback to External Storage ( /storage/emulated/0 )
		val external = Environment.getExternalStorageDirectory()
		if (external.exists() && external.canRead()) {
			return external
		}

		// 3. Fallback to Root ( / )
		return File("/")
	}

	/**
	 * Reads the contents of the specified [directory], applies filters, and prepares items for display.
	 *
	 * This method executes the following steps:
	 * 1. Filters filesystem entries based on the current picker mode ([isFolderPickerOnly] or [isFilePickerOnly]).
	 * 2. Formats metadata for each item (file size for files, last modified date for all).
	 * 3. Sorts the resulting list to prioritize directories first, followed by an alphabetical sort of names.
	 * 4. Switches to the UI thread to trigger [renderState] and update the display.
	 *
	 * @param directory The filesystem directory to scan and display.
	 */
	private fun loadDirectory(directory: File) {
		val activity = weakReferenceOfActivity.get() ?: return

		try {
			// Efficient filtering at filesystem level
			val filter = FileFilter { file ->
				// Always show directories to allow navigation
				if (file.isDirectory) return@FileFilter true

				// Apply user Filters
				when {
					isFolderPickerOnly -> false // Hide files in folder-only mode
					isFilePickerOnly -> true    // Show files in file-only mode
					else -> true               // Show everything in mixed mode
				}
			}

			val files = directory.listFiles(filter) ?: emptyArray()
			val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

			val browserItems = files.map { file ->
				val isDir = file.isDirectory
				val path = file.absolutePath

				// Calculate Metadata
				val lastModified = dateFormat.format(Date(file.lastModified()))
				val infoText = if (isDir) {
					// For folders, counting children is expensive. 
					// We'll show "Directory" or if fast, count. 
					// For now, let's just show Date to keep it instant.
					lastModified
				} else {
					// For files, show size
					val size = Formatter.formatFileSize(activity, file.length())
					"$size • $lastModified"
				}

				BrowserItem(
					file = file,
					name = file.name,
					path = path,
					isDirectory = isDir,
					info = infoText,
					isSelected = selectedPaths.contains(path)
				)
			}.sortedWith(
				compareByDescending<BrowserItem> { it.isDirectory }
					.thenBy { it.name.lowercase() }
			)

			activity.runOnUiThread {
				renderState(directory, browserItems)
			}

		} catch (error: Exception) {
			logger.e("Failed to load directory ${directory.absolutePath}: ${error.message}")
		}
	}

	/**
	 * Updates the user interface to reflect the current directory contents and navigation state.
	 *
	 * This function performs the following UI updates:
	 * 1. Updates or initializes the [ListView] adapter with the provided [items].
	 * 2. Refreshes the breadcrumb navigation bar to show the current path of the [directory].
	 * 3. Toggles the visibility of the "Back" navigation button based on the navigation history.
	 * 4. Refreshes the state of action buttons (e.g., selection counts).
	 *
	 * @param directory The current [File] directory being displayed.
	 * @param items The list of [BrowserItem] objects (files and folders) within the current directory.
	 */
	private fun renderState(directory: File, items: List<BrowserItem>) {
		val activityRef = weakReferenceOfActivity.get() ?: return
		val view = dialogBuilder?.view ?: return

		browserState = browserState.copy(items = items)

		// 1. Adapter
		val listView = view.findViewById<ListView>(R.id.list_of_files_folders)
		if (browserAdapter == null) {
			browserAdapter = BrowserAdapter(activityRef, items) {
				activityRef.lifecycleScope.launch {
					handleItemInteraction(it)
				}
			}
			listView.adapter = browserAdapter
		} else {
			browserAdapter?.swapData(items)
		}

		updateBreadcrumbs(view, activityRef, directory)
		val backBtn = view.findViewById<TextView>(R.id.btn_go_to_previous_dir)
		backBtn?.visibility = if (browserState.history.size > 1) View.VISIBLE else View.GONE
		updateActionButtons()
	}

	/**
	 * Orchestrates the logic when a user clicks on an item in the file browser.
	 *
	 * If the item is a directory, it updates the [browserState] history and triggers a
	 * background load of the new directory contents.
	 *
	 * If the item is a file:
	 * - In multi-selection mode, it toggles the file's selection state and refreshes the UI.
	 * - In single-selection mode, it immediately finalizes the selection with that file.
	 *
	 * @param item The [BrowserItem] that the user interacted with.
	 */
	private suspend fun handleItemInteraction(item: BrowserItem) {
		if (item.isDirectory) {
			// Navigate Down
			val newHistory = browserState.history + item.file
			browserState = browserState.copy(
				currentDir = item.file,
				history = newHistory
			)
			executeInBackground {
				loadDirectory(item.file)
			}
		} else {
			// Select File
			if (isMultiSelection) {
				if (selectedPaths.contains(item.path)) {
					selectedPaths.remove(item.path)
				} else {
					selectedPaths.add(item.path)
				}
				// Optimistic UI update
				item.isSelected = !item.isSelected
				browserAdapter?.notifyDataSetChanged()
				updateActionButtons()
			} else {
				finalizeSelection(listOf(item.path))
			}
		}
	}

	/**
	 * Navigates to the parent directory by popping the last entry from the navigation history.
	 *
	 * This method updates the internal [browserState] with the new directory and triggers
	 * an asynchronous reload of the file list. Navigation is only performed if there is
	 * a parent directory available in the history stack.
	 */
	private fun navigateUp() {
		if (browserState.history.size > 1) {
			val newHistory = browserState.history.dropLast(1)
			val parentDir = newHistory.last()

			browserState = browserState.copy(
				currentDir = parentDir,
				history = newHistory
			)

			executeInBackground {
				loadDirectory(parentDir)
			}
		}
	}

	/**
	 * Processes the final selection logic based on the current picker mode and user interaction.
	 *
	 * The priority logic is as follows:
	 * 1. If the user has explicitly selected items (files or folders) via checkboxes/clicks,
	 *    those items are finalized.
	 * 2. If no items are selected and the picker is in "Folder Picker" mode, the current
	 *    directory being browsed is treated as the selection.
	 */
	private suspend fun handleConfirmSelection() {
		// Priority 1: User selection
		if (selectedPaths.isNotEmpty()) {
			finalizeSelection(selectedPaths.toList())
			return
		}

		// Priority 2: Current folder (Folder Picker Mode)
		if (isFolderPickerOnly && !isFilePickerOnly) {
			browserState.currentDir?.let { dir ->
				finalizeSelection(listOf(dir.absolutePath))
			}
		}
	}

	/**
	 * Concludes the selection process by passing the list of selected file system paths
	 * to the callback and dismissing the picker.
	 *
	 * @param paths A list of absolute paths representing the selected files or directories.
	 */
	private suspend fun finalizeSelection(paths: List<String>) {
		if (paths.isNotEmpty()) {
			onFileSelection(paths)
			close()
		}
	}

	/**
	 * Updates the visual breadcrumb trail to reflect the current directory hierarchy.
	 *
	 * This method parses the absolute path of the [currentDir], creates individual [TextView]
	 * elements for each path segment, and populates the breadcrumb container. It also
	 * automatically scrolls the breadcrumb view to the end (right) to ensure the
	 * current location is visible.
	 *
	 * @param root The root view of the dialog containing the breadcrumb UI components.
	 * @param activity The context used to resolve colors and instantiate views.
	 * @param currentDir The current directory to be displayed in the breadcrumb trail.
	 */
	private fun updateBreadcrumbs(root: View, activity: Activity, currentDir: File) {
		val container = root.findViewById<LinearLayout>(R.id.container_breadcrumb) ?: return
		container.removeAllViews()

		val path = currentDir.absolutePath
		val segments = path.split(File.separator).filter { it.isNotBlank() }

		val colorPrimary = activity.getColor(R.color.color_text_primary)
		val colorSecondary = activity.getColor(R.color.color_text_secondary)

		segments.forEachIndexed { index, segment ->
			val tv = TextView(activity).apply {
				text = segment
				setPadding(12, 8, 12, 8)
				setTextColor(colorPrimary)
				textSize = 12f
			}
			container.addView(tv)

			if (index < segments.size - 1) {
				container.addView(TextView(activity).apply {
					text = ">"
					setTextColor(colorSecondary)
					textSize = 10f
					setPadding(4, 0, 4, 0)
				})
			}
		}

		root.findViewById<HorizontalScrollView>(R.id.container_breadcrumb_scroll)
			?.let { scroll ->
				scroll.post { scroll.fullScroll(View.FOCUS_RIGHT) }
			}
	}

	/**
	 * Updates the visual state of the dialog's positive action button.
	 *
	 * This method refreshes the button text to include the current selection count
	 * when [isMultiSelection] is enabled and items are selected. If no items are
	 * selected or multi-selection is disabled, it displays the default [positiveButtonText].
	 */
	private fun updateActionButtons() {
		val btn = dialogBuilder?.view
			?.findViewById<TextView>(R.id.btn_dialog_positive) ?: return

		val count = selectedPaths.size
		btn.text = if (isMultiSelection && count > 0)
			"$positiveButtonText ($count)" else positiveButtonText
	}

	/**
	 * A private [BaseAdapter] implementation specifically designed for displaying a list of
	 * files and folders within a browser or picker interface.
	 *
	 * This adapter utilizes a [WeakReference] to the [BaseActivity] to ensure memory safety
	 * when launching coroutines for UI tasks like text normalization. It follows the
	 * ViewHolder pattern for optimal scrolling performance and provides a clean interface
	 * for handling item clicks and data swaps.
	 *
	 * @property activity The host [BaseActivity] context, used for inflation and coroutine scope access.
	 * @property items The initial data set of [BrowserItem] objects to be rendered.
	 * @property onClick A high-order function invoked when an item in the list is clicked.
	 */
	private class BrowserAdapter(
		activity: BaseActivity,
		private var items: List<BrowserItem>,
		private val onClick: (BrowserItem) -> Unit
	) : BaseAdapter() {

		/**
		 * A private container used to cache View references for the [BrowserAdapter].
		 *
		 * By storing references to the internal UI components of a list row, the [ViewHolder]
		 * pattern avoids repeated and expensive [View.findViewById] calls during scrolling,
		 * ensuring a smooth frame rate.
		 *
		 * @param view The root [View] of the layout, from which child views are extracted.
		 */
		private class ViewHolder(view: View) {
			/**
			 * [TextView] responsible for displaying the filename or folder name.
			 * Used to provide a human-readable label for the item.
			 */
			val nameText: TextView = view.findViewById(R.id.txt_file_folder_name)

			/**
			 * [TextView] responsible for displaying metadata such as file size or date modified.
			 * Used to provide additional information about the item.
			 */
			val metaText: TextView = view.findViewById(R.id.txt_file_folder_metadata)

			/**
			 * [ImageView] representing the file type (e.g., folder icon or file icon).
			 * Used to differentiate between files and folders.
			 */
			val icon: ImageView = view.findViewById(R.id.img_file_type_indicator)

			/**
			 * [ImageView] acting as a selection indicator (checkbox) for the item.
			 * Only visible when [isMultiSelection] is enabled.
			 */
			val checkbox: ImageView = view.findViewById(R.id.img_checkbox_selection)
		}

		/**
		 * [WeakReference] to the parent activity to prevent memory leaks.
		 * Since adapters can outlive their activities (e.g., during long-running background tasks),
		 * this ensures the Activity can be reclaimed by the Garbage Collector.
		 */
		private val weakReferenceOfActivity = WeakReference(activity)

		/**
		 * Safely retrieves the activity instance from the [WeakReference].
		 * Returns null if the activity has been destroyed.
		 */
		private val safeActivityRef get() = weakReferenceOfActivity.get()

		/**
		 * The [LayoutInflater] used to instantiate layout XML files into their
		 * corresponding [View] objects. It is initialized using the context retrieved
		 * from the [safeActivityRef].
		 */
		private val inflater = LayoutInflater.from(safeActivityRef)

		/**
		 * Updates the adapter's data set with a new list of items and refreshes the UI.
		 *
		 * @param newItems The updated list of [BrowserItem] objects to be displayed.
		 */
		fun swapData(newItems: List<BrowserItem>) {
			this.items = newItems
			notifyDataSetChanged()
		}

		/**
		 * Returns the total number of items currently held in the adapter's data set.
		 *
		 * @return The size of the [items] list.
		 */
		override fun getCount() = items.size

		/**
		 * Retrieves the data item associated with a specified position in the data set.
		 *
		 * @param position The index of the item to retrieve.
		 * @return The [BrowserItem] at the specified [position].
		 */
		override fun getItem(position: Int) = items[position]

		/**
		 * Returns a row ID for the specified position.
		 * In this implementation, the position itself is used as the unique ID.
		 *
		 * @param position The index of the item.
		 * @return The position as a [Long].
		 */
		override fun getItemId(position: Int) = position.toLong()

		/**
		 * Provides a view for a specific data item in the adapter's data set.
		 *
		 * This implementation follows the ViewHolder pattern to optimize performance by reducing
		 * [View.findViewById] calls. It handles item recycling, sets standard file/folder
		 * metadata synchronously, and initiates an asynchronous text normalization process
		 * for non-Latin symbols.
		 *
		 * @param position The position of the item within the adapter's data set.
		 * @param convertView The old view to reuse, if possible.
		 * @param parent The parent that this view will eventually be attached to.
		 * @return A [View] corresponding to the data at the specified position.
		 */
		override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
			val view: View
			val holder: ViewHolder

			// Handle View recycling and ViewHolder assignment
			if (convertView == null) {
				view = inflater.inflate(R.layout.dialog_file_folder_picker_item_1, parent, false)
				holder = ViewHolder(view)
				view.tag = holder
			} else {
				view = convertView
				holder = view.tag as ViewHolder
			}

			val item = getItem(position)
			val currentName = item.name

			// Synchronous UI updates: Clear old data and set primary text/icons
			holder.nameText.text = currentName
			holder.metaText.text = item.info
			holder.icon.setImageResource(
				if (item.isDirectory) R.drawable.img_folder_indicator
				else R.drawable.img_file_indicator
			)

			holder.checkbox.visibility = if (item.isSelected) View.VISIBLE else View.GONE

			/*
			 * Launch character normalization in the background.
			 * We pass currentName to the extension function to verify that the TextView
			 * hasn't been recycled for another item before the UI is updated.
			 */
			safeActivityRef?.lifecycleScope?.launch {
				holder.nameText.normalizeTallSymbols(currentName)
			}

			view.setOnClickListener { onClick(item) }

			return view
		}
	}
}