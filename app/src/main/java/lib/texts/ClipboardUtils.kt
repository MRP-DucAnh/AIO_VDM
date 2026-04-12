package lib.texts

import android.content.*
import android.content.ClipData.*
import android.content.ClipboardManager.*
import android.content.Context.*
import lib.process.*
import lib.texts.ClipboardUtils.activeListeners
import lib.texts.ClipboardUtils.copyTextToClipboard
import lib.texts.ClipboardUtils.getTextFromClipboard
import java.lang.ref.*

/**
 * Utility object for managing system clipboard operations asynchronously.
 * * This object serves as a centralized bridge to the Android [ClipboardManager].
 * It simplifies common tasks such as copying text/HTML, appending to existing clips,
 * and managing clipboard change listeners with a focus on memory safety and
 * non-blocking UI performance.
 *
 * ### Threading Model
 * All public methods in this object are `suspend` functions and utilize `withIOContext`
 * (typically [kotlinx.coroutines.Dispatchers.IO]). This ensures that IPC (Inter-Process
 * Communication) calls to the system service do not cause frame drops or ANRs
 * (Application Not Responding) on the Main thread.
 *
 * ### Memory Safety
 * For listener management, this object employs a proxy pattern with [java.lang.ref.WeakReference]
 * to prevent the system [ClipboardManager] from leaking [Context] or UI-bound
 * [OnPrimaryClipChangedListener] instances.
 */
object ClipboardUtils {

	/**
	 * A registry that maps original [OnPrimaryClipChangedListener] instances to their
	 * corresponding [ListenerProxy] wrappers.
	 */
	private val activeListeners =
		mutableMapOf<OnPrimaryClipChangedListener, OnPrimaryClipChangedListener>()

	/**
	 * A protective proxy that wraps a [OnPrimaryClipChangedListener] using a [WeakReference].
	 *
	 * ### Why this is used:
	 * The Android System [ClipboardManager] is a system-level service that can outlive
	 * Activities and Fragments. If you register a listener directly, the system service
	 * may hold a strong reference to your UI component, causing a **Memory Leak**.
	 *
	 * This proxy ensures that if the [originalListener] is garbage collected, the
	 * system service holds a reference to an empty proxy instead of a leaked object.
	 *
	 * @param originalListener The actual callback implementation to be invoked.
	 */
	private class ListenerProxy(
		originalListener: OnPrimaryClipChangedListener
	) : OnPrimaryClipChangedListener {

		private val weakListener = WeakReference(originalListener)

		/**
		 * Invoked by the System Clipboard service. Forwards the event to the
		 * wrapped listener only if it hasn't been garbage collected.
		 */
		override fun onPrimaryClipChanged() {
			weakListener.get()?.onPrimaryClipChanged()
		}
	}

	/**
	 * Safely clears the system clipboard content across all applications.
	 *
	 * This method replaces the current primary clip with an empty plain-text clip.
	 * It uses [withIOContext] to ensure the IPC (Inter-Process Communication) call
	 * to the System Clipboard Service does not block the Main thread.
	 *
	 * ### Implementation Note:
	 * On Android 10 (API 29) and above, background apps cannot clear the clipboard
	 * unless they are the default Input Method Editor (IME) or have focus. Ensure
	 * this is called while the app is in the foreground.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * Uses a safe cast to [ClipboardManager].
	 */
	@JvmStatic
	suspend fun clearClipboard(context: Context?) = withIOContext {
		(context?.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.apply {
			setPrimaryClip(newPlainText("", ""))
		}
	}

	/**
	 * Checks whether the clipboard contains a valid, non-empty text entry.
	 *
	 * This method performs a multi-stage validation:
	 * 1. Accesses the [ClipboardManager].
	 * 2. Verifies the existence of a [ClipboardManager.getPrimaryClip].
	 * 3. Ensures the clip contains at least one item.
	 * 4. Validates that the first item's text property is not null and not empty.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @return `true` if the clipboard contains usable text; `false` otherwise.
	 */
	@JvmStatic
	suspend fun hasTextInClipboard(context: Context?): Boolean = withIOContext {
		val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.primaryClip?.takeIf { clip ->
			clip.itemCount > 0 && clip.getItemAt(0).text?.isNotEmpty() == true
		} != null
	}

	/**
	 * Retrieves the HTML-formatted string from the clipboard, if present.
	 *
	 * Unlike plain text, HTML content is stored in a specific property of the [ClipData.Item].
	 * This method attempts to extract that content. If the clipboard contains plain text
	 * without an HTML representation, this will return an empty string.
	 *
	 * ### Use Case:
	 * Use this when you need to preserve styling (bold, links, colors) when pasting
	 * into a Rich Text Editor or WebView.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @return The HTML string content, or an empty string if no HTML data is available.
	 */
	@JvmStatic
	suspend fun getHtmlFromClipboard(context: Context?): String = withIOContext {
		val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.primaryClip?.takeIf { clip ->
			clip.itemCount > 0
		}?.getItemAt(0)?.htmlText ?: ""
	}

	/**
	 * Copies formatted HTML content to the system clipboard.
	 *
	 * This method creates an HTML-specific [ClipData] which allows supporting applications
	 * (like email clients or note-taking apps) to paste the content with rich formatting
	 * preserved. It utilizes [ClipData.newHtmlText] to set both the user-visible text
	 * and the underlying HTML markup.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @param html The HTML string to be copied. If null or empty, no operation is performed.
	 */
	@JvmStatic
	suspend fun copyHtmlToClipboard(context: Context?, html: String?) = withIOContext {
		html?.takeIf { it.isNotEmpty() }?.let { validHtml ->
			(context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).apply {
				setPrimaryClip(newHtmlText("html_clip", validHtml, validHtml))
			}
		}
	}

	/**
	 * Appends new text to the existing content currently stored in the clipboard.
	 *
	 * This is a composite operation that:
	 * 1. Fetches the current clipboard text via [getTextFromClipboard].
	 * 2. Concatenates the current content with the new [text].
	 * 3. Re-saves the combined string back to the clipboard via [copyTextToClipboard].
	 *
	 * ### Performance Note:
	 * Since this involves two separate IPC (Inter-Process Communication) calls to the
	 * system server, it is wrapped in [withIOContext] to prevent UI stutters.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @param text The new string to append to the existing clipboard content.
	 */
	@JvmStatic
	suspend fun appendTextToClipboard(context: Context?, text: String?) = withIOContext {
		text?.takeIf { it.isNotEmpty() }?.let { validText ->
			val current = getTextFromClipboard(context)
			copyTextToClipboard(context, current + validText)
		}
	}

	/**
	 * Retrieves the current plain text content from the system clipboard.
	 *
	 * This method accesses the [ClipboardManager] and extracts the text from the first item
	 * in the [ClipboardManager.getPrimaryClip]. It safely handles cases where the clipboard
	 * might be empty or contain non-text data (like images or intents) by returning an empty string.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @return The text content as a [String], or an empty string if the clipboard is empty
	 * or the content cannot be converted to text.
	 */
	@JvmStatic
	suspend fun getTextFromClipboard(context: Context?): String = withIOContext {
		val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.primaryClip?.takeIf { clip ->
			clip.itemCount > 0
		}?.getItemAt(0)?.text?.toString() ?: ""
	}

	/**
	 * Copies the provided text to the system clipboard.
	 *
	 * This method creates a new [ClipData] labeled "text_clip" and sets it as the
	 * primary clip. It includes a safety check to ensure that null or empty strings
	 * do not overwrite the clipboard with blank data.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @param text The string to be copied. If null or empty, no operation is performed.
	 */
	@JvmStatic
	suspend fun copyTextToClipboard(context: Context?, text: String?) = withIOContext {
		text?.takeIf { it.isNotEmpty() }?.let { validText ->
			(context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).apply {
				setPrimaryClip(newPlainText("text_clip", validText))
			}
		}
	}

	/**
	 * Registers a listener to be notified when the primary clip on the clipboard changes.
	 * * This function wraps the [OnPrimaryClipChangedListener] in a [ListenerProxy] to prevent
	 * strong-reference memory leaks and stores it in [activeListeners] for later removal.
	 * The operation is executed asynchronously on an IO-optimized thread.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @param listener The callback to be invoked on clipboard changes. If null, no action is taken.
	 * @return A [kotlinx.coroutines.Job] representing the background registration task.
	 */
	@JvmStatic
	suspend fun setClipboardListener(
		context: Context?,
		listener: OnPrimaryClipChangedListener? = null
	) = withIOContext {
		listener?.let { originalListener ->
			val proxy = ListenerProxy(originalListener)
			val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
			clipboard.addPrimaryClipChangedListener(proxy)
			activeListeners[originalListener] = proxy
		}
	}

	/**
	 * Unregisters a previously added clipboard listener.
	 * * This function retrieves the associated [ListenerProxy] from the internal map and
	 * removes it from the System [ClipboardManager]. This prevents the listener from
	 * continuing to receive updates after its host component (e.g., Fragment or Activity)
	 * is destroyed.
	 *
	 * @param context The context used to retrieve the [CLIPBOARD_SERVICE].
	 * @param listener The original listener instance that was previously registered.
	 * @return A [kotlinx.coroutines.Job] representing the background unregistration task.
	 */
	@JvmStatic
	suspend fun removeClipboardListener(
		context: Context?,
		listener: OnPrimaryClipChangedListener? = null
	) = withIOContext {
		listener?.let { originalListener ->
			val proxy = activeListeners.remove(originalListener)

			if (proxy != null) {
				val clipboard = context?.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
				clipboard.removePrimaryClipChangedListener(proxy)
			}
		}
	}
}