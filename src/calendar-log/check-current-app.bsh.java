// Required global variables: APP_NAME, APP_PACKAGE, APP_TIME_COUNT, APP_CHECK_INTERVAL, APP_CALENDAR_NAME
// Required local variables: event_duration, event_title, event_description, event_location, event_calendar_name

import android.content.Context;
import android.os.PowerManager;

// Check if the screen is currently on before reading the foreground app
PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
boolean screenOn = pm.isInteractive();
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
int globalAppTimeCountBefore = magicText.evaluateInt("{" + "v=APP_TIME_COUNT" + "}");
System.out.println("APP_NAME (before) = `" + globalAppNameBefore + "`");
System.out.println("APP_PACKAGE (before) = `" + globalAppPackageBefore + "`");
System.out.println("APP_TIME_COUNT (before) = `" + globalAppTimeCountBefore + "`");
System.out.println();

String branchTaken = "";
String globalAppNameAfter;
String globalAppPackageAfter;
int globalAppTimeCountAfter;

if (fgAppPackage.equals(globalAppPackageBefore)) {
	branchTaken = "same_app_incrementing";

	int appCheckIntervalMins;
	if (fgAppPackage.equals("")) {
		appCheckIntervalMins = 0;
	} else {
		appCheckIntervalMins = (int) magicText.evaluateInt("{" + "v=APP_CHECK_INTERVAL[minutes]" + "}");
	}
	System.out.println("appCheckIntervalMins = `" + appCheckIntervalMins + "`");
	System.out.println();

	globalAppTimeCountAfter = globalAppTimeCountBefore + appCheckIntervalMins;
	variableSetter.setInt("APP_TIME_COUNT", globalAppTimeCountAfter);

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
	variableSetter.setInt("APP_TIME_COUNT", 0);
	globalAppTimeCountAfter = 0;
}

System.out.println("branchTaken = `" + branchTaken + "`");

// Calendar logging only happens in the "app changed" branch
boolean logToCalendar = branchTaken.equals("app_changed") && globalAppTimeCountBefore > 6;
System.out.println("logToCalendar = `" + logToCalendar + "`");

int eventDurationMins = 0;
String eventTitle = "";
String eventDescription = "";
String eventLocation = "";
String eventCalendarName = "";
if (logToCalendar) {
	eventDurationMins = globalAppTimeCountBefore;
	eventTitle = globalAppNameBefore;
	eventDescription = globalAppPackageBefore;
	eventCalendarName = magicText.evaluateString("{" + "v=APP_CALENDAR_NAME" + "}");
}

// insert-calendar-event.bsh reads these. event_duration = 0 means there is no event to insert
variableSetter.setInt("event_duration", eventDurationMins);
variableSetter.setString("event_title", eventTitle);
variableSetter.setString("event_description", eventDescription);
variableSetter.setString("event_location", eventLocation);
variableSetter.setString("event_calendar_name", eventCalendarName);

System.out.println("event_duration = `" + eventDurationMins + "`");
System.out.println("event_title = `" + eventTitle + "`");
System.out.println("event_description = `" + eventDescription + "`");
System.out.println("event_location = `" + eventLocation + "`");
System.out.println("event_calendar_name = `" + eventCalendarName + "`");
System.out.println();

System.out.println("APP_NAME (after) = `" + globalAppNameAfter + "`");
System.out.println("APP_PACKAGE (after) = `" + globalAppPackageAfter + "`");
System.out.println("APP_TIME_COUNT (after) = `" + globalAppTimeCountAfter + "`");
System.out.println();