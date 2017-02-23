package us.gizmonic.trusteddevicesnfcfix;

import android.app.Application;
import android.hardware.display.DisplayManager;

import java.lang.reflect.Constructor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

/**
 * Created by cliff on 2/20/17.
 */

public class NfcFix implements IXposedHookLoadPackage {

    private static Object mScreenStateHelper;
    private static Object nfcServiceObject;

    public static final String PACKAGE_NFC = "com.android.nfc";
    
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        // this checks to make sure we are in the nfc package
        if (!lpparam.packageName.equals(PACKAGE_NFC)) {
            return;
        }

        // Great, we're in the right place, now let's hook some methods.
        XposedBridge.log("Loaded app: " + lpparam.packageName);

        /*
         *  applyRouting method hooks
         */
        XC_MethodHook applyRoutingHook =new XC_MethodHook () {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                final int currScreenState;
                currScreenState = (Integer) XposedHelpers.callMethod(mScreenStateHelper, "checkScreenState");
                XposedBridge.log("currScreenState=" + (Integer) currScreenState);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
            }
        };

        /*
         *  screenStateHelper method hooks. Overrides output with screen state=3 TODO: use logic to decide screen state.
         */
        XC_MethodHook screenStateHelperHook = new XC_MethodHook () {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // don't need to do anything before the method
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("Tricking computeDiscoveryParameters to think screen is ON_UNLOCKED");
                param.setResult(3);
            }
        };

        /*
         * initNfcService method hooks. This should be called when NFC service is started.
         */
        XC_MethodHook initNfcServiceHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("In initNfcServiceHook");
                nfcServiceObject = param.thisObject;
            }
        };

        /*
         * EXECUTION BEGINS HERE
         */

        // Grab reference to NfcService class
        Class<?> NfcService = null;

        if (NfcService == null) {
            try {
                XposedBridge.log("Looking up reference to NfcService class");
                NfcService = findClass(PACKAGE_NFC + ".NfcService", lpparam.classLoader);
            } catch (XposedHelpers.ClassNotFoundError e) {
                XposedBridge.log("Class NfcService not found: " + e.getMessage().toString());
                e.printStackTrace();
            }
        }

        // Trying to hook into constructor
        boolean hookedSuccessfully = true;
        try {
            XposedBridge.log("Searching for NfcServiceConstructor");
            Constructor<?> NfcServiceConstructor = XposedHelpers.findConstructorBestMatch(NfcService, Application.class);
            XposedBridge.hookMethod(NfcServiceConstructor, initNfcServiceHook);
        } catch (NoSuchMethodError e) {
            XposedBridge.log("Method NfcServiceConstructor not found: " + e.getMessage().toString());
            hookedSuccessfully = false;
        }

        if (!hookedSuccessfully) {
            try {
                XposedBridge.log("Attempting to hook onCreate");
                findAndHookMethod(NfcService, "onCreate", initNfcServiceHook);
            } catch (NoSuchMethodError e) {
                XposedBridge.log("Method onCreate not found: " + e.getMessage().toString());
                e.printStackTrace();
            }
        }

        // create our method hooks
        findAndHookMethod(PACKAGE_NFC + "ScreenStateHelper", lpparam.classLoader, "checkScreenState", screenStateHelperHook);

        findAndHookMethod(PACKAGE_NFC + "NfcService", lpparam.classLoader, "applyRouting", boolean.class, applyRoutingHook);
    }
}
