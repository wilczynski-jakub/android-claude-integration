/* MacroDroid: reads local rules_json; writes success, action_type, action_view_id, action_seconds */

import org.json.JSONObject;

int RAW_RULE_LOG_LENGTH = 300;

String firstChars(String text, int maxLength) {
	if (text.length() <= maxLength) {
		return text;
	}

	return text.substring(0, maxLength);
}

void writeOutputs(String actionType, String actionViewId, int actionSeconds) {
	variableSetter.setString("action_type", actionType);
	variableSetter.setString("action_view_id", actionViewId);
	variableSetter.setInt("action_seconds", actionSeconds);
}

// Clear the outputs first, so a run that fails midway never leaves the previous run's values behind.
// "success" is only cleared here: the block sets it to true itself, after its native action has run.
writeOutputs("", "", 0);
variableSetter.setBoolean("success", false);

String rulesJson = magicText.evaluateString("{" + "lv=rules_json" + "}");
System.out.println("rulesJson (first " + RAW_RULE_LOG_LENGTH + " chars) = `" + firstChars(rulesJson, RAW_RULE_LOG_LENGTH) + "`");

String actionType = "";
String actionViewId = "";
int actionSeconds = 0;

try {
	JSONObject action = new JSONObject(rulesJson).optJSONObject("action");
	if (action == null) {
		System.out.println("error = `Rule has no action`");
	} else {
		actionType = action.optString("type");
		actionViewId = action.optString("viewId");
		actionSeconds = action.optInt("seconds");
	}
} catch (Exception exception) {
	System.out.println("exception = `" + exception + "`");
}

System.out.println("actionType = `" + actionType + "`");
System.out.println("actionViewId = `" + actionViewId + "`");
System.out.println("actionSeconds = `" + actionSeconds + "`");

writeOutputs(actionType, actionViewId, actionSeconds);
