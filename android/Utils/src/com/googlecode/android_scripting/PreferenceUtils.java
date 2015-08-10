package com.googlecode.android_scripting;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * @author palaciodelgado [at] gmail [dot] com
 */
public class PreferenceUtils {

    // TODO (miguelpalacio): if this class is not used. Delete it.

    public static void saveToPreferences(Context context, String preferenceName, String preferenceValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(preferenceName, preferenceValue);
        editor.apply();
    }

    public static String readFromPreferences(Context context, String preferenceName, String defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getString(preferenceName, defaultValue);
    }

    public static void saveToPreferences(Context context, String PreferenceFile,
                                         String preferenceName, String preferenceValue) {
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(PreferenceFile, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(preferenceName, preferenceValue);
        editor.apply();
    }

    public static String readFromPreferences(Context context, String PreferenceFile,
                                             String preferenceName, String defaultValue) {
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(PreferenceFile, Context.MODE_PRIVATE);
        return sharedPreferences.getString(preferenceName, defaultValue);
    }
}
