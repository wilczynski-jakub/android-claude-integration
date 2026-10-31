// Required global variables: LOC_LAST_LAT, LOC_LAST_LON, LOC_MAP_URL, LOC_TRAVEL_MINS, LOC_MAX_EVENT_MINS, LOC_CHECK_INTERVAL, LOC_CALENDAR_NAME
// Required local variables: event_duration_mins, event_title, event_description, event_location, event_calendar_name

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.location.LocationManager;
import android.os.Build;

double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
	double earthRadiusMeters = 6371000.0;
	double deltaLat = Math.toRadians(lat2 - lat1);
	double deltaLon = Math.toRadians(lon2 - lon1);

	double haversine = Math.sin(deltaLat / 2.0) * Math.sin(deltaLat / 2.0)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
			* Math.sin(deltaLon / 2.0) * Math.sin(deltaLon / 2.0);

	return 2.0 * earthRadiusMeters * Math.atan2(
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
double globalLocLastLatBefore = magicText.evaluateDecimal("{" + "v=LOC_LAST_LAT" + "}");
double globalLocLastLonBefore = magicText.evaluateDecimal("{" + "v=LOC_LAST_LON" + "}");
String globalLocMapUrlBefore = magicText.evaluateString("{" + "v=LOC_MAP_URL" + "}");
int globalLocTravelMinsBefore = magicText.evaluateInt("{" + "v=LOC_TRAVEL_MINS" + "}");
System.out.println("LOC_LAST_LAT (before) = `" + globalLocLastLatBefore + "`");
System.out.println("LOC_LAST_LON (before) = `" + globalLocLastLonBefore + "`");
System.out.println("LOC_MAP_URL (before) = `" + globalLocMapUrlBefore + "`");
System.out.println("LOC_TRAVEL_MINS (before) = `" + globalLocTravelMinsBefore + "`");

int globalLocMaxEventMins = magicText.evaluateInt("{" + "v=LOC_MAX_EVENT_MINS" + "}");
System.out.println("LOC_MAX_EVENT_MINS = `" + globalLocMaxEventMins + "`");
System.out.println();

boolean previousLocationSet =
        globalLocLastLatBefore != 0 &&
        globalLocLastLonBefore != 0;

// Calculate distance between previous and current location
double distanceMeters = previousLocationSet
        ? calculateDistance(globalLocLastLatBefore, globalLocLastLonBefore, currentLat, currentLon)
        : 0.0;

System.out.println("previousLocationSet = `" + previousLocationSet + "`");
System.out.println("distanceMeters = `" + distanceMeters + "`");
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
} else if (previousLocationSet && distanceMeters > 100.0 + locationAccuracy && globalLocTravelMinsBefore < globalLocMaxEventMins) {
    branchTaken = "location_changed";

    int globalLocCheckIntervalMins = magicText.evaluateInt("{" + "v=LOC_CHECK_INTERVAL[minutes]" + "}");
    int globalLocTravelMinsAfter = globalLocTravelMinsBefore + globalLocCheckIntervalMins;
    variableSetter.setInt("LOC_TRAVEL_MINS", globalLocTravelMinsAfter);

    System.out.println("branchTaken = `" + branchTaken + "`");
    System.out.println("globalLocCheckIntervalMins = `" + globalLocCheckIntervalMins + "`");
    System.out.println("LOC_TRAVEL_MINS (after) = `" + globalLocTravelMinsAfter + "`");
} else if (globalLocTravelMinsBefore > 0) {
    // Location stopped changing -> create calendar event
    branchTaken = "location_stopped";

    eventDurationMins = globalLocTravelMinsBefore;
    eventTitle = "commuting...";
    eventDescription = "TO: " + currentMapUrl;
    eventLocation = globalLocMapUrlBefore;
    eventCalendarName = magicText.evaluateString("{" + "v=LOC_CALENDAR_NAME" + "}");

    System.out.println("branchTaken = `" + branchTaken + "`");

    variableSetter.setInt("LOC_TRAVEL_MINS", 0);
    variableSetter.setString("LOC_MAP_URL", currentMapUrl);

    System.out.println("LOC_TRAVEL_MINS (after) = `" + magicText.evaluateInt("{" + "v=LOC_TRAVEL_MINS" + "}") + "`");
    System.out.println("LOC_MAP_URL (after) = `" + currentMapUrl + "`");
} else {
    // First detected location change
    branchTaken = "first_location_change";
    variableSetter.setString("LOC_MAP_URL", currentMapUrl);

    System.out.println("branchTaken = `" + branchTaken + "`");
    System.out.println("LOC_MAP_URL (after) = `" + currentMapUrl + "`");
}

// insert-calendar-event.bsh reads these. event_duration_mins = 0 means there is no event to insert
variableSetter.setInt("event_duration_mins", eventDurationMins);
variableSetter.setString("event_title", eventTitle);
variableSetter.setString("event_description", eventDescription);
variableSetter.setString("event_location", eventLocation);
variableSetter.setString("event_calendar_name", eventCalendarName);

System.out.println();
System.out.println("event_duration_mins = `" + eventDurationMins + "`");
System.out.println("event_title = `" + eventTitle + "`");
System.out.println("event_description = `" + eventDescription + "`");
System.out.println("event_location = `" + eventLocation + "`");
System.out.println("event_calendar_name = `" + eventCalendarName + "`");

// Store current location
if (locationKnown) {
    variableSetter.setDecimal("LOC_LAST_LAT", currentLat);
    variableSetter.setDecimal("LOC_LAST_LON", currentLon);

    System.out.println();
    System.out.println("LOC_LAST_LAT (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_LAST_LAT" + "}") + "`");
    System.out.println("LOC_LAST_LON (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_LAST_LON" + "}") + "`");
    System.out.println("LOC_TRAVEL_MINS (after) = `" + magicText.evaluateDecimal("{" + "v=LOC_TRAVEL_MINS" + "}") + "`");
}
