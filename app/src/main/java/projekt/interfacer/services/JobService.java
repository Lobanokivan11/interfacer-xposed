/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * Xposed adaptation for Substratum-like theming
 */

package projekt.interfacer.services;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import projekt.interfacer.utils.IOUtils;
import projekt.interfacer.utils.SoundUtils;

public class SubstratumXposed implements IXposedHookLoadPackage {
    private static final String TAG = "SubstratumXposed";
    private static final boolean DEBUG = true;
    private static final String SUBSTRATUM_PACKAGE = "projekt.substratum";
    private static final String INTERFACER_PACKAGE = "projekt.interfacer";
    private static final String[] AUTHORIZED_CALLERS = {INTERFACER_PACKAGE, SUBSTRATUM_PACKAGE};
    private static final Signature SUBSTRATUM_SIGNATURE = new Signature("..."); // ваша подпись
    private static final Signature[] AUTHORIZED_SIGNATURES = {SUBSTRATUM_SIGNATURE};

    private static List<Sound> SOUNDS = Arrays.asList(
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "Effect_Tick", "Effect_Tick", RingtoneManager.TYPE_RINGTONE),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "lock_sound", "Lock"),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "unlock_sound", "Unlock"),
        new Sound(IOUtils.SYSTEM_THEME_UI_SOUNDS_PATH, "/SoundsCache/ui/", "low_battery_sound", "LowBattery"),
        new Sound(IOUtils.SYSTEM_THEME_ALARM_PATH, "/SoundsCache/alarms/", "alarm", "alarm", RingtoneManager.TYPE_ALARM),
        new Sound(IOUtils.SYSTEM_THEME_NOTIFICATION_PATH, "/SoundsCache/notifications/", "notification", "notification", RingtoneManager.TYPE_NOTIFICATION),
        new Sound(IOUtils.SYSTEM_THEME_RINGTONE_PATH, "/SoundsCache/ringtones/", "ringtone", "ringtone", RingtoneManager.TYPE_RINGTONE)
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android")) return;

        XposedHelpers.findAndHookMethod(
            "com.android.server.om.OverlayManagerService",
            lpparam.classLoader,
            "setEnabled",
            String.class, boolean.class, int.class, boolean.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    boolean enable = (boolean) param.args[1];
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Overlay " + packageName + " will be " + (enable ? "enabled" : "disabled"));
                        // logic
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerService",
            lpparam.classLoader,
            "installPackageAsUser",
            String.class, int.class, String.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Package " + packageName + " installed");
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerService",
            lpparam.classLoader,
            "deletePackageAsUser",
            String.class, int.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String packageName = (String) param.args[0];
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Package " + packageName + " deleted");
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "android.graphics.Typeface",
            lpparam.classLoader,
            "recreateDefaults",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        log("Fonts refreshed");
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "android.media.RingtoneManager",
            lpparam.classLoader,
            "setActualDefaultRingtoneUri",
            Context.class, int.class, Uri.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isCallerAuthorized(Binder.getCallingUid())) {
                        int type = (int) param.args[1];
                        Uri uri = (Uri) param.args[2];
                        log("Setting default ringtone for type " + type + " to " + uri);
                    }
                }
            }
        );

        XposedHelpers.findAndHookMethod(
            "com.android.server.am.ActivityManagerService",
            lpparam.classLoader,
            "killBackgroundProcesses",
            String.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String processName = (String) param.args[0];
                    if ("com.android.systemui".equals(processName) && isCallerAuthorized(Binder.getCallingUid())) {
                        log("Restarting SystemUI");
                    }
                }
            }
        );
    }

    private static void log(String msg) {
        if (DEBUG) XposedBridge.log(TAG + ": " + msg);
    }

    private boolean isCallerAuthorized(int uid) {
        String callingPackage = getPackageManager().getPackagesForUid(uid)[0];
        for (String AUTHORIZED_CALLER : AUTHORIZED_CALLERS) {
            if (TextUtils.equals(callingPackage, AUTHORIZED_CALLER)) {
                for (Signature AUTHORIZED_SIGNATURE : AUTHORIZED_SIGNATURES) {
                    if (doSignaturesMatch(callingPackage, AUTHORIZED_SIGNATURE)) {
                        log("\'" + callingPackage + "\' is an authorized calling package...");
                        return true;
                    }
                }
            }
        }
        log("\'" + callingPackage + "\' is not an authorized calling package.");
        return false;
    }

    private boolean doSignaturesMatch(String packageName, Signature signature) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            if (pi.signatures != null && pi.signatures.length == 1 && signature.equals(pi.signatures[0])) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void copyFonts(String pid, String zipFileName) {
        try {
            File cacheDir = new File(Environment.getDataDirectory(), "cache/FontCache/");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File systemFonts = new File("/system/fonts");
            IOUtils.copyFolder(systemFonts.getAbsolutePath(), cacheDir.getAbsolutePath());
        } catch (Exception e) {
            log("Error copying fonts: " + e);
        }
    }

    private void applyThemedSounds(String pid, String zipFileName) {
        try {
            File cacheDir = new File(Environment.getDataDirectory(), "cache/SoundsCache/");
            if (!cacheDir.exists()) cacheDir.mkdirs();
        } catch (Exception e) {
            log("Error applying sounds: " + e);
        }
    }

    private void copyBootAnimation(String fileName) {
        try {
            File source = new File(fileName);
            File dest = new File(IOUtils.SYSTEM_THEME_BOOTANIMATION_PATH);
            IOUtils.bufferedCopy(source, dest);
        } catch (Exception e) {
            log("Error copying bootanimation: " + e);
        }
    }

    private static class Sound {
        String themePath;
        String cachePath;
        String soundName;
        String soundPath;
        int type;

        Sound(String themePath, String cachePath, String soundName, String soundPath) {
            this.themePath = themePath;
            this.cachePath = cachePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
        }

        Sound(String themePath, String cachePath, String soundName, String soundPath, int type) {
            this(themePath, cachePath, soundName, soundPath);
            this.type = type;
        }
    }
}
