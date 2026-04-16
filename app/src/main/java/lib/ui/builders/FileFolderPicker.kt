package lib.ui.builders

import android.app.*
import android.os.*
import android.text.format.Formatter.*
import android.view.*
import android.widget.*
import androidx.lifecycle.*
import app.core.bases.*
import com.aio.*
import kotlinx.coroutines.*
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.normalizeTallSymbols
import java.io.*
import java.lang.ref.*
import java.text.*
import java.util.*

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

	private val logger = LogHelperUtils.from(javaClass)
	private val weakReferenceOfActivity = WeakReference(baseActivity)
	private val safeActivityRef get() = weakReferenceOfActivity.get()
	private val selectedPaths = mutableSetOf<String>()
	private var dialogBuilder: DialogBuilder? = null
	private var browserAdapter: BrowserAdapter? = null
	private var browserState = BrowserState()
	private var navigationJob: Job? = null

	private data class BrowserState(
		val currentDir: File? = null,
		val history: List<File> = emptyList(),
		val items: List<BrowserItem> = emptyList()
	)

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

	suspend fun show() {
		withMainContext {
			dialogBuilder?.show()
			initiateBrowsing()
		}
	}

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

	private fun initializeUI() {
		safeActivityRef?.let { activityRef ->
			dialogBuilder = DialogBuilder(activityRef).apply {
				setView(R.layout.dialog_file_folder_picker_1)
				setCancelable(isCancellable)

				dialog.setOnDismissListener { onUserAbortedProcess.invoke() }
				dialog.setOnCancelListener { onUserAbortedProcess.invoke() }

				val dialogTitle = view.findViewById<TextView>(R.id.txt_dialog_title)
				dialogTitle?.text = titleText
				updateActionButtons()

				setOnClickForNegativeButton {
					onUserAbortedProcess()
					close()
				}

				setOnClickForPositiveButton {
					activityRef.lifecycleScope.launch {
						handleConfirmSelection()
					}
				}

				val previousButton = view.findViewById<TextView>(R.id.btn_go_to_previous_dir)
				previousButton?.setOnClickListener {
					activityRef.activityCoroutineScope.launch { navigateUp() }
				}
			}
		}
	}

	private suspend fun initiateBrowsing() {
		withIOContext {
			val root = resolveStartingDirectory()
			browserState = browserState.copy(
				currentDir = root,
				history = listOf(root)
			)
			loadDirectory(root)
		}
	}

	private suspend fun resolveStartingDirectory(): File {
		return withIOContext {
			if (!initialPath.isNullOrBlank()) {
				val file = File(initialPath)
				if (file.exists() && file.isDirectory && file.canRead()) {
					return@withIOContext file
				}
			}

			val external = Environment.getExternalStorageDirectory()
			if (external.exists() && external.canRead()) {
				return@withIOContext external
			}

			return@withIOContext File("/")
		}
	}

	private fun loadDirectory(directory: File) {
		navigationJob?.cancel()
		safeActivityRef?.let { activityRef ->
			navigationJob = activityRef.activityCoroutineScope.launch {
				withIOContext {
					try {
						val filter = FileFilter { file ->
							if (file.isDirectory) return@FileFilter true
							when {
								isFolderPickerOnly -> false
								isFilePickerOnly -> true
								else -> true
							}
						}

						val files = directory.listFiles(filter) ?: emptyArray()
						val locale = Locale.getDefault()
						val dateFormat = SimpleDateFormat("MMM d, yyyy", locale)

						val browserItems = files.map { file ->
							val isDir = file.isDirectory
							val path = file.absolutePath

							val lastModified = dateFormat.format(Date(file.lastModified()))
							val infoText = if (isDir) {
								lastModified
							} else {
								val size = formatFileSize(activityRef, file.length())
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

						renderState(directory, browserItems)
					} catch (error: Exception) {
						val absolutePath = directory.absolutePath
						logger.e("Failed to load directory $absolutePath:", error)
					}
				}
			}
		}
	}

	private suspend fun renderState(directory: File, items: List<BrowserItem>) {
		withMainContext {
			safeActivityRef?.let { activityRef ->
				val view = dialogBuilder?.view ?: return@withMainContext
				browserState = browserState.copy(items = items)

				val listView = view.findViewById<ListView>(R.id.list_of_files_folders)
				if (browserAdapter == null) {
					browserAdapter = BrowserAdapter(activityRef, items) {
						activityRef.lifecycleScope.launch {
							handleItemInteraction(it)
						}
					}
					listView.adapter = browserAdapter
				} else browserAdapter?.swapData(items)

				updateBreadcrumbs(view, activityRef, directory)

				val backBtn = view.findViewById<TextView>(R.id.btn_go_to_previous_dir)
				backBtn?.visibility = if (browserState.history.size > 1)
					View.VISIBLE else View.GONE

				updateActionButtons()
			}
		}
	}

	private suspend fun handleItemInteraction(item: BrowserItem) {
		if (item.isDirectory) {
			withMainContext {
				val newHistory = browserState.history + item.file
				browserState = browserState.copy(
					currentDir = item.file,
					history = newHistory
				)
			}
			loadDirectory(item.file)
		} else {
			if (isMultiSelection) {
				withMainContext {
					if (selectedPaths.contains(item.path)) {
						selectedPaths.remove(item.path)
					} else {
						selectedPaths.add(item.path)
					}
					item.isSelected = !item.isSelected
					browserAdapter?.notifyDataSetChanged()
					updateActionButtons()
				}
			} else {
				finalizeSelection(listOf(item.path))
			}
		}
	}

	private suspend fun navigateUp() {
		val history = browserState.history
		if (history.size <= 1) return

		val newHistory = history.dropLast(1)
		val parentDir = newHistory.last()

		withMainContext {
			browserState = browserState.copy(
				currentDir = parentDir,
				history = newHistory
			)
		}

		loadDirectory(parentDir)
	}

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

	private suspend fun finalizeSelection(paths: List<String>) {
		if (paths.isNotEmpty()) {
			onFileSelection(paths)
			close()
		}
	}

	private suspend fun updateBreadcrumbs(root: View, activity: Activity, currentDir: File) {
		withMainContext {
			val breadcrumb = root.findViewById<LinearLayout>(R.id.container_breadcrumb)
			val container = breadcrumb ?: return@withMainContext
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
					scroll.post {
						scroll.fullScroll(View.FOCUS_RIGHT)
					}
				}
		}
	}

	private fun updateActionButtons() {
		val btn = dialogBuilder?.view
			?.findViewById<TextView>(R.id.btn_dialog_positive) ?: return

		val count = selectedPaths.size
		btn.text = if (isMultiSelection && count > 0)
			"$positiveButtonText ($count)" else positiveButtonText
	}

	private class BrowserAdapter(
		activity: BaseActivity,
		private var items: List<BrowserItem>,
		private val onClick: (BrowserItem) -> Unit
	) : BaseAdapter() {

		private class ViewHolder(view: View) {
			val nameText: TextView = view.findViewById(R.id.txt_file_folder_name)
			val metaText: TextView = view.findViewById(R.id.txt_file_folder_metadata)
			val icon: ImageView = view.findViewById(R.id.img_file_type_indicator)
			val checkbox: ImageView = view.findViewById(R.id.img_checkbox_selection)
			var processingJob: Job? = null
		}

		private val weakReferenceOfActivity = WeakReference(activity)
		private val safeActivityRef get() = weakReferenceOfActivity.get()

		private val inflater = LayoutInflater.from(safeActivityRef)
		fun swapData(newItems: List<BrowserItem>) {
			this.items = newItems
			notifyDataSetChanged()
		}

		override fun getCount() = items.size

		override fun getItem(position: Int) = items[position]

		override fun getItemId(position: Int) = position.toLong()

		override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
			val view: View
			val holder: ViewHolder

			if (convertView == null) {
				val resId = R.layout.dialog_file_folder_picker_item_1
				view = inflater.inflate(resId, parent, false)
				holder = ViewHolder(view)
				view.tag = holder
			} else {
				view = convertView
				holder = view.tag as ViewHolder
				holder.processingJob?.cancel()
			}

			val item = getItem(position)
			val currentName = item.name

			holder.nameText.text = currentName
			holder.metaText.text = item.info
			holder.icon.setImageResource(
				if (item.isDirectory) R.drawable.img_folder_indicator
				else R.drawable.img_file_indicator
			)

			holder.checkbox.visibility = if (item.isSelected)
				View.VISIBLE else View.GONE

			holder.processingJob = safeActivityRef?.lifecycleScope?.launch {
				holder.nameText.normalizeTallSymbols(originalText = currentName)
			}

			view.setOnClickListener { onClick(item) }
			return view
		}
	}
}