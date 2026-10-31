// Required local variables: goal_name, goal_description, max_api_calls, agent_memory_json, match_result_json, ask_success, save_result_json, exec_success, user_instruction

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

String RULES_FILE_NAME = "rules.json";
String DISABLED_STATUS = "disabled";
String SCREEN_UNCHANGED_REASON = "action did not change the screen";
int MAX_UNCHANGED_SCREEN_STREAK = 3;
int MAX_UNCHANGED_WAIT_STREAK = 5;
int MAX_SAME_SCREEN_EXECUTIONS = 3;
String NO_USER_INSTRUCTION = "none";
String NO_SCREEN = "";
String SAME_SCREEN_MARK = " [same screen as now]";
String OTHER_SCREEN_MARK = " [a different screen]";
int MAX_HISTORY_ENTRIES = 15;
int HISTORY_SCREEN_LINES = 3;
int SAME_SCREEN_WAIT_MS = 3000;
int RAW_RESULT_LOG_LENGTH = 300;

// "<" and ">" stand for "{" and "}" so MacroDroid never treats the JSON examples as magic text.
String SYSTEM_PROMPT_TEMPLATE =
	"You generate exactly one rule for an Android UI automation engine.\n\n"
	+ "The screen is a list of elements, one per line, as \"text | viewId\". The viewId has the package prefix removed, and long texts are cut to 60 characters.\n"
	+ "An element that has no view id is listed with nothing after the bar. Use such an element to recognise a screen, in a condition with \"viewIdContains\": \"\"; it is often the only thing that tells two screens of one app apart. It cannot be the target of a CLICK.\n"
	+ "A condition <\"textContains\": A, \"viewIdContains\": B> matches when some element's text contains A and its viewId contains B.\n"
	+ "A rule matches when all its \"when\" conditions match.\n\n"
	+ "Allowed actions:\n"
	+ "<\"type\": \"CLICK\", \"textContains\": \"...\", \"viewIdContains\": \"...\">\n"
	+ "<\"type\": \"BACK\">\n"
	+ "<\"type\": \"WAIT\", \"seconds\": N>\n\n"
	+ "Decide what kind of rule is needed:\n"
	+ "- If the screen shows an error or failure message (for example that a device cannot be reached, or that something failed), do not dismiss it and do not retry: return <\"scope\": \"none\", \"reason\": \"the error text\">, so that a person decides what to do.\n"
	+ "- If the goal is to print and the screen is the home screen of the Canon PRINT app (its tiles such as Document Print, Photo Print, Scan and Smartphone Copy), treat that as an error too: the document to print is not open. Do not pick a tile; return <\"scope\": \"none\", \"reason\": \"...\"> saying so.\n"
	+ "- If the screen shows a harmless popup that is not part of the goal flow and reports no error (a tip, an advertisement, a rating request), return a global rule that dismisses it. Global rules have no \"expect\" and no \"nextStep\".\n"
	+ "- Otherwise return a step rule for the current step that moves the goal forward.\n"
	+ "- If the screen itself states that the goal has been achieved (for example \"Printing is completed!\" or a message that the result was saved), finish the goal on this screen, because that statement will be gone afterwards:\n"
	+ "\t- when the screen still offers a button that closes or confirms it (such as OK, Exit or Done), return a step rule that presses that button, with \"finishesGoal\": true. The goal then counts as finished as soon as that action has run, and nothing more is done.\n"
	+ "\t- when nothing is left to press, return <\"scope\": \"done\", \"doneWhen\": [conditions]> with conditions that match this screen and no earlier one.\n"
	+ "\tA screen that only starts the operation, or shows it still in progress, is never the finish: leave \"finishesGoal\" false there.\n"
	+ "- If the goal cannot be advanced from this screen, return <\"scope\": \"none\", \"reason\": \"...\">.\n\n"
	+ "A user instruction, when it is not \"none\", comes from the person watching the device after the automation got stuck. It outranks the history, your own reading of the screen and the instruction above about error messages: return the rule that does what it says on this screen.\n\n"
	+ "If the history says that waiting did not change this screen, return a WAIT rule again only when the screen itself shows work in progress (a progress or please-wait message). Otherwise the waiting was a mistake: act on the screen, or return scope \"none\".\n\n"
	+ "The history lists, oldest first, what was already done in this run. Each action ends with [same screen as now] or [a different screen]; that mark is exact, so trust it over any resemblance between the texts.\n"
	+ "- A screen marked [a different screen] is not the current one, however alike the first texts look. If no action is marked [same screen as now], the current screen is new: act on it normally. Apps often ask for the same button twice in a row, for example a Print button and then a Print button on a confirmation screen.\n"
	+ "- Only when an action marked [same screen as now] already did what you were about to do should you pick a different action.\n"
	+ "- Do not return scope \"none\" merely because an action may have to be repeated or may not work: the engine retries actions and stops loops by itself. Keep \"none\" for error messages and for screens where no element can advance the goal.\n\n"
	+ "Output only a JSON object, with no prose and no markdown:\n"
	+ "<\n"
	+ "\t\"scope\": \"global\" or \"step\",\n"
	+ "\t\"rule\": <\n"
	+ "\t\t\"id\": \"short-kebab-case-id\",\n"
	+ "\t\t\"when\": [conditions],\n"
	+ "\t\t\"action\": one action,\n"
	+ "\t\t\"expect\": [conditions],\n"
	+ "\t\t\"nextStep\": \"step name\",\n"
	+ "\t\t\"finishesGoal\": true or false\n"
	+ "\t>\n"
	+ ">\n\n"
	+ "Requirements:\n"
	+ "- In \"when\" and in CLICK actions use only texts and viewIds that appear on the current screen.\n"
	+ "- Choose short but distinctive substrings that identify this screen only; avoid numbers, dates and file names.\n"
	+ "- \"expect\" describes what should be on the screen after the action; use an empty list if you cannot predict it.\n"
	+ "- \"nextStep\" is the current step or a new short step name; it never says the goal is finished.\n"
	+ "- If failed rules are given, their action did not change the screen: never return a rule with the same action, return one that takes a different action.";

String magicValue(String name) {
	return magicText.evaluateString("{" + name + "}");
}

JSONObject parseJSON(String text) {
	if (text.trim().length() == 0) {
		return new JSONObject();
	}

	return new JSONObject(text);
}

String firstChars(String text, int maxLength) {
	if (text.length() <= maxLength) {
		return text;
	}

	return text.substring(0, maxLength);
}

boolean isTrue(Object value) {
	return String.valueOf(value).trim().equalsIgnoreCase("true");
}

void planError(JSONObject plan, String message) {
	plan.put("call", "ERROR");
	plan.put("message", message);
}

void showToast(String toastText) {
	new Handler(Looper.getMainLooper()).post(new Runnable() {
		public void run() {
			Toast.makeText(appContext, toastText, Toast.LENGTH_SHORT).show();
		}
	});
}

// ruleResult is the parsed match_result_json or save_result_json: both carry the resolved "action" and its "rule_id".
String actionTypeOf(JSONObject ruleResult) {
	JSONObject action = ruleResult.optJSONObject("action");
	if (action == null) {
		return "";
	}

	return action.optString("type");
}

void showActionToast(JSONObject ruleResult, String ruleOrigin) {
	String toastText = actionTypeOf(ruleResult) + ": " + ruleOrigin + " " + ruleResult.optString("rule_id");
	System.out.println("toastText = `" + toastText + "`");

	showToast(toastText);
}

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

JSONObject stepsOfGoal(JSONObject rules, String goalName) {
	JSONObject goals = rules.optJSONObject("goals");
	JSONObject goalRules = goals == null ? null : goals.optJSONObject(goalName);
	JSONObject steps = goalRules == null ? null : goalRules.optJSONObject("steps");
	if (steps == null) {
		return new JSONObject();
	}

	return steps;
}

boolean isDisabled(JSONObject rule) {
	return rule.optString("status").equals(DISABLED_STATUS);
}

// Takes the rules with this id out of use and returns how many there were. Removed rules are gone;
// disabled ones stay in the file, where Match skips them and Claude is shown them so they are not proposed again.
int retireRulesIn(JSONArray ruleList, String ruleId, boolean shouldRemove) {
	if (ruleList == null) {
		return 0;
	}

	int retiredRuleCount = 0;
	for (int i = ruleList.length() - 1; i >= 0; i--) {
		JSONObject rule = ruleList.getJSONObject(i);
		if (!rule.optString("id").equals(ruleId)) {
			continue;
		}

		if (shouldRemove) {
			ruleList.remove(i);
			retiredRuleCount++;
		} else if (!isDisabled(rule)) {
			rule.put("status", DISABLED_STATUS);
			rule.put("disabledReason", SCREEN_UNCHANGED_REASON);
			retiredRuleCount++;
		}
	}

	return retiredRuleCount;
}

int retireRules(String goalName, String ruleId, boolean shouldRemove) {
	File rulesFile = new File(appContext.getFilesDir(), RULES_FILE_NAME);
	JSONObject rules = new JSONObject(readFile(rulesFile));
	int retiredRuleCount = retireRulesIn(rules.optJSONArray("globalRules"), ruleId, shouldRemove);
	JSONObject steps = stepsOfGoal(rules, goalName);
	Iterator stepNames = steps.keys();
	while (stepNames.hasNext()) {
		String stepName = (String) stepNames.next();
		retiredRuleCount += retireRulesIn(steps.optJSONArray(stepName), ruleId, shouldRemove);
	}

	if (retiredRuleCount > 0) {
		writeFile(rulesFile, rules.toString(2));
	}

	return retiredRuleCount;
}

void appendDisabledRules(JSONArray ruleList, StringBuilder failedRulesDescription) {
	if (ruleList == null) {
		return;
	}

	for (int i = 0; i < ruleList.length(); i++) {
		JSONObject rule = ruleList.getJSONObject(i);
		if (isDisabled(rule)) {
			JSONObject failedRule = new JSONObject();
			failedRule.put("when", rule.optJSONArray("when"));
			failedRule.put("action", rule.optJSONObject("action"));
			failedRulesDescription.append("\n").append(failedRule.toString());
		}
	}
}

// The disabled global rules and the disabled rules of this step, one JSON object per line, or "none".
String describeFailedRules(String goalName, String step) {
	File rulesFile = new File(appContext.getFilesDir(), RULES_FILE_NAME);
	if (!rulesFile.exists()) {
		return "none";
	}

	JSONObject rules = new JSONObject(readFile(rulesFile));
	StringBuilder failedRulesDescription = new StringBuilder();
	appendDisabledRules(rules.optJSONArray("globalRules"), failedRulesDescription);
	appendDisabledRules(stepsOfGoal(rules, goalName).optJSONArray(step), failedRulesDescription);
	if (failedRulesDescription.length() == 0) {
		return "none";
	}

	return failedRulesDescription.toString();
}

// The texts of the first lines of a filtered screen: enough to recognise that screen in the history.
String summariseScreen(String filteredScreen) {
	String[] lines = filteredScreen.split("\n");
	StringBuilder summary = new StringBuilder();
	for (int i = 0; i < lines.length && i < HISTORY_SCREEN_LINES; i++) {
		int separatorIndex = lines[i].lastIndexOf(" | ");
		String text = separatorIndex < 0 ? lines[i] : lines[i].substring(0, separatorIndex);
		if (summary.length() > 0) {
			summary.append("; ");
		}

		summary.append(text);
	}

	return summary.toString();
}

String describeAction(JSONObject ruleResult) {
	JSONObject action = ruleResult.optJSONObject("action");
	String type = actionTypeOf(ruleResult);
	if (type.equals("CLICK")) {
		return "CLICK \"" + action.optString("textContains") + "\"";
	}

	if (type.equals("WAIT")) {
		return "WAIT " + action.optInt("seconds") + " s";
	}

	return type;
}

// A note in this run's history that is not tied to any particular screen.
void addHistory(JSONObject agentMemory, String text) {
	addScreenHistory(agentMemory, text, NO_SCREEN);
}

// Appends to this run's history; the same entry repeated back to back is counted instead of listed again.
// The screen it happened on is kept, so that it can later be said for certain whether that screen is back.
void addScreenHistory(JSONObject agentMemory, String text, String filteredScreen) {
	JSONArray history = agentMemory.optJSONArray("history");
	if (history == null) {
		history = new JSONArray();
	}

	JSONObject lastEntry = history.length() == 0 ? null : history.getJSONObject(history.length() - 1);
	boolean isRepeatOfLastEntry = lastEntry != null
		&& lastEntry.optString("text").equals(text)
		&& lastEntry.optString("screen").equals(filteredScreen);
	if (isRepeatOfLastEntry) {
		lastEntry.put("count", lastEntry.optInt("count") + 1);
	} else {
		JSONObject entry = new JSONObject();
		entry.put("text", text);
		entry.put("screen", filteredScreen);
		entry.put("count", 1);
		history.put(entry);
	}

	JSONArray recentHistory = new JSONArray();
	for (int i = Math.max(0, history.length() - MAX_HISTORY_ENTRIES); i < history.length(); i++) {
		recentHistory.put(history.getJSONObject(i));
	}

	agentMemory.put("history", recentHistory);
}

// Two screens can open with the same few texts and still be different screens, so whether an entry happened
// on the screen shown now is worked out here, exactly, and stated outright instead of being left to guesswork.
String describeHistory(JSONObject agentMemory, String currentScreen) {
	JSONArray history = agentMemory.optJSONArray("history");
	if (history == null || history.length() == 0) {
		return "none";
	}

	StringBuilder description = new StringBuilder();
	for (int i = 0; i < history.length(); i++) {
		JSONObject entry = history.getJSONObject(i);
		description.append("\n- ").append(entry.optString("text"));
		if (entry.optInt("count") > 1) {
			description.append(" (" + entry.optInt("count") + " times in a row)");
		}

		String entryScreen = entry.optString("screen");
		if (entryScreen.length() > 0) {
			description.append(isSameScreen(entryScreen, currentScreen) ? SAME_SCREEN_MARK : OTHER_SCREEN_MARK);
		}
	}

	return description.toString();
}

// Counts, within this run, how often this rule's CLICK or BACK was run on this very screen. Coming back to the
// same screen and doing the same thing over and over is a loop, however many other screens lie in between.
// Waiting is left out: a WAIT rule is meant to repeat on the same screen.
boolean isLoopingRule(JSONObject agentMemory, JSONObject matchResult, String filteredScreen) {
	if (actionTypeOf(matchResult).equals("WAIT")) {
		return false;
	}

	JSONObject executionCounts = agentMemory.optJSONObject("executionCounts");
	if (executionCounts == null) {
		executionCounts = new JSONObject();
	}

	String executionKey = matchResult.optString("rule_id") + "@" + filteredScreen.hashCode();
	int executionCount = executionCounts.optInt(executionKey) + 1;
	executionCounts.put(executionKey, executionCount);
	agentMemory.put("executionCounts", executionCounts);
	System.out.println("executionCount = `" + executionCount + "`");

	return executionCount > MAX_SAME_SCREEN_EXECUTIONS;
}

// ruleResult's "next_step" is the step the rule moves to, stepBefore the step it was found in.
void rememberExecutedRule(JSONObject agentMemory, JSONObject ruleResult, String screenBefore, String stepBefore) {
	addScreenHistory(agentMemory, "On screen [" + summariseScreen(screenBefore) + "]: " + describeAction(ruleResult), screenBefore);

	JSONObject executedRule = new JSONObject();
	executedRule.put("ruleId", ruleResult.optString("rule_id"));
	executedRule.put("actionType", actionTypeOf(ruleResult));
	executedRule.put("screenBefore", screenBefore);
	executedRule.put("stepBefore", stepBefore);
	executedRule.put("stepAfter", ruleResult.optString("next_step"));
	executedRule.put("finishesGoal", isTrue(ruleResult.opt("finishes_goal")));
	agentMemory.put("executedRule", executedRule);
}

boolean isSameScreen(String filteredScreen, String otherFilteredScreen) {
	return filteredScreen.length() > 0 && filteredScreen.equals(otherFilteredScreen);
}

// How many times in a row this rule has now been executed without the screen changing.
int countUnchangedScreen(JSONObject agentMemory, String ruleId) {
	JSONObject streak = agentMemory.optJSONObject("unchangedScreenStreak");
	boolean isSameRule = streak != null && streak.optString("ruleId").equals(ruleId);
	int count = isSameRule ? streak.optInt("count") + 1 : 1;

	JSONObject newStreak = new JSONObject();
	newStreak.put("ruleId", ruleId);
	newStreak.put("count", count);
	agentMemory.put("unchangedScreenStreak", newStreak);

	return count;
}

// A CLICK or BACK that leaves the screen exactly as it was either achieved nothing or hit a slow app.
// So the rule is tried again from the step it was found in, and only disabled once it has failed
// MAX_UNCHANGED_SCREEN_STREAK times in a row. A WAIT is removed after MAX_UNCHANGED_WAIT_STREAK fruitless
// repeats. Returns true when Match has to run again because of any of that.
boolean retryOrDisableUselessRule(String goalName, JSONObject agentMemory, String filteredScreen, JSONObject plan) {
	JSONObject executedRule = agentMemory.optJSONObject("executedRule");
	agentMemory.remove("executedRule");
	if (executedRule == null) {
		return false;
	}

	String ruleId = executedRule.optString("ruleId");
	String actionType = executedRule.optString("actionType");
	boolean isScreenUnchanged = isSameScreen(filteredScreen, executedRule.optString("screenBefore"));
	System.out.println("executedRule.ruleId = `" + ruleId + "`");
	System.out.println("executedRule.actionType = `" + actionType + "`");
	System.out.println("isScreenUnchanged = `" + isScreenUnchanged + "`");
	if (!isScreenUnchanged) {
		agentMemory.remove("unchangedScreenStreak");

		return false;
	}

	int unchangedScreenCount = countUnchangedScreen(agentMemory, ruleId);
	System.out.println("unchangedScreenCount = `" + unchangedScreenCount + "`");

	if (actionType.equals("WAIT")) {
		// Waiting is meant to repeat, so it gets more tries; but a wait that never changes anything is a dead end.
		// The rule is then removed, so that Claude decides afresh with the fruitless waiting in the history.
		if (unchangedScreenCount < MAX_UNCHANGED_WAIT_STREAK) {
			return false;
		}

		int removedRuleCount = retireRules(goalName, ruleId, true);
		System.out.println("removedRuleCount = `" + removedRuleCount + "`");
		addHistory(agentMemory, "Waiting that many times did not change the screen, so that wait rule was dropped");

		showToast("Wait rule dropped, screen did not change: " + ruleId);

		agentMemory.put("lastAskedScreen", "");
		agentMemory.remove("unchangedScreenStreak");
	} else if (unchangedScreenCount >= MAX_UNCHANGED_SCREEN_STREAK) {
		addHistory(agentMemory, "That action did not change the screen");
		int disabledRuleCount = retireRules(goalName, ruleId, false);
		System.out.println("disabledRuleCount = `" + disabledRuleCount + "`");
		addHistory(agentMemory, "That action was given up on as useless");

		showToast("Rule disabled, screen did not change: " + ruleId);

		// Forget the last question too, otherwise this same screen would only be waited on, never asked about again.
		agentMemory.put("lastAskedScreen", "");
		agentMemory.remove("unchangedScreenStreak");
	} else {
		addHistory(agentMemory, "That action did not change the screen");

		// The step goes back so the rule can match again; if the screen changes late after all, the step it led to is restored.
		agentMemory.put("lateScreenChange", executedRule);
	}

	System.out.println();
	plan.put("currentStep", executedRule.optString("stepBefore"));
	plan.put("call", "MATCH");

	return true;
}

// The retried rule's action did work, only slower than the wait after it: carry on from the step it leads to.
// Returns true when Match has to run again for that step.
boolean resumeAfterLateScreenChange(JSONObject agentMemory, String filteredScreen, JSONObject plan) {
	JSONObject retriedRule = agentMemory.optJSONObject("lateScreenChange");
	agentMemory.remove("lateScreenChange");
	if (retriedRule == null || isSameScreen(filteredScreen, retriedRule.optString("screenBefore"))) {
		return false;
	}

	System.out.println("lateScreenChange.ruleId = `" + retriedRule.optString("ruleId") + "`");
	System.out.println();

	agentMemory.remove("unchangedScreenStreak");
	plan.put("currentStep", retriedRule.optString("stepAfter"));
	plan.put("call", "MATCH");

	return true;
}

String buildPrompt(String goalName, String goalDescription, String step, String historyDescription, String failedRulesDescription, String userInstruction, String filteredScreen) {
	return "Goal: " + goalName + "\n"
		+ "Goal description: " + goalDescription + "\n"
		+ "Current step: " + step + "\n"
		+ "User instruction for this screen: " + userInstruction + "\n"
		+ "History of this run: " + historyDescription + "\n"
		+ "Failed rules (their action did not change the screen): " + failedRulesDescription + "\n"
		+ "Screen:\n" + filteredScreen;
}

void planAfterMatch(String goalName, String goalDescription, int maxApiCalls, JSONObject agentMemory, JSONObject plan) {
	String matchResultJSON = magicValue("lv=match_result_json");
	System.out.println("matchResultJSON (first " + RAW_RESULT_LOG_LENGTH + " chars) = `" + firstChars(matchResultJSON, RAW_RESULT_LOG_LENGTH) + "`");

	JSONObject matchResult = parseJSON(matchResultJSON);
	String matchError = matchResult.optString("match_error");
	System.out.println("matchResult.done = `" + matchResult.opt("done") + "`");
	System.out.println("matchResult.match_found = `" + matchResult.opt("match_found") + "`");
	System.out.println("matchResult.rule_id = `" + matchResult.optString("rule_id") + "`");
	System.out.println("matchResult.next_step = `" + matchResult.optString("next_step") + "`");
	System.out.println("matchResult.match_error = `" + matchError + "`");
	System.out.println();

	if (matchError.length() > 0) {
		planError(plan, "Match rule: " + matchError);
		return;
	}

	if (!matchResult.has("next_step")) {
		planError(plan, "Match rule returned no result");
		return;
	}

	String filteredScreen = matchResult.optString("filtered_screen");
	if (resumeAfterLateScreenChange(agentMemory, filteredScreen, plan)) {
		return;
	}

	if (retryOrDisableUselessRule(goalName, agentMemory, filteredScreen, plan)) {
		return;
	}

	String nextStep = matchResult.optString("next_step");
	plan.put("currentStep", nextStep);
	if (isTrue(matchResult.opt("done"))) {
		plan.put("call", "DONE");
		plan.put("message", "API calls: " + agentMemory.optInt("apiCalls"));
		return;
	}

	if (planAskForUserInstruction(goalName, goalDescription, matchResult, filteredScreen, agentMemory, plan)) {
		return;
	}

	if (isTrue(matchResult.opt("match_found"))) {
		if (isLoopingRule(agentMemory, matchResult, filteredScreen)) {
			planError(plan, "Loop detected: rule " + matchResult.optString("rule_id") + " was already run "
				+ MAX_SAME_SCREEN_EXECUTIONS + " times on this same screen");
			return;
		}

		showActionToast(matchResult, "matched rule");
		rememberExecutedRule(agentMemory, matchResult, filteredScreen, matchResult.optString("current_step"));
		plan.put("call", "EXECUTE_MATCH");

		return;
	}

	boolean isSameScreenAsLastAsk = isSameScreen(filteredScreen, agentMemory.optString("lastAskedScreen"));
	System.out.println("isSameScreenAsLastAsk = `" + isSameScreenAsLastAsk + "`");
	if (isSameScreenAsLastAsk) {
		Thread.sleep(SAME_SCREEN_WAIT_MS);
		plan.put("call", "MATCH");
		return;
	}

	if (agentMemory.optInt("apiCalls") >= maxApiCalls) {
		planError(plan, "API call limit reached (" + maxApiCalls + ")");
		return;
	}

	planAsk(goalName, goalDescription, nextStep, NO_USER_INSTRUCTION, filteredScreen, agentMemory, plan);
}

void planAsk(String goalName, String goalDescription, String step, String userInstruction, String filteredScreen, JSONObject agentMemory, JSONObject plan) {
	agentMemory.put("apiCalls", agentMemory.optInt("apiCalls") + 1);
	agentMemory.put("lastAskedScreen", filteredScreen);
	agentMemory.put("lastAskedStep", step);

	String historyDescription = describeHistory(agentMemory, filteredScreen);
	String failedRulesDescription = describeFailedRules(goalName, step);
	System.out.println("historyDescription = `" + historyDescription + "`");
	System.out.println("failedRulesDescription = `" + failedRulesDescription + "`");
	System.out.println("userInstruction = `" + userInstruction + "`");

	plan.put("currentStep", step);
	plan.put("call", "ASK");
	plan.put("prompt", buildPrompt(goalName, goalDescription, step, historyDescription, failedRulesDescription, userInstruction, filteredScreen));
	plan.put("systemPrompt", SYSTEM_PROMPT_TEMPLATE.replace("<", "{").replace(">", "}"));
}

// The person watching the device said what this screen needs, so Claude is asked right away, whatever rule
// already matches here: that rule is the one the instruction replaces, so it is removed first.
// Returns true when the instruction was turned into a question for Claude.
boolean planAskForUserInstruction(String goalName, String goalDescription, JSONObject matchResult, String filteredScreen, JSONObject agentMemory, JSONObject plan) {
	String userInstruction = agentMemory.optString("userInstruction");
	agentMemory.remove("userInstruction");
	if (userInstruction.length() == 0) {
		return false;
	}

	if (isTrue(matchResult.opt("match_found"))) {
		int removedRuleCount = retireRules(goalName, matchResult.optString("rule_id"), true);
		System.out.println("removedRuleCount = `" + removedRuleCount + "`");
	}

	planAsk(goalName, goalDescription, matchResult.optString("current_step"), userInstruction, filteredScreen, agentMemory, plan);
	return true;
}

// The block only comes back here after an error when its dialog collected an instruction from the user.
void planAfterError(JSONObject agentMemory, JSONObject plan) {
	String userInstruction = magicValue("lv=user_instruction").trim();
	boolean isVariableMissing = userInstruction.equals("{" + "lv=user_instruction" + "}");
	System.out.println("userInstruction = `" + userInstruction + "`");
	System.out.println("isVariableMissing = `" + isVariableMissing + "`");
	if (isVariableMissing || userInstruction.length() == 0) {
		planError(plan, "Continued after an error without a user instruction");
		return;
	}

	variableSetter.setString("user_instruction", "");
	agentMemory.put("userInstruction", userInstruction);
	agentMemory.remove("executedRule");
	agentMemory.remove("lateScreenChange");
	addHistory(agentMemory, "The automation got stuck and the user gave this instruction: " + userInstruction);
	plan.put("call", "MATCH");
}

void planAfterAsk(JSONObject plan) {
	String askSuccess = magicValue("lv=ask_success");
	System.out.println("askSuccess = `" + askSuccess + "`");
	if (!isTrue(askSuccess)) {
		planError(plan, "Ask Claude failed");
		return;
	}

	plan.put("call", "SAVE");
}

void planAfterSave(JSONObject agentMemory, JSONObject plan) {
	String saveResultJSON = magicValue("lv=save_result_json");
	System.out.println("saveResultJSON (first " + RAW_RESULT_LOG_LENGTH + " chars) = `" + firstChars(saveResultJSON, RAW_RESULT_LOG_LENGTH) + "`");

	JSONObject saveResult = parseJSON(saveResultJSON);
	System.out.println("saveResult.saved = `" + saveResult.opt("saved") + "`");
	System.out.println("saveResult.rule_id = `" + saveResult.optString("rule_id") + "`");
	System.out.println("saveResult.next_step = `" + saveResult.optString("next_step") + "`");
	System.out.println("saveResult.save_error = `" + saveResult.optString("save_error") + "`");
	System.out.println();

	if (!saveResult.has("saved")) {
		planError(plan, "Save rule returned no result");
		return;
	}

	// The screen moved on between Claude being asked and its answer being checked, so the answer is only
	// out of date: look at the screen again. Forgetting the last question lets Claude be asked about it afresh.
	if (isTrue(saveResult.opt("screen_mismatch"))) {
		agentMemory.put("lastAskedScreen", "");
		addHistory(agentMemory, "A proposed rule no longer fitted the screen, which had changed meanwhile");
		plan.put("call", "MATCH");
		return;
	}

	// Claude judged the screen to prove the goal finished, and Save confirmed its conditions hold on that screen.
	if (isTrue(saveResult.opt("done"))) {
		plan.put("call", "DONE");
		plan.put("message", "API calls: " + agentMemory.optInt("apiCalls"));
		return;
	}

	if (!isTrue(saveResult.opt("saved"))) {
		planError(plan, "Save rule: " + saveResult.optString("save_error"));
		return;
	}

	plan.put("currentStep", saveResult.optString("next_step"));
	showActionToast(saveResult, "new rule from Claude");
	rememberExecutedRule(agentMemory, saveResult, agentMemory.optString("lastAskedScreen"), agentMemory.optString("lastAskedStep"));
	plan.put("call", "EXECUTE_SAVED");
}

void planAfterExecute(JSONObject agentMemory, JSONObject plan) {
	String execSuccess = magicValue("lv=exec_success");
	System.out.println("execSuccess = `" + execSuccess + "`");
	if (!isTrue(execSuccess)) {
		planError(plan, "Execute UI action failed");
		return;
	}

	// The rule was made on a screen that itself said the goal was achieved, and its action only closed that
	// screen: stop here, since the next screen would no longer show that anything was accomplished.
	JSONObject executedRule = agentMemory.optJSONObject("executedRule");
	boolean finishesGoal = executedRule != null && executedRule.optBoolean("finishesGoal");
	System.out.println("finishesGoal = `" + finishesGoal + "`");
	if (finishesGoal) {
		plan.put("call", "DONE");
		plan.put("message", "API calls: " + agentMemory.optInt("apiCalls"));
		return;
	}

	plan.put("call", "MATCH");
}

// Clears the variable the planned call writes its result to, so a call that fails
// without writing anything is never mistaken for the result of an earlier call.
void clearResultOf(String call) {
	if (call.equals("MATCH")) {
		variableSetter.setString("match_result_json", "");
	} else if (call.equals("ASK")) {
		variableSetter.setBoolean("ask_success", false);
		variableSetter.setString("response_text", "");
	} else if (call.equals("SAVE")) {
		variableSetter.setString("save_result_json", "");
	} else if (call.startsWith("EXECUTE")) {
		variableSetter.setBoolean("exec_success", false);
	}
}

String goalName = magicValue("lv=goal_name");
String goalDescription = magicValue("lv=goal_description");
int maxApiCalls = Integer.parseInt(magicValue("lv=max_api_calls").trim());
JSONObject agentMemory = parseJSON(magicValue("lv=agent_memory_json"));
String lastCall = agentMemory.optString("lastCall");

System.out.println("goal_name = `" + goalName + "`");
System.out.println("goalDescription = `" + goalDescription + "`");
System.out.println("maxApiCalls = `" + maxApiCalls + "`");
System.out.println("lastCall = `" + lastCall + "`");
System.out.println("agentMemory.apiCalls (before) = `" + agentMemory.optInt("apiCalls") + "`");
System.out.println();

JSONObject plan = new JSONObject();
plan.put("call", "MATCH");
plan.put("message", "");

try {
	if (lastCall.equals("MATCH")) {
		planAfterMatch(goalName, goalDescription, maxApiCalls, agentMemory, plan);
	} else if (lastCall.equals("ASK")) {
		planAfterAsk(plan);
	} else if (lastCall.equals("SAVE")) {
		planAfterSave(agentMemory, plan);
	} else if (lastCall.startsWith("EXECUTE")) {
		planAfterExecute(agentMemory, plan);
	} else if (lastCall.equals("ERROR")) {
		planAfterError(agentMemory, plan);
	}
} catch (Exception exception) {
	planError(plan, exception.toString());
}

agentMemory.put("lastCall", plan.getString("call"));
clearResultOf(plan.getString("call"));

System.out.println();
System.out.println("plan.call = `" + plan.getString("call") + "`");
System.out.println("plan.message = `" + plan.getString("message") + "`");
System.out.println("plan.currentStep = `" + plan.optString("currentStep") + "`");
System.out.println("agentMemory.apiCalls (after) = `" + agentMemory.optInt("apiCalls") + "`");

variableSetter.setString("call", plan.getString("call"));
variableSetter.setString("message", plan.getString("message"));
variableSetter.setString("agent_memory_json", agentMemory.toString());
if (plan.has("currentStep")) {
	variableSetter.setString("current_step", plan.getString("currentStep"));
}

if (plan.has("prompt")) {
	variableSetter.setString("prompt", plan.getString("prompt"));
	variableSetter.setString("system_prompt", plan.getString("systemPrompt"));
}

