/* MacroDroid: reads local goal, current_step, contents_str, file rules.json; writes file rules.json; returns resultJson */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

String resultJson = "";

String RULES_FILE_NAME = "rules.json";
String RULES_BACKUP_FILE_NAME = "rules.backup.json";

// Change this value to wipe every saved rule once. The next run finds a rules file that does not carry it,
// moves that file aside and starts from an empty one. It is only a label: the clock is never consulted.
String RULES_RESET_ID = "2026-10-12 15:55";
String DEFAULT_FIRST_STEP = "start";
String DONE_STEP = "done";
String NO_VIEW_ID_KEY_PREFIX = "index:";
int MAX_TEXT_LENGTH = 60;
int MAX_FAILURES = 2;

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

JSONObject createEmptyRules() {
	JSONObject rules = new JSONObject();
	rules.put("resetId", RULES_RESET_ID);
	rules.put("globalRules", new JSONArray());
	rules.put("goals", new JSONObject());

	return rules;
}

JSONObject createEmptyGoal(String resetId) {
	JSONObject goalRules = new JSONObject();
	goalRules.put("resetId", resetId);
	goalRules.put("description", "");
	goalRules.put("firstStep", DEFAULT_FIRST_STEP);
	goalRules.put("doneWhen", new JSONArray());
	goalRules.put("steps", new JSONObject());

	return goalRules;
}

// Returns the saved rules, unless they predate the current RULES_RESET_ID: then they are moved aside as a
// backup and an empty set carrying that id takes their place, which is why a given id resets only once.
JSONObject loadRules(File rulesFile) {
	if (rulesFile.exists()) {
		JSONObject rules = new JSONObject(readFile(rulesFile));
		String resetId = rules.optString("resetId");
		System.out.println("rules.resetId = `" + resetId + "`");
		if (resetId.equals(RULES_RESET_ID)) {
			return rules;
		}

		File backupFile = new File(rulesFile.getParentFile(), RULES_BACKUP_FILE_NAME);
		if (backupFile.exists()) {
			backupFile.delete();
		}

		boolean isMovedToBackup = rulesFile.renameTo(backupFile);
		System.out.println("RULES_RESET_ID = `" + RULES_RESET_ID + "`");
		System.out.println("isMovedToBackup = `" + isMovedToBackup + "`");
		System.out.println();
	}

	JSONObject emptyRules = createEmptyRules();
	writeFile(rulesFile, emptyRules.toString(2));

	return emptyRules;
}

// Change the value returned for a goal to wipe the rules of that one goal, once: the next run finds the goal
// saved under another id and starts it empty. Other goals and the global rules are left alone.
// Like RULES_RESET_ID, the value is only a label.
String goalResetId(String goal) {
	if (goal.equals("oralb-devices")) {
		return "2026-10-12 22:10";
	}

	return "";
}

JSONObject getOrCreateGoal(JSONObject rules, String goal, File rulesFile) {
	JSONObject goals = rules.getJSONObject("goals");
	JSONObject savedGoal = goals.optJSONObject(goal);
	String resetId = goalResetId(goal);
	boolean isGoalCurrent = savedGoal != null && savedGoal.optString("resetId").equals(resetId);
	System.out.println("isGoalCurrent = `" + isGoalCurrent + "`");
	if (!isGoalCurrent) {
		goals.put(goal, createEmptyGoal(resetId));
		writeFile(rulesFile, rules.toString(2));
	}

	return goals.getJSONObject(goal);
}

String cleanViewId(String viewId) {
	return viewId.replaceAll("\\$[0-9]+$", "");
}

String shortViewId(String viewId) {
	int prefixEnd = viewId.indexOf(":id/");
	if (prefixEnd < 0) {
		return viewId;
	}

	return viewId.substring(prefixEnd + 4);
}

// MacroDroid keys an element that has no view id by its position on the screen ("index:26").
boolean hasNoViewId(String screenKey) {
	return screenKey.startsWith(NO_VIEW_ID_KEY_PREFIX);
}

// Returns the screen key as MacroDroid reports it, keeping its "$2", "$3"... suffix: that suffix is what
// tells apart several elements sharing one viewId, so the click needs it to hit the right one.
// An element without a view id is recognisable by its text alone, so it can satisfy a condition that names
// no view id; it is never returned as something to click, because there is no view id to click.
String findScreenKey(JSONObject screen, JSONObject condition, boolean mustHaveViewId) {
	String textPart = condition.optString("textContains").toLowerCase();
	String viewIdPart = condition.optString("viewIdContains").toLowerCase();
	Iterator keys = screen.keys();
	while (keys.hasNext()) {
		String screenKey = (String) keys.next();
		boolean isWithoutViewId = hasNoViewId(screenKey);
		if (isWithoutViewId && (mustHaveViewId || viewIdPart.length() > 0)) {
			continue;
		}

		String text = screen.optString(screenKey).toLowerCase();
		boolean isViewIdMatch = isWithoutViewId || screenKey.toLowerCase().contains(viewIdPart);
		if (text.contains(textPart) && isViewIdMatch) {
			return screenKey;
		}
	}

	return null;
}

boolean allConditionsMatch(JSONObject screen, JSONArray conditions) {
	if (conditions == null) {
		return true;
	}

	for (int i = 0; i < conditions.length(); i++) {
		if (findScreenKey(screen, conditions.getJSONObject(i), false) == null) {
			return false;
		}
	}

	return true;
}

boolean isGoalDone(JSONObject screen, JSONArray doneWhen) {
	if (doneWhen == null || doneWhen.length() == 0) {
		return false;
	}

	return allConditionsMatch(screen, doneWhen);
}

boolean isRuleActive(JSONObject rule) {
	return !rule.optString("status").equals("disabled") && rule.optInt("failures") < MAX_FAILURES;
}

JSONObject resolveAction(JSONObject ruleAction, JSONObject screen) {
	JSONObject action = new JSONObject(ruleAction.toString());
	if (action.optString("type").equals("CLICK")) {
		String viewId = findScreenKey(screen, action, true);
		if (viewId == null) {
			return null;
		}

		action.put("viewId", viewId);
	}

	return action;
}

JSONObject findMatchingRule(JSONArray rules, JSONObject screen) {
	if (rules == null) {
		return null;
	}

	for (int i = 0; i < rules.length(); i++) {
		JSONObject rule = rules.getJSONObject(i);
		boolean matches = isRuleActive(rule)
			&& allConditionsMatch(screen, rule.optJSONArray("when"))
			&& resolveAction(rule.getJSONObject("action"), screen) != null;
		if (matches) {
			return rule;
		}
	}

	return null;
}

String buildFilteredScreen(JSONObject screen) {
	HashSet seenLines = new HashSet();
	StringBuilder filtered = new StringBuilder();
	Iterator keys = screen.keys();
	while (keys.hasNext()) {
		String viewId = (String) keys.next();
		String text = screen.optString(viewId).replaceAll("\\s+", " ").trim();
		if (text.length() < 2) {
			continue;
		}

		if (text.length() > MAX_TEXT_LENGTH) {
			text = text.substring(0, MAX_TEXT_LENGTH);
		}

		// An element without a view id is listed too, with nothing after the bar: its text is often the only
		// thing that tells two screens of one app apart.
		String shownViewId = hasNoViewId(viewId) ? "" : shortViewId(cleanViewId(viewId));
		String line = text + " | " + shownViewId;
		if (seenLines.add(line)) {
			filtered.append(line).append("\n");
		}
	}

	return filtered.toString();
}

String goal = magicText.evaluateString("{" + "lv=goal" + "}");
String currentStep = magicText.evaluateString("{" + "lv=current_step" + "}");

System.out.println("goal = `" + goal + "`");
System.out.println("currentStep = `" + currentStep + "`");
System.out.println();

boolean done = false;
boolean matchFound = false;
boolean finishesGoal = false;
String ruleId = "";
String nextStep = "";
JSONObject action = new JSONObject();
String filteredScreen = "";
String matchError = "";

try {
	JSONObject screen = new JSONObject(magicText.evaluateString("{" + "lv=contents_str" + "}"));
	File rulesFile = new File(appContext.getFilesDir(), RULES_FILE_NAME);
	JSONObject rules = loadRules(rulesFile);
	JSONObject goalRules = getOrCreateGoal(rules, goal, rulesFile);
	if (currentStep.length() == 0) {
		currentStep = goalRules.getString("firstStep");
		System.out.println("currentStep (from firstStep) = `" + currentStep + "`");
	}

	filteredScreen = buildFilteredScreen(screen);
	nextStep = currentStep;
	done = isGoalDone(screen, goalRules.optJSONArray("doneWhen"));
	if (!done) {
		JSONObject rule = findMatchingRule(rules.optJSONArray("globalRules"), screen);
		boolean isGlobal = rule != null;
		if (!isGlobal) {
			JSONArray stepRules = goalRules.getJSONObject("steps").optJSONArray(currentStep);
			rule = findMatchingRule(stepRules, screen);
		}

		if (rule != null) {
			System.out.println("isGlobal = `" + isGlobal + "`");
			matchFound = true;
			ruleId = rule.optString("id");
			action = resolveAction(rule.getJSONObject("action"), screen);
			if (!isGlobal) {
				// Older rules may still name their next step "done"; a step name no longer finishes the goal, so stay put.
				String ruleNextStep = rule.optString("nextStep", currentStep);
				nextStep = ruleNextStep.equals(DONE_STEP) ? currentStep : ruleNextStep;
				finishesGoal = rule.optBoolean("finishesGoal");
			}
		}
	}
} catch (Exception exception) {
	matchError = exception.toString();
}

System.out.println("done = `" + done + "`");
System.out.println("matchFound = `" + matchFound + "`");
System.out.println("finishesGoal = `" + finishesGoal + "`");
System.out.println("ruleId = `" + ruleId + "`");
System.out.println("nextStep = `" + nextStep + "`");
System.out.println("action = `" + action + "`");
System.out.println("matchError = `" + matchError + "`");
System.out.println("filteredScreen = `" + filteredScreen + "`");

JSONObject result = new JSONObject();
result.put("done", done);
result.put("match_found", matchFound);
result.put("finishes_goal", finishesGoal);
result.put("rule_id", ruleId);
result.put("current_step", currentStep);
result.put("next_step", nextStep);
result.put("filtered_screen", filteredScreen);
result.put("match_error", matchError);
result.put("action", action);
resultJson = result.toString();

resultJson;
