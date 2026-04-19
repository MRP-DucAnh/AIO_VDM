package lib.device

import android.content.*
import android.content.Intent.*
import android.net.*
import android.webkit.MimeTypeMap.*
import androidx.core.content.FileProvider.*
import androidx.documentfile.provider.*
import app.core.bases.*
import com.aio.*
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.ToastView.Companion.showToast
import java.io.*
import java.lang.ref.*

object ShareUtility {

	@JvmStatic
	suspend fun shareUrl(context: Context?, fileURL: String,
	                     titleText: String = "Share",
	                     onDone: () -> Unit = {}) {
		withMainContext {
			WeakReference(context).get()?.let { contextRef ->
				Intent(ACTION_SEND).apply {
					type = "text/plain"
					putExtra(EXTRA_TEXT, fileURL)
					contextRef.startActivity(createChooser(this, titleText))
					onDone()
				}
			}
		}
	}

	@JvmStatic
	suspend fun shareDocumentFile(context: Context?,
	                              documentFile: DocumentFile,
	                              titleText: String = "Share",
	                              onDone: () -> Unit = {}) {
		withMainContext {
			WeakReference(context).get()?.let { contextRef ->
				val file = File(documentFile.uri.path ?: return@withMainContext)
				val fileUri: Uri = getUriForFile(
					contextRef,
					"${contextRef.packageName}.provider", file
				)

				Intent(ACTION_SEND).apply {
					type = contextRef.contentResolver.getType(documentFile.uri)
					putExtra(EXTRA_STREAM, fileUri)
					addFlags(FLAG_GRANT_READ_URI_PERMISSION)
					contextRef.startActivity(createChooser(this, titleText))
					onDone()
				}
			}
		}
	}

	@JvmStatic
	suspend fun openFile(file: File, context: Context?) {
		withMainContext {
			WeakReference(context).get()?.let { contextRef ->
				val fileUri: Uri = getUriForFile(
					contextRef,
					"${contextRef.packageName}.provider", file
				)
				val mimeType = getSingleton()
					.getMimeTypeFromExtension(file.extension) ?: "*/*"

				val openFileIntent = Intent(ACTION_VIEW).apply {
					setDataAndType(fileUri, mimeType)
					addFlags(FLAG_GRANT_READ_URI_PERMISSION)
				}

				if (openFileIntent.resolveActivity(contextRef.packageManager) != null) {
					contextRef.startActivity(openFileIntent)
				} else {
					if (contextRef is BaseActivity) {
						val msgId = R.string.title_no_app_found_to_open_this_file
						showToast(contextRef, msgId)
					}
				}
			}
		}
	}

	@JvmStatic
	suspend fun shareMediaFile(context: Context?, file: File) {
		withMainContext {
			WeakReference(context).get()?.let { contextRef ->
				try {
					val fileUri: Uri = getUriForFile(
						contextRef,
						"${contextRef.packageName}.provider", file
					)
					val shareIntent = Intent(ACTION_SEND).apply {
						type = contextRef.contentResolver.getType(fileUri) ?: "audio/*"
						putExtra(EXTRA_STREAM, fileUri)
						addFlags(FLAG_GRANT_READ_URI_PERMISSION)
					}
					val titleString = getText(R.string.title_sharing_media_file)
					val intentChooser = createChooser(shareIntent, titleString)
					contextRef.startActivity(intentChooser)
				} catch (error: Exception) {
					error.printStackTrace()
				}
			}
		}
	}

	@JvmStatic
	suspend fun shareVideo(context: Context?, videoFile: DocumentFile,
	                       title: String = "Share", onDone: () -> Unit = {}) {
		withMainContext {
			WeakReference(context).get()?.let { safeContextRef ->
				Intent(ACTION_SEND).apply {
					val videoUri = videoFile.uri
					type = "video/*"
					putExtra(EXTRA_STREAM, videoUri)
					addFlags(FLAG_GRANT_READ_URI_PERMISSION)
					safeContextRef.startActivity(createChooser(this, title))
					onDone()
				}
			}
		}
	}

	@JvmStatic
	suspend fun shareText(context: Context?, text: String,
	                      title: String = "Share", onDone: () -> Unit = {}) {
		withMainContext {
			WeakReference(context).get()?.let { safeContextRef ->
				Intent(ACTION_SEND).apply {
					type = "text/plain"
					putExtra(EXTRA_TEXT, text)
					safeContextRef.startActivity(createChooser(this, title))
					onDone()
				}
			}
		}
	}

	@JvmStatic
	suspend fun openApkFile(baseActivity: BaseActivity?,
	                        apkFile: File, authority: String) {
		withMainContext {
			WeakReference(baseActivity).get()?.let { safeContextRef ->
				val intent = Intent(ACTION_VIEW).apply {
					flags = FLAG_ACTIVITY_NEW_TASK or FLAG_GRANT_READ_URI_PERMISSION
					val apkUri: Uri = getUriForFile(safeContextRef, authority, apkFile)
					setDataAndType(apkUri, "application/vnd.android.package-archive")
				}
				safeContextRef.startActivity(intent)
			}
		}
	}
}