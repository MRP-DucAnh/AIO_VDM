@file:Suppress("DEPRECATION")

package lib.ui

import android.app.*
import android.content.Context.*
import android.content.pm.*
import android.content.res.*
import android.graphics.*
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory.*
import android.graphics.drawable.*
import android.media.*
import android.media.MediaMetadataRetriever.*
import android.renderscript.*
import android.text.*
import android.text.style.*
import android.view.*
import android.view.View.*
import android.view.animation.*
import android.view.animation.AnimationUtils.*
import android.view.inputmethod.*
import android.widget.*
import androidx.annotation.*
import androidx.appcompat.app.AppCompatDelegate.*
import androidx.core.content.ContextCompat.*
import androidx.core.graphics.*
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.*
import androidx.core.view.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.bases.*
import app.core.engines.settings.AIOSettings.Companion.DARK_MODE_INDICATOR_FIE
import com.aio.R
import com.bumptech.glide.*
import kotlinx.coroutines.*
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isImageByName
import lib.process.*
import java.io.*
import java.net.*
import java.net.HttpURLConnection.*
import java.text.BreakIterator.*
import java.util.*

object ViewUtility {
	private val logger = LogHelperUtils.from(javaClass)

	@JvmStatic
	fun unbindDrawables(view: View?) {
		try {
			view?.background?.callback = null
			if (view is ImageView) {
				view.setImageBitmap(null)
			} else if (view is ViewGroup) {
				for (index in 0 until view.childCount) {
					unbindDrawables(view.getChildAt(index))
				}
				if (view !is AdapterView<*>) {
					view.removeAllViews()
				}
			}
		} catch (error: Exception) {
			logger.e("Error unbinding drawables from views:", error)
		}
	}

	@JvmStatic
	fun setViewOnClickListener(
		clickListener: OnClickListener?,
		activity: Activity?, @IdRes vararg ids: Int
	) {
		for (id in ids) {
			activity?.findViewById<View>(id).apply {
				this?.setOnClickListener(clickListener)
			}
		}
	}

	@JvmStatic
	fun setViewOnClickListener(
		onClickListener: OnClickListener?,
		layout: View, @IdRes vararg ids: Int
	) {
		for (id in ids) {
			layout.findViewById<View>(id).apply {
				this?.setOnClickListener(onClickListener)
			}
		}
	}

	@JvmStatic
	suspend fun animateInfiniteRotation(activity: Activity?, view: View?) {
		withMainContext {
			if (activity == null) return@withMainContext
			val animResId = R.anim.anim_rotate_clockwise
			val animation = loadAnimation(activity, animResId)
			view?.startAnimation(animation)
		}
	}

	@JvmStatic
	suspend fun showOnScreenKeyboard(activity: Activity?, focusedView: View?) {
		withMainContext {
			if (activity == null) return@withMainContext
			val inputService = activity.getSystemService(INPUT_METHOD_SERVICE)
			val inputMethodManager = inputService as InputMethodManager
			focusedView?.requestFocus()
			inputMethodManager.showSoftInput(focusedView, 0)
		}
	}

	@JvmStatic
	suspend fun hideOnScreenKeyboard(activity: Activity?, focusedView: View?) {
		withMainContext {
			if (activity == null) return@withMainContext

			if (focusedView != null) {
				val service = activity.getSystemService(INPUT_METHOD_SERVICE)
				val inputMethodManager = service as InputMethodManager
				inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
			}
		}
	}

	@JvmStatic
	suspend fun TextView.normalizeTallSymbols(
		originalText: String = text.toString(),
		reductionFactor: Float = 0.8f,
		onDone: (Spannable) -> Unit = {}
	) {
		if (originalText.isEmpty()) return
		withIOContext {
			val spannable = SpannableStringBuilder(originalText)
			val boundaryIterator = getCharacterInstance(Locale.getDefault())
			boundaryIterator.setText(originalText)

			var currentStart = boundaryIterator.first()
			var currentEnd = boundaryIterator.next()

			while (currentEnd != DONE) {
				val cluster = originalText.substring(currentStart, currentEnd)
				val shouldReduce = cluster.any { char ->
					!Character.isSpaceChar(char) &&
						!Character.UnicodeScript.of(char.code).isLatin()
				}

				if (shouldReduce) {
					try {
						spannable.setSpan(
							RelativeSizeSpan(reductionFactor),
							currentStart,
							currentEnd,
							Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
						)
					} catch (error: Exception) {
						logger.d("normalizeTallSymbols: span error $error")
					}
				}

				currentStart = currentEnd
				currentEnd = boundaryIterator.next()
			}

			withMainContext {
				if (this@normalizeTallSymbols.text.toString() == originalText) {
					this@normalizeTallSymbols.text = spannable
					onDone(spannable)
				}
			}
		}
	}

	private fun Character.UnicodeScript?.isLatin(): Boolean {
		return this == Character.UnicodeScript.LATIN
	}

	@JvmStatic
	suspend fun tintDrawableWithPrimaryColor(targetDrawable: Drawable?) {
		withMainContext {
			if (targetDrawable == null) return@withMainContext
			val tintColor = getColor(INSTANCE, R.color.color_primary)
			DrawableCompat.setTint(targetDrawable, tintColor)
		}
	}

	@JvmStatic
	suspend fun tintDrawableWithSecondaryColor(targetDrawable: Drawable?) {
		withMainContext {
			if (targetDrawable == null) return@withMainContext
			val tintColor = getColor(INSTANCE, R.color.color_secondary)
			DrawableCompat.setTint(targetDrawable, tintColor)
		}
	}

	@JvmStatic
	suspend fun tintDrawableWithProvidedColor(
		targetDrawable: Drawable?, colorResId: Int
	) {
		withMainContext {
			if (targetDrawable == null) return@withMainContext
			val tintColor = getColor(INSTANCE, colorResId)
			DrawableCompat.setTint(targetDrawable, tintColor)
		}
	}

	@JvmStatic
	suspend fun isOnScreenKeyboardVisible(activity: Activity?): Boolean {
		return withMainContext {
			if (activity == null) return@withMainContext false
			val rootView = activity.findViewById<View>(android.R.id.content)
			val rect = Rect()
			rootView?.getWindowVisibleDisplayFrame(rect)
			val screenHeight = rootView?.rootView?.height
			val keypadHeight = screenHeight?.minus(rect.bottom)
			return@withMainContext if (keypadHeight != null) {
				keypadHeight > 100
			} else {
				false
			}
		}
	}

	@JvmStatic
	suspend fun toggleViewVisibility(
		targetView: View,
		shouldAnimate: Boolean = false,
		animTimeout: Long = 300
	) {
		withMainContext {
			if (shouldAnimate) {
				if (targetView.isVisible) {
					targetView
						.animate()
						.alpha(0f)
						.setDuration(animTimeout)
						.withEndAction { targetView.visibility = GONE }

				} else {
					targetView.alpha = 0f
					targetView.visibility = VISIBLE
					targetView
						.animate()
						.alpha(1f)
						.setDuration(animTimeout)
				}
			} else {
				targetView.visibility = if (targetView.isVisible) GONE
				else VISIBLE
			}
		}
	}

	@JvmStatic
	suspend fun hideView(
		targetView: View?, visibility: Int = GONE,
		shouldAnimate: Boolean = false, animTimeout: Long = 500
	) {
		return withMainContext {
			if (targetView == null) return@withMainContext
			if (!targetView.isVisible) return@withMainContext

			if (shouldAnimate) {
				targetView
					.animate()
					.alpha(0f)
					.setDuration(animTimeout)
				targetView.visibility = visibility
			} else targetView.visibility = visibility
		}
	}

	@JvmStatic
	suspend fun showView(
		targetView: View?, shouldAnimate: Boolean = false,
		animTimeout: Long = 500
	) {
		return withMainContext {
			if (targetView == null) return@withMainContext
			if (targetView.isVisible) return@withMainContext

			if (shouldAnimate) {
				targetView.alpha = 0f
				targetView.visibility = VISIBLE
				targetView.animate().alpha(1f)
					.setDuration(animTimeout)
			} else {
				targetView.visibility = VISIBLE
			}
		}
	}

	@JvmStatic
	suspend fun getTopCutoutHeight(activity: Activity?): Int {
		return withMainContext {
			if (activity == null) return@withMainContext 0
			val windowInsets = activity.window?.decorView?.rootWindowInsets
			val displayCutout = windowInsets?.displayCutout
			return@withMainContext displayCutout?.safeInsetTop ?: 0
		}
	}

	@JvmStatic
	suspend fun setTopMarginWithCutout(view: View?, activity: Activity?) {
		withMainContext {
			if (view == null) return@withMainContext
			if (activity == null) return@withMainContext
			val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
			val topCutoutHeight = getTopCutoutHeight(activity)
			layoutParams.topMargin = topCutoutHeight
			view.layoutParams = layoutParams
		}
	}

	@JvmStatic
	suspend fun measureViewSize(view: View): IntArray {
		return withMainContext {
			view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
			return@withMainContext intArrayOf(view.measuredWidth, view.measuredHeight)
		}
	}

	@JvmStatic
	suspend fun animateViewVisibility(
		targetView: View, visibility: Int, duration: Int
	) {
		withMainContext {
			val alpha = if (visibility == VISIBLE) 1f else 0f
			targetView
				.animate()
				.alpha(alpha)
				.setDuration(duration.toLong())
				.withStartAction {
					if (visibility == VISIBLE) {
						targetView.visibility = VISIBLE
					}
				}
				.withEndAction {
					if (visibility != VISIBLE) {
						targetView.visibility = visibility
					}
				}
		}
	}

	@JvmStatic
	suspend fun animateFadInOutAnim(targetView: View?) {
		withMainContext {
			if (targetView == null) return@withMainContext
			val current = targetView.animation
			if (current != null && !current.hasEnded())
				return@withMainContext

			val anim = AlphaAnimation(0f, 1f).apply {
				duration = 500
				repeatCount = Animation.INFINITE
				repeatMode = Animation.REVERSE
			}

			targetView.startAnimation(anim)
		}
	}

	@JvmStatic
	suspend fun closeAnyAnimation(view: View?) = withMainContext {
		view?.clearAnimation()
	}

	@JvmStatic
	suspend fun fadeOutView(
		view: View?, duration: Long = 300L,
		onAnimationEnd: (() -> Unit)? = null
	) {
		withMainContext {
			if (view == null) return@withMainContext
			val fadeOut = AlphaAnimation(1.0f, 0.0f).apply {
				this.duration = duration
				fillAfter = true

				setAnimationListener(object : Animation.AnimationListener {
					override fun onAnimationStart(animation: Animation?) {}
					override fun onAnimationEnd(animation: Animation?) {
						onAnimationEnd?.invoke()
					}

					override fun onAnimationRepeat(animation: Animation?) {}
				})
			}
			view.startAnimation(fadeOut)
		}
	}

	@JvmStatic
	suspend fun loadThumbnailFromUrl(
		thumbnailUrl: String, targetImageView: ImageView,
		placeHolderDrawableId: Int? = null
	) {
		withIOContext {
			var input: InputStream? = null
			try {
				val url = URL(thumbnailUrl)
				val connection = url.openConnection() as HttpURLConnection
				connection.doInput = true
				connection.connectTimeout = 5000
				connection.readTimeout = 5000
				connection.connect()

				input = connection.inputStream
				val bitmap = decodeStream(input) ?: return@withIOContext
				val isPortrait = bitmap.height > bitmap.width

				val rotatedBitmap = if (isPortrait) {
					rotateBitmap(bitmap, 90f)
				} else bitmap

				withMainContext {
					Glide.with(targetImageView.context)
						.load(rotatedBitmap).into(targetImageView)
				}

				connection.disconnect()
			} catch (error: Exception) {
				logger.e("Error loading thumbnail from a remote url:", error)
				withMainContext {
					if (placeHolderDrawableId == null) return@withMainContext
					targetImageView.setImageResource(placeHolderDrawableId)
				}
			} finally {
				runCatching { input?.close() }
			}
		}
	}

	@JvmStatic
	suspend fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
		return withContext(Dispatchers.Default) {
			val matrix = Matrix().apply { postRotate(angle) }
			val rotatedBitmap = Bitmap.createBitmap(
				bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
			)

			if (rotatedBitmap != bitmap && !bitmap.isRecycled) {
				bitmap.recycle()
			}

			return@withContext rotatedBitmap
		}
	}

	@JvmStatic
	suspend fun getThumbnailFromFile(
		targetFile: File, fallbackThumbUrl: String? = null,
		requiredThumbWidth: Int
	): Bitmap? {
		return withIOContext {
			if (isAudioByName(targetFile.name)) {
				extractAudioAlbumArt(targetFile)
					?.let { return@withIOContext it }

			} else if (isImageByName(targetFile.name)) {
				getBitmapFromFile(imageFile = targetFile)
					?.let {
						val scaleBitmap = scaleBitmap(it, requiredThumbWidth)
						return@withIOContext scaleBitmap
					}

			} else if (targetFile.name.endsWith(".apk", true)) {
				var apkBitmap: Bitmap? = null
				getApkThumbnail(
					apkFile = targetFile,
					onApkIconFound = { bmp -> apkBitmap = bmp; bmp }
				)

				if (apkBitmap != null) {
					return@withIOContext apkBitmap
				}
			}

			val retriever = MediaMetadataRetriever()
			try {
				var originalBitmap: Bitmap? = null
				if (!fallbackThumbUrl.isNullOrEmpty()) {
					originalBitmap =
						getBitmapFromThumbnailUrl(fallbackThumbUrl)
				}

				if (originalBitmap == null) {
					retriever.setDataSource(targetFile.absolutePath)
					originalBitmap = retriever
						.getFrameAtTime(5_000_000, OPTION_CLOSEST_SYNC)
						?: retriever.frameAtTime
				}

				originalBitmap?.let {
					if (it.width <= 0) return@withIOContext null
					val aspectRatio = it.height.toFloat() / it.width
					val targetHeight = (requiredThumbWidth * aspectRatio).toInt()
					val scaledBitmap = it.scale(requiredThumbWidth, targetHeight, false)
					return@withIOContext scaledBitmap
				}
			} catch (error: Exception) {
				logger.e("Error retrieving thumbnail from a file:", error)
			} finally {
				runCatching { retriever.release() }
			}

			return@withIOContext null
		}
	}

	@JvmStatic
	suspend fun getApkThumbnail(
		apkFile: File, imageViewHolder: ImageView? = null,
		defaultThumbDrawable: Drawable? = null,
		onApkIconFound: ((Bitmap) -> Bitmap)? = null
	): Boolean {
		return withIOContext {
			val apkExtension = ".apk".lowercase(Locale.ROOT)
			val centerInside = ImageView.ScaleType.CENTER_INSIDE

			if (!apkFile.exists() || !apkFile.name.endsWith(apkExtension)) {
				withMainContext { imageViewHolder?.apply { scaleType = centerInside } }
				return@withIOContext false
			}

			val packageManager = INSTANCE.packageManager
			return@withIOContext try {
				val apkPath = apkFile.absolutePath
				val packageInfo = packageManager
					.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA)

				packageInfo?.applicationInfo?.let { appInfo ->
					appInfo.sourceDir = apkPath
					appInfo.publicSourceDir = apkPath

					val drawableIcon = appInfo.loadIcon(packageManager)
					withMainContext {
						imageViewHolder?.setImageDrawable(drawableIcon)
						imageViewHolder?.scaleType = centerInside
						imageViewHolder?.setPadding(0, 0, 0, 0)
						val bitmap = drawableToBitmap(drawableIcon)
						if (bitmap != null) onApkIconFound?.invoke(bitmap)
					}

					return@withIOContext true
				}

				withMainContext {
					imageViewHolder
						?.setImageDrawable(defaultThumbDrawable)
				}

				false
			} catch (error: Exception) {
				logger.e("Error extracting app icon from apk file:", error)
				withMainContext {
					imageViewHolder?.apply {
						val fitCenter = ImageView.ScaleType.FIT_CENTER
						scaleType = fitCenter
						setPadding(0, 0, 0, 0)
						setImageDrawable(defaultThumbDrawable)
					}
				}
				false
			}
		}
	}

	@JvmStatic
	suspend fun drawableToBitmap(drawable: Drawable): Bitmap? {
		return withIOContext {
			if (drawable is BitmapDrawable) return@withIOContext drawable.bitmap
			val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
			val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1

			return@withIOContext try {
				val bitmap = createBitmap(width, height)
				val canvas = Canvas(bitmap)
				drawable.setBounds(0, 0, canvas.width, canvas.height)
				drawable.draw(canvas)
				bitmap
			} catch (error: Exception) {
				logger.e("Error converting drawable to a bitmap:", error)
				null
			}
		}
	}

	@JvmStatic
	suspend fun scaleBitmap(targetBitmap: Bitmap, requiredThumbWidth: Int): Bitmap {
		return withIOContext {
			if (requiredThumbWidth <= 0 || targetBitmap.width <= 0) {
				return@withIOContext targetBitmap
			}

			val aspectRatio = targetBitmap.height.toFloat() / targetBitmap.width
			val targetHeight = (requiredThumbWidth * aspectRatio).toInt()
			if (targetBitmap.width == requiredThumbWidth &&
				targetBitmap.height == targetHeight
			) return@withIOContext targetBitmap

			return@withIOContext targetBitmap.scale(
				requiredThumbWidth, targetHeight, false
			)
		}
	}

	@JvmStatic
	suspend fun extractAudioAlbumArt(audioFile: File): Bitmap? {
		return withIOContext {
			if (!audioFile.exists()) return@withIOContext null
			val retriever = MediaMetadataRetriever()

			return@withIOContext try {
				retriever.setDataSource(audioFile.absolutePath)
				val embeddedPicture = retriever.embeddedPicture
					?: return@withIOContext null

				val optionsBounds = Options().apply {
					inJustDecodeBounds = true
				}

				decodeByteArray(embeddedPicture, 0,
				                embeddedPicture.size, optionsBounds)

				val maxSize = 412
				val outWidth = optionsBounds.outWidth
				val outHeight = optionsBounds.outHeight
				val maxOf = maxOf(outWidth, outHeight)
				val scale = maxOf(1, maxOf / maxSize)

				val decodeOptions = Options().apply {
					inSampleSize = scale
				}

				decodeByteArray(
					embeddedPicture, 0,
					embeddedPicture.size, decodeOptions
				)
			} catch (error: Exception) {
				logger.e("Error extracting audio album art:", error)
				null
			} finally {
				runCatching {
					retriever.release()
				}
			}
		}
	}

	@JvmStatic
	suspend fun getBitmapFromThumbnailUrl(thumbnailUrl: String?): Bitmap? {
		return withIOContext {
			if (thumbnailUrl.isNullOrEmpty()) return@withIOContext null
			var connection: HttpURLConnection? = null
			var inputStream: InputStream? = null

			return@withIOContext try {
				val url = URL(thumbnailUrl)
				connection = url.openConnection() as? HttpURLConnection
				connection?.apply {
					connectTimeout = 5000
					readTimeout = 5000
					doInput = true
					connect()
				}

				val isValidResponse = connection?.responseCode == HTTP_OK
				val isImageContent = connection?.contentType
					?.contains("image", true) == true

				if (isValidResponse && isImageContent) {
					inputStream = BufferedInputStream(connection.inputStream)
					decodeStream(inputStream)
				} else null
			} catch (error: Exception) {
				logger.e("Error getting bitmap from a url:", error)
				null
			} finally {
				runCatching { inputStream?.close() }
				connection?.disconnect()
			}
		}
	}

	@JvmStatic
	suspend fun saveBitmapToFile(
		bitmapToSave: Bitmap, fileName: String,
		format: CompressFormat = CompressFormat.JPEG,
		quality: Int = 60
	): String? {
		return withIOContext {
			try {
				val modePrivate = MODE_PRIVATE
				val appContext = INSTANCE
				appContext.openFileOutput(fileName, modePrivate).use { outputStream ->
					if (!bitmapToSave.compress(format, quality, outputStream)) {
						return@withIOContext null
					}
				}

				"${appContext.filesDir}/$fileName"
			} catch (error: Throwable) {
				logger.e("Error found while saving bitmap to a file:", error)
				null
			}
		}
	}

	@JvmStatic
	suspend fun getBitmapFromFile(imageFile: File): Bitmap? {
		return withIOContext {
			try {
				if (imageFile.exists() && imageFile.isFile) {
					decodeFile(imageFile.absolutePath)
				} else {
					null
				}
			} catch (error: Exception) {
				logger.e("Error found while getting bitmap from a file:", error)
				return@withIOContext null
			}
		}
	}

	@JvmStatic
	suspend fun isBlackThumbnail(targetImageFile: File?): Boolean {
		return withIOContext {
			if (targetImageFile == null || !targetImageFile.exists()) {
				return@withIOContext false
			}

			val options = Options().apply { inJustDecodeBounds = true }
			decodeFile(targetImageFile.absolutePath, options)

			val maxSize = 64
			val scale = maxOf(1, maxOf(options.outWidth, options.outHeight) / maxSize)
			val decodeOptions = Options().apply { inSampleSize = scale }
			val bitmap = decodeFile(targetImageFile.absolutePath, decodeOptions)
				?: return@withIOContext false

			for (x in 0 until bitmap.width) {
				for (y in 0 until bitmap.height) {
					if (bitmap[x, y] != Color.BLACK) {
						bitmap.recycle()
						return@withIOContext false
					}
				}
			}

			bitmap.recycle()
			return@withIOContext true
		}
	}

	@Suppress("DEPRECATION")
	suspend fun blurBitmap(bitmap: Bitmap, radius: Float = 20f): Bitmap {
		return withIOContext {
			val safeConfig = bitmap.config ?: Bitmap.Config.ARGB_8888
			val rs = RenderScript.create(INSTANCE)

			val input = Allocation.createFromBitmap(rs, bitmap)
			val output = Allocation.createTyped(rs, input.type)

			val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
			script.setRadius(radius.coerceIn(0f, 25f))
			script.setInput(input)
			script.forEach(output)

			val blurred = createBitmap(bitmap.width, bitmap.height, safeConfig)
			output.copyTo(blurred)
			rs.destroy()
			return@withIOContext blurred
		}
	}

	@JvmStatic
	suspend fun TextView.setLeftSideDrawable(drawableResIdRes: Int) {
		withMainContext {
			val drawable = getDrawable(INSTANCE, drawableResIdRes)
				?: return@withMainContext

			val intrinsicWidth = drawable.intrinsicWidth
			val intrinsicHeight = drawable.intrinsicHeight
			drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
			this@setLeftSideDrawable.setCompoundDrawables(drawable, null, null, null)
		}
	}

	@JvmStatic
	suspend fun TextView.setRightSideDrawable(
		drawableResId: Int,
		preserveExistingDrawables: Boolean = false) {
		withMainContext {
			val endIcon = getDrawable(INSTANCE, drawableResId)
				?: return@withMainContext

			val intrinsicWidth = endIcon.intrinsicWidth
			val intrinsicHeight = endIcon.intrinsicHeight
			endIcon.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
			val textView = this@setRightSideDrawable

			if (preserveExistingDrawables) {
				val (left, top, _, bottom) = textView.compoundDrawables
				textView.setCompoundDrawables(left, top, endIcon, bottom)
			} else {
				textView.setCompoundDrawables(null, null, endIcon, null)
			}
		}
	}

	@JvmStatic
	suspend fun View.matchHeightToTopCutout(baseActivity: BaseActivity) {
		withMainContext {
			doOnLayout {
				baseActivity
					.activityCoroutineScope
					.launch { updateCutoutHeight() }
			}
		}
	}

	@JvmStatic
	suspend fun View.updateCutoutHeight() {
		withMainContext {
			val rootWindowInsets = rootWindowInsets
			val cutout = rootWindowInsets?.displayCutout

			if (cutout != null) {
				val cutoutHeight = cutout.boundingRects
					.firstOrNull { it.top == 0 }
					?.height() ?: 0

				val params = layoutParams
				params.height = cutoutHeight
				layoutParams = params
			}
		}
	}

	@JvmStatic
	suspend fun TextView.setTextColorKT(colorResId: Int) {
		withMainContext {
			val color = INSTANCE.getColor(colorResId)
			this@setTextColorKT.setTextColor(color)
		}
	}

	@JvmStatic
	suspend fun Int.dpToPx(): Int {
		return withIOContext {
			val displayMetrics = Resources.getSystem().displayMetrics
			(this@dpToPx * displayMetrics.density).toInt()
		}
	}

	@JvmStatic
	suspend fun changesSystemTheme(activity: BaseActivity) {
		withMainContext {
			val tempFile = File(INSTANCE.filesDir, DARK_MODE_INDICATOR_FIE)
			when (tempFile.exists()) {
				true -> {
					setDefaultNightMode(MODE_NIGHT_YES)
					activity.setDarkSystemBarTheme()
				}

				false -> {
					setDefaultNightMode(MODE_NIGHT_NO)
					activity.setLightSystemBarTheme()
				}
			}
		}
	}

	@JvmStatic
	suspend fun getCurrentDeviceOrientation(activity: Activity): String {
		return withMainContext {
			when (activity.resources.configuration.orientation) {
				Configuration.ORIENTATION_LANDSCAPE -> "landscape"
				Configuration.ORIENTATION_PORTRAIT -> "portrait"
				else -> "undefined"
			}
		}
	}

	@JvmStatic
	suspend fun View.setBounceClick(scaleDown: Float = 0.92f,
	                                duration: Long = 120L,
	                                onClick: (View, Boolean) -> Unit) {
		withMainContext {
			var isPressedInside = false
			setOnClickListener { onClick(it, isPressedInside) }
			setOnTouchListener { v, event ->
				when (event.action) {
					MotionEvent.ACTION_DOWN -> {
						isPressedInside = true
						v.animate()
							.scaleX(scaleDown)
							.scaleY(scaleDown)
							.setDuration(duration)
							.start()
					}

					MotionEvent.ACTION_MOVE -> {
						val inside = event.x in 0f..v.width.toFloat() &&
							event.y in 0f..v.height.toFloat()

						if (!inside && isPressedInside) {
							isPressedInside = false
							v.animate()
								.scaleX(1f)
								.scaleY(1f)
								.setDuration(duration)
								.start()
						}
					}

					MotionEvent.ACTION_UP -> {
						v.animate().scaleX(1f).scaleY(1f)
							.setDuration(duration).withEndAction {
								if (isPressedInside) v.performClick()
							}
							.start()
					}

					MotionEvent.ACTION_CANCEL -> {
						isPressedInside = false
						v.animate().scaleX(1f).scaleY(1f)
							.setDuration(duration).start()
					}
				}
				true
			}
		}
	}

	@JvmStatic
	fun shrinkTextToFitView(textView: TextView?,
	                        text: String, endMatch: String) {
		if (textView == null) return

		val width = textView.width
		val paddingStart = textView.paddingStart
		val paddingEnd = textView.paddingEnd
		val availableWidth = width - paddingStart - paddingEnd

		if (availableWidth <= 0) {
			textView.doOnLayout {
				shrinkTextToFitView(textView, text, endMatch)
			}
			return
		}

		var newText = text
		val paint = Paint(textView.paint)

		try {
			if (newText.endsWith(endMatch, true)) {
				val measureText = paint.measureText(newText)
				while (measureText > availableWidth && newText.length > 4) {
					newText = (if (newText.endsWith(endMatch, true)) {
						newText.dropLast(endMatch.length)
					} else newText.dropLast(1))
				}
			}

			textView.text = newText
		} catch (error: Exception) {
			logger.e("Error shrinking text to fit view:", error)
			textView.text = text
		}
	}
}