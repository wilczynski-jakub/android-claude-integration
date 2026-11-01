# [plan-next-step.bsh](plan-next-step.bsh.java)

On each run, the code reads the saved memory and the result of the previous step, **chooses the next step** and writes it to `planned_next_step`. It does not invoke any UI actions, only plans them and updates `agentMemory`.

## Main code

1. Reads `goal_name`, `goal_description` and `max_api_calls`, and parses `agent_memory_json` into `agentMemory` (*JSONObject*).
2. Takes `previousStep` from the memory: the step chosen on the previous run.
3. Creates a `plan` with a default of `MATCH` and an empty `statusMessage`. If `previousStep` is empty or unknown, this plan stands.
4. Inside a `try` block, calls [`planAfterMatch`](#planaftermatch), [`planAfterAsk`](#planafterask), [`planAfterSave`](#planaftersave), [`planAfterExecute`](#planafterexecute) or [`planAfterError`](#planaftererror), depending on `previousStep`. An exception calls [`planError`](#planerror).
5. Saves the chosen step in `agentMemory.previousStep`.
6. Calls [`clearResultOf`](#clearresultof) for the chosen step.
7. Writes `planned_next_step`, `status_message` and `agent_memory_json`, plus `current_step`, `prompt` and `system_prompt` when the plan has them.

### Call tree

<div>
<samp>│</samp><br>
<details><summary><a id="planaftermatch"></a><a href="#after-match-planaftermatch"><code>planAfterMatch</code></a><br>Chooses the step after Match.</summary>
<dl><dd>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a id="resumeafterlatescreenchange"></a><code>resumeAfterLateScreenChange</code><br>
<samp>│&nbsp;&nbsp;&nbsp;</samp>Moves on if a retried action changed the screen late.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="issamescreen"></a><code>isSameScreen</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Compares two filtered screens exactly. An empty screen is never the same.<br>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a id="retryordisableuselessrule"></a><code>retryOrDisableUselessRule</code><br>
<samp>│&nbsp;&nbsp;&nbsp;</samp>Retries an action that left the screen unchanged, or retires its rule.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a href="#issamescreen"><code>isSameScreen</code></a><br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a id="countunchangedscreen"></a><code>countUnchangedScreen</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;</samp>Counts how many times in a row a rule left the screen unchanged.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a id="retirerules"></a><code>retireRules</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;</samp>Removes or disables a rule in the whole goal and in the global rules.<br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a id="stepsofgoal"></a><code>stepsOfGoal</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;</samp>Returns the steps of a goal, or an empty object.<br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="retirerulesin"></a><code>retireRulesIn</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Does the same in one list.<br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="isdisabled"></a><code>isDisabled</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Checks whether a rule has the <code>disabled</code> status.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a id="addhistory"></a><code>addHistory</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;</samp>Adds an entry that is not tied to a screen.<br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="addscreenhistory"></a><code>addScreenHistory</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Adds an entry with a screen and keeps the last 15.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="showtoast"></a><code>showToast</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Shows a toast on the main thread.<br>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a id="planaskforuserinstruction"></a><code>planAskForUserInstruction</code><br>
<samp>│&nbsp;&nbsp;&nbsp;</samp>Replaces the matching rule when a user instruction is waiting.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a href="#retirerules"><code>retireRules</code></a><br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="planask"></a><code>planAsk</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Prepares the question for Claude and counts it in <code>apiCalls</code>.<br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a id="describehistory"></a><code>describeHistory</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;</samp>Builds the history text for the prompt.<br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a href="#issamescreen"><code>isSameScreen</code></a><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a id="describefailedrules"></a><code>describeFailedRules</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;</samp>Lists the disabled rules for the prompt.<br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a href="#stepsofgoal"><code>stepsOfGoal</code></a><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="appenddisabledrules"></a><code>appendDisabledRules</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Appends the disabled rules from one list.<br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a href="#isdisabled"><code>isDisabled</code></a><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="buildprompt"></a><code>buildPrompt</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Builds the prompt for Claude.<br>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a id="isloopingrule"></a><code>isLoopingRule</code><br>
<samp>│&nbsp;&nbsp;&nbsp;</samp>Detects a CLICK or BACK repeated more than 3 times on one screen.<br>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a id="showactiontoast"></a><code>showActionToast</code><br>
<samp>│&nbsp;&nbsp;&nbsp;</samp>Shows the action type, where the rule came from and its ID.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a href="#showtoast"><code>showToast</code></a><br>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a id="rememberexecutedrule"></a><code>rememberExecutedRule</code><br>
<samp>│&nbsp;&nbsp;&nbsp;</samp>Adds the action to the history and saves the rule in <code>executedRule</code>.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a href="#addscreenhistory"><code>addScreenHistory</code></a><br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;├──&nbsp;</samp><a id="summarisescreen"></a><code>summariseScreen</code><br>
<samp>│&nbsp;&nbsp;&nbsp;│&nbsp;&nbsp;&nbsp;</samp>Returns the texts of the first 3 lines of a screen, without view IDs.<br>
<samp>│&nbsp;&nbsp;&nbsp;│</samp><br>
<samp>│&nbsp;&nbsp;&nbsp;└──&nbsp;</samp><a id="describeaction"></a><code>describeAction</code><br>
<samp>│&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</samp>Returns a short description such as <code>CLICK "OK"</code> or <code>WAIT 5 s</code>.<br>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a href="#issamescreen"><code>isSameScreen</code></a><br>
<samp>│</samp><br>
<samp>└──&nbsp;</samp><a href="#planask"><code>planAsk</code></a>
</dd></dl>
</details>
<samp>│</samp><br>
<details><summary><a id="planaftersave"></a><a href="#after-save-planaftersave"><code>planAfterSave</code></a><br>Chooses the step after saving a rule.</summary>
<dl><dd>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a href="#addhistory"><code>addHistory</code></a><br>
<samp>│</samp><br>
<samp>├──&nbsp;</samp><a href="#showactiontoast"><code>showActionToast</code></a><br>
<samp>│</samp><br>
<samp>└──&nbsp;</samp><a href="#rememberexecutedrule"><code>rememberExecutedRule</code></a>
</dd></dl>
</details>
<samp>│</samp><br>
<samp>•&nbsp;</samp><a id="planafterask"></a><a href="#after-ask-planafterask"><code>planAfterAsk</code></a><br>
<samp>&nbsp;&nbsp;</samp>Chooses the step after asking Claude.<br>
<samp>│</samp><br>
<samp>•&nbsp;</samp><a id="planafterexecute"></a><a href="#after-execute_match-or-execute_saved-planafterexecute"><code>planAfterExecute</code></a><br>
<samp>&nbsp;&nbsp;</samp>Chooses the step after an action ran.<br>
<samp>│</samp><br>
<details><summary><a id="planaftererror"></a><a href="#after-error-planaftererror"><code>planAfterError</code></a><br>Takes the user's instruction after an error.</summary>
<dl><dd>
<samp>│</samp><br>
<samp>└──&nbsp;</samp><a href="#addhistory"><code>addHistory</code></a>
</dd></dl>
</details>
<samp>│</samp><br>
<samp>•&nbsp;</samp><a id="clearresultof"></a><code>clearResultOf</code><br>
<samp>&nbsp;&nbsp;</samp>Clears the chosen step's old result.
</div>

<details>
<summary><h4>Helpers used across the tree</h4></summary>

These are left out of the tree above because many functions call them.

| Function | What it does |
| --- | --- |
| <a id="magicvalue"></a>`magicValue` | Reads a magic text value as a string. |
| <a id="parsejson"></a>`parseJSON` | Parses JSON. Empty text gives an empty object. |
| <a id="firstchars"></a>`firstChars` | Shortens text for logs. |
| <a id="istrue"></a>`isTrue` | Checks whether a value reads as `true`, in any letter case. |
| <a id="planerror"></a>`planError` | Sets `ERROR` and the error text in the plan. |
| <a id="actiontypeof"></a>`actionTypeOf` | Returns the action type from a Match or Save result, or empty text. |
| <a id="readfile"></a>`readFile` | Reads a whole file. |
| <a id="writefile"></a>`writeFile` | Writes a whole file. |

</details>

## Choosing the next step

### After `MATCH`: [`planAfterMatch`](#planaftermatch)

Conditions are checked in order, and **the first one that holds ends the function**.

| Condition | Result |
| --- | --- |
| `match_error` is set | `ERROR` |
| The result has no `next_step` | `ERROR` |
| A retried action changed the screen after all | `MATCH` with the step that action led to |
| The last action left the screen unchanged, and it must be retried or its rule retired | `MATCH` with the step from before the action |
| `done` is true | `DONE` |
| A user instruction is waiting | Removes the matching rule, then `ASK` |
| A rule was found, but it is looping | `ERROR` |
| A rule was found | `EXECUTE_MATCH` |
| This screen was already asked about | Waits 3 s, then `MATCH` |
| `apiCalls` has reached `max_api_calls` | `ERROR` |
| Anything else | `ASK` |

### After `ASK`: [`planAfterAsk`](#planafterask)

`ask_success` set to true gives `SAVE`. Any other value calls [`planError`](#planerror).

### After `SAVE`: [`planAfterSave`](#planaftersave)

| Condition, in this order | Result |
| --- | --- |
| The result has no `saved` | `ERROR` |
| `screen_mismatch` is true | Clears `lastAskedScreen`, adds a history entry, then `MATCH` |
| `done` is true | `DONE` |
| `saved` is not true | `ERROR` with the text of `save_error` |
| The rule was saved | `EXECUTE_SAVED` |

### After `EXECUTE_MATCH` or `EXECUTE_SAVED`: [`planAfterExecute`](#planafterexecute)

`exec_success` other than true calls [`planError`](#planerror). A rule with `finishesGoal` gives `DONE`, and any other rule gives `MATCH`.

### After `ERROR`: [`planAfterError`](#planaftererror)

Without a `user_instruction`, it calls [`planError`](#planerror) again. With one, it clears that variable, saves the instruction in the memory, removes `executedRule` and `ruleToRetryWhenScreenUnchanged`, calls [`addHistory`](#addhistory) and chooses `MATCH`.

## Actions that leave the screen unchanged

On every Match result, [`retryOrDisableUselessRule`](#retryordisableuselessrule) compares the filtered screen with the one saved before the last action. The comparison in [`isSameScreen`](#issamescreen) is *exact*, and an empty screen never counts as the same screen.

| Action | Unchanged in a row | What happens |
| --- | --- | --- |
| CLICK, BACK | 1st and 2nd time | Adds a history entry, saves `ruleToRetryWhenScreenUnchanged`, returns to the step from before the action, then `MATCH`. |
| CLICK, BACK | **3rd time** | The rule gets the `disabled` status and stays in the file. The code clears `lastAskedScreen` and returns to `MATCH`. |
| WAIT | 1st to 4th time | No reaction. The remaining [`planAfterMatch`](#planaftermatch) conditions run as usual. |
| WAIT | **5th time** | The rule is removed. The code clears `lastAskedScreen` and returns to `MATCH`. |

Separately, [`isLoopingRule`](#isloopingrule) counts CLICK and BACK runs for each rule and screen pair within one run of the agent. **The fourth run gives `ERROR`**, even if other screens came in between.

## *JSONObject* `agentMemory`

| Field | Contents |
| --- | --- |
| `previousStep` | The step chosen on the previous run |
| `apiCalls` | The number of questions prepared for Claude |
| `history` | Up to 15 entries, each with text, a screen and a repeat count |
| `executedRule` | The last rule: its ID, action type, the screen and step before the action, the step after it, and `finishesGoal` |
| `executionCounts` | Run counters for each rule and screen pair |
| `unchangedScreenStreak` | A rule ID and how many of its actions in a row left the screen unchanged |
| `ruleToRetryWhenScreenUnchanged` | A retried rule whose action may still change the screen |
| `lastAskedScreen`, `lastAskedStep` | The screen and step of the last question |
| `userInstruction` | An instruction waiting to be passed to Claude |
