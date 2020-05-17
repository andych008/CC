package com.billy.cc.core.ipc;

import android.util.Log;

public class CP_Util {
    private static final String TAG = "CP_Caller";
    private static final String VERBOSE_TAG = "CP_Caller_VERBOSE";
    static boolean DEBUG = false;
    public static boolean VERBOSE_LOG = false;

    static {
        if (BuildConfig.DEBUG) {
            DEBUG = true;
            VERBOSE_LOG = true;
        }
    }

    public static void log(String s, Object... args) {
        if (DEBUG) {
            s = format(s, args);
            Log.i(TAG, "(" + Thread.currentThread().getName() + ")"
                    + " >>>> " + s);
        }
    }

    public static void verboseLog(String s, Object... args) {
        if (VERBOSE_LOG) {
            s = format(s, args);
            Log.i(VERBOSE_TAG, "(" + Thread.currentThread().getName() + ")"
                    + " >>>> " + s);
        }
    }

    public static void logError(String s, Object... args) {
        if (DEBUG) {
            s = format(s, args);
            Log.e(TAG, "(" + Thread.currentThread().getName() + ")"
                    + " >>>> " + s);
        }
    }

    public static void printStackTrace(Throwable t) {
        if (DEBUG && t != null) {
            t.printStackTrace();
        }
    }

    private static String format(String s, Object... args) {
        try {
            if (args != null && args.length > 0) {
                s = String.format(s, args);
            }
        } catch (Exception e) {
            CP_Util.printStackTrace(e);
        }
        return s;
    }
}
