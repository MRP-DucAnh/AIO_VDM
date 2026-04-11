package app.ui.others.information;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static lib.device.DeviceInfoUtils.getDeviceInformation;
import static lib.process.CommonTimeUtils.delay;
import static lib.ui.MsgDialogUtils.showMessageDialog;
import static lib.ui.ViewUtility.showOnScreenKeyboard;
import static lib.ui.builders.ToastView.showToast;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.content.res.AppCompatResources;

import com.aio.R;

import app.core.bases.BaseActivity;
import app.ui.main.MotherActivity;
import lib.process.LogHelperUtils;
import lib.ui.builders.DialogBuilder;

/**
 * An activity that allows users to provide feedback about the application.
 * <p>
 * This screen provides a form for users to report various issues, such as app crashes,
 * video playback problems, or excessive ads. It collects the user's email, a custom message,
 * and a checklist of common problems. The collected information, along with device details,
 * is then sent to a backend service for analysis.
 * <p>
 * This activity can also be launched directly from the crash handler, in which case it will
 * pre-select the "App Crashed" option and display a dialog to the user.
 */
public class UserFeedbackActivity extends BaseActivity {

	/**
	 * Intent extra key to specify the origin of the intent that started this activity.
	 * This helps in determining if the activity was launched from a specific context,
	 * such as the crash handler.
	 *
	 * @see #FROM_CRASH_HANDLER
	 */
	public static final String WHERE_DIS_YOU_COME_FROM = "WHERE_DIS_YOU_COME_FROM";

	/**
	 * Constant used as an intent extra value.
	 * It indicates that the {@link UserFeedbackActivity} was launched from the application's
	 * crash handling mechanism. This allows the activity to pre-fill certain fields,
	 * such as checking the "App Crashed" checkbox, to streamline the feedback process for the user
	 * after a crash.
	 *
	 * @see #WHERE_DIS_YOU_COME_FROM
	 * @see #handleIntentExtra()
	 */
	public static final int FROM_CRASH_HANDLER = 12;

	/**
	 * Logger for this activity, used for debugging and logging events.
	 */
	private final LogHelperUtils logger = LogHelperUtils.from(getClass());

	/**
	 * The button that triggers sending the user's feedback.
	 * When clicked, it collects data from the input fields and checkboxes,
	 * validates the input, and sends the compiled feedback message.
	 */
	private View buttonSendMessage;

	/**
	 * Input field for the user to enter their email address.
	 * This is used to contact the user regarding their feedback if necessary.
	 */
	private EditText editEmailField, editMessageField;

	/**
	 * A {@link View} that acts as the back button in the action bar,
	 * allowing the user to navigate back to the previous screen or close the current activity.
	 */
	private View buttonBack, containerEditEmailField, containerEditMessageField;

	/**
	 * Checkbox for users to indicate that they are experiencing no sound in videos.
	 */
	private CheckBox checkBoxNoSound, checkBoxIncompleteVideo,
		checkBoxAppCrashed, checkBoxGlitchedVideos,
		checkBoxShowingManyAds, checkBoxOtherProblem;

	/**
	 * Specifies the layout resource file for this activity. This method is called by the
	 * {@link BaseActivity} to inflate the user interface.
	 *
	 * @return The resource ID of the layout file (e.g., {@code R.layout.activity_feedback_1}).
	 */
	@Override
	public int onRenderingLayout() {
		return R.layout.activity_user_feedback_1;
	}

	/**
	 * Initializes the activity's views, sets up click listeners, and processes any
	 * data passed through the intent, such as handling the flow when the activity
	 * is opened from a crash report.
	 */
	@Override
	public void onAfterLayoutRender() {
		initializeViews();
		initializeViewClickEvents();
		handleIntentExtra();
	}

	/**
	 * Handles the back button press action.
	 * Overrides the default behavior to close the activity with a custom swipe animation.
	 */
	@Override
	public void onBackPressActivity() {
		closeActivityWithSwipeAnimation(false);
	}

	/**
	 * Initializes and binds the UI components from the XML layout to their corresponding Java fields.
	 * This includes buttons, input fields, and checkboxes used for collecting user feedback.
	 */
	private void initializeViews() {
		buttonBack = findViewById(R.id.btn_left_actionbar);
		buttonSendMessage = findViewById(R.id.btn_send_message);
		editEmailField = findViewById(R.id.edit_email_field);
		editMessageField = findViewById(R.id.edit_message_field);
		containerEditEmailField = findViewById(R.id.container_edit_email_field);
		containerEditMessageField = findViewById(R.id.container_edit_message_field);
		checkBoxNoSound = findViewById(R.id.checkbox_no_sound);
		checkBoxIncompleteVideo = findViewById(R.id.checkbox_incomplete_video);
		checkBoxAppCrashed = findViewById(R.id.checkbox_app_crashed);
		checkBoxGlitchedVideos = findViewById(R.id.checkbox_glitch_in_video);
		checkBoxShowingManyAds = findViewById(R.id.checkbox_showing_too_many_ads);
		checkBoxOtherProblem = findViewById(R.id.checkbox_other_problem);
	}

	/**
	 * Retrieves the text entered by the user in the message input field.
	 *
	 * @return The user's feedback message as a String.
	 */
	private String getUserMessage() {
		return editMessageField.getText().toString();
	}

	/**
	 * Retrieves the user's email from the email input field.
	 *
	 * @return A string containing the email entered by the user.
	 */
	private String getUserEmail() {
		return editEmailField.getText().toString();
	}

	/**
	 * Checks if the user has provided both an email address and a message.
	 *
	 * @return {@code true} if both the user message and email fields are not empty,
	 * {@code false} otherwise.
	 */
	private boolean isUserMessageValid() {
		return !getUserMessage().isEmpty() && !getUserEmail().isEmpty();
	}

	/**
	 * Initializes click listeners for various UI elements.
	 * This includes the back button, send message button, and the containers for the
	 * email and message input fields. Clicking the input containers makes them
	 * focusable and shows the on-screen keyboard.
	 */
	private void initializeViewClickEvents() {
		buttonBack.setOnClickListener(view -> onBackPressActivity());

		buttonSendMessage.setOnClickListener(view -> {
			String messageToSend = generateMessage();
			if (messageToSend.isEmpty()) {
				doSomeVibration(50);
				String toastMessage = getString(R.string.title_enter_your_email_message_first);
				showToast(this, toastMessage, -1);
				return;
			}

			doSomeVibration(20);
			showToast(this, getString(R.string.title_feedbacks_sent_successfully), -1);
			resetFormFields();
		});

		containerEditEmailField.setOnClickListener(view -> {
			editEmailField.setFocusable(true);
			editEmailField.selectAll();
			showOnScreenKeyboard(this, editEmailField);
		});

		containerEditMessageField.setOnClickListener(view -> {
			editMessageField.setFocusable(true);
			editMessageField.selectAll();
			showOnScreenKeyboard(this, editMessageField);
		});
	}

	/**
	 * Clears all input fields and checkboxes on the feedback form. This is typically called after
	 * the user has successfully submitted their feedback, preparing the form for a new entry.
	 * It resets the email and message fields to be empty and unchecks all issue-related checkboxes.
	 */
	private void resetFormFields() {
		editEmailField.setText("");
		editMessageField.setText("");
		checkBoxNoSound.setChecked(false);
		checkBoxIncompleteVideo.setChecked(false);
		checkBoxAppCrashed.setChecked(false);
		checkBoxGlitchedVideos.setChecked(false);
		checkBoxShowingManyAds.setChecked(false);
		checkBoxOtherProblem.setChecked(false);
	}

	/**
	 * Generates a comprehensive feedback message string.
	 * <p>
	 * This method constructs a report by concatenating the user's custom message, their email,
	 * a list of selected issues (from checkboxes), and detailed device information.
	 * It first validates that both the email and message fields are non-empty. If they are,
	 * it appends them to the report. Then, it checks which problem-related checkboxes
	 * are ticked and adds the corresponding issue descriptions. If no specific issue is
	 * selected, it notes that as well. Finally, it appends a block of device information
	 * obtained from {@link lib.device.DeviceInfoUtils#getDeviceInformation(android.content.Context)}.
	 *
	 * @return A formatted string containing the complete feedback message, or an empty
	 * string if the user's message or email is missing.
	 */
	private String generateMessage() {
		StringBuilder msgBuilder = new StringBuilder();
		String userMessage = getUserMessage();
		String userEmail = getUserEmail();

		if (isUserMessageValid()) {
			msgBuilder.append(getString(R.string.title_user_message))
				.append(userMessage).append("\n\n");
			msgBuilder.append(getString(R.string.title_user_email))
				.append(userEmail).append("\n\n");
		} else return "";

		// Append selected issues
		msgBuilder.append(getString(R.string.title_issues_reported));
		if (checkBoxNoSound.isChecked())
			msgBuilder.append(getString(R.string.title_no_sound_in_videos));
		if (checkBoxIncompleteVideo.isChecked())
			msgBuilder.append(getString(R.string.title_videos_are_incomplete));
		if (checkBoxAppCrashed.isChecked())
			msgBuilder.append(getString(R.string.title_app_crashed_during_use));
		if (checkBoxGlitchedVideos.isChecked())
			msgBuilder.append(getString(R.string.title_videos_are_glitching));
		if (checkBoxShowingManyAds.isChecked())
			msgBuilder.append(getString(R.string.title_too_many_ads_are_being_shown));
		if (checkBoxOtherProblem.isChecked())
			msgBuilder.append(getString(R.string.title_other_problems_experienced));

		// No specific issues
		if (!checkBoxNoSound.isChecked() && !checkBoxIncompleteVideo.isChecked() &&
			!checkBoxAppCrashed.isChecked() && !checkBoxGlitchedVideos.isChecked() &&
			!checkBoxShowingManyAds.isChecked() && !checkBoxOtherProblem.isChecked()) {
			msgBuilder.append(getString(R.string.title_no_specific_issues_reported));
		}

		// Append device information
		String deviceInfo = getDeviceInformation(this);
		msgBuilder.append(getString(R.string.title_device_information)).append(deviceInfo);
		return msgBuilder.toString();
	}

	/**
	 * Checks for incoming {@link Intent} data.
	 * If the activity is started from the crash handler (indicated by the {@code FROM_CRASH_HANDLER} extra),
	 * it automatically checks the "App Crashed" checkbox and displays a dialog to the user,
	 * asking them to confirm sending a crash report.
	 */
	private void handleIntentExtra() {
		Intent intent = getIntent();
		if (intent.getIntExtra(WHERE_DIS_YOU_COME_FROM, -1) == FROM_CRASH_HANDLER) {
			checkBoxAppCrashed.setChecked(true);

			DialogBuilder dialogBuilder = showMessageDialog(
				this, false, true,
				getString(R.string.title_oops_app_was_crashed),
				getString(R.string.text_app_crash_feedback_message),
				getString(R.string.title_send_feedback),
				getString(R.string.title_cancel),
				false, null, null,
				messageTextView -> {
					messageTextView.setText(R.string.text_app_crash_feedback_message);
					return null;
				},
				null, null,
				positiveButtonText -> {
					if (getActivity() == null) return null;
					int drawableResId = R.drawable.ic_button_actionbar_send;
					Drawable drawable = AppCompatResources.getDrawable(getActivity(), drawableResId);
					if (drawable != null) {
						int intrinsicWidth = drawable.getIntrinsicWidth();
						int intrinsicHeight = drawable.getIntrinsicHeight();
						drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight);
					}
					positiveButtonText.setCompoundDrawables(drawable, null, null, null);
					return null;
				},
				null, null, null
			);

			if (dialogBuilder == null) return;

			dialogBuilder.setOnClickForPositiveButton(view -> {
				dialogBuilder.close();
				showToast(this, null, R.string.title_feedbacks_sent_successfully);

				delay(200, () -> {
					try {
						Intent activityIntent = new Intent(getActivity(), MotherActivity.class);
						int flags = FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP;
						activityIntent.setFlags(flags);
						startActivity(activityIntent);
						finish();
					} catch (Exception error) {
						logger.e("Error sending feedback: ${error.message}", error);
					}
				});
			});
		}
	}
}