// Required global variables: APP_NAME, APP_PACKAGE, APP_USAGE_MINS, APP_CHECK_INTERVAL, APP_CALENDAR_NAME
// Required local variables: event_duration_mins, event_title, event_description, event_location, event_calendar_name

import android.content.Context;
import android.os.PowerManager;

// Check if the screen is currently on before reading the foreground app
PowerManager powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
boolean screenOn = powerManager.isInteractive();
System.out.println("screenOn = `" + screenOn + "`");

String fgAppName = "";
String fgAppPackage = "";
if (screenOn) {
	fgAppName = magicText.evaluateString("{" + "fg_app_name" + "}");
	fgAppPackage = magicText.evaluateString("{" + "fg_app_package" + "}");
}
System.out.println("fgAppName (current read) = `" + fgAppName + "`");
System.out.println("fgAppPackage (current read) = `" + fgAppPackage + "`");
System.out.println();

String globalAppNameBefore = magicText.evaluateString("{" + "v=APP_NAME" + "}");
String globalAppPackageBefore = magicText.evaluateString("{" + "v=APP_PACKAGE" + "}");
int globalAppUsageMinsBefore = magicText.evaluateInt("{" + "v=APP_USAGE_MINS" + "}");
System.out.println("APP_NAME (before) = `" + globalAppNameBefore + "`");
System.out.println("APP_PACKAGE (before) = `" + globalAppPackageBefore + "`");
System.out.println("APP_USAGE_MINS (before) = `" + globalAppUsageMinsBefore + "`");
System.out.println();

String branchTaken = "";
String globalAppNameAfter;
String globalAppPackageAfter;
int globalAppUsageMinsAfter;

if (fgAppPackage.equals(globalAppPackageBefore)) {
	branchTaken = "same_app_incrementing";

	int globalAppCheckIntervalMins;
	if (fgAppPackage.equals("")) {
		globalAppCheckIntervalMins = 0;
	} else {
		globalAppCheckIntervalMins = (int) magicText.evaluateInt("{" + "v=APP_CHECK_INTERVAL[minutes]" + "}");
	}
	System.out.println("globalAppCheckIntervalMins = `" + globalAppCheckIntervalMins + "`");
	System.out.println();

	globalAppUsageMinsAfter = globalAppUsageMinsBefore + globalAppCheckIntervalMins;
	variableSetter.setInt("APP_USAGE_MINS", globalAppUsageMinsAfter);

	globalAppNameAfter = globalAppNameBefore;
	globalAppPackageAfter = globalAppPackageBefore;
} else {
	branchTaken = "app_changed";

	if (!fgAppPackage.equals("")) {
		// Start tracking the new foreground app
		variableSetter.setString("APP_NAME", fgAppName);
		variableSetter.setString("APP_PACKAGE", fgAppPackage);
		globalAppNameAfter = fgAppName;
		globalAppPackageAfter = fgAppPackage;
	} else {
		// Screen is off, clear the tracked app
		variableSetter.setString("APP_NAME", "");
		variableSetter.setString("APP_PACKAGE", "");
		globalAppNameAfter = "";
		globalAppPackageAfter = "";
	}
	variableSetter.setInt("APP_USAGE_MINS", 0);
	globalAppUsageMinsAfter = 0;
}

System.out.println("branchTaken = `" + branchTaken + "`");

// Calendar logging only happens in the "app changed" branch
boolean logToCalendar = branchTaken.equals("app_changed") && globalAppUsageMinsBefore > 6;
System.out.println("logToCalendar = `" + logToCalendar + "`");

int eventDurationMins = 0;
String eventTitle = "";
String eventDescription = "";
String eventLocation = "";
String eventCalendarName = "";
if (logToCalendar) {
	eventDurationMins = globalAppUsageMinsBefore;
	eventTitle = globalAppNameBefore;
	eventDescription = globalAppPackageBefore;
	eventCalendarName = magicText.evaluateString("{" + "v=APP_CALENDAR_NAME" + "}");
}

// insert-calendar-event.bsh reads these. event_duration_mins = 0 means there is no event to insert
variableSetter.setInt("event_duration_mins", eventDurationMins);
variableSetter.setString("event_title", eventTitle);
variableSetter.setString("event_description", eventDescription);
variableSetter.setString("event_location", eventLocation);
variableSetter.setString("event_calendar_name", eventCalendarName);

System.out.println("event_duration_mins = `" + eventDurationMins + "`");
System.out.println("event_title = `" + eventTitle + "`");
System.out.println("event_description = `" + eventDescription + "`");
System.out.println("event_location = `" + eventLocation + "`");
System.out.println("event_calendar_name = `" + eventCalendarName + "`");
System.out.println();

System.out.println("APP_NAME (after) = `" + globalAppNameAfter + "`");
System.out.println("APP_PACKAGE (after) = `" + globalAppPackageAfter + "`");
System.out.println("APP_USAGE_MINS (after) = `" + globalAppUsageMinsAfter + "`");
System.out.println();