package app.ui.main.fragments.settings.dialogs

import android.view.*
import android.widget.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.bases.*
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import app.core.engines.settings.AIOSettings.Companion.SYSTEM_GALLERY
import app.core.engines.settings.AIOSettingsRepo.getSettings
import com.aio.*
import kotlinx.coroutines.*
import lib.files.*
import lib.files.FileSystemUtility.openAllFilesAccessSettings
import lib.process.*
import lib.texts.CommonTextUtils.fromHtmlStringToSpanned
import lib.texts.CommonTextUtils.getText
import lib.ui.*
import lib.ui.builders.*
import java.lang.ref.*

/**
 * A specialized dialog component that allows users to choose between the App's Private Folder
 * and the System Gallery for storing downloaded media.
 *
 * This class manages the selection process as an atomic transaction:
 * * **Temporary State**: Updates to the download location are reflected in the UI immediately
 * but are not finalized unless the user clicks the positive action button.
 * * **Automatic Restoration**: If the dialog is canceled or dismissed via back-navigation
 * without applying, the class restores the [originalLocation] to ensure data integrity.
 * * **Permission Handling**: Seamlessly integrates with [FileSystemUtility] to verify
 * write permissions for public storage, providing a guided UX if permissions are missing.
 * * **Thread Safety**: Uses a combination of [WeakReference] for memory safety and
 * [withMainContext]/[withIOContext] to ensure all UI and database operations occur on
 * the correct threads.
 *
 * @param baseActivity The host activity context used for UI inflation and coroutine scoping.
 */
class DownloadLocationSelector(baseActivity: BaseActivity) {

	/**
	 * Utility for diagnostic logging and error tracking, specifically tagged with
	 * the current class name for streamlined debugging in Logcat.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * A [WeakReference] wrapping the host [BaseActivity].
	 * * This ensures the selector does not cause memory leaks if it outlives the
	 * Activity's lifecycle (e.g., during orientation changes or long-running
	 * background tasks).
	 */
	private val weakReferenceOfBaseActivity = WeakReference(baseActivity)

	/**
	 * Provides a null-safe reference to the host Activity.
	 * * Returns the [BaseActivity] instance if it is still valid and has not been
	 * garbage collected. This is used to safely access the activityCoroutineScope
	 * or perform UI operations.
	 */
	private val safeBaseActivityRef
		get() = weakReferenceOfBaseActivity.get()

	/**
	 * A state flag that tracks whether the user confirmed their selection via
	 * the "Apply" button.
	 * * If this remains `false` when the dialog is dismissed, the class will
	 * automatically roll back any temporary changes to the original state.
	 */
	private var hasSettingApplied = false

	/**
	 * Stores the initial download location type retrieved at the moment the
	 * selector is instantiated.
	 * * This value serves as the "source of truth" for the restoration logic
	 * in case the user cancels the selection process.
	 */
	private val originalLocation = getSettings().defaultDownloadLocationType

	/**
	 * Lazily initializes the download location selection dialog and configures its
	 * UI components and interaction logic.
	 *
	 * This property encapsulates the complete setup for the location selector:
	 * 1. **UI Inflation**: Sets the layout resource and establishes initial
	 * cancelable behavior.
	 * 2. **View Binding**: Locates and references internal components like buttons,
	 * radio indicators, and description labels.
	 * 3. **Dynamic Content**: Injects the current download directory path into
	 * the gallery description text using HTML-formatted strings for enhanced styling.
	 * 4. **Synchronous State Sync**: Immediately sets the correct radio button
	 * drawables based on existing user preferences to prevent visual lag upon display.
	 * 5. **Interaction Handlers**:
	 * * **Private/Gallery Buttons**: Updates temporary in-memory settings and
	 * refreshes UI indicators.
	 * * **Permission Checks**: Validates full file system access when selecting
	 * the System Gallery, triggering a permission dialog and vibration if access
	 * is missing.
	 * * **Apply Button**: Finalizes the "transaction" by flagging the settings
	 * as applied and committing the new configuration to the database.
	 * 6. **Lifecycle Safety**: Attaches listeners to handle cancellations and
	 * dismissals, ensuring that settings are reverted if the user exits without
	 * saving.
	 *
	 * @return A fully configured [DialogBuilder] instance ready for display.
	 */
	private val dialog by lazy {
		DialogBuilder(safeBaseActivityRef).apply {
			setView(R.layout.dialog_default_location_1)
			setCancelable(true)

			view.apply {
				val privateBtn = findViewById<View>(R.id.button_app_private_folder)
				val galleryBtn = findViewById<View>(R.id.btn_system_gallery)
				val applyBtn = findViewById<View>(R.id.btn_dialog_positive_container)
				val privateRadio = findViewById<ImageView>(R.id.btn_app_private_folder_radio)
				val galleryRadio = findViewById<ImageView>(R.id.button_system_gallery_radio)
				val defaultGalleryDirTxt = findViewById<TextView>(R.id.txt_system_gallery_description)

				safeBaseActivityRef?.let { activityRef ->
					activityRef.getString(
						R.string.text_visible_in_system_gallery,
						getSettings().selectedDownloadDirectory
					).let {
						fromHtmlStringToSpanned(it).let { htmlString ->
							defaultGalleryDirTxt.text = htmlString
						}
					}
				}

				val isPrivate = getSettings().defaultDownloadLocationType == PRIVATE_FOLDER
				privateRadio.setImageResource(
					if (isPrivate) R.drawable.ic_button_checked_circle
					else R.drawable.ic_button_unchecked_circle
				)
				galleryRadio.setImageResource(
					if (isPrivate) R.drawable.ic_button_unchecked_circle
					else R.drawable.ic_button_checked_circle
				)

				privateBtn.setOnClickListener {
					safeBaseActivityRef?.activityCoroutineScope?.launch {
						logger.d("User selected: Private Folder")
						getSettings().defaultDownloadLocationType = PRIVATE_FOLDER
						updateRadioButtons(privateRadio, galleryRadio)
					}
				}

				galleryBtn.setOnClickListener {
					safeBaseActivityRef?.activityCoroutineScope?.launch {
						logger.d("User selected: System Gallery")
						if (FileSystemUtility.hasFullFileSystemAccess(INSTANCE)) {
							getSettings().defaultDownloadLocationType = SYSTEM_GALLERY
							updateRadioButtons(privateRadio, galleryRadio)
						} else {
							safeBaseActivityRef?.doSomeVibration()
							MsgDialogUtils.getMessageDialog(
								baseActivityInf = safeBaseActivityRef,
								titleText = getText(R.string.title_permission_needed),
								isTitleVisible = true,
								isNegativeButtonVisible = false,
								messageTextViewCustomize = {
									it.setText(R.string.text_app_dont_have_write_permission_msg)
								}
							)?.let { msgDialogBuilder ->
								msgDialogBuilder.setOnClickForPositiveButton {
									safeBaseActivityRef?.activityCoroutineScope?.launch {
										msgDialogBuilder.close()
										delay(500)
										safeBaseActivityRef?.let { activityRef ->
											openAllFilesAccessSettings(activityRef)
										}
									}
								}
							}?.show()
						}
					}
				}

				applyBtn.setOnClickListener {
					safeBaseActivityRef?.activityCoroutineScope?.launch {
						hasSettingApplied = true
						getSettings().updateInDB()
						safeBaseActivityRef?.doSomeVibration()
						ToastView.showToast(safeBaseActivityRef, R.string.title_setting_applied)
						close()
					}
				}
			}

			dialog.setOnCancelListener {
				safeBaseActivityRef?.activityCoroutineScope?.launch {
					restoreIfNotApplied()
				}
			}

			dialog.setOnDismissListener {
				safeBaseActivityRef?.activityCoroutineScope?.launch {
					restoreIfNotApplied()
				}
			}
		}
	}

	/**
	 * Synchronizes the visual state of the selection radio buttons with the current settings.
	 *
	 * This method updates the image resources for both the App Private Folder and System
	 * Gallery selection indicators. It ensures UI consistency by executing on the
	 * [withMainContext], swapping the checked and unchecked circle drawables based
	 * on whether the private folder path is currently active.
	 *
	 * @param privateRadio The [ImageView] acting as the radio button for the internal app storage.
	 * @param galleryRadio The [ImageView] acting as the radio button for the public system gallery.
	 */
	private suspend fun updateRadioButtons(privateRadio: ImageView, galleryRadio: ImageView) {
		withMainContext {
			val isPrivate = getSettings().defaultDownloadLocationType == PRIVATE_FOLDER
			privateRadio.setImageResource(
				if (isPrivate) R.drawable.ic_button_checked_circle
				else R.drawable.ic_button_unchecked_circle
			)
			galleryRadio.setImageResource(
				if (isPrivate) R.drawable.ic_button_unchecked_circle
				else R.drawable.ic_button_checked_circle
			)
		}
	}

	/**
	 * Reverts the download location setting to its original state if the user dismisses
	 * the dialog without explicitly applying changes.
	 *
	 * This method acts as a safety mechanism to prevent partial or accidental setting
	 * changes. It executes within [withIOContext] to perform the database update
	 * off the main thread, ensuring that [originalLocation] is restored and persisted
	 * only if [hasSettingApplied] remains false.
	 */
	private suspend fun restoreIfNotApplied() {
		withIOContext {
			if (!hasSettingApplied) {
				getSettings().defaultDownloadLocationType = originalLocation
				getSettings().updateInDB()
			}
		}
	}

	/**
	 * Displays the download location selection dialog to the user.
	 *
	 * This method is thread-safe, utilizing [withMainContext] to interact with the
	 * [DialogBuilder]. It includes a safety check using [takeIf] to ensure the
	 * dialog is not already visible before attempting to call [show].
	 */
	suspend fun show() {
		withMainContext {
			takeIf { !dialog.isShowing }?.run {
				dialog.show()
			}
		}
	}

	/**
	 * Safely dismisses the download location selection dialog.
	 *
	 * Executed within [withMainContext], this method ensures that the dialog is
	 * removed from the window manager only if it is currently being displayed.
	 * Closing the dialog typically triggers the dismiss listener, which handles
	 * any necessary setting restoration.
	 */
	suspend fun close() {
		withMainContext {
			takeIf { dialog.isShowing }?.run {
				dialog.close()
			}
		}
	}
}