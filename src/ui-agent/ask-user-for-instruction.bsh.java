// Required local variable: status_message

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

String userInstruction = "";

int DIALOG_TIMEOUT_SECONDS = 120;
String DIALOG_TITLE = "UI automation is stuck";
String DIALOG_FIELD_HINT = "What should happen on this screen?";

int getOverlayWindowType() {
	if (Build.VERSION.SDK_INT >= 26) {
		return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
	}

	return WindowManager.LayoutParams.TYPE_PHONE;
}

void showInstructionDialog(Context context, String message, String[] instructionHolder, AlertDialog[] dialogHolder, CountDownLatch dialogClosed) {
	final EditText instructionField = new EditText(context);
	instructionField.setHint(DIALOG_FIELD_HINT);

	AlertDialog dialog = new AlertDialog.Builder(context)
		.setTitle(DIALOG_TITLE)
		.setMessage(message)
		.setView(instructionField)
		.setPositiveButton("Send to Claude", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface clickedDialog, int which) {
				instructionHolder[0] = instructionField.getText().toString();
			}
		})
		.setNegativeButton("Give up", null)
		.setOnDismissListener(new DialogInterface.OnDismissListener() {
			public void onDismiss(DialogInterface dismissedDialog) {
				dialogClosed.countDown();
			}
		})
		.create();

	dialog.getWindow().setType(getOverlayWindowType());
	// the device default theme anchors alert dialogs to the bottom of the screen
	dialog.getWindow().setGravity(Gravity.CENTER);
	dialogHolder[0] = dialog;
	dialog.show();
}

// Returns "" when the dialog was cancelled or timed out.
String askForInstruction(String message, int timeoutSeconds) {
	final Context context = new ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault_Dialog_Alert);
	final String[] instructionHolder = new String[1];
	final AlertDialog[] dialogHolder = new AlertDialog[1];
	final CountDownLatch dialogClosed = new CountDownLatch(1);
	Handler mainHandler = new Handler(Looper.getMainLooper());

	mainHandler.post(new Runnable() {
		public void run() {
			showInstructionDialog(context, message, instructionHolder, dialogHolder, dialogClosed);
		}
	});

	if (!dialogClosed.await(timeoutSeconds, TimeUnit.SECONDS)) {
		mainHandler.post(new Runnable() {
			public void run() {
				if (dialogHolder[0] != null && dialogHolder[0].isShowing()) {
					dialogHolder[0].dismiss();
				}
			}
		});
		dialogClosed.await(5, TimeUnit.SECONDS);
	}

	return instructionHolder[0] != null ? instructionHolder[0] : "";
}

// Clear the output first, so a run that fails midway never leaves the previous run's instruction behind.
variableSetter.setString("user_instruction", "");

String errorMessage = magicText.evaluateString("{" + "lv=status_message" + "}");
System.out.println("errorMessage = `" + errorMessage + "`");

String dialogMessage = "It stopped with this error:\n" + errorMessage
	+ "\n\nSay what should happen on the current screen. Claude turns it into a rule, replacing any rule that already matches this screen.";
userInstruction = askForInstruction(dialogMessage, DIALOG_TIMEOUT_SECONDS).trim();
System.out.println("userInstruction = `" + userInstruction + "`");

userInstruction; // Output Variable
