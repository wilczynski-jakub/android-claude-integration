// Required local variables: rule_result_json, action_dict

import org.json.JSONObject;

int RAW_RULE_LOG_LENGTH = 300;

String firstChars(String text, int maxLength) {
	if (text.length() <= maxLength) {
		return text;
	}

	return text.substring(0, maxLength);
}

void writeActionDict(String actionType, String actionViewId, int actionSeconds) {
	JSONObject actionDict = new JSONObject();

	actionDict.put("type", actionType);
	actionDict.put("view_id", actionViewId);
	actionDict.put("seconds", actionSeconds);

	variableSetter.setDict("action_dict", actionDict.toString());
}

// Clear the outputs first, so a run that fails midway never leaves the previous run's values behind.
// "success" is only cleared here: the block sets it to true itself, after its native action has run.
writeActionDict("", "", 0);
variableSetter.setBoolean("success", false);

String ruleResultJSON = magicText.evaluateString("{" + "lv=rule_result_json" + "}");
System.out.println("ruleResultJSON (first " + RAW_RULE_LOG_LENGTH + " chars) = `" + firstChars(ruleResultJSON, RAW_RULE_LOG_LENGTH) + "`");

String actionType = "";
String actionViewId = "";
int actionSeconds = 0;

try {
	JSONObject action = new JSONObject(ruleResultJSON).optJSONObject("action");

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

writeActionDict(actionType, actionViewId, actionSeconds);

System.out.println("action_dict[type] = `" + magicText.evaluateString("{" + "lv=action_dict[type]" + "}") + "`");
System.out.println("action_dict[view_id] = `" + magicText.evaluateString("{" + "lv=action_dict[view_id]" + "}") + "`");
System.out.println("action_dict[seconds] = `" + magicText.evaluateInt("{" + "lv=action_dict[seconds]" + "}") + "`");
