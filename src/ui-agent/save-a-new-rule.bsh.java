// Required local variables: goal_name, current_step, response_text, contents_json

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

String saveResultJSON = "";

String RULES_FILE_NAME = "rules.json";
String DEFAULT_FIRST_STEP = "start";
String DONE_STEP = "done";
String NO_VIEW_ID_KEY_PREFIX = "index:";
String SCREEN_MISMATCH_SUFFIX = "the current screen";

String readFile(File file) {
	BufferedReader reader = new BufferedReader(new FileReader(file));
	StringBuilder content = new StringBuilder();
	String line;
	while ((line = reader.readLine()) != null) {
		content.append(line).append("\n");
	}

	reader.close();

	return content.toString();
}

void writeFile(File file, String content) {
	FileWriter writer = new FileWriter(file);
	writer.write(content);
	writer.close();
}

JSONObject loadRules(File rulesFile) {
	if (!rulesFile.exists()) {
		JSONObject rules = new JSONObject();
		rules.put("globalRules", new JSONArray());
		rules.put("goals", new JSONObject());

		return rules;
	}

	return new JSONObject(readFile(rulesFile));
}

JSONObject getOrCreateGoal(JSONObject rules, String goalName) {
	JSONObject goals = rules.getJSONObject("goals");
	if (!goals.has(goalName)) {
		JSONObject goalRules = new JSONObject();
		goalRules.put("description", "");
		goalRules.put("firstStep", DEFAULT_FIRST_STEP);
		goalRules.put("doneWhen", new JSONArray());
		goalRules.put("steps", new JSONObject());
		goals.put(goalName, goalRules);
	}

	return goals.getJSONObject(goalName);
}

// MacroDroid keys an element that has no view id by its position on the screen ("index:26").
boolean hasNoViewId(String screenKey) {
	return screenKey.startsWith(NO_VIEW_ID_KEY_PREFIX);
}

// Returns the screen key as MacroDroid reports it, keeping its "$2", "$3"... suffix: that suffix is what
// tells apart several elements sharing one viewId, so the click needs it to hit the right one.
// An element without a view id is recognisable by its text alone, so it can satisfy a condition that names
// no view id; it is never returned as something to click, because there is no view id to click.
String findScreenKey(JSONObject screenContents, JSONObject condition, boolean mustHaveViewId) {
	String textContains = condition.optString("textContains").toLowerCase();
	String viewIdContains = condition.optString("viewIdContains").toLowerCase();
	Iterator keys = screenContents.keys();
	while (keys.hasNext()) {
		String screenKey = (String) keys.next();
		boolean isWithoutViewId = hasNoViewId(screenKey);
		if (isWithoutViewId && (mustHaveViewId || viewIdContains.length() > 0)) {
			continue;
		}

		String text = screenContents.optString(screenKey).toLowerCase();
		boolean isViewIdMatch = isWithoutViewId || screenKey.toLowerCase().contains(viewIdContains);
		if (text.contains(textContains) && isViewIdMatch) {
			return screenKey;
		}
	}

	return null;
}

boolean allConditionsMatch(JSONObject screenContents, JSONArray conditions) {
	if (conditions == null) {
		return true;
	}

	for (int i = 0; i < conditions.length(); i++) {
		if (findScreenKey(screenContents, conditions.getJSONObject(i), false) == null) {
			return false;
		}
	}

	return true;
}

JSONObject resolveAction(JSONObject ruleAction, JSONObject screenContents) {
	JSONObject action = new JSONObject(ruleAction.toString());
	if (action.optString("type").equals("CLICK")) {
		String viewId = findScreenKey(screenContents, action, true);
		if (viewId == null) {
			return null;
		}

		action.put("viewId", viewId);
	}

	return action;
}

JSONObject parseResponse(String responseText) {
	int jsonStart = responseText.indexOf("{");
	int jsonEnd = responseText.lastIndexOf("}");
	if (jsonStart < 0 || jsonEnd <= jsonStart) {
		throw new Exception("No JSON object in response");
	}

	return new JSONObject(responseText.substring(jsonStart, jsonEnd + 1));
}

String validateRule(JSONObject rule, JSONObject screenContents) {
	if (rule.optString("id").length() == 0) {
		return "Rule has no id";
	}

	JSONObject action = rule.optJSONObject("action");
	if (action == null) {
		return "Rule has no action";
	}

	String type = action.optString("type");
	if (!type.equals("CLICK") && !type.equals("BACK") && !type.equals("WAIT")) {
		return "Unknown action type: " + type;
	}

	if (type.equals("WAIT") && action.optInt("seconds") <= 0) {
		return "WAIT has no seconds";
	}

	if (!allConditionsMatch(screenContents, rule.optJSONArray("when"))) {
		return "Conditions do not match the current screen";
	}

	if (resolveAction(action, screenContents) == null) {
		return "Click target not found on the current screen";
	}

	return "";
}

boolean ruleIdExists(JSONArray rules, String ruleId) {
	for (int i = 0; i < rules.length(); i++) {
		if (rules.getJSONObject(i).optString("id").equals(ruleId)) {
			return true;
		}
	}

	return false;
}

JSONArray getOrCreateStepRules(JSONObject goalRules, String step) {
	JSONObject steps = goalRules.getJSONObject("steps");
	if (!steps.has(step)) {
		steps.put(step, new JSONArray());
	}

	return steps.getJSONArray(step);
}

String goalName = magicText.evaluateString("{" + "lv=goal_name" + "}");
String currentStep = magicText.evaluateString("{" + "lv=current_step" + "}");
String responseText = magicText.evaluateString("{" + "lv=response_text" + "}");

System.out.println("goal_name = `" + goalName + "`");
System.out.println("currentStep = `" + currentStep + "`");
System.out.println("responseText = `" + responseText + "`");
System.out.println();

boolean saved = false;
String scope = "";
String ruleId = "";
String nextStep = currentStep;
JSONObject action = new JSONObject();
String saveError = "";
boolean goalDone = false;
boolean finishesGoal = false;

try {
	JSONObject screenContents = new JSONObject(magicText.evaluateString("{" + "lv=contents_json" + "}"));
	File rulesFile = new File(appContext.getFilesDir(), RULES_FILE_NAME);
	JSONObject rules = loadRules(rulesFile);
	JSONObject goalRules = getOrCreateGoal(rules, goalName);
	if (currentStep.length() == 0) {
		currentStep = goalRules.getString("firstStep");
		nextStep = currentStep;
		System.out.println("currentStep (from firstStep) = `" + currentStep + "`");
	}

	JSONObject response = parseResponse(responseText);
	scope = response.optString("scope");
	if (scope.equals("none")) {
		saveError = "Claude: " + response.optString("reason");
	} else if (scope.equals("done")) {
		// The goal only counts as finished on evidence: conditions that hold on the screen being looked at right now.
		JSONArray doneWhen = response.optJSONArray("doneWhen");
		if (doneWhen == null || doneWhen.length() == 0) {
			saveError = "Done has no doneWhen conditions";
		} else if (!allConditionsMatch(screenContents, doneWhen)) {
			saveError = "doneWhen does not match the current screen";
		} else {
			goalRules.put("doneWhen", doneWhen);
			writeFile(rulesFile, rules.toString(2));
			System.out.println("doneWhen = `" + doneWhen + "`");
			System.out.println();

			goalDone = true;
		}
	} else if (!scope.equals("global") && !scope.equals("step")) {
		saveError = "Unknown scope: " + scope;
	} else {
		JSONObject rule = response.getJSONObject("rule");
		saveError = validateRule(rule, screenContents);
		// A screen that only flashes by (a brief "please wait") is gone by the time it is read again here. The rule
		// was still written for a screen that really was shown, so it is kept for the next time that screen is
		// caught; it is only not executed now, because its target can no longer be found.
		boolean isRuleForEarlierScreen = saveError.endsWith(SCREEN_MISMATCH_SUFFIX);
		System.out.println("isRuleForEarlierScreen = `" + isRuleForEarlierScreen + "`");
		if (saveError.length() == 0 || isRuleForEarlierScreen) {
			JSONArray targetRules;
			if (scope.equals("global")) {
				rule.remove("expect");
				rule.remove("nextStep");
				rule.remove("finishesGoal");
				targetRules = rules.getJSONArray("globalRules");
			} else {
				// A rule cannot finish the goal by naming its next step "done": that takes a "done" answer with evidence.
				String ruleNextStep = rule.optString("nextStep");
				if (ruleNextStep.length() == 0 || ruleNextStep.equals(DONE_STEP)) {
					rule.put("nextStep", currentStep);
				}

				nextStep = rule.getString("nextStep");
				finishesGoal = rule.optBoolean("finishesGoal");
				targetRules = getOrCreateStepRules(goalRules, currentStep);
			}

			if (ruleIdExists(targetRules, rule.getString("id"))) {
				rule.put("id", rule.getString("id") + "-" + System.currentTimeMillis());
			}

			rule.put("status", "unverified");
			rule.put("failures", 0);
			targetRules.put(rule);
			writeFile(rulesFile, rules.toString(2));
			System.out.println("rulesFile = `" + rulesFile + "`");
			System.out.println("rule = `" + rule + "`");
			System.out.println();

			if (!isRuleForEarlierScreen) {
				saved = true;
				ruleId = rule.getString("id");
				action = resolveAction(rule.getJSONObject("action"), screenContents);
			}
		}
	}
} catch (Exception exception) {
	saveError = exception.toString();
}

System.out.println("saved = `" + saved + "`");
System.out.println("scope = `" + scope + "`");
System.out.println("ruleId = `" + ruleId + "`");
System.out.println("nextStep = `" + nextStep + "`");
System.out.println("action = `" + action + "`");
System.out.println("saveError = `" + saveError + "`");
System.out.println("goalDone = `" + goalDone + "`");
System.out.println("finishesGoal = `" + finishesGoal + "`");

// The screen is read again here, a few seconds after the one Claude was shown: when it has moved on meanwhile,
// the answer is merely out of date, which the planner handles by looking at the screen again instead of failing.
boolean isScreenMismatch = saveError.endsWith(SCREEN_MISMATCH_SUFFIX);
System.out.println("isScreenMismatch = `" + isScreenMismatch + "`");

JSONObject saveResult = new JSONObject();
saveResult.put("saved", saved);
saveResult.put("screen_mismatch", isScreenMismatch);
saveResult.put("done", goalDone);
saveResult.put("finishes_goal", finishesGoal);
saveResult.put("scope", scope);
saveResult.put("rule_id", ruleId);
saveResult.put("next_step", nextStep);
saveResult.put("action", action);
saveResult.put("save_error", saveError);
saveResultJSON = saveResult.toString();

saveResultJSON; // Output Variable

