/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * Xposed adaptation for Substratum-like theming (internal only)
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class IOUtils implements IXposedHookLoadPackage {
    private static final String TAG = "SubstratumXposed";
    private static final boolean DEBUG = true;
    private static final String SUBSTRATUM_PACKAGE = "projekt.substratum";
    private static final String INTERFACER_PACKAGE = "projekt.interfacer";
    private static final String[] AUTHORIZED_CALLERS = {INTERFACER_PACKAGE, SUBSTRATUM_PACKAGE};
    private static final Signature SUBSTRATUM_SIGNATURE = new Signature("..."); // ваша подпись
    private static final Signature[] AUTHORIZED_SIGNATURES = {SUBSTRATUM_SIGNATURE};
    private static final String MODULE_DATA_DIR = "/data/data/projekt.interfacer/";
    private static final String THEME_CACHE_DIR = MODULE_DATA_DIR + "theme/";
    private static final String FONTS_CACHE_DIR = THEME_CACHE_DIR + "fonts/";
    private static final String AUDIO_CACHE_DIR = THEME_CACHE_DIR + "audio/";
    private static final String BOOTANIMATION_CACHE = THEME_CACHE_DIR + "bootanimation.zip";

    private static List<Sound> SOUNDS = Arrays.asList(
        new Sound(AUDIO_CACHE_DIR + "ui/", "Effect_Tick", "Effect_Tick", RingtoneManager.TYPE_RINGTONE),
        new Sound(AUDIO_CACHE_DIR + "ui/", "lock_sound", "Lock"),
        new Sound(AUDIO_CACHE_DIR + "ui/", "unlock_sound", "Unlock"),
        new Sound(AUDIO_CACHE_DIR + "ui/", "low_battery_sound", "LowBattery"),
        new Sound(AUDIO_CACHE_DIR + "alarms/", "alarm", "alarm", RingtoneManager.TYPE_ALARM),
        new Sound(AUDIO_CACHE_DIR + "notifications/", "notification", "notification", RingtoneManager.TYPE_NOTIFICATION),
        new Sound(AUDIO_CACHE_DIR + "ringtones/", "ringtone", "ringtone", RingtoneManager.TYPE_RINGTONE)
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
                        // Логика копирования файлов (внутренняя)
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

    private static void createDirIfNotExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                log("Could not create directory: " + dirPath);
            }
        }
    }

    private static void copyFolder(File source, File dest) {
        if (!dest.exists()) {
            boolean created = dest.mkdirs();
            if (!created) {
                log("Could not create destination folder: " + dest.getAbsolutePath());
            }
        }
        File[] files = source.listFiles();
        for (File file : files) {
            try {
                File newFile = new File(dest.getAbsolutePath() + File.separator + file.getName());
                if (file.isFile()) {
                    bufferedCopy(file, newFile);
                } else {
                    copyFolder(file, newFile);
                }
            } catch (Exception e) {
                log("Error copying file: " + e);
            }
        }
    }

    private static void bufferedCopy(File source, File dest) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            byte[] buff = new byte[32 * 1024];
            int len;
            while ((len = in.read(buff)) != -1) {
                out.write(buff, 0, len);
            }
        } catch (Exception e) {
            log("Error copying file: " + e);
        }
    }

    private static void unzip(String source, String destination) {
        try (ZipInputStream inputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(source)))) {
            ZipEntry zipEntry;
            int count;
            byte[] buffer = new byte[8192];
            while ((zipEntry = inputStream.getNextEntry()) != null) {
                File file = new File(destination, zipEntry.getName());
                File dir = zipEntry.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to ensure directory: " + dir.getAbsolutePath());
                }
                if (zipEntry.isDirectory()) {
                    continue;
                }
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    while ((count = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, count);
                    }
                }
            }
        } catch (Exception e) {
            log("Error unzipping: " + e);
        }
    }

    private static class Sound {
        String themePath;
        String soundName;
        String soundPath;
        int type;

        Sound(String themePath, String soundName, String soundPath) {
            this.themePath = themePath;
            this.soundName = soundName;
            this.soundPath = soundPath;
        }

        Sound(String themePath, String soundName, String soundPath, int type) {
            this(themePath, soundName, soundPath);
            this.type = type;
        }
    }
}
