package lib.ui;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.applyDimension;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.DefaultTimeBar;

/**
 * A customized version of {@link DefaultTimeBar} that implements rounded corner clipping
 * for its visual components.
 * * This class extends the standard media controller time bar to provide a more refined,
 * modern aesthetic by applying a geometric clip path to its drawing surface. It is marked
 * with {@link UnstableApi} as it relies on internal ExoPlayer/Media3 UI components which
 * may change in future releases.
 * * The rounding logic is typically handled by intercepting {@code onSizeChanged} to
 * define the clipping bounds and {@code onDraw} to enforce the rounded boundary
 * before the superclass renders the progress, scrubber, and markers.
 */
@UnstableApi
public class RoundedTimeBar extends DefaultTimeBar {
	/**
	 * Configuration for the paint used to render rounded elements.
	 * Initialized with the {@link Paint#ANTI_ALIAS_FLAG} to ensure smooth edges
	 * during drawing operations on the {@link Canvas}.
	 */
	private final Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

	/**
	 * The geometric path used to define the clipping region for rounded corners.
	 * This path is calculated once per size change to optimize the {@code onDraw} cycle.
	 */
	private final Path clipPath = new Path();

	/**
	 * A mutable rectangle used to hold the bounds of the view during path calculations.
	 * Reusing a single {@link RectF} instance prevents frequent object allocations
	 * and reduces Garbage Collection overhead during layout passes.
	 */
	private final RectF rectF = new RectF();

	/**
	 * The radius used for the rounded corners, calculated in pixels.
	 * This value is derived from a density-independent (dp) unit to ensure
	 * visual consistency across devices with different screen densities.
	 */
	private final float cornerRadius;

	/**
	 * Constructs a new {@code RoundedTimeBar} and initializes the corner radius.
	 * * This constructor extracts the display metrics to convert a baseline of 10dp
	 * into the appropriate pixel value for the current device.
	 *
	 * @param context The {@link Context} the view is running in.
	 * @param attrs   The attributes of the XML tag that is inflating the view.
	 */
	public RoundedTimeBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
		cornerRadius = applyDimension(COMPLEX_UNIT_DIP, 10, displayMetrics);
	}

	/**
	 * Callback invoked when the size of this view has changed.
	 * <p>
	 * This implementation updates the internal [RectF] to match the new dimensions and
	 * regenerates the [Path] used for clipping. By updating the path here rather than in
	 * [onDraw], we ensure that expensive path calculations are only performed when
	 * the layout actually changes, optimizing rendering performance.
	 *
	 * @param w    Current width of this view.
	 * @param h    Current height of this view.
	 * @param oldw Old width of this view.
	 * @param oldh Old height of this view.
	 */
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		// Update the bounding rectangle to the new dimensions
		rectF.set(0, 0, w, h);

		// Reset and re-calculate the rounded clipping path based on the updated radius
		clipPath.reset();
		clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW);
	}

	/**
	 * Renders the view's content while applying a rounded corner clip.
	 * <p>
	 * This function handles the drawing cycle by:
	 * 1. Saving the current canvas state.
	 * 2. Applying the pre-calculated [clipPath] to the canvas, ensuring all content
	 * (including children or backgrounds) is constrained within the rounded bounds.
	 * 3. Invoking the superclass draw logic to render the view's actual content.
	 * 4. Restoring the canvas to its original state to avoid affecting subsequent
	 * drawing operations.
	 *
	 * @param canvas The [Canvas] on which the background and content will be drawn.
	 */
	@Override
	public void onDraw(Canvas canvas) {
		canvas.save();
		canvas.clipPath(clipPath);
		super.onDraw(canvas);
		canvas.restore();
	}

	/**
	 * Retrieves the {@link Paint} object used for rendering rounded UI elements.
	 * * This instance contains the styling configurations such as color, antialiasing,
	 * and stroke settings applied when drawing rounded rectangles or circles
	 * within the view.
	 *
	 * @return The current {@link Paint} instance used for rounded drawing operations.
	 */
	public Paint getRoundPaint() {
		return roundPaint;
	}
}