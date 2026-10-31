// Required global variable: CALENDAR_ACCOUNT
// Required local variables: event_duration_mins, event_title, event_description, event_location, event_calendar_name

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.util.Calendar;
import java.util.TimeZone;

int eventDurationMins = magicText.evaluateInt("{" + "lv=event_duration_mins" + "}");
String eventTitle = magicText.evaluateString("{" + "lv=event_title" + "}");
String eventDescription = magicText.evaluateString("{" + "lv=event_description" + "}");
String eventLocation = magicText.evaluateString("{" + "lv=event_location" + "}");
String eventCalendarName = magicText.evaluateString("{" + "lv=event_calendar_name" + "}");

System.out.println("event_duration_mins = `" + eventDurationMins + "`");
System.out.println("event_title = `" + eventTitle + "`");
System.out.println("event_description = `" + eventDescription + "`");
System.out.println("event_location = `" + eventLocation + "`");
System.out.println("event_calendar_name = `" + eventCalendarName + "`");
System.out.println();

// event_duration_mins > 0 means that a previous script prepared an event
if (eventDurationMins > 0) {
	Calendar eventStart = Calendar.getInstance();
	eventStart.add(Calendar.MINUTE, -eventDurationMins);
	System.out.println("eventStart = `" + eventStart.getTime().toString() + "`");

	long calendarId = 1;
	Cursor cursor = appContext.getContentResolver().query(
		CalendarContract.Calendars.CONTENT_URI,
		new String[]{CalendarContract.Calendars._ID},
		CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " = ? AND " +
		CalendarContract.Calendars.ACCOUNT_NAME + " = ?",
		new String[]{eventCalendarName,magicText.evaluateString("{" + "v=CALENDAR_ACCOUNT" + "}")},
		null
	);
	if (cursor != null && cursor.moveToFirst()) {
		calendarId = cursor.getLong(0);
		cursor.close();
	} else {
		System.out.println("calendarError = `lookup returned nothing, used fallback calendarId=1`");
	}
	System.out.println("calendarIdUsed = `" + calendarId + "`");

	long startMillis = eventStart.getTimeInMillis();
	long endMillis = startMillis + (eventDurationMins * 60000L);

	ContentValues values = new ContentValues();
	values.put(CalendarContract.Events.DTSTART, startMillis);
	values.put(CalendarContract.Events.DTEND, endMillis);
	values.put(CalendarContract.Events.TITLE, eventTitle);
	values.put(CalendarContract.Events.DESCRIPTION, eventDescription);
	values.put(CalendarContract.Events.EVENT_LOCATION, eventLocation);
	values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
	values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

	try {
		Uri uri = appContext.getContentResolver().insert(
			CalendarContract.Events.CONTENT_URI, values
		);
		System.out.println("eventUri = `" + uri + "`");
	} catch (Exception exception) {
		System.out.println("calendarError = `" + exception.getClass().getSimpleName() + ": " + exception.getMessage() + "`");
	}

	// Mark the event as handled
	variableSetter.setInt("event_duration_mins", 0);
}
