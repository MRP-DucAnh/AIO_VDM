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
import lib.files.*
import lib.process.*
import java.io.*
import java.net.*
import java.net.HttpURLConnection.*
import java.text.BreakIterator.*
import java.util.*

object ViewUtility {

	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Recursively traverses a view hierarchy to detach drawables and clear image resources
	 * to mitigate memory leaks and reduce heap pressure.
	 *
	 * This function performs several critical cleanup tasks:
	 * 1. Detaches callbacks from the view's background drawable to prevent the [Drawable]
	 * from holding a reference to the [View].
	 * 2. If the view is an [ImageView], it explicitly nulls the bitmap content to
	 * facilitate immediate garbage collection of large image data.
	 * 3. If the view is a [ViewGroup], it recursively calls itself on all children and
	 * eventually removes all child views (excluding [AdapterView] types like ListView
	 * which manage their own child lifecycles).
	 * * Note: This should ideally be called on the Main thread as it modifies the view hierarchy.
	 *
	 * @param view The root [View] or [ViewGroup] to begin the unbinding process.
	 */
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

	/**
	 * Assigns a single [View.OnClickListener] to multiple views identified by their resource IDs
	 * within the scope of an [Activity].
	 *
	 * This utility function iterates through a provided list of view IDs, attempts to locate
	 * each view within the activity's layout, and applies the listener. It is designed
	 * with null safety to ensure that if the activity or a specific view ID is not found,
	 * the application does not crash.
	 *
	 * @param clickListener The [View.OnClickListener] implementation to be attached to the views.
	 * Pass null to remove existing listeners.
	 * @param activity The [Activity] context containing the target views.
	 * @param ids A variable number of integer resource IDs ([IdRes]) representing the views.
	 */
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

	/**
	 * Sets a common [View.OnClickListener] on multiple [View]s within a parent [View] hierarchy.
	 *
	 * This function provides batch click listener assignment for multiple views within a
	 * specific layout or view hierarchy. More efficient than activity-level assignment when
	 * working with fragment layouts, custom views, or recycled list items. Handles null
	 * views gracefully and applies the same listener behavior across all specified view IDs.
	 *
	 * @param onClickListener The [View.OnClickListener] to assign to all specified views.
	 *                        Can be null to clear existing click listeners from the views.
	 * @param layout The parent [View] containing the target views to receive click listeners.
	 *               All view IDs are searched within this layout's hierarchy.
	 * @param ids A vararg of integer resource IDs ([IdRes]) identifying the views to receive
	 *            the click listener. Views not found in the layout are safely ignored.
	 */
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

	/**
	 * Starts an infinite clockwise rotation animation on the given [view] for loading indicators.
	 *
	 * This function applies a continuous rotating animation to create spinning loader effects,
	 * commonly used for progress indicators or loading states. The animation runs indefinitely
	 * until explicitly stopped.
	 *
	 * @param activity The [Activity] context used to load animation resources.
	 *
	 * @param view The [View] to animate with continuous clockwise rotation.
	 *             If null, no animation is started.
	 */
	@JvmStatic
	suspend fun animateInfiniteRotation(activity: Activity?, view: View?) {
		withMainContext {
			if (activity == null) return@withMainContext
			val animResId = R.anim.anim_rotate_clockwise
			val animation = loadAnimation(activity, animResId)
			view?.startAnimation(animation)
		}
	}

	/**
	 * Programmatically triggers the display of the soft keyboard and requests focus for a specific view.
	 *
	 * This function retrieves the [InputMethodManager] system service and ensures the target
	 * [focusedView] gains focus before attempting to show the input method. This is particularly
	 * useful for improving user experience in search screens or forms where immediate text
	 * entry is expected. The operation is safely wrapped in a Main thread context.
	 *
	 * @param activity The [Activity] context used to access system input services.
	 * @param focusedView The [View] (typically an EditText) that should receive the input focus.
	 */
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

	/**
	 * Programmatically hides the soft keyboard by dismissing the current input session.
	 *
	 * This function uses the [InputMethodManager] to request that the input window associated
	 * with the [focusedView] be hidden. It is a best practice to call this when a user
	 * completes a form, submits a search, or navigates away from an input-heavy screen.
	 * The function includes null safety checks for both the activity and the view.
	 *
	 * @param activity The [Activity] context used to access system input services.
	 * @param focusedView The [View] currently holding the window token from which the
	 * keyboard should be dismissed.
	 */
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

	/**
	 * Adjusts the font size of non-Latin characters (such as CJK, Arabic, or Emoji)
	 * within a [TextView] to maintain visual consistency across mixed-script text.
	 *
	 * Many non-Latin scripts have naturally taller glyphs than the Latin alphabet, which can lead
	 * to uneven line spacing or visual misalignment. This function iterates through the text
	 * using a BreakIterator to identify grapheme clusters. If a cluster contains symbols
	 * outside the Latin script, a [RelativeSizeSpan] is applied to reduce its size by the
	 * specified [reductionFactor]. The heavy text analysis is performed on an IO context,
	 * with the final UI update occurring on the Main thread.
	 *
	 * @param reductionFactor The scale factor to apply to non-Latin symbols (defaults to 0.8f).
	 * @param onDone An optional callback triggered on the Main thread with the final
	 * [Spannable] after processing is finished.
	 */
	@JvmStatic
	suspend fun TextView.normalizeTallSymbols(
		reductionFactor: Float = 0.8f,
		onDone: (Spannable) -> Unit = {}
	) {
		withIOContext {
			val fullText = this@normalizeTallSymbols.text.toString()
			if (fullText.isEmpty()) return@withIOContext

			val spannable = SpannableStringBuilder(fullText)
			val boundaryIterator = getCharacterInstance(Locale.getDefault())
			boundaryIterator.setText(fullText)

			var currentStart = boundaryIterator.first()
			var currentEnd = boundaryIterator.next()

			while (currentEnd != DONE) {
				val cluster = fullText.substring(currentStart, currentEnd)
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
				this@normalizeTallSymbols.text = spannable
				onDone(spannable)
			}
		}
	}

	/**
	 * Extension helper to identify if a [Character.UnicodeScript] belongs to the Latin family.
	 */
	private fun Character.UnicodeScript?.isLatin(): Boolean {
		return this == Character.UnicodeScript.LATIN
	}

	/**
	 * Applies the application's primary theme color as a tint to a [Drawable].
	 *
	 * This function retrieves the color resource defined by `R.color.color_primary` and
	 * applies it to the target drawable using [DrawableCompat]. This ensures consistent
	 * tinting across different Android API levels. The operation is executed on the
	 * Main thread to ensure safe interaction with UI resources.
	 *
	 * @param targetDrawable The [Drawable] to be tinted. If null, the function exits silently.
	 */
	@JvmStatic
	suspend fun tintDrawableWithPrimaryColor(targetDrawable: Drawable?) {
		withMainContext {
			if (targetDrawable == null) return@withMainContext
			val tintColor = getColor(INSTANCE, R.color.color_primary)
			DrawableCompat.setTint(targetDrawable, tintColor)
		}
	}

	/**
	 * Applies the application's secondary theme color as a tint to a [Drawable].
	 *
	 * Similar to the primary tint function, this retrieves the color resource defined
	 * by `R.color.color_secondary`. It is ideal for accenting secondary UI elements
	 * or icons while maintaining theme consistency.
	 *
	 * @param targetDrawable The [Drawable] to be tinted. If null, the function exits silently.
	 */
	@JvmStatic
	suspend fun tintDrawableWithSecondaryColor(targetDrawable: Drawable?) {
		withMainContext {
			if (targetDrawable == null) return@withMainContext
			val tintColor = getColor(INSTANCE, R.color.color_secondary)
			DrawableCompat.setTint(targetDrawable, tintColor)
		}
	}

	/**
	 * Applies a specific color resource as a tint to a [Drawable].
	 *
	 * This utility provides a flexible way to tint drawables with any provided color
	 * resource ID. It handles the color retrieval and tinting logic on the Main
	 * thread, ensuring that the visual change is synchronized with the UI.
	 *
	 * @param targetDrawable The [Drawable] to be tinted. If null, the function exits silently.
	 * @param colorResId The integer resource ID of the color to be applied (e.g., R.color.red).
	 */
	@JvmStatic
	suspend fun tintDrawableWithProvidedColor(targetDrawable: Drawable?, colorResId: Int) {
		withMainContext {
			if (targetDrawable == null) return@withMainContext
			val tintColor = getColor(INSTANCE, colorResId)
			DrawableCompat.setTint(targetDrawable, tintColor)
		}
	}

	/**
	 * Detects whether the soft keyboard (Input Method Editor) is currently visible on the screen.
	 *
	 * Since Android doesn't provide a direct "isKeyboardVisible" listener, this function
	 * uses a layout-based heuristic. It compares the height of the activity's root content
	 * area with the total screen height. If the difference (the occluded area) exceeds
	 * a 100px threshold, it is assumed that the soft keyboard is currently occupying
	 * that space.
	 *
	 * @param activity The [Activity] context used to access the window's root view and display frame.
	 * @return True if the keyboard height is calculated to be over 100px; false if the activity
	 * is null, the view hierarchy is inaccessible, or the keyboard is hidden.
	 */
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

	/**
	 * Toggles the visibility of a [View] between [View.VISIBLE] and [View.GONE].
	 *
	 * This utility function switches the current visibility state of the [targetView].
	 * If [shouldAnimate] is true, it performs a smooth alpha transition:
	 * - **Hiding:** Animates alpha to 0 and sets visibility to [View.GONE] upon completion.
	 * - **Showing:** Sets visibility to [View.VISIBLE] with 0 alpha and animates to 1.0.
	 *
	 * If animation is disabled, the visibility is swapped immediately. The entire
	 * operation is executed on the Main thread to ensure thread safety with the
	 * Android View system.
	 *
	 * @param targetView The [View] to toggle.
	 * @param shouldAnimate If true, applies a fade-in or fade-out animation during the toggle.
	 * @param animTimeout The duration of the animation in milliseconds (defaults to 300ms).
	 */
	@JvmStatic
	suspend fun toggleViewVisibility(
		targetView: View,
		shouldAnimate: Boolean = false,
		animTimeout: Long = 300
	) {
		withMainContext {
			if (shouldAnimate) {
				if (targetView.isVisible) {
					targetView.animate().alpha(0f)
						.setDuration(animTimeout)
						.withEndAction { targetView.visibility = GONE }

				} else {
					targetView.alpha = 0f
					targetView.visibility = VISIBLE
					targetView.animate().alpha(1f).setDuration(animTimeout)
				}
			} else {
				targetView.visibility = if (targetView.isVisible) GONE
				else VISIBLE
			}
		}
	}

	/**
	 * Hides a [View] with an optional fade-out animation and customizable final visibility state.
	 *
	 * This function handles the transition of a view from visible to hidden. If animation
	 * is enabled, it smoothly reduces the view's alpha to 0 before applying the final
	 * visibility constant ([View.GONE] or [View.INVISIBLE]). It includes an early
	 * return check to avoid redundant operations if the view is already hidden.
	 *
	 * @param targetView The [View] to be hidden.
	 * @param visibility The final visibility state (defaults to [View.GONE]).
	 * @param shouldAnimate If true, performs a fade-out transition over the specified timeout.
	 * @param animTimeout The duration of the fade-out animation in milliseconds (defaults to 500ms).
	 */
	@JvmStatic
	suspend fun hideView(
		targetView: View?, visibility: Int = GONE,
		shouldAnimate: Boolean = false, animTimeout: Long = 500
	) {
		return withMainContext {
			if (targetView == null) return@withMainContext
			if (!targetView.isVisible) return@withMainContext

			if (shouldAnimate) {
				targetView.animate().alpha(0f)
					.setDuration(animTimeout)
				targetView.visibility = visibility
			} else {
				targetView.visibility = visibility
			}
		}
	}

	/**
	 * Shows a [View] with an optional fade-in animation and safety checks.
	 *
	 * This function makes a view visible to the user. When [shouldAnimate] is true,
	 * it initializes the view with 0 alpha (fully transparent), sets visibility to
	 * [View.VISIBLE], and then animates the alpha to 1.0. It intelligently returns
	 * early if the view is already visible to prevent resetting active animations
	 * or visibility states.
	 *
	 * @param targetView The [View] to be displayed.
	 * @param shouldAnimate If true, performs a smooth fade-in transition.
	 * @param animTimeout The duration of the fade-in animation in milliseconds
	 *                    (defaults to 500ms).
	 */
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
				targetView.animate().alpha(1f).setDuration(animTimeout)
			} else {
				targetView.visibility = VISIBLE
			}
		}
	}

	/**
	 * Retrieves the vertical height of the display cutout (notch) at the top of the screen.
	 *
	 * This function accesses the [WindowInsets] from the activity's decor view to identify
	 * the safe inset area occupied by hardware obstructions like camera notches or sensors.
	 * This measurement is crucial for calculating the "safe area" in edge-to-edge layouts,
	 * ensuring UI elements are not clipped or hidden.
	 *
	 * @param activity The [Activity] context used to access window-level UI information.
	 * @return The height of the top cutout in pixels, or 0 if no cutout exists or
	 * the activity/insets are unavailable.
	 */
	@JvmStatic
	suspend fun getTopCutoutHeight(activity: Activity?): Int {
		return withMainContext {
			if (activity == null) return@withMainContext 0
			val windowInsets = activity.window?.decorView?.rootWindowInsets
			val displayCutout = windowInsets?.displayCutout
			return@withMainContext displayCutout?.safeInsetTop ?: 0
		}
	}

	/**
	 * Adjusts a [View]'s top margin to prevent it from being obscured by a display cutout (notch).
	 *
	 * This function retrieves the height of the device's display cutout (if present) and
	 * updates the [ViewGroup.MarginLayoutParams] of the target view accordingly. This is
	 * essential for edge-to-edge layouts where content needs to be manually offset to
	 * avoid being hidden behind camera notches or "island" cutouts.
	 * * The layout modification is executed on the Main thread to ensure immediate UI
	 * synchronization and layout invalidation.
	 *
	 * @param view The [View] that requires a margin adjustment. Must have or support
	 * [ViewGroup.MarginLayoutParams].
	 * @param activity The [Activity] context used to access the window's [WindowInsets]
	 * and [DisplayCutout] data.
	 */
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

	/**
	 * Measures the intrinsic size (width and height) of a [view] without layout constraints.
	 *
	 * This function performs measurement with UNSPECIFIED spec to determine the view's
	 * natural dimensions based on its content and layout parameters. Useful for calculating
	 * required space before actual layout, or for custom layout implementations.
	 *
	 * @param view The [View] to measure for intrinsic width and height dimensions.
	 * @return An [IntArray] containing measured width at index 0 and height at index 1 in pixels,
	 *         or the view's current dimensions if measurement cannot be performed.
	 */
	@JvmStatic
	suspend fun measureViewSize(view: View): IntArray {
		return withMainContext {
			view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
			return@withMainContext intArrayOf(view.measuredWidth, view.measuredHeight)
		}
	}

	/**
	 * Animates a [View]'s visibility change using a smooth alpha (fade) transition.
	 *
	 * This function intelligently manages the [View.VISIBLE], [View.INVISIBLE], or [View.GONE]
	 * states. When showing the view, it sets the visibility to VISIBLE at the start of the
	 * animation so the fade-in is visible. When hiding the view, it waits for the animation
	 * to complete before setting the final visibility state to ensure a smooth exit.
	 *
	 * @param targetView The [View] to animate.
	 * @param visibility The target visibility constant ([View.VISIBLE],
	 *                   [View.INVISIBLE], or [View.GONE]).
	 * @param duration The duration of the fade animation in milliseconds.
	 */
	@JvmStatic
	suspend fun animateViewVisibility(targetView: View, visibility: Int, duration: Int) {
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

	/**
	 * Starts a continuous "breathing" or pulsing fade-in/fade-out animation on a [View].
	 *
	 * This function applies an [AlphaAnimation] that infinitely repeats and reverses,
	 * creating a glowing or pulsing effect. It includes a safety check to prevent
	 * stacking multiple animations if one is already running and hasn't ended.
	 * The animation runs from 0% to 100% opacity over 500ms per cycle.
	 *
	 * @param targetView The [View] to apply the pulse animation to. If null, the function
	 * exits silently.
	 */
	@JvmStatic
	suspend fun animateFadInOutAnim(targetView: View?) {
		withMainContext {
			if (targetView == null) return@withMainContext
			val current = targetView.animation
			if (current != null && !current.hasEnded()) {
				return@withMainContext
			}

			val anim = AlphaAnimation(0f, 1f).apply {
				duration = 500
				repeatCount = Animation.INFINITE
				repeatMode = Animation.REVERSE
			}

			targetView.startAnimation(anim)
		}
	}

	/**
	 * Cancels and clears any active [Animation] currently running on the specified [View].
	 *
	 * This function is executed on the Main thread to safely interact with the view hierarchy.
	 * It is essential for stopping infinite animations (like loading spinners) before
	 * a view is detached or reused.
	 *
	 * @param view The [View] whose animation should be cleared. If null, the function
	 * exits silently.
	 */
	@JvmStatic
	suspend fun closeAnyAnimation(view: View?) = withMainContext {
		view?.clearAnimation()
	}

	/**
	 * Applies a smooth fade-out animation to a [View] using an [AlphaAnimation].
	 *
	 * This function transitions the view's opacity from fully opaque (1.0) to fully
	 * transparent (0.0). It uses [Animation.AnimationListener] to notify the caller
	 * when the animation has completed, which is useful for triggering secondary
	 * actions like changing visibility to GONE or removing the view from a parent.
	 *
	 * @param view The [View] to be animated.
	 * @param duration The length of the animation in milliseconds (defaults to 300ms).
	 * @param onAnimationEnd An optional lambda to execute once the fade-out completes.
	 */
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

	/**
	 * Downloads an image from a URL, processes orientation, and loads it into an [ImageView].
	 *
	 * This function handles the network request on an IO context. It includes logic to
	 * detect if an image is in portrait mode and automatically rotates it 90 degrees
	 * for consistent horizontal thumbnail display. It uses [Glide] for efficient memory
	 * management and bitmap caching when rendering to the target view.
	 *
	 * @param thumbnailUrl The remote web address of the image.
	 * @param targetImageView The [ImageView] where the final bitmap will be displayed.
	 * @param placeHolderDrawableId An optional resource ID for a drawable to display if
	 * the download fails or an error occurs.
	 */
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
				withMainContext {
					logger.e("Error while loading thumbnail from a remote url:", error)
					if (placeHolderDrawableId != null) {
						targetImageView.setImageResource(placeHolderDrawableId)
					}
				}
			} finally {
				try {
					input?.close()
				} catch (e: Exception) {
				}
			}
		}
	}

	/**
	 * Rotates a [Bitmap] by a specified angle using a transformation matrix.
	 *
	 * This function utilizes a [Matrix] to perform the rotation. It is executed on
	 * [Dispatchers.Default] as bitmap manipulation is a CPU-intensive task. To prevent
	 * memory leaks, the original bitmap is automatically recycled if it is no longer
	 * needed and hasn't already been freed.
	 *
	 * @param bitmap The source [Bitmap] to rotate.
	 * @param angle The rotation angle in degrees clockwise.
	 * @return A new rotated [Bitmap] instance.
	 */
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

	/**
	 * Generates a representative thumbnail for a given [File] based on its media type.
	 *
	 * This function intelligently identifies the file type (Audio, Image, APK, or Video)
	 * and applies the appropriate extraction method:
	 * - **Audio:** Extracts embedded album art.
	 * - **Image:** Decodes and scales the image file.
	 * - **APK:** Extracts the application icon.
	 * - **Video:** Captures a frame at the 5-second mark (or the first available frame).
	 *
	 * If a [fallbackThumbUrl] is provided and primary extraction fails, it attempts to
	 * download the thumbnail. The resulting bitmap is scaled to match the [requiredThumbWidth]
	 * while maintaining the original aspect ratio.
	 *
	 * @param targetFile The file for which to generate a thumbnail.
	 * @param fallbackThumbUrl An optional URL to use if local extraction is unsuccessful.
	 * @param requiredThumbWidth The target width for the final thumbnail in pixels.
	 * @return A scaled [Bitmap] representing the file, or null if no thumbnail could be generated.
	 */
	@JvmStatic
	suspend fun getThumbnailFromFile(
		targetFile: File, fallbackThumbUrl: String? = null,
		requiredThumbWidth: Int
	): Bitmap? {
		return withIOContext {
			if (FileSystemUtility.isAudioByName(targetFile.name)) {
				extractAudioAlbumArt(targetFile)?.let { return@withIOContext it }
			} else if (FileSystemUtility.isImageByName(targetFile.name)) {
				getBitmapFromFile(imageFile = targetFile)?.let {
					return@withIOContext scaleBitmap(it, requiredThumbWidth)
				}
			} else if (targetFile.name.endsWith(".apk", true)) {
				var apkBitmap: Bitmap? = null
				getApkThumbnail(targetFile, onApkIconFound = { bmp -> apkBitmap = bmp; bmp })
				if (apkBitmap != null) return@withIOContext apkBitmap
			}

			val retriever = MediaMetadataRetriever()
			try {
				var originalBitmap: Bitmap? = null
				if (!fallbackThumbUrl.isNullOrEmpty()) {
					originalBitmap = getBitmapFromThumbnailUrl(fallbackThumbUrl)
				}

				if (originalBitmap == null) {
					retriever.setDataSource(targetFile.absolutePath)
					originalBitmap = retriever
						.getFrameAtTime(5_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
						?: retriever.frameAtTime
				}

				originalBitmap?.let {
					if (it.width <= 0) return@withIOContext null
					val aspectRatio = it.height.toFloat() / it.width
					val targetHeight = (requiredThumbWidth * aspectRatio).toInt()
					return@withIOContext it.scale(requiredThumbWidth, targetHeight, false)
				}
			} catch (error: Exception) {
				logger.e("Error while retrieving thumbnail from a file:", error)
			} finally {
				retriever.release()
			}

			return@withIOContext null
		}
	}

	/**
	 * Extracts and displays the application icon from an uninstalled APK file.
	 *
	 * This function uses the [PackageManager] to parse the archive info of a local APK file.
	 * Once the application info is retrieved, it loads the associated icon and updates the
	 * provided [ImageView] on the Main thread. It also provides a callback to return the
	 * icon as a [Bitmap] for further processing. The extraction process is performed
	 * within an IO context to avoid blocking the UI during package parsing.
	 *
	 * @param apkFile The physical .apk file on the device storage.
	 * @param imageViewHolder An optional [ImageView] to display the extracted icon.
	 * @param defaultThumbDrawable A fallback drawable to display if the icon extraction fails.
	 * @param onApkIconFound An optional callback triggered with the icon's [Bitmap] representation
	 * once successfully extracted.
	 * @return True if the APK is valid and the icon was successfully loaded; false otherwise.
	 */
	@JvmStatic
	suspend fun getApkThumbnail(
		apkFile: File, imageViewHolder: ImageView? = null,
		defaultThumbDrawable: Drawable? = null,
		onApkIconFound: ((Bitmap) -> Bitmap)? = null
	): Boolean {
		return withIOContext {
			val apkExtension = ".apk".lowercase(Locale.ROOT)
			if (!apkFile.exists() || !apkFile.name.endsWith(apkExtension)) {
				withMainContext {
					imageViewHolder?.apply {
						scaleType = ImageView.ScaleType.CENTER_INSIDE
					}
				}
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
						imageViewHolder?.scaleType = ImageView.ScaleType.CENTER_INSIDE
						imageViewHolder?.setPadding(0, 0, 0, 0)
						val bitmap = drawableToBitmap(drawableIcon)
						if (bitmap != null) {
							onApkIconFound?.invoke(bitmap)
						}
					}

					return@withIOContext true
				}

				withMainContext {
					imageViewHolder
						?.setImageDrawable(defaultThumbDrawable)
				}

				false
			} catch (error: Exception) {
				logger.e("Error found while extracting app icon from apk file:", error)
				withMainContext {
					imageViewHolder?.apply {
						scaleType = ImageView.ScaleType.FIT_CENTER
						setPadding(0, 0, 0, 0)
						setImageDrawable(defaultThumbDrawable)
					}
				}
				false
			}
		}
	}

	/**
	 * Converts a [Drawable] object into a [Bitmap] representation.
	 *
	 * This function handles different types of drawables by checking if the source
	 * is already a [BitmapDrawable]. If not, it creates a new empty bitmap based
	 * on the drawable's intrinsic dimensions and renders the drawable onto a
	 * [Canvas]. The operation is performed within an IO context to handle
	 * potential allocation overhead safely.
	 *
	 * @param drawable The source [Drawable] to be converted.
	 * @return A [Bitmap] containing the visual content of the drawable; null if
	 * an error occurs during bitmap creation or canvas drawing.
	 */
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
				logger.e("Error found while converting drawable to a bitmap:", error)
				null
			}
		}
	}

	/**
	 * Resizes a [Bitmap] to a specific width while maintaining its original aspect ratio.
	 *
	 * This function calculates the target height proportionally based on the
	 * [requiredThumbWidth]. It includes sanity checks to avoid redundant scaling
	 * if the bitmap is already at the target dimensions or if invalid parameters
	 * are provided. The scaling process is offloaded to an IO context.
	 *
	 * @param targetBitmap The source [Bitmap] to be scaled.
	 * @param requiredThumbWidth The desired width for the resulting bitmap in pixels.
	 * @return A scaled version of the [Bitmap], or the original instance if
	 * scaling was unnecessary or parameters were invalid.
	 */
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

	/**
	 * Extracts the embedded album artwork from an audio file.
	 *
	 * This function utilizes [MediaMetadataRetriever] to access the ID3 tags or metadata
	 * of the specified file. To optimize memory consumption, it first decodes the image
	 * bounds and then applies a scaling factor to ensure the resulting [Bitmap] does
	 * not exceed a maximum dimension of 412 pixels. The operation is offloaded to an
	 * IO context to handle the overhead of media parsing safely.
	 *
	 * @param audioFile The physical file object pointing to the audio track.
	 * @return A downsampled [Bitmap] of the album art if available; null if no
	 * artwork exists, the file is missing, or an error occurs during extraction.
	 */
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

				decodeByteArray(
					embeddedPicture, 0,
					embeddedPicture.size, optionsBounds
				)

				val maxSize = 412
				val scale = maxOf(
					1, maxOf(
						optionsBounds.outWidth,
						optionsBounds.outHeight
					) / maxSize
				)

				val decodeOptions = Options().apply {
					inSampleSize = scale
				}

				decodeByteArray(
					embeddedPicture, 0,
					embeddedPicture.size, decodeOptions
				)
			} catch (error: Exception) {
				logger.e("Error found while extracting audio album art:", error)
				null
			} finally {
				retriever.release()
			}
		}
	}

	/**
	 * Downloads and decodes a [Bitmap] from a remote URL.
	 *
	 * This function establishes a network connection to the provided URL and attempts
	 * to decode the resulting input stream into a usable Bitmap. It performs validation
	 * on the response code and content type to ensure only valid image data is processed.
	 * The operation is executed within an IO context to prevent blocking the Main thread,
	 * with built-in timeouts and proper resource cleanup in the finally block.
	 *
	 * @param thumbnailUrl The web URL of the image to be downloaded.
	 * @return A [Bitmap] object if the download and decoding were successful; null
	 * if the URL is empty, the connection fails, the content is not an image, or
	 * an error occurs during the process.
	 */
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

				if (connection?.responseCode == HTTP_OK &&
					connection.contentType?.contains("image", true) == true
				) {
					inputStream = BufferedInputStream(connection.inputStream)
					decodeStream(inputStream)
				} else null
			} catch (error: Exception) {
				logger.e("Error found while getting thumbnail from a remote url:", error)
				null
			} finally {
				try {
					inputStream?.close()
				} catch (_: IOException) {
				}
				connection?.disconnect()
			}
		}
	}

	/**
	 * Persists a [Bitmap] to the application's internal storage.
	 *
	 * This function compresses the provided bitmap into the specified format and saves
	 * it to a file within the internal private storage directory. It utilizes the
	 * "use" block for automatic stream management and returns the absolute path to
	 * the saved file upon success. The task is offloaded to an IO context to handle
	 * file system latency safely.
	 *
	 * @param bitmapToSave The [Bitmap] instance to be written to disk.
	 * @param fileName The desired name for the file (e.g., "thumbnail_1.jpg").
	 * @param format The compression algorithm to use (defaults to JPEG).
	 * @param quality Hint to the compressor, 0-100. 0 meaning compress for small size,
	 * 100 meaning compress for max quality.
	 * @return The absolute file system path of the saved image, or null if compression
	 * or file I/O failed.
	 */
	@JvmStatic
	suspend fun saveBitmapToFile(
		bitmapToSave: Bitmap,
		fileName: String,
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

	/**
	 * Decodes a [Bitmap] from a physical file on the device storage.
	 *
	 * This function performs safety checks to verify that the file exists and is indeed
	 * a file before attempting to decode it. The decoding process is executed within
	 * an IO context to ensure that heavy file system operations do not interfere with
	 * the UI thread's performance.
	 *
	 * @param imageFile The [File] object representing the image to be loaded.
	 * @return A [Bitmap] object if the decoding is successful; null if the file
	 * does not exist, is not a valid file, or if a decoding error occurs.
	 */
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

	/**
	 * Analyzes an image file to determine if it consists entirely of black pixels.
	 *
	 * This utility is used to detect corrupted or empty video thumbnails. To optimize
	 * performance and memory usage, the function decodes a downsampled (64px max)
	 * version of the image rather than the full-resolution file. The pixel-by-pixel
	 * analysis is performed on an IO context to avoid blocking the main thread.
	 *
	 * @param targetImageFile The image file to be analyzed.
	 * @return True if the file exists and every pixel in the downsampled bitmap is
	 * exactly [Color.BLACK], false otherwise.
	 */
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

	/**
	 * Applies a Gaussian blur effect to a [Bitmap] using the RenderScript framework.
	 *
	 * This function handles the complex allocation and script execution required for hardware-accelerated
	 * image processing. It processes the transformation on an IO context to avoid blocking the UI thread
	 * during heavy pixel manipulation.
	 *
	 * @param bitmap The source image to be blurred.
	 * @param radius The blur intensity, restricted by system limits to a range between 0f and 25f.
	 * Defaults to 20f.
	 * @return A new [Bitmap] instance containing the blurred version of the original image.
	 */
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

	/**
	 * Assigns a drawable resource to the left (start) side of the [TextView].
	 *
	 * This extension function retrieves the specified drawable, maintains its intrinsic
	 * dimensions, and sets it as the primary left compound drawable. It ensures all
	 * UI operations are handled on the Main thread for immediate visual consistency.
	 *
	 * @receiver The [TextView] to be updated.
	 * @param drawableResIdRes The resource ID of the drawable to place on the left.
	 */
	@JvmStatic
	suspend fun TextView.setLeftSideDrawable(drawableResIdRes: Int) {
		withMainContext {
			val drawable = getDrawable(INSTANCE, drawableResIdRes)
			drawable?.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
			this@setLeftSideDrawable.setCompoundDrawables(drawable, null, null, null)
		}
	}

	/**
	 * Assigns a drawable resource to the right (end) side of the [TextView].
	 *
	 * This function allows for the placement of a drawable on the right side of the text,
	 * with an option to either overwrite all existing compound drawables or preserve
	 * those currently assigned to the left, top, or bottom positions.
	 *
	 * @receiver The [TextView] to be updated.
	 * @param drawableResId The resource ID of the drawable to place on the right.
	 * @param preserveExistingDrawables If true, currently set drawables in other positions
	 * will remain; otherwise, they are cleared.
	 */
	@JvmStatic
	suspend fun TextView.setRightSideDrawable(
		drawableResId: Int,
		preserveExistingDrawables: Boolean = false
	) {
		withMainContext {
			val newDrawable = getDrawable(INSTANCE, drawableResId)
			newDrawable?.setBounds(
				0, 0, newDrawable.intrinsicWidth,
				newDrawable.intrinsicHeight
			)

			val textView = this@setRightSideDrawable
			if (preserveExistingDrawables) {
				val (left, top, _, bottom) = textView.compoundDrawables
				textView.setCompoundDrawables(left, top, newDrawable, bottom)
			} else {
				textView.setCompoundDrawables(null, null, newDrawable, null)
			}
		}
	}

	/**
	 * Automatically adjusts the height of a [View] to match the dimensions of the system's
	 * top display cutout (notch).
	 *
	 * This extension function waits for the view to be laid out before triggering the
	 * height adjustment. It is particularly useful for creating background fillers or
	 * status bar backgrounds that need to extend precisely into the cutout area on
	 * edge-to-edge displays.
	 *
	 * @receiver The [View] whose height will be synchronized with the cutout.
	 * @param baseActivity The host activity providing the coroutine scope for the update logic.
	 */
	@JvmStatic
	suspend fun View.matchHeightToTopCutout(baseActivity: BaseActivity) {
		withMainContext {
			doOnLayout {
				baseActivity.activityCoroutineScope.launch {
					updateCutoutHeight()
				}
			}
		}
	}

	/**
	 * Queries the system window insets to calculate and apply the height of the
	 * top display cutout to the [View].
	 *
	 * This method identifies the specific bounding rectangle of the display cutout
	 * that is positioned at the top of the screen. If a cutout is detected, the view's
	 * [ViewGroup.layoutParams] are updated to match that height exactly.
	 *
	 * @receiver The [View] to be resized.
	 */
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

	/**
	 * Sets the text color of a [TextView] using a color resource ID.
	 *
	 * This extension function simplifies the process of applying localized color resources
	 * by handling the context-based color retrieval through the application instance.
	 * It ensures the UI update is safely executed on the Main thread.
	 *
	 * @receiver The [TextView] whose color will be updated.
	 * @param colorResId The resource ID of the color to be applied.
	 */
	@JvmStatic
	suspend fun TextView.setTextColorKT(colorResId: Int) {
		withMainContext {
			this@setTextColorKT.setTextColor(INSTANCE.getColor(colorResId))
		}
	}

	/**
	 * Converts a density-independent pixel (dp) value to a physical pixel (px) value.
	 *
	 * This extension function leverages the system-wide display metrics to ensure
	 * consistent UI scaling across devices with varying screen densities. It performs
	 * the calculation within an IO context to maintain Main thread responsiveness,
	 * though the underlying resource access is lightweight.
	 *
	 * @receiver The integer value in dp to be converted.
	 * @return The equivalent pixel value as an [Int], rounded down.
	 */
	@JvmStatic
	suspend fun Int.dpToPx(): Int {
		return withIOContext {
			(this@dpToPx * Resources.getSystem().displayMetrics.density).toInt()
		}
	}

	/**
	 * Dynamically updates the application's visual theme and system bar aesthetics
	 * based on the presence of a dark mode indicator file.
	 *
	 * This method acts as the primary orchestrator for theme switching. It synchronizes
	 * the AppCompat delegate's night mode setting with the system's status and
	 * navigation bar themes to ensure a cohesive visual experience.
	 *
	 * @param activity The [BaseActivity] instance used to access system UI controllers.
	 */
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

	/**
	 * Retrieves the current screen orientation of the specified [Activity].
	 *
	 * This method queries the device's system configuration to determine if the
	 * UI is currently rendered in portrait or landscape mode. It is designed
	 * as a suspending function to ensure the orientation check is safely
	 * synchronized with the Main thread, preventing potential race conditions
	 * during configuration changes.
	 *
	 * Logic Flow:
	 * 1. Switches to [withMainContext] to safely access [Resources].
	 * 2. Evaluates the [Configuration.orientation] integer against system constants.
	 * 3. Returns a normalized string representation: "landscape", "portrait", or "undefined".
	 *
	 * @param activity The host [Activity] whose resources will be queried.
	 * @return A [String] describing the current orientation.
	 */
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

	/**
	 * Attaches a "bounce" click effect to a [View].
	 *
	 * When the user presses down on the view, it animates to a slightly smaller size.
	 * When the finger is lifted (`ACTION_UP`), the view animates back to its original size
	 * and then triggers the provided `onClick` lambda. The use of an [OvershootInterpolator]
	 * gives the animation a slight "bounce" or "overshoot" effect, making the interaction feel more dynamic.
	 *
	 * If the touch event is canceled (`ACTION_CANCEL`), the view simply animates back to its
	 * original size without triggering the click action.
	 *
	 * This replaces the standard `setOnClickListener` and should be used instead to achieve the
	 * bounce effect.
	 *
	 * @receiver The [View] to which the bounce click listener will be attached.
	 * @param scaleDown The target scale for the view when pressed down. A value of `0.92f` means
	 *                  the view will shrink to 92% of its original size. Defaults to `0.92f`.
	 * @param duration The duration of the scale-down and scale-up animations in milliseconds.
	 *                 Defaults to `120L`.
	 * @param onClick A high-order function to be executed when the click action is completed
	 *                (i.e., on `ACTION_UP`).
	 */
	@JvmStatic
	suspend fun View.setBounceClick(
		scaleDown: Float = 0.92f,
		duration: Long = 120L,
		onClick: (View, Boolean) -> Unit
	) {
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

	/**
	 * Dynamically reduces the length of a string to ensure it fits within the horizontal
	 * constraints of a [TextView] without triggering system ellipses or clipping.
	 *
	 * This utility is particularly useful for UI elements like file paths or titles where
	 * a specific suffix (like a file extension) should be preserved or handled specifically
	 * during the reduction process.
	 *
	 * @param textView The target [TextView] where the text will be rendered.
	 * @param text The original string to be fitted.
	 * @param endMatch The specific suffix string to look for and remove during the
	 * shrinking process.
	 */
	@JvmStatic
	fun shrinkTextToFitView(textView: TextView?, text: String, endMatch: String) {
		if (textView == null) return
		val availableWidth = textView.width - textView.paddingStart - textView.paddingEnd
		if (availableWidth <= 0) {
			textView.doOnLayout {
				shrinkTextToFitView(textView, text, endMatch)
			}
			return
		}

		var newText = text
		val paint = Paint(textView.paint)

		try {
			if (newText.endsWith(endMatch, ignoreCase = true)) {
				while (paint.measureText(newText) > availableWidth && newText.length > 4) {
					newText = if (newText.endsWith(endMatch, ignoreCase = true)) {
						newText.dropLast(endMatch.length)
					} else {
						newText.dropLast(1)
					}
				}
			}

			textView.text = newText
		} catch (error: Exception) {
			textView.text = text
		}
	}
}