// Required local variables: regex_to_search, screen_contents_json

import java.util.Iterator;
import java.util.regex.Pattern;
import org.json.JSONObject;

String regexToSearch = magicText.evaluateString("{" + "lv=regex_to_search" + "}");
JSONObject screenContents = new JSONObject(magicText.evaluateString("{" + "lv=screen_contents_json" + "}"));
System.out.println("regex_to_search = `" + regexToSearch + "`");
System.out.println("screen_contents_json = `" +screenContents + "`");

Pattern pattern = Pattern.compile(regexToSearch);
boolean foundOnScreen = false;
Iterator keys = screenContents.keys();

while (keys.hasNext() && !foundOnScreen) {
	String text = screenContents.optString((String) keys.next());
	foundOnScreen = pattern.matcher(text).find();
}

System.out.println("screen texts count = `" + screenContents.length() + "`");
System.out.println("foundOnScreen = `" + foundOnScreen + "`");

foundOnScreen; // Output Variable
