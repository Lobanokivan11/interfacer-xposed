/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * Xposed adaptation for Substratum-like theming (internal only)
 */

package projekt.interfacer.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.util.Arrays;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;

public class SoundUtilsXposed {
    private static final String TAG = "SoundUtilsXposed";
    private static final boolean DEBUG = true;

    private static final String MODULE_DATA_DIR = "/data/data/projekt.interfacer/";
    private static final String THEME_AUDIO_DIR = MODULE_DATA_DIR + "theme/audio/";
    private static final String UI_SOUNDS_DIR = THEME_AUDIO_DIR + "ui/";
    private static final String ALARMS_DIR = THEME_AUDIO_DIR + "alarms/";
    private static final String RINGTONES_DIR = THEME_AUDIO_DIR + "ringtones/";
    private static final String NOTIFICATIONS_DIR = THEME_AUDIO_DIR + "notifications/";

    private static void log(String msg) {
        if (DEBUG) XposedBridge.log(TAG + ": " + msg);
    }

    public static void hookRingtoneManager() {
        XposedHelpers.findAndHookMethod(
            RingtoneManager.class,
            "setActualDefaultRingtoneUri",
            Context.class, int.class, Uri.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.args[0];
                    int type = (int) param.args[1];
                    Uri uri = (Uri) param.args[2];
                    log("Setting default ringtone for type " + type + " to " + uri);
                    try {
                        String originalPath = uri != null ? uri.getPath() : null;
                        if (originalPath != null && originalPath.startsWith("/system/")) {
                            log("System sound detected, no replacement needed.");
                            return;
                        }
                        String internalPath = null;
                        switch (type) {
                            case RingtoneManager.TYPE_RINGTONE:
                                internalPath = RINGTONES_DIR + "ringtone.ogg";
                                break;
                            case RingtoneManager.TYPE_NOTIFICATION:
                                internalPath = NOTIFICATIONS_DIR + "notification.ogg";
                                break;
                            case RingtoneManager.TYPE_ALARM:
                                internalPath = ALARMS_DIR + "alarm.ogg";
                                break;
                            default:
                                log("Unsupported sound type: " + type);
                                return;
                        }
                        File internalSoundFile = new File(internalPath);
                        if (!internalSoundFile.exists()) {
                            log("Internal sound file not found: " + internalPath);
                            return;
                        }
                        Uri internalUri = Uri.fromFile(internalSoundFile);
                        param.args[2] = internalUri;
                        log("Replaced URI with internal path: " + internalUri);
                    } catch (Exception e) {
                        log("Error replacing ringtone URI: " + e);
                    }
                }
            }
        );
    }

    public static void hookMediaStore() {
        XposedHelpers.findAndHookMethod(
            MediaStore.Audio.Media.class,
            "getContentUriForPath",
            String.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String path = (String) param.args[0];
                    Uri uri = (Uri) param.getResult();
                    log("MediaStore request for path: " + path + " -> " + uri);
                    // logic
                }
            }
        );
    }

    public static boolean setAudible(Context context, File ringtone, int type, String name) {
        try {
            String mimeType = name.endsWith(".ogg") ? "application/ogg" : "application/mp3";
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DATA, ringtone.getAbsolutePath());
            values.put(MediaStore.MediaColumns.TITLE, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.SIZE, ringtone.length());
            values.put(MediaStore.Audio.Media.IS_RINGTONE, type == RingtoneManager.TYPE_RINGTONE);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, type == RingtoneManager.TYPE_NOTIFICATION);
            values.put(MediaStore.Audio.Media.IS_ALARM, type == RingtoneManager.TYPE_ALARM);
            values.put(MediaStore.Audio.Media.IS_MUSIC, false);

            Uri uri = MediaStore.Audio.Media.getContentUriForPath(ringtone.getAbsolutePath());
            Uri newUri = context.getContentResolver().insert(uri, values);

            RingtoneManager.setActualDefaultRingtoneUri(context, type, newUri);
            return true;
        } catch (Exception e) {
            log("Error setting audible: " + e);
            return false;
        }
    }

    public static boolean setUISounds(ContentResolver resolver, String soundName, String location) {
        if (allowedUISound(soundName)) {
            Settings.Global.putStringForUser(resolver, soundName, location, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    private static boolean allowedUISound(String targetValue) {
        String[] allowed = {"lock_sound", "unlock_sound", "low_battery_sound"};
        return Arrays.asList(allowed).contains(targetValue);
    }

    public static void setDefaultAudible(Context context, int type) {
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, null);
        } catch (Exception e) {
            log("Error restoring default audible: " + e);
        }
    }

    public static void setDefaultUISounds(ContentResolver resolver, String soundName, String soundFile) {
        Settings.Global.putStringForUser(resolver, soundName, "/system/media/audio/ui/" + soundFile, UserHandle.USER_CURRENT);
    }
}
