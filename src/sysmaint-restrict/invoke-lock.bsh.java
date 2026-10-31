/* MacroDroid: reads SYSMAINT_RESTRICT_VALUE, SYSMAINT_RESTRICT_SINCE, SYSMAINT_RESTRICT_CAUSE, {system_time}; writes SYSMAINT_RESTRICT_VALUE; returns triggerEnabledSecondsAgo */

import android.accessibilityservice.AccessibilityService;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

boolean sysmaintRestrictValue = magicText.evaluateBoolean("{" + "v=SYSMAINT_RESTRICT_VALUE" + "}");
String sysmaintRestrictCause = magicText.evaluateString("{" + "v=SYSMAINT_RESTRICT_CAUSE" + "}");

System.out.println("SYSMAINT_RESTRICT_VALUE (should be true) = `" + sysmaintRestrictValue + "`");
System.out.println("SYSMAINT_RESTRICT_CAUSE = `" + sysmaintRestrictCause + "`");
System.out.println();

int systemTime = magicText.evaluateInt("{" + "system_time" + "}");
int sysmaintRestrictSince = magicText.evaluateInt("{" + "v=SYSMAINT_RESTRICT_SINCE" + "}");
int triggerEnabledSecondsAgo = systemTime - sysmaintRestrictSince;

System.out.println("SYSMAINT_RESTRICT_SINCE = `" + sysmaintRestrictSince + "`");
System.out.println("triggerEnabledSecondsAgo = `" + triggerEnabledSecondsAgo + "`");
System.out.println();

int waitSeconds = 600;

if (triggerEnabledSecondsAgo < waitSeconds) {
	accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
	DevicePolicyManager dpm = (DevicePolicyManager) appContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
	dpm.lockNow();
} else {
	variableSetter.setBoolean("SYSMAINT_RESTRICT_VALUE", false);
	System.out.println("SYSMAINT_RESTRICT_VALUE (after) = `false`");
}

triggerEnabledSecondsAgo;
