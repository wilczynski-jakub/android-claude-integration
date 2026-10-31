// Required global variables: LOC_NOW_LAT, LOC_NOW_LON, LOC_MAP_URL, LOC_TRAVEL_TIME, LOC_MAX_EVENT_TIME, LOC_CHECK_INTERVAL, LOC_CALENDAR_NAME
// Required local variables: event_duration, event_title, event_description, event_location, event_calendar_name

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;

double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
	double earthRadiusM = 6371000.0;
	double deltaLat = Math.toRadians(lat2 - lat1);
	double deltaLon = Math.toRadians(lon2 - lon1);

	double haversine = Math.sin(deltaLat / 2.0) * Math.sin(deltaLat / 2.0)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
			* Math.sin(deltaLon / 2.0) * Math.sin(deltaLon / 2.0);

	return 2.0 * earthRadiusM * Math.atan2(
			Math.sqrt(haversine),
			Math.sqrt(1.0 - haversine)
	);
}

// Read current location
double currentLat = magicText.evaluateDecimal("{" + "last_loc_lat" + "}");
double currentLon = magicText.evaluateDecimal("{" + "last_loc_long" + "}");
double locationAccuracy = magicText.evaluateDecimal("{" + "last_loc_accuracy" + "}");
String currentMapUrl = magicText.evaluateString("{" + "last_loc_link" + "}");
System.out.println("currentLat = `" + currentLat + "`");
System.out.println("currentLon = `" + currentLon + "`");
System.out.println();
System.out.println("locationAccuracy = `" + locationAccuracy + "`");
System.out.println("currentMapUrl = `" + currentMapUrl + "`");
System.out.println();

// An unknown location reads as 0,0 with accuracy 0 -- never store it as a real position
boolean locationKnown = locationAccuracy > 0 && (currentLat != 0 || currentLon != 0);
System.out.println("locationKnown = `" + locationKnown + "`");
System.out.println();

// Load previously stored location state
double globalLocNowLatBefore = magicText.evaluateDecimal("{" + "v=LOC_NOW_LAT" + "}");
double globalLocNowLonBefore = magicText.evaluateDecimal("{" + "v=LOC_NOW_LON" + "}");
String globalLocMapUrlBefore = magicText.evaluateString("{" + "v=LOC_MAP_URL" + "}");
int globalLocTravelTimeBefore = magicText.evaluateInt("{" + "v=LOC_TRAVEL_TIME" + "}");
System.out.println("LOC_NOW_LAT (before) = `" + globalLocNowLatBefore + "`");
System.out.println("LOC_NOW_LON (before) = `" + globalLocNowLonBefore + "`");
System.out.println("LOC_MAP_URL (before) = `" + globalLocMapUrlBefore + "`");
System.out.println("LOC_TRAVEL_TIME (before) = `" + globalLocTravelTimeBefore + "`");

int globalLocMaxEventTime = magicText.evaluateInt("{" + "v=LOC_MAX_EVENT_TIME" + "}");
System.out.println("LOC_MAX_EVENT_TIME = `" + globalLocMaxEventTime + "`");
System.out.println();

boolean previousLocationSet =
        globalLocNowLatBefore != 0 &&
        globalLocNowLonBefore != 0;

// Calculate distance between previous and current location
double distance = previousLocationSet
        ? calculateDistance(globalLocNowLatBefore, globalLocNowLonBefore, currentLat, currentLon)
        : 0.0;

System.out.println("previousLocationSet = `" + previousLocationSet + "`");
System.out.println("distanceM = `" + distance + "`");
System.out.println();

int eventDurationMins = 0;
String eventTitle = "";
String eventDescription = "";
String eventLocation = "";
String eventCalendarName = "";

String branchTaken = "";

if (!locationKnown) {
    branchTaken = "location_unknown";
    System.out.println("branchTaken = `" + branchTaken + "`");
} else if (previousLocationSet && distance > 100.0 + locationAccuracy && globalLocTravelTimeBefore < globalLocMaxEventTime) {
    branchTaken = "location_changed";

    int locCheckIntervalMins = magicText.evaluateInt("{" + "v=LOC_CHECK_INTERVAL[minutes]" + "}");
    int globalLocTravelTimeAfter = globalLocTravelTimeBefore + locCheckIntervalMins;
    variableSetter.setInt("LOC_TRAVEL_TIME", globalLocTravelTimeAfter);

    System.out.println("branchTaken = `" + branchTaken + "`");
    System.out.println("locCheckIntervalMins = `" + locCheckIntervalMins + "`");
    System.out.println("LOC_TRAVEL_TIME (after) = `" + globalLocTravelTimeAfter + "`");
} else if (globalLocTravelTimeBefore > 0) {
    // Location stopped changing -> create calendar event
    branchTaken = "location_stopped";

    eventDurationMins = globalLocTravelTimeBefore;
    eventTitle = "commuting...";
    eventDescription = "TO: " + currentMapUrl;
    eventLocation = globalLocMapUrlBefore;
    eventCalendarName = magicText.evaluateString("{" + "v=LOC_CALENDAR_NAME" + "}");

    System.out.println("branchTaken = `" + branchTaken + "`");

    variableSetter.setInt("LOC_TRAVEL_TIME", 0);
    variableSetter.setString("LOC_MAP_URL", currentMapUrl);

    System.out.println("LOC_TRAVEL_TIME (after) = `" + magicText.evaluateInt("{" + "v=LOC_TRAVEL_TIME" + "}") + "`");
    System.out.println("LOC_MAP_URL (after) = `" + currentMapUrl + "`");
} else {
    // First detected location change
    branchTaken = "first_location_change";
    variableSetter.setString("LOC_MAP_URL", currentMapUrl);

    System.out.println("branchTaken = `" + branchTaken + "`");
    System.out.println("LOC_MAP_URL (after) = `" + currentMapUrl + "`");
}

// insert-calendar-event.bsh reads these. event_duration = 0 means there is no event to insert
variableSetter.setInt("event_duration", eventDurationMins);
variableSetter.setString("event_title", eventTitle);
variableSetter.setString("event_description", eventDescription);
variableSetter.setString("event_location", eventLocation);
variableSetter.setString("event_calendar_name", eventCalendarName);

System.out.println();
System.out.println("event_duration = `" + eventDurationMins + "`");
System.out.println("event_title = `" + eventTitle + "`");
System.out.println("event_description = `" + eventDescription + "`");
System.out.println("event_location = `" + eventLocation + "`");
System.out.println("event_calendar_name = `" + eventCalendarName + "`");

// Store current location
if (locationKnown) {
    variableSetter.setDecimal("LOC_NOW_LAT", currentLat);
    variableSetter.setDecimal("LOC_NOW_LON", currentLon);

    System.out.println();
    System.out.println("LOC_NOW_LAT (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_NOW_LAT" + "}") + "`");
    System.out.println("LOC_NOW_LON (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_NOW_LON" + "}") + "`");
    System.out.println("LOC_TRAVEL_TIME (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_TRAVEL_TIME" + "}") + "`");
}
