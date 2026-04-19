package lib.device

import android.content.*
import android.content.Intent.*
import android.net.*
import android.webkit.*
import androidx.core.content.*
import androidx.documentfile.provider.*
import app.core.bases.*
import com.aio.*
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.ToastView.Companion.showToast
import java.io.*

object ShareUtility {

	private fun getMimeType(context: Context, uri: Uri, file: File? = null): String {
		return context.contentResolver.getType(uri)
			?: file?.let {
				val singleton = MimeTypeMap.getSingleton()
				singleton.getMimeTypeFromExtension(it.extension)
			}
			?: "*/*"
	}

	@JvmStatic
	suspend fun shareUrl(context: Context, fileURL: String, title: String = "Share") {
		withMainContext {
			if (fileURL.isBlank()) return@withMainContext
			val intent = Intent(ACTION_SEND).apply {
				type = "text/plain"
				putExtra(EXTRA_TEXT, fileURL)
			}
			context.startActivity(createChooser(intent, title))
		}
	}

	@JvmStatic
	suspend fun shareDocumentFile(
		context: Context, documentFile: DocumentFile, title: String = "Share") {
		withMainContext {
			val intent = Intent(ACTION_SEND).apply {
				type = documentFile.type ?: "application/octet-stream"
				putExtra(EXTRA_STREAM, documentFile.uri)
				addFlags(FLAG_GRANT_READ_URI_PERMISSION)
			}
			context.startActivity(createChooser(intent, title))
		}
	}

	@JvmStatic
	suspend fun shareMediaFile(
		context: Context, file: File, title: String? = null) {
		withMainContext {
			try {
				val authority = "${context.packageName}.provider"
				val fileUri = FileProvider.getUriForFile(context, authority, file)

				val intent = Intent(ACTION_SEND).apply {
					type = getMimeType(context, fileUri, file)
					putExtra(EXTRA_STREAM, fileUri)
					addFlags(FLAG_GRANT_READ_URI_PERMISSION)
				}

				val chooserTitle = title ?: getText(R.string.title_sharing_media_file)
				context.startActivity(createChooser(intent, chooserTitle))
			} catch (error: Exception) {
				error.printStackTrace()
			}
		}
	}

	@JvmStatic
	suspend fun openFile(context: Context, file: File) = withMainContext {
		try {
			val authority = "${context.packageName}.provider"
			val fileUri = FileProvider.getUriForFile(context, authority, file)
			val mimeType = getMimeType(context, fileUri, file)

			val intent = Intent(ACTION_VIEW).apply {
				setDataAndType(fileUri, mimeType)
				addFlags(FLAG_GRANT_READ_URI_PERMISSION)
				if (context !is BaseActivity) {
					addFlags(FLAG_ACTIVITY_NEW_TASK)
				}
			}

			context.startActivity(intent)
		} catch (error: ActivityNotFoundException) {
			if (context is BaseActivity) {
				val msgId = R.string.title_no_app_found_to_open_this_file
				showToast(context, msgId)
			}
		}
	}

	@JvmStatic
	suspend fun shareText(context: Context, text: String, title: String = "Share") {
		withMainContext {
			if (text.isBlank()) return@withMainContext
			val intent = Intent(ACTION_SEND).apply {
				type = "text/plain"
				putExtra(EXTRA_TEXT, text)
			}
			context.startActivity(createChooser(intent, title))
		}
	}

	@JvmStatic
	suspend fun openApkFile(context: Context, apkFile: File) {
		withMainContext {
			try {
				val authority = "${context.packageName}.provider"
				val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
				val intent = Intent(ACTION_VIEW).apply {
					setDataAndType(apkUri, "application/vnd.android.package-archive")
					addFlags(FLAG_GRANT_READ_URI_PERMISSION or FLAG_ACTIVITY_NEW_TASK)
				}
				context.startActivity(intent)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}
	}
}