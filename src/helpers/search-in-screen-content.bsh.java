/* MacroDroid: reads regex_to_search, contents_str (local variables, contents_str from get-current-screen-content.bsh); writes nothing; returns foundOnScreen (true when any text on screen matches the regex) */

import java.util.Iterator;
import java.util.regex.Pattern;
import org.json.JSONObject;

String regexToSearch = magicText.evaluateString("{" + "lv=regex_to_search" + "}");
JSONObject screen = new JSONObject(magicText.evaluateString("{" + "lv=contents_str" + "}"));
Pattern pattern = Pattern.compile(regexToSearch);

System.out.println("regex_to_search = `" + regexToSearch + "`");

boolean foundOnScreen = false;
Iterator keys = screen.keys();

while (keys.hasNext() && !foundOnScreen) {
	String text = screen.optString((String) keys.next());

	foundOnScreen = pattern.matcher(text).find();
}

System.out.println("screen texts count = `" + screen.length() + "`");
System.out.println("foundOnScreen = `" + foundOnScreen + "`");

foundOnScreen;
