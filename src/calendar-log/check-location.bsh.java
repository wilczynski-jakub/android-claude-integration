/* MacroDroid: reads LOC_NOW_LAT, LOC_NOW_LON, LOC_MAP_URL, LOC_TRAVEL_TIME, LOC_MAX_EVENT_TIME, LOC_CHECK_INTERVAL, CALENDAR_ACCOUNT, {last_loc_lat}, {last_loc_long}, {last_loc_accuracy}, {last_loc_link}; writes LOC_TRAVEL_TIME, LOC_MAP_URL, LOC_NOW_LAT, LOC_NOW_LON */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import java.util.Calendar;
import java.util.TimeZone;

void showNotification(int notificationId, String title) {
	NotificationManager notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);

	String channelId = "macrodroid_java_code_channel";
	if (Build.VERSION.SDK_INT >= 26) {
		NotificationChannel channel = new NotificationChannel(
			channelId, "MacroDroid Java Code", NotificationManager.IMPORTANCE_DEFAULT
		);
		notificationManager.createNotificationChannel(channel);
	}

	Notification.Builder notificationBuilder = new Notification.Builder(appContext);
	if (Build.VERSION.SDK_INT >= 26) {
		notificationBuilder.setChannelId(channelId);
	}
	notificationBuilder.setContentTitle("《▪︎》 " + title);
	notificationBuilder.setSmallIcon(android.R.drawable.ic_dialog_info);
	notificationBuilder.setAutoCancel(true);

	notificationManager.notify(notificationId, notificationBuilder.build());
}

void notifyLocationOff() {
	showNotification(1002, "-location-on-");
}

double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
	double earthRadiusM = 6371000.0;
	double deltaLat = Math.toRadians(lat2 - lat1);
	double deltaLon = Math.toRadians(lon2 - lon1);

	double a = Math.sin(deltaLat / 2.0) * Math.sin(deltaLat / 2.0)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
			* Math.sin(deltaLon / 2.0) * Math.sin(deltaLon / 2.0);

	return 2.0 * earthRadiusM * Math.atan2(
			Math.sqrt(a),
			Math.sqrt(1.0 - a)
	);
}

/* Check whether location services are on */
LocationManager locationManager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
boolean locationEnabled = Build.VERSION.SDK_INT >= 28
		? locationManager.isLocationEnabled()
		: locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
				|| locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

System.out.println("location_enabled = `" + locationEnabled + "`");

if (!locationEnabled) {
	notifyLocationOff();
	System.out.println("notification_shown = `true`");
	Thread.sleep(30000);
}
System.out.println();

/* Read current location */
double currentLat = magicText.evaluateDecimal("{" + "last_loc_lat" + "}");
double currentLon = magicText.evaluateDecimal("{" + "last_loc_long" + "}");
double locationAccuracy = magicText.evaluateDecimal("{" + "last_loc_accuracy" + "}");
String currentMapUrl = magicText.evaluateString("{" + "last_loc_link" + "}");

System.out.println("current_lat = `" + currentLat + "`");
System.out.println("current_lon = `" + currentLon + "`");
System.out.println();
System.out.println("location_accuracy = `" + locationAccuracy + "`");
System.out.println("current_map_url = `" + currentMapUrl + "`");
System.out.println();

/* An unknown location reads as 0,0 with accuracy 0 -- never store it as a real position */
boolean locationKnown = locationAccuracy > 0 && (currentLat != 0 || currentLon != 0);
System.out.println("location_known = `" + locationKnown + "`");
System.out.println();

/* Load previously stored location state */
double LOC_NOW_LAT_before = magicText.evaluateDecimal("{" + "v=LOC_NOW_LAT" + "}");
double LOC_NOW_LON_before = magicText.evaluateDecimal("{" + "v=LOC_NOW_LON" + "}");
String LOC_MAP_URL_before = magicText.evaluateString("{" + "v=LOC_MAP_URL" + "}");
int LOC_TRAVEL_TIME_before = magicText.evaluateInt("{" + "v=LOC_TRAVEL_TIME" + "}");
System.out.println("LOC_NOW_LAT (before) = `" + LOC_NOW_LAT_before + "`");
System.out.println("LOC_NOW_LON (before) = `" + LOC_NOW_LON_before + "`");
System.out.println("LOC_MAP_URL (before) = `" + LOC_MAP_URL_before + "`");
System.out.println("LOC_TRAVEL_TIME (before) = `" + LOC_TRAVEL_TIME_before + "`");

int LOC_MAX_EVENT_TIME = magicText.evaluateInt("{" + "v=LOC_MAX_EVENT_TIME" + "}");
System.out.println("LOC_MAX_EVENT_TIME = `" + LOC_MAX_EVENT_TIME + "`");
System.out.println();

boolean previousLocationSet =
		LOC_NOW_LAT_before != 0 &&
		LOC_NOW_LON_before != 0;

/* Calculate distance between previous and current location */
double distance = previousLocationSet
		? calculateDistance(LOC_NOW_LAT_before, LOC_NOW_LON_before, currentLat, currentLon)
		: 0.0;

System.out.println("previous_location_set = `" + previousLocationSet + "`");
System.out.println("distance_m = `" + distance + "`");
System.out.println();

String branchTaken = "";

if (!locationKnown) {
	branchTaken = "location_unknown";
	System.out.println("branch_taken = `" + branchTaken + "`");

/* Location changed */
} else if (previousLocationSet && distance > 100.0 + locationAccuracy && LOC_TRAVEL_TIME_before < LOC_MAX_EVENT_TIME) {
	branchTaken = "location_changed";

	int locCheckIntervalMins = magicText.evaluateInt("{" + "v=LOC_CHECK_INTERVAL[minutes]" + "}");
	int LOC_TRAVEL_TIME_after = LOC_TRAVEL_TIME_before + locCheckIntervalMins;
	variableSetter.setInt("LOC_TRAVEL_TIME", LOC_TRAVEL_TIME_after);

	System.out.println("branch_taken = `" + branchTaken + "`");
	System.out.println("LOC_CHECK_INTERVAL_mins = `" + locCheckIntervalMins + "`");
	System.out.println("LOC_TRAVEL_TIME (after) = `" + LOC_TRAVEL_TIME_after + "`");
} else if (LOC_TRAVEL_TIME_before > 0) {

	/* Location stopped changing -> create calendar event */
	branchTaken = "location_stopped";
	int eventDuration = LOC_TRAVEL_TIME_before;

	Calendar eventStart = Calendar.getInstance();
	eventStart.add(Calendar.MINUTE, -eventDuration);

	System.out.println("branch_taken = `" + branchTaken + "`");
	System.out.println("event_duration_min = `" + eventDuration + "`");
	System.out.println("event_start = `" + eventStart.getTime().toString() + "`");

	long calID = 1;

	Cursor cur = appContext.getContentResolver().query(
		CalendarContract.Calendars.CONTENT_URI,
		new String[]{CalendarContract.Calendars._ID},
		CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " = ? AND " +
		CalendarContract.Calendars.ACCOUNT_NAME + " = ?",
		new String[]{"Tasker/MacroDroid: Location change", magicText.evaluateString("{" + "v=CALENDAR_ACCOUNT" + "}")},
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
	long endMillis = startMillis + (eventDuration * 60000L);

	ContentValues values = new ContentValues();
	values.put(CalendarContract.Events.DTSTART, startMillis);
	values.put(CalendarContract.Events.DTEND, endMillis);
	values.put(CalendarContract.Events.TITLE, "commuting...");
	values.put(CalendarContract.Events.DESCRIPTION, "TO: " + currentMapUrl);
	values.put(CalendarContract.Events.EVENT_LOCATION, LOC_MAP_URL_before);
	values.put(CalendarContract.Events.CALENDAR_ID, calID);
	values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());

	try {
		Uri uri = appContext.getContentResolver().insert(
			CalendarContract.Events.CONTENT_URI,
			values
		);
		System.out.println("event_uri = `" + uri + "`");
	} catch (Exception e) {
		System.out.println("calendar_error = `" + e.getClass().getSimpleName()
			+ ": " + e.getMessage() + "`");
	}

	variableSetter.setInt("LOC_TRAVEL_TIME", 0);
	variableSetter.setString("LOC_MAP_URL", currentMapUrl);

	System.out.println("LOC_TRAVEL_TIME (after) = `0`");
	System.out.println("LOC_MAP_URL (after) = `" + currentMapUrl + "`");

} else {

	/* First detected location change */
	branchTaken = "first_location_change";
	variableSetter.setString("LOC_MAP_URL", currentMapUrl);

	System.out.println("branch_taken = `" + branchTaken + "`");
	System.out.println("LOC_MAP_URL (after) = `" + currentMapUrl + "`");
}

/* Store current location */
if (locationKnown) {
	variableSetter.setDecimal("LOC_NOW_LAT", currentLat);
	variableSetter.setDecimal("LOC_NOW_LON", currentLon);

	System.out.println();
	System.out.println("LOC_NOW_LAT (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_NOW_LAT" + "}") + "`");
	System.out.println("LOC_NOW_LON (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_NOW_LON" + "}") + "`");
	System.out.println("LOC_TRAVEL_TIME (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_TRAVEL_TIME" + "}") + "`");
}
