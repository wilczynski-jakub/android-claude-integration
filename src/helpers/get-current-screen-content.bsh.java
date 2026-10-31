import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import org.json.JSONObject;

String NO_VIEW_ID_KEY_PREFIX = "index:";

int nodeIndex = 0;

// A repeated view id gets "$2", "$3"... so that every element keeps its own key, as in MacroDroid's output.
String uniqueKey(JSONObject screen, String baseKey) {
	if (!screen.has(baseKey)) {
		return baseKey;
	}

	int copyNumber = 2;

	while (screen.has(baseKey + "$" + copyNumber)) {
		copyNumber++;
	}

	return baseKey + "$" + copyNumber;
}

void collectText(AccessibilityNodeInfo node, JSONObject screen) {
	if (node == null) {
		return;
	}

	int currentIndex = nodeIndex;
	nodeIndex++;

	CharSequence text = node.getText();

	if (text != null && text.length() > 0) {
		String viewId = node.getViewIdResourceName();
		String baseKey = viewId != null ? viewId : NO_VIEW_ID_KEY_PREFIX + currentIndex;

		screen.put(uniqueKey(screen, baseKey), text.toString());
	}

	for (int childIndex = 0; childIndex < node.getChildCount(); childIndex++) {
		collectText(node.getChild(childIndex), screen);
	}
}

List windows = accessibility.getWindows();
JSONObject screenContents = new JSONObject();

for (int windowIndex = 0; windowIndex < windows.size(); windowIndex++) {
	AccessibilityWindowInfo window = (AccessibilityWindowInfo) windows.get(windowIndex);
	collectText(window.getRoot(), screenContents);
}

String screenContentsJSON = screenContents.toString();

System.out.println("windows count = `" + windows.size() + "`");
System.out.println("screenContentsJSON = `" +screenContentsJSON + "`");

screenContentsJSON; // Output Variable
