package app.ui.main.fragments.downloads.fragments.finished

import app.core.engines.downloader.AIODownload

/**
 * Defines callback interfaces for handling user interaction events with completed download items
 * displayed in the finished downloads section of the application.
 *
 * This interface serves as a contract between the finished downloads UI fragment and its hosting
 * activity or parent fragment, enabling the propagation of user gestures (clicks and long-clicks)
 * on individual download items that have completed their execution.
 *
 * Implementers of this interface are responsible for defining appropriate behaviors when users
 * interact with finished download entries, such as opening downloaded files, showing detailed
 * information, or initiating context-specific actions like sharing or deletion.
 *
 * ## Usage Example:
 * ```
 * class DownloadsActivity : AppCompatActivity(), FinishedTasksClickEvents {
 *     override fun onFinishedDownloadClick(downloadModel: DownloadDataModel) {
 *         // Open the downloaded file or show details
 *         openDownloadedFile(downloadModel.filePath)
 *     }
 *
 *     override fun onFinishedDownloadLongClick(downloadModel: DownloadDataModel) {
 *         // Show context menu with options for this download
 *         showDownloadContextMenu(downloadModel)
 *     }
 * }
 * ```
 *
 * ## Implementation Notes:
 * - The host component should register an implementation of this interface with the
 *   finished downloads fragment to receive interaction events.
 * - Consider distinguishing between different types of finished downloads (successful,
 *   failed, cancelled) when implementing the callback methods.
 * - For list-based implementations, ensure proper view recycling and model binding
 *   to maintain consistent interaction behavior.
 */
interface FinishedTasksClickEvents {

	/**
	 * Handles the primary click/tap event on a finished download list item.
	 *
	 * This method is invoked when the user performs a standard click gesture on a
	 * completed download entry in the finished downloads list. Typically, this action
	 * should trigger the default behavior for a completed download, such as:
	 * - Opening the downloaded file with an appropriate application
	 * - Navigating to a detailed view of the download
	 * - Initiating playback for media files
	 * - Executing installation for application packages
	 *
	 * @param downloadModel The data model containing complete information about the
	 *                      finished download, including metadata, file location,
	 *                      download status, and any relevant execution details.
	 *                      This model represents the specific download item that
	 *                      received the user interaction.
	 *
	 * @see AIODownload for detailed information about the available download
	 *      properties that can be utilized in the callback implementation.
	 */
	fun onFinishedDownloadClick(downloadModel: AIODownload)

	/**
	 * Handles the long-press (contextual) event on a finished download list item.
	 *
	 * This method is invoked when the user performs a sustained press gesture on a
	 * completed download entry. Long-click interactions typically trigger contextual
	 * or secondary actions, such as:
	 * - Displaying a context menu with additional options
	 * - Enabling multi-selection mode for batch operations
	 * - Showing detailed metadata or statistics
	 * - Initiating sharing or exporting operations
	 *
	 * ## Common Implementation Patterns:
	 * 1. **Context Menu Display**: Show a popup menu with actions like:
	 *    - Share file
	 *    - Delete download
	 *    - Retry failed download
	 *    - View download details
	 * 2. **Selection Mode**: Enter multi-selection state for batch operations
	 * 3. **Preview Mode**: Show expanded details or preview of the download
	 *
	 * @param downloadModel The data model representing the completed download that
	 *                      received the long-press interaction. Contains all relevant
	 *                      download information needed for contextual decision-making.
	 *
	 * @return Implementations may optionally return a Boolean value if used as a
	 *         direct event handler, though the interface currently defines this as
	 *         a Unit-returning function for flexibility.
	 */
	fun onFinishedDownloadLongClick(downloadModel: AIODownload)
}