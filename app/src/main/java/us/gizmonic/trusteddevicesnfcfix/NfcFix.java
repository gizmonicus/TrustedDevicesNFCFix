package us.gizmonic.trusteddevicesnfcfix;

import android.app.Application;
import java.lang.reflect.Constructor;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

/**
 * Created by cliff on 2/20/17.
 */

public class NfcFix implements IXposedHookLoadPackage {

    private static Object mScreenStateHelper;
    private static Object nfcServiceObject;
    private static boolean debug = true;
    public static final String PACKAGE_NFC = "com.android.nfc";
    static final int SCREEN_STATE_UNKNOWN = 0;
    static final int SCREEN_STATE_OFF = 1;
    static final int SCREEN_STATE_ON_LOCKED = 2;
    static final int SCREEN_STATE_ON_UNLOCKED = 3;

    // TODO: Make this configurable on the fly
    public void debugMsg(String message) {
      if (debug) {
        XposedBridge.log("NfcFix--> " + message);
      }
    }

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        /*
         *  applyRouting method hooks
         */
        XC_MethodHook applyRoutingHook =new XC_MethodHook () {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                final int currScreenState;
                currScreenState = (Integer) XposedHelpers.callMethod(mScreenStateHelper, "checkScreenState");
                debugMsg("applyRoutingHook: currScreenState=" + (Integer) currScreenState);

                if ((currScreenState != SCREEN_STATE_ON_LOCKED)) {
                  debugMsg("applyRoutingHook: nothing to do, returning");
                  XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", -1);
                  return;
                }

                debugMsg("applyRoutingHook: setting NeedScreenOnState");
                XposedHelpers.setAdditionalInstanceField(param.thisObject, "NeedScreenOnState", true);
                synchronized (param.thisObject) { // Not sure if this is correct, but NfcService.java insists on having accesses to the mScreenState variable synchronized, so I'm doing the same here
                  XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", XposedHelpers.getIntField(param.thisObject, "mScreenState"));
                  XposedHelpers.setIntField(param.thisObject, "mScreenState", SCREEN_STATE_ON_UNLOCKED);
                }
            }
        };

        /*
         *  screenStateHelper method hooks. Overrides output with screen state=3 TODO: use logic to decide screen state.
         */
        XC_MethodHook screenStateHelperHook = new XC_MethodHook () {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                  debugMsg("screenStateHelperHook: Attempting to set NeedScreenOnState");
                  Boolean NeedScreenOnState = (Boolean)XposedHelpers.getAdditionalInstanceField(param.thisObject, "NeedScreenOnState") ;
                  if (NeedScreenOnState == null || NeedScreenOnState == false)
                    return;

                  param.setResult(SCREEN_STATE_ON_UNLOCKED);
                } catch (Exception e) {
                  debugMsg("screenStateHelperHook: beforeHookedMethod threw exception: " + e.getMessage().toString());
                  e.printStackTrace();
                }
            }
        };

        /*
         * initNfcService method hooks. This should be called when NFC service is started.
         */
        XC_MethodHook initNfcServiceHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                nfcServiceObject = param.thisObject;

                try {
                  debugMsg("initNfcServiceHook: Trying to set mScreenStateHelper");
                  mScreenStateHelper = XposedHelpers.getObjectField(param.thisObject, "mScreenStateHelper");
                } catch (NoSuchFieldError e) {
                  debugMsg("initNfcServiceHook: Field mScreenStateHelper not found: " + e.getMessage().toString());
                  e.printStackTrace();
                }
            }
        };

        /*
         * EXECUTION BEGINS HERE
         */

        // this checks to make sure we are in the nfc package
        if (!lpparam.packageName.equals(PACKAGE_NFC)) {
            return;
        }

        // Great, we're in the right place, now let's hook some methods.
        debugMsg("Main: Loaded app: " + lpparam.packageName);

        /*
         * Grab reference to NfcService class or constructor. I think most of this code
         * is superfluous, but I'm leaving it in because, well, it fucking works.
         */
        Class<?> NfcService = null;

        if (NfcService == null) {
            try {
                debugMsg("Main: Looking up reference to NfcService class");
                NfcService = findClass(PACKAGE_NFC + ".NfcService", lpparam.classLoader);
            } catch (XposedHelpers.ClassNotFoundError e) {
                debugMsg("Main: Class NfcService not found: " + e.getMessage().toString());
                e.printStackTrace();
            }
        }

        // Trying to hook into constructor
        boolean hookedSuccessfully = true;
        try {
            debugMsg("Main: Searching for NfcServiceConstructor");
            Constructor<?> NfcServiceConstructor = XposedHelpers.findConstructorBestMatch(NfcService, Application.class);
            debugMsg("Main: Found NfcServiceConstructor");
            XposedBridge.hookMethod(NfcServiceConstructor, initNfcServiceHook);
        } catch (NoSuchMethodError e) {
            debugMsg("Main: Method NfcServiceConstructor not found: " + e.getMessage().toString());
            hookedSuccessfully = false;
        }

        if (!hookedSuccessfully) {
            try {
                debugMsg("Main: Attempting to hook onCreate");
                findAndHookMethod(NfcService, "onCreate", initNfcServiceHook);
                debugMsg("Main: Attempting to hook onCreate");
            } catch (NoSuchMethodError e) {
                debugMsg("Main: Method onCreate not found: " + e.getMessage().toString());
                e.printStackTrace();
            }
        }
        // create our method hooks
        findAndHookMethod(PACKAGE_NFC + ".ScreenStateHelper", lpparam.classLoader, "checkScreenState", screenStateHelperHook);
        findAndHookMethod(PACKAGE_NFC + ".NfcService", lpparam.classLoader, "applyRouting", boolean.class, applyRoutingHook);
    }
}
