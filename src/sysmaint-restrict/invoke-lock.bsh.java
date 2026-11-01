// Required global variables: SYSMAINT_RESTRICT_ENABLED, SYSMAINT_RESTRICT_SINCE, SYSMAINT_RESTRICT_CAUSE

import android.accessibilityservice.AccessibilityService;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

boolean sysmaintRestrictEnabled = magicText.evaluateBoolean("{" + "v=SYSMAINT_RESTRICT_ENABLED" + "}");
String sysmaintRestrictCause = magicText.evaluateString("{" + "v=SYSMAINT_RESTRICT_CAUSE" + "}");
System.out.println("SYSMAINT_RESTRICT_ENABLED (should be true) = `" + sysmaintRestrictEnabled + "`");
System.out.println("SYSMAINT_RESTRICT_CAUSE = `" + sysmaintRestrictCause + "`");
System.out.println();

int systemTimeSeconds = magicText.evaluateInt("{" + "system_time" + "}");
int sysmaintRestrictSince = magicText.evaluateInt("{" + "v=SYSMAINT_RESTRICT_SINCE" + "}");
int restrictEnabledSecondsAgo = systemTimeSeconds - sysmaintRestrictSince;

System.out.println("SYSMAINT_RESTRICT_SINCE = `" + sysmaintRestrictSince + "`");
System.out.println("restrictEnabledSecondsAgo = `" + restrictEnabledSecondsAgo + "`");
System.out.println();

int restrictDurationSeconds = 600;

if (restrictEnabledSecondsAgo < restrictDurationSeconds) {
	accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
	DevicePolicyManager dpm = (DevicePolicyManager) appContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
	dpm.lockNow();
} else {
	variableSetter.setBoolean("SYSMAINT_RESTRICT_ENABLED", false);
	System.out.println("SYSMAINT_RESTRICT_ENABLED (after) = `" + magicText.evaluateBoolean("{" + "v=SYSMAINT_RESTRICT_ENABLED" + "}") + "`");
}

restrictEnabledSecondsAgo; // Output Variable
