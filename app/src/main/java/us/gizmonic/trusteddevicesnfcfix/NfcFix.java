package us.gizmonic.trusteddevicesnfcfix;

import android.hardware.display.DisplayManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by cliff on 2/20/17.
 */

public class NfcFix implements IXposedHookLoadPackage {
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.android.nfc")) {
            return;
        }
        XposedBridge.log("Loaded app: " + lpparam.packageName);

        findAndHookMethod("com.android.nfc.ScreenStateHelper", lpparam.classLoader, "checkScreenState", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // this will be called before the clock was updated by the original method
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // this will be called after the clock was updated by the original method
                XposedBridge.log("Tricking computeDiscoveryParameters to think screen is ON_UNLOCKED");
                param.setResult(3);
            }
        });
    }
}
