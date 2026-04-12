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
 * A sophisticated, customizable file and folder picker component for Android.
 *
 * This class manages a directory-browsing dialog that allows users to navigate the
 * filesystem and select one or more files or directories. It supports specific
 * picking modes (files-only, folders-only, or mixed), multi-selection, and
 * provides a breadcrumb-based navigation system.
 *
 * It is designed to be memory-safe by using weak references to the host activity
 * and leverages a state-driven rendering approach to keep the UI synchronized
 * with the filesystem.
 *
 * @property baseActivity The parent activity context; used for UI inflation and scope management.
 * @property initialPath The starting directory path. If null or invalid, defaults to a safe storage root.
 * @property isCancellable Whether the user can dismiss the picker without making a selection.
 * @property isFolderPickerOnly When true, restricts the final selection to directories only.
 * @property isFilePickerOnly When true, restricts the final selection to files only.
 * @property isMultiSelection Enables the selection of multiple items via checkboxes.
 * @property titleText The header text displayed at the top of the picker dialog.
 * @property positiveButtonText The label for the confirmation/action button.
 * @property onUserAbortedProcess Callback triggered if the picker is canceled or dismissed.
 * @property onFileSelection Callback that returns the list of absolute paths chosen by the user.
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
	 * Represents the immutable UI state of the file browser at any given moment.
	 *
	 * This data class encapsulates the current navigational context, the history of
	 * visited directories (the breadcrumb trail), and the list of items currently
	 * visible to the user. Using an immutable state object facilitates "unidirectional
	 * data flow," making it easier to track changes and debug navigation logic.
	 *
	 * @property currentDir The [File] object representing the directory currently being viewed.
	 * Null if the browser hasn't initialized a starting path.
	 * @property history An ordered list of [File] objects representing the folder hierarchy
	 * traversed by the user, used for navigating "back" or via breadcrumbs.
	 * @property items The processed list of [BrowserItem] models currently displayed in the
	 * browser's list view.
	 */
	private data class BrowserState(
		val currentDir: File? = null,
		val history: List<File> = emptyList(),
		val items: List<BrowserItem> = emptyList()
	)

	/**
	 * A UI model representing a single entry (file or folder) in the browser list.
	 *
	 * This data class decouples the raw filesystem [File] object from the specific
	 * strings and states required for display in the [BrowserAdapter]. It tracks
	 * essential properties like the display name, calculated metadata [info],
	 * and the current selection status.
	 *
	 * @property file The underlying [File] object from the filesystem.
	 * @property name The display name of the item (usually the filename).
	 * @property path The absolute filesystem path to the item.
	 * @property isDirectory True if the item represents a folder, false if it is a file.
	 * @property info A pre-formatted string containing metadata (e.g., "2 MB • Oct 12, 2026").
	 * @property isSelected A mutable state flag indicating if the user has selected this item.
	 */
	private data class BrowserItem(
		val file: File,
		val name: String,
		val path: String,
		val isDirectory: Boolean,
		val info: String,
		var isSelected: Boolean = false
	)

	/**
	 * The primary initialization block for the class.
	 * * This block is executed immediately when the object is instantiated. It triggers
	 * the [initializeUI] function to set up layout configurations, click listeners,
	 * and initial state before any data loading begins.
	 */
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
	 * (like the adapter and dialog builder) are pulled out to prevent memory leaks,
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

			view.findViewById<TextView>(R.id.btn_go_to_previous_dir)
				?.setOnClickListener { navigateUp() }
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
	 * Reads and processes the contents of a filesystem directory to be displayed in the UI.
	 *
	 * This function performs the following operations:
	 * 1. **Filtering:** Uses a [FileFilter] to include or exclude files based on the picker
	 * configuration ([isFolderPickerOnly], [isFilePickerOnly]).
	 * 2. **Metadata Generation:** Formats file sizes and modification dates into human-readable
	 * strings.
	 * 3. **Mapping:** Transforms raw [File] objects into [BrowserItem] UI models, checking
	 * against [selectedPaths] to maintain selection state.
	 * 4. **Sorting:** Organizes the final list so that directories appear at the top, followed
	 * by files in alphabetical order.
	 * 5. **State Rendering:** Dispatches the final list to [renderState] on the UI thread.
	 *
	 * @param directory The [File] object representing the directory to scan.
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
	 * Updates the UI components to reflect the current directory and its contents.
	 *
	 * This function synchronizes the [browserState] with the provided [items], initializes
	 * or updates the [BrowserAdapter], and refreshes navigational elements like
	 * breadcrumbs and the back button. It ensures that the [ListView] is properly
	 * populated and that the action buttons reflect the current selection state.
	 *
	 * @param directory The current [File] directory being rendered.
	 * @param items The list of [BrowserItem] objects found within the directory.
	 */
	private fun renderState(directory: File, items: List<BrowserItem>) {
		val activityRef = weakReferenceOfActivity.get() ?: return
		val view = dialogBuilder?.view ?: return

		browserState = browserState.copy(items = items)

		// 1. Adapter initialization or data swap
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

		// Update navigation breadcrumbs
		updateBreadcrumbs(view, activityRef, directory)

		// Manage visibility of the 'back' button based on navigation history depth
		val backBtn = view.findViewById<TextView>(R.id.btn_go_to_previous_dir)
		backBtn?.visibility = if (browserState.history.size > 1) View.VISIBLE else View.GONE

		updateActionButtons()
	}

	/**
	 * Handles user interaction with a specific item in the browser list.
	 *
	 * If the item is a directory, the browser navigates into it and updates the navigation
	 * history. If the item is a file, the behavior depends on the selection mode:
	 * - In multi-selection mode, it toggles the selection state of the file and updates the UI.
	 * - In single-selection mode, it immediately finalizes the selection with that file.
	 *
	 * @param item The [BrowserItem] that was clicked or interacted with.
	 */
	private suspend fun handleItemInteraction(item: BrowserItem) {
		if (item.isDirectory) {
			val newHistory = browserState.history + item.file
			browserState = browserState.copy(
				currentDir = item.file,
				history = newHistory
			)
			loadDirectory(item.file)
		} else {
			// Select File logic
			if (isMultiSelection) {
				if (selectedPaths.contains(item.path)) {
					selectedPaths.remove(item.path)
				} else {
					selectedPaths.add(item.path)
				}
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
	 * Processes the user's selection confirmation based on the current picker mode.
	 * * This function evaluates the state of [selectedPaths]. If specific paths are selected,
	 * they are prioritized. In "Folder Picker Only" mode, if no specific items are selected,
	 * it defaults to selecting the currently viewed directory.
	 */
	private suspend fun handleConfirmSelection() {
		if (selectedPaths.isNotEmpty()) {
			finalizeSelection(selectedPaths.toList())
			return
		}

		if (isFolderPickerOnly && !isFilePickerOnly) {
			browserState.currentDir?.let { dir ->
				finalizeSelection(listOf(dir.absolutePath))
			}
		}
	}

	/**
	 * Completes the selection process by emitting the chosen paths and closing the browser.
	 *
	 * @param paths A list of absolute file or directory paths to be returned to the caller.
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