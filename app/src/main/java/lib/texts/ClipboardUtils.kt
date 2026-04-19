package lib.texts

import android.content.*
import android.content.ClipData.*
import android.content.ClipboardManager.*
import android.content.Context.*
import lib.process.*
import java.lang.ref.*

object ClipboardUtils {

	private val activeListeners =
		mutableMapOf<OnPrimaryClipChangedListener,
			OnPrimaryClipChangedListener>()

	private class ListenerProxy(
		originalListener: OnPrimaryClipChangedListener) :
		OnPrimaryClipChangedListener {

		private val weakListener = WeakReference(originalListener)

		override fun onPrimaryClipChanged() {
			weakListener.get()?.onPrimaryClipChanged()
		}
	}

	@JvmStatic
	suspend fun clearClipboard(context: Context?): ClipboardManager? {
		return withIOContext {
			val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
			(systemService as? ClipboardManager)?.apply {
				setPrimaryClip(newPlainText("", ""))
			}
		}
	}

	@JvmStatic
	suspend fun hasTextInClipboard(context: Context?): Boolean {
		return withIOContext {
			val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
			val clipboard = systemService as ClipboardManager
			clipboard.primaryClip?.takeIf { clip ->
				clip.itemCount > 0 &&
					clip.getItemAt(0).text?.isNotEmpty() == true
			} != null
		}
	}

	@JvmStatic
	suspend fun getHtmlFromClipboard(context: Context?): String {
		return withIOContext {
			val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
			val clipboard = systemService as ClipboardManager
			clipboard.primaryClip?.takeIf { clip ->
				clip.itemCount > 0
			}?.getItemAt(0)?.htmlText ?: ""
		}
	}

	@JvmStatic
	suspend fun copyHtmlToClipboard(context: Context?, html: String?)
		: ClipboardManager? {
		return withIOContext {
			html?.takeIf { it.isNotEmpty() }?.let { validHtml ->
				val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
				(systemService as ClipboardManager).apply {
					setPrimaryClip(newHtmlText("html_clip", validHtml, validHtml))
				}
			}
		}
	}

	@JvmStatic
	suspend fun appendTextToClipboard(context: Context?, text: String?)
		: ClipboardManager? {
		return withIOContext {
			text?.takeIf { it.isNotEmpty() }?.let { validText ->
				val current = getTextFromClipboard(context)
				copyTextToClipboard(context, current + validText)
			}
		}
	}

	@JvmStatic
	suspend fun getTextFromClipboard(context: Context?): String {
		return withIOContext {
			val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
			val clipboard = systemService as ClipboardManager
			clipboard.primaryClip?.takeIf { clip ->
				clip.itemCount > 0
			}?.getItemAt(0)?.text?.toString() ?: ""
		}
	}

	@JvmStatic
	suspend fun copyTextToClipboard(context: Context?, text: String?)
		: ClipboardManager? {
		return withIOContext {
			text?.takeIf { it.isNotEmpty() }?.let { validText ->
				val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
				(systemService as ClipboardManager).apply {
					setPrimaryClip(newPlainText("text_clip", validText))
				}
			}
		}
	}

	@JvmStatic
	suspend fun setClipboardListener(
		context: Context?,
		listener: OnPrimaryClipChangedListener? = null
	) = withIOContext {
		listener?.let { originalListener ->
			val proxy = ListenerProxy(originalListener)
			val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
			val clipboard = systemService as ClipboardManager
			clipboard.addPrimaryClipChangedListener(proxy)
			activeListeners[originalListener] = proxy
		}
	}

	@JvmStatic
	suspend fun removeClipboardListener(
		context: Context?,
		listener: OnPrimaryClipChangedListener? = null
	) = withIOContext {
		listener?.let { originalListener ->
			val proxy = activeListeners.remove(originalListener)

			if (proxy != null) {
				val systemService = context?.getSystemService(CLIPBOARD_SERVICE)
				val clipboard = systemService as ClipboardManager
				clipboard.removePrimaryClipChangedListener(proxy)
			}
		}
	}
}