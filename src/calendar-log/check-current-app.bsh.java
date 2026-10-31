/* MacroDroid: reads APP_NAME, APP_PACKAGE, APP_TIME_COUNT, APP_CHECK_INTERVAL, CALENDAR_ACCOUNT, {fg_app_name}, {fg_app_package}; writes APP_NAME, APP_PACKAGE, APP_TIME_COUNT */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.CalendarContract;
import java.util.Calendar;
import java.util.TimeZone;

/* Check if the screen is currently on before reading the foreground app */
PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
boolean screenOn = pm.isInteractive();
System.out.println("screenOn = `" + screenOn + "`");

String app_name = "";
String app_package = "";
if (screenOn) {
	app_name = magicText.evaluateString("{" + "fg_app_name" + "}");
	app_package = magicText.evaluateString("{" + "fg_app_package" + "}");
}
System.out.println("app_name (current read) = `" + app_name + "`");
System.out.println("app_package (current read) = `" + app_package + "`");
System.out.println();

/* Load previously stored state from global variables — grouped together, no blank lines between them */
String APP_NAME_before = magicText.evaluateString("{" + "v=APP_NAME" + "}");
System.out.println("APP_NAME (before) = `" + APP_NAME_before + "`");

String APP_PACKAGE_before = magicText.evaluateString("{" + "v=APP_PACKAGE" + "}");
System.out.println("APP_PACKAGE (before) = `" + APP_PACKAGE_before + "`");

int APP_TIME_before = magicText.evaluateInt("{" + "v=APP_TIME_COUNT" + "}");
System.out.println("APP_TIME_COUNT (before) = `" + APP_TIME_before + "`");
System.out.println();

String branchTaken = "";
String APP_NAME_after;
String APP_PACKAGE_after;
int APP_TIME_after;

if (app_package.equals(APP_PACKAGE_before)) {
	branchTaken = "same_app_incrementing";

	int appCheckIntervalMins;
	if (app_package.equals("")) {
		appCheckIntervalMins = 0;
	} else {
		appCheckIntervalMins = (int)magicText.evaluateInt("{" + "v=APP_CHECK_INTERVAL[minutes]" + "}");
	}

	System.out.println("APP_CHECK_INTERVAL_mins = `" + appCheckIntervalMins + "`");
	System.out.println();

	APP_TIME_after = APP_TIME_before + appCheckIntervalMins;
	variableSetter.setInt("APP_TIME_COUNT", APP_TIME_after);

	APP_NAME_after = APP_NAME_before;
	APP_PACKAGE_after = APP_PACKAGE_before;
} else {
	branchTaken = "app_changed";

	if (!app_package.equals("")) {
		/* Start tracking the new foreground app */
		variableSetter.setString("APP_NAME", app_name);
		variableSetter.setString("APP_PACKAGE", app_package);
		APP_NAME_after = app_name;
		APP_PACKAGE_after = app_package;
	} else {
		/* Screen is off, clear the tracked app */
		variableSetter.setString("APP_NAME", "");
		variableSetter.setString("APP_PACKAGE", "");
		APP_NAME_after = "";
		APP_PACKAGE_after = "";
	}
	variableSetter.setInt("APP_TIME_COUNT", 0);
	APP_TIME_after = 0;
}

System.out.println("branch_taken = `" + branchTaken + "`");

/* Calendar logging only happens in the "app changed" branch */
boolean logToCalendar = branchTaken.equals("app_changed") && APP_TIME_before > 6;

if (logToCalendar) {
	Calendar eventStart = Calendar.getInstance();
	eventStart.add(Calendar.MINUTE, -APP_TIME_before);

	String event_name = APP_NAME_before;
	String event_description = APP_PACKAGE_before;
	int event_duration = APP_TIME_before;

	System.out.println("log_to_calendar = `true`");
	System.out.println("event_name = `" + event_name + "`");
	System.out.println("event_description = `" + event_description + "`");
	System.out.println("event_duration_min = `" + event_duration + "`");
	System.out.println("event_start = `" + eventStart.getTime().toString() + "`");

	long calID = 1;
	Cursor cur = appContext.getContentResolver().query(
		CalendarContract.Calendars.CONTENT_URI,
		new String[]{CalendarContract.Calendars._ID},
		CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " = ? AND " +
		CalendarContract.Calendars.ACCOUNT_NAME + " = ?",
		new String[]{"Tasker/MacroDroid: App log", magicText.evaluateString("{" + "v=CALENDAR_ACCOUNT" + "}")},
		null
	);
	if (cur != null && cur.moveToFirst()) {
		calID = cur.getLong(0);
		cur.close();
	} else {
		System.out.println("calendar_error = `lookup returned nothing, used fallback calID=1`");
	}
	System.out.println("calendar_id_used = `" + calID + "`");

	long startMillis = eventStart.getTimeInMillis();
	long endMillis = startMillis + (event_duration * 60000L);

	ContentValues values = new ContentValues();
	values.put(CalendarContract.Events.DTSTART, startMillis);
	values.put(CalendarContract.Events.DTEND, endMillis);
	values.put(CalendarContract.Events.TITLE, event_name);
	values.put(CalendarContract.Events.DESCRIPTION, event_description);
	values.put(CalendarContract.Events.CALENDAR_ID, calID);
	values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

	try {
		Uri uri = appContext.getContentResolver().insert(
			CalendarContract.Events.CONTENT_URI, values
		);
		System.out.println("event_uri = `" + uri + "`");
	} catch (Exception e) {
		System.out.println("calendar_error = `" + e.getClass().getSimpleName() + ": " + e.getMessage() + "`");
	}
} else {
	System.out.println("log_to_calendar = `false`");
}

System.out.println();

/* All "after" values — grouped together, no blank lines between them */
System.out.println("APP_NAME (after) = `" + APP_NAME_after + "`");
System.out.println("APP_PACKAGE (after) = `" + APP_PACKAGE_after + "`");
System.out.println("APP_TIME_COUNT (after) = `" + APP_TIME_after + "`");
System.out.println();
