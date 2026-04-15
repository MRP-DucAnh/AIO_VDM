package lib.ui.builders;

import static android.view.LayoutInflater.from;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aio.R;

import java.lang.ref.WeakReference;

import app.core.bases.interfaces.BaseActivityInf;
import lib.process.LogHelperUtils;

public class DialogBuilder {

	private final LogHelperUtils logger = LogHelperUtils.from(getClass());
	private final WeakReference<BaseActivityInf> weakReferenceBaseActivity;
	private WeakReference<View> weakCustomView;
	private AlertDialog dialog;

	public DialogBuilder(@Nullable BaseActivityInf baseActivityInf) {
		this.weakReferenceBaseActivity = new WeakReference<>(baseActivityInf);
	}

	public void show() {
		try {
			if (dialog == null) dialog = create();
			BaseActivityInf activityInf = weakReferenceBaseActivity.get();
			if (activityInf == null || activityInf.getActivity() == null) return;

			Activity activity = activityInf.getActivity();
			if (!activity.isFinishing() &&
				!activity.isDestroyed() &&
				!dialog.isShowing()) {
				dialog.show();
			}
		} catch (Exception error) {
			logger.d("Dialog show failed: " + error.getMessage());
		}
	}

	@NonNull
	public AlertDialog getDialog() {
		if (dialog == null) {
			String msg = "Dialog not initialized. Call show() first.";
			throw new IllegalStateException(msg);
		}
		return this.dialog;
	}

	public void setOnClickListener(int viewId, @NonNull OnClickListener listener) {
		if (weakCustomView == null || weakCustomView.get() == null) return;
		View view = weakCustomView.get().findViewById(viewId);
		if (view != null) view.setOnClickListener(listener);
	}

	public void enableSlideUpAnimation() {
		setDialogAnimation(R.style.style_dialog_animation);
	}

	public void enableBottomPosition() {
		if (dialog != null && dialog.getWindow() != null) {
			dialog.getWindow().setGravity(Gravity.BOTTOM);
			LayoutParams params = dialog.getWindow().getAttributes();
			params.y = 0;
			dialog.getWindow().setAttributes(params);
			int transparent = R.color.transparent;
			dialog.getWindow().setBackgroundDrawableResource(transparent);
			dialog.getWindow().setLayout(MATCH_PARENT, WRAP_CONTENT);
		}
	}

	public void setCancelable(boolean cancelable) {
		if (dialog == null) create();
		dialog.setCancelable(cancelable);
		dialog.setCanceledOnTouchOutside(cancelable);
	}

	public boolean isShowing() {
		return dialog != null && dialog.isShowing();
	}

	public void close() {
		try {
			if (dialog != null) {
				dialog.setOnDismissListener(null);
				dialog.setOnCancelListener(null);
				if (dialog.isShowing()) dialog.dismiss();
				dialog = null;
			}
			View view = weakCustomView != null ? weakCustomView.get() : null;
			if (view != null) clearAllClickListeners(view);
			weakCustomView = null;
		} catch (Exception error) {
			logger.e("Dialog close failed: " + error.getMessage());
		}
	}

	@NonNull
	public View getView() {
		View view = weakCustomView != null ? weakCustomView.get() : null;
		if (view == null) {
			throw new IllegalStateException("No view set or view released.");
		}
		return view;
	}

	@NonNull
	public DialogBuilder setView(int layoutResId) {
		Activity activity = getActivity();
		if (activity == null) return this;
		View view = from(activity).inflate(layoutResId, null);
		this.weakCustomView = new WeakReference<>(view);
		return this;
	}

	@NonNull
	public DialogBuilder setView(@NonNull View view) {
		this.weakCustomView = new WeakReference<>(view);
		return this;
	}

	@NonNull
	public View getPositiveButtonView() {
		int resId = R.id.btn_dialog_positive_container;
		return getView().findViewById(resId);
	}

	@NonNull
	public DialogBuilder setOnClickForPositiveButton(
		@NonNull OnClickListener listener) {
		getPositiveButtonView().setOnClickListener(listener);
		return this;
	}

	@NonNull
	public View getNegativeButtonView() {
		int resId = R.id.button_dialog_negative_container;
		return getView().findViewById(resId);
	}

	@NonNull
	public DialogBuilder setOnClickForNegativeButton(
		@NonNull OnClickListener listener) {
		getNegativeButtonView().setOnClickListener(listener);
		return this;
	}

	@Nullable
	public Activity getActivity() {
		BaseActivityInf inf = weakReferenceBaseActivity.get();
		return inf != null ? inf.getActivity() : null;
	}

	private AlertDialog create() {
		Activity activity = getActivity();
		if (activity == null || activity.isFinishing() ||
			activity.isDestroyed()) return null;

		Builder builder = new Builder(activity, R.style.style_dialog);
		View view = weakCustomView != null ? weakCustomView.get() : null;
		if (view == null) return null;

		builder.setView(view);
		dialog = builder.create();
		dialog.setOnDismissListener(d -> close());
		applyBottomPositioning();
		return dialog;
	}

	private void applyBottomPositioning() {
		enableBottomPosition();
		enableSlideUpAnimation();
	}

	private void setDialogAnimation(int animationResId) {
		if (dialog != null && dialog.getWindow() != null) {
			dialog.getWindow()
				.getAttributes()
				.windowAnimations = animationResId;
		}
	}

	private void clearAllClickListeners(@NonNull View view) {
		view.setOnClickListener(null);
		if (view instanceof ViewGroup group) {
			for (int i = 0; i < group.getChildCount(); i++) {
				clearAllClickListeners(group.getChildAt(i));
			}
		}
	}

	public interface OnCancelListener
		extends DialogInterface.OnCancelListener {
		@Override
		void onCancel(DialogInterface dialog);
	}
}